import logging
import uuid
from pathlib import Path

from sqlalchemy.orm import Session

from app.models.ai_feedback_report import AIPrediction, NguonAI, TrangThaiDuyet, VaiTroDuDoan
from app.models.language import Language
from app.models.object import Object
from app.models.object_alias import ObjectAlias
from app.models.scan_history import ScanHistory
from app.models.translation import Translation, NguonDuLieu
from app.repositories.language_repo import LanguageRepository
from app.repositories.object_repo import ObjectRepository, normalize_object_code
from app.repositories.translation_repo import TranslationRepository
from app.schemas.common import ScanRequest, ScanResponse, TranslationResponse, ViDuResponse
from app.services.gemini_service import GeminiService
from app.services.prediction_payload import dump_prediction_payload, load_prediction_payload
from app.services.training_image_service import TrainingImageService
from app.services.tts_service import TTSService
from app.utils.cloudinary_helper import upload_image
from app.utils.image import check_image_quality
from app.utils.timezone import now_vietnam


UPLOAD_DIR = Path(__file__).resolve().parents[2] / "uploads" / "scans"
logger = logging.getLogger(__name__)

# Các class YOLO custom đã train — không cần thêm training data
YOLO_CUSTOM_CLASSES = {
    "backpack", "calculator", "eraser", "notebook", "pen",
    "pencil", "ruler", "scissors", "sharpener", "water_bottle",
}

# Các class COCO model đã biết — không cần thêm training data
COCO_CLASSES = {
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
    "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
    "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
    "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
    "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
    "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
    "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
    "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
    "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
    "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
    "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
    "toothbrush",
}

YOLO_KNOWN_CLASSES = YOLO_CUSTOM_CLASSES | COCO_CLASSES


class ScanService:
    def __init__(self):
        self.obj_repo = ObjectRepository()
        self.trans_repo = TranslationRepository()
        self.lang_repo = LanguageRepository()
        self.gemini = GeminiService()
        self.tts = TTSService()
        self.training_images = TrainingImageService()

    def _get_aliases(self, db: Session, object_id: int) -> list[str]:
        rows = db.query(ObjectAlias).filter(ObjectAlias.doi_tuong_id == object_id).all()
        return [a.ten_hien_thi or a.ma_bi_danh for a in rows]

    def process_scan(self, db: Session, request: ScanRequest) -> ScanResponse:
        object_code = normalize_object_code(request.object_code)
        obj = self.obj_repo.get_by_code(db, object_code)

        if obj:
            translations = self._approved_translations(db, obj.id)
            return ScanResponse(
                source="internal_db",
                object_id=obj.id,
                object_code=obj.ma_doi_tuong,
                category_name=obj.category.ten_danh_muc if obj.category else None,
                translations=self._to_dtos(db, translations),
                aliases=self._get_aliases(db, obj.id),
            )

        return ScanResponse(
            source="not_found",
            object_id=0,
            object_code=object_code,
            translations=[],
        )

    def process_scan_image(
        self,
        db: Session,
        image_bytes: bytes,
        nguoi_dung_id: int | None = None,
        base_url: str | None = None,
    ) -> ScanResponse:
        # Bước 1: quick scan — model nhẹ, quota cao
        quick = self.gemini.identify_object_quick(image_bytes)

        if quick.get("_error") == "quota_exceeded":
            return ScanResponse(source="gemini_quota_exceeded", object_id=0, object_code="quota_exceeded", translations=[])
        if quick.get("_error"):
            return ScanResponse(source="gemini_failed", object_id=0, object_code="unknown", translations=[])

        object_code = normalize_object_code(quick.get("object_code", "unknown") or "unknown")
        confidence = quick.get("confidence", 0)
        is_clear = quick.get("is_clear", True)

        if object_code == "unknown":
            return ScanResponse(source="gemini_failed", object_id=0, object_code="unknown", translations=[])

        # Nếu nhận diện mơ hồ → leo lên model mạnh hơn để xác nhận + lấy vocab luôn
        if confidence < 80 or not is_clear:
            logger.info("Low confidence (%d, clear=%s), escalating to full identify", confidence, is_clear)
            gemini_result = self.gemini.identify_object(image_bytes)
            if gemini_result.get("_error"):
                return ScanResponse(source="gemini_failed", object_id=0, object_code="unknown", translations=[])
            object_code = normalize_object_code(gemini_result.get("object_code", "unknown") or "unknown")
            if object_code == "unknown":
                return ScanResponse(source="gemini_failed", object_id=0, object_code="unknown", translations=[])
        else:
            gemini_result = None  # confidence tốt, chưa cần vocab — check DB trước

        obj = self.obj_repo.get_by_code(db, object_code)

        if obj:
            object_code = obj.ma_doi_tuong
            existing_translations = self._approved_translations(db, obj.id)
            if existing_translations:
                return ScanResponse(
                    source="internal_db",
                    object_id=obj.id,
                    object_code=obj.ma_doi_tuong,
                    category_name=obj.category.ten_danh_muc if obj.category else None,
                    translations=self._to_dtos(db, existing_translations),
                    aliases=self._get_aliases(db, obj.id),
                )

        # DB miss và chưa có vocab → gọi full identify để sinh vocab
        if gemini_result is None:
            gemini_result = self.gemini.identify_object(image_bytes)
            if gemini_result.get("_error"):
                return ScanResponse(source="gemini_failed", object_id=0, object_code="unknown", translations=[])

        translations_raw = gemini_result.get("translations", [])
        if not translations_raw:
            return ScanResponse(source="gemini_failed", object_id=0, object_code="unknown", translations=[])

        existing_pending = self._find_pending_review_prediction(db, object_code)
        if existing_pending:
            scan, prediction, source_payload = self._create_image_only_pending_scan(
                db=db,
                nguoi_dung_id=nguoi_dung_id,
                obj=obj,
                object_code=object_code,
                source_prediction=existing_pending,
                image_bytes=image_bytes,
                base_url=base_url,
            )
            source_translations = source_payload.get("translations") or translations_raw
            return ScanResponse(
                source="gemini_pending_review",
                object_id=obj.id if obj else 0,
                object_code=obj.ma_doi_tuong if obj else object_code,
                category_name=obj.category.ten_danh_muc if obj and obj.category else source_payload.get("category"),
                translations=self._pending_translation_dtos(
                    source_translations,
                    object_code=obj.ma_doi_tuong if obj else object_code,
                    object_id=obj.id if obj else 0,
                ),
                lich_su_quet_id=scan.id,
                prediction_id=prediction.id,
                pending_review=True,
            )

        scan, prediction = self._create_pending_review(
            db=db,
            nguoi_dung_id=nguoi_dung_id,
            obj=obj,
            object_code=object_code,
            gemini_result=gemini_result,
            image_bytes=image_bytes,
            base_url=base_url,
        )

        return ScanResponse(
            source="gemini_pending_review",
            object_id=obj.id if obj else 0,
            object_code=obj.ma_doi_tuong if obj else object_code,
            category_name=obj.category.ten_danh_muc if obj and obj.category else gemini_result.get("category"),
            translations=self._pending_translation_dtos(
                translations_raw,
                object_code=obj.ma_doi_tuong if obj else object_code,
                object_id=obj.id if obj else 0,
            ),
            lich_su_quet_id=scan.id,
            prediction_id=prediction.id,
            pending_review=True,
        )

    def get_translations_by_object_code(self, db: Session, object_code: str):
        obj = self.obj_repo.get_by_code(db, object_code)
        if not obj:
            return None
        translations = self._approved_translations(db, obj.id)
        return self._to_dtos(db, translations)

    def _approved_translations(self, db: Session, object_id: int) -> list[Translation]:
        return [
            t for t in self.trans_repo.get_by_object_id(db, object_id)
            if getattr(t, "thoi_gian_xoa", None) is None and bool(t.da_xac_nhan)
        ]

    def _ensure_language(self, db: Session, lang_code: str) -> Language:
        lang = db.query(Language).filter(Language.ma_ngon_ngu == lang_code).first()
        if not lang:
            lang_names = {"en": "English", "vi": "Vietnamese"}
            lang = Language(
                ma_ngon_ngu=lang_code,
                ten_ngon_ngu=lang_names.get(lang_code, lang_code.upper()),
                dang_hoat_dong=True,
            )
            db.add(lang)
            db.flush()
        return lang

    def _create_pending_review(
        self,
        db: Session,
        nguoi_dung_id: int | None,
        obj: Object | None,
        object_code: str,
        gemini_result: dict,
        image_bytes: bytes,
        base_url: str | None,
    ) -> tuple[ScanHistory, AIPrediction]:
        _, quality_reason, quality_score = check_image_quality(image_bytes)
        image_url = self._save_scan_image(image_bytes, base_url)
        scan = ScanHistory(
            nguoi_dung_id=nguoi_dung_id,
            doi_tuong_id=obj.id if obj else None,
            do_tin_cay=1.0,
            url_anh=image_url,
            thoi_gian=now_vietnam(),
        )
        db.add(scan)
        db.flush()

        payload = dict(gemini_result)
        payload["source"] = "scan_image"
        payload["scan_image_url"] = image_url
        if obj:
            payload["existing_object_id"] = obj.id

        prediction = AIPrediction(
            lich_su_quet_id=scan.id,
            nguon_ai=NguonAI.gemini,
            nhan_du_doan=object_code,
            do_tin_cay=1.0,
            mo_ta=dump_prediction_payload(payload, object_code),
            trang_thai=TrangThaiDuyet.cho_duyet,
            vai_tro=VaiTroDuDoan.chinh,
        )
        db.add(prediction)
        db.flush()
        self.training_images.create_candidate(
            db,
            lich_su_quet_id=scan.id,
            du_doan_id=prediction.id,
            doi_tuong_id=obj.id if obj else None,
            url_anh=image_url,
            nhan=object_code,
            nguon_du_lieu="gemini",
            do_tin_cay=1.0,
            ghi_chu=quality_reason,
            diem_chat_luong=quality_score,
        )
        db.commit()
        return scan, prediction

    def _find_pending_review_prediction(self, db: Session, object_code: str) -> AIPrediction | None:
        predictions = (
            db.query(AIPrediction)
            .filter(
                AIPrediction.nguon_ai == NguonAI.gemini,
                AIPrediction.nhan_du_doan == object_code,
                AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
                AIPrediction.vai_tro == VaiTroDuDoan.chinh,
            )
            .order_by(AIPrediction.thoi_gian.asc())
            .yield_per(100)
        )
        for prediction in predictions:
            payload = self._prediction_payload(prediction)
            if payload.get("translations"):
                return prediction
        return None

    def _create_image_only_pending_scan(
        self,
        db: Session,
        nguoi_dung_id: int | None,
        obj: Object | None,
        object_code: str,
        source_prediction: AIPrediction,
        image_bytes: bytes,
        base_url: str | None,
    ) -> tuple[ScanHistory, AIPrediction, dict]:
        _, quality_reason, quality_score = check_image_quality(image_bytes)
        image_url = self._save_scan_image(image_bytes, base_url)
        source_payload = self._prediction_payload(source_prediction)
        scan = ScanHistory(
            nguoi_dung_id=nguoi_dung_id,
            doi_tuong_id=obj.id if obj else None,
            do_tin_cay=1.0,
            url_anh=image_url,
            thoi_gian=now_vietnam(),
        )
        db.add(scan)
        db.flush()

        payload = dict(source_payload)
        payload["source"] = "scan_image_duplicate"
        payload["review_kind"] = "image_only"
        payload["duplicate_of_prediction_id"] = source_prediction.id
        payload["vai_tro"] = VaiTroDuDoan.anh_bo_sung.value
        payload["du_doan_goc_id"] = source_prediction.id
        payload["scan_image_url"] = image_url
        payload["object_code"] = object_code
        if obj:
            payload["existing_object_id"] = obj.id

        prediction = AIPrediction(
            lich_su_quet_id=scan.id,
            nguon_ai=NguonAI.gemini,
            nhan_du_doan=object_code,
            do_tin_cay=1.0,
            mo_ta=dump_prediction_payload(payload, object_code),
            trang_thai=TrangThaiDuyet.cho_duyet,
            vai_tro=VaiTroDuDoan.anh_bo_sung,
            du_doan_goc_id=source_prediction.id,
        )
        db.add(prediction)
        db.flush()
        self.training_images.create_candidate(
            db,
            lich_su_quet_id=scan.id,
            du_doan_id=prediction.id,
            doi_tuong_id=obj.id if obj else None,
            url_anh=image_url,
            nhan=object_code,
            nguon_du_lieu="gemini",
            do_tin_cay=1.0,
            ghi_chu=quality_reason,
            diem_chat_luong=quality_score,
        )
        db.commit()
        return scan, prediction, source_payload

    def _prediction_payload(self, prediction: AIPrediction) -> dict:
        return load_prediction_payload(prediction.mo_ta, prediction.nhan_du_doan)

    def _save_scan_image(self, image_bytes: bytes, base_url: str | None) -> str | None:
        image_url = upload_image(image_bytes)
        if image_url or not base_url:
            return image_url

        UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
        filename = f"{uuid.uuid4().hex}.jpg"
        filepath = UPLOAD_DIR / filename
        filepath.write_bytes(image_bytes)
        return f"{base_url}/uploads/scans/{filename}"

    def _pending_translation_dtos(
        self,
        translations_raw: list,
        object_code: str,
        object_id: int,
    ) -> list[TranslationResponse]:
        translations: list[TranslationResponse] = []
        for t_data in translations_raw:
            if not isinstance(t_data, dict):
                continue

            lang_code = t_data.get("lang_code") or "en"
            word_name = t_data.get("word_name") or object_code.replace("_", " ").title()
            examples = []
            for sentence in (t_data.get("example_sentences") or [])[:3]:
                if not sentence:
                    continue
                if isinstance(sentence, dict):
                    en_text = str(sentence.get("en") or "").strip()
                    vi_text = str(sentence.get("vi") or "").strip() or None
                else:
                    en_text = str(sentence).strip()
                    vi_text = None
                if en_text:
                    examples.append(ViDuResponse(
                        id=0,
                        cau_vi_du=en_text,
                        dich_nghia=vi_text,
                        nguon_du_lieu="gemini",
                    ))
            translations.append(TranslationResponse(
                id=0,
                object_id=object_id,
                language_id=0,
                language_code=lang_code,
                language_name=self._language_name(lang_code),
                word_name=word_name,
                phonetic=t_data.get("phonetic"),
                part_of_speech=t_data.get("part_of_speech"),
                definition=t_data.get("definition"),
                examples=examples,
                audio_url=self.tts.get_audio_url(word_name, lang_code),
                data_source="gemini",
            ))
        return translations

    def _language_name(self, lang_code: str) -> str:
        names = {"en": "English", "vi": "Vietnamese", "ja": "Japanese", "ko": "Korean"}
        return names.get((lang_code or "").lower(), (lang_code or "en").upper())

    def _to_dtos(self, db: Session, translations: list[Translation]) -> list[TranslationResponse]:
        responses: list[TranslationResponse] = []
        audio_updated = False
        for translation in translations:
            response, updated = self._to_dto(db, translation)
            responses.append(response)
            audio_updated = audio_updated or updated
        if audio_updated:
            db.commit()
        return responses

    def _to_dto(self, db: Session, t: Translation) -> tuple[TranslationResponse, bool]:
        lang = self.lang_repo.get_by_id(db, t.ngon_ngu_id)
        lang_code = lang.ma_ngon_ngu if lang else "en"
        audio_url = t.am_thanh_url
        audio_updated = False
        if not audio_url:
            audio_url = self.tts.get_audio_url(t.tu_vung, lang_code)
            if audio_url:
                t.am_thanh_url = audio_url
                audio_updated = True
        examples = [
            ViDuResponse(id=e.id, cau_vi_du=e.cau_vi_du, dich_nghia=e.dich_nghia, nguon_du_lieu=e.nguon_du_lieu)
            for e in sorted((t.examples or []), key=lambda item: item.id or 0)[:3]
        ]
        return TranslationResponse(
            id=t.id,
            object_id=t.doi_tuong_id,
            language_id=t.ngon_ngu_id,
            language_code=lang.ma_ngon_ngu if lang else "",
            language_name=lang.ten_ngon_ngu if lang else "",
            word_name=t.tu_vung,
            phonetic=t.phien_am,
            part_of_speech=t.loai_tu,
            definition=t.dinh_nghia,
            examples=examples,
            audio_url=audio_url,
            data_source=t.nguon_du_lieu.value if isinstance(t.nguon_du_lieu, NguonDuLieu) else (str(t.nguon_du_lieu) if t.nguon_du_lieu else None),
        ), audio_updated
