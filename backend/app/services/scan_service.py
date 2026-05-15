import json
import os
import uuid

from sqlalchemy.orm import Session

from app.models.ai_feedback_report import AIPrediction, NguonAI, TrangThaiDuyet
from app.models.language import Language
from app.models.object import Object
from app.models.scan_history import ScanHistory
from app.models.translation import Translation, NguonDuLieu
from app.repositories.language_repo import LanguageRepository
from app.repositories.object_repo import ObjectRepository
from app.repositories.translation_repo import TranslationRepository
from app.schemas.common import ScanRequest, ScanResponse, TranslationResponse, ViDuResponse
from app.services.gemini_service import GeminiService
from app.services.tts_service import TTSService
from app.utils.cloudinary_helper import upload_image
from app.utils.timezone import now_vietnam


UPLOAD_DIR = "uploads/scans"


class ScanService:
    def __init__(self):
        self.obj_repo = ObjectRepository()
        self.trans_repo = TranslationRepository()
        self.lang_repo = LanguageRepository()
        self.gemini = GeminiService()
        self.tts = TTSService()

    def process_scan(self, db: Session, request: ScanRequest) -> ScanResponse:
        object_code = request.object_code.lower().strip()
        obj = self.obj_repo.get_by_code(db, object_code)

        if obj:
            translations = self._approved_translations(db, obj.id)
            return ScanResponse(
                source="internal_db",
                object_id=obj.id,
                object_code=obj.ma_doi_tuong,
                category_name=obj.category.ten_danh_muc if obj.category else None,
                translations=[self._to_dto(db, t) for t in translations],
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
        user_id: int | None = None,
        base_url: str | None = None,
    ) -> ScanResponse:
        gemini_result = self.gemini.identify_object(image_bytes)
        if not gemini_result:
            return ScanResponse(source="gemini_failed", object_id=0, object_code="unknown", translations=[])

        if gemini_result.get("_error") == "quota_exceeded":
            return ScanResponse(source="gemini_quota_exceeded", object_id=0, object_code="quota_exceeded", translations=[])

        if gemini_result.get("_error"):
            return ScanResponse(source="gemini_failed", object_id=0, object_code="unknown", translations=[])

        object_code = (gemini_result.get("object_code", "unknown") or "unknown").lower().strip()
        translations_raw = gemini_result.get("translations", [])
        obj = self.obj_repo.get_by_code(db, object_code)

        if object_code == "unknown" or not translations_raw:
            return ScanResponse(source="gemini_failed", object_id=0, object_code="unknown", translations=[])

        if obj:
            existing_translations = self._approved_translations(db, obj.id)
            if existing_translations:
                return ScanResponse(
                    source="internal_db",
                    object_id=obj.id,
                    object_code=obj.ma_doi_tuong,
                    category_name=obj.category.ten_danh_muc if obj.category else None,
                    translations=[self._to_dto(db, t) for t in existing_translations],
                )

        scan, prediction = self._create_pending_review(
            db=db,
            user_id=user_id,
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
            scan_id=scan.id,
            prediction_id=prediction.id,
            pending_review=True,
        )

    def get_translations_by_object_code(self, db: Session, object_code: str):
        obj = self.obj_repo.get_by_code(db, object_code)
        if not obj:
            return None
        translations = self._approved_translations(db, obj.id)
        return [self._to_dto(db, t) for t in translations]

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
        user_id: int | None,
        obj: Object | None,
        object_code: str,
        gemini_result: dict,
        image_bytes: bytes,
        base_url: str | None,
    ) -> tuple[ScanHistory, AIPrediction]:
        image_url = self._save_scan_image(image_bytes, base_url)
        scan = ScanHistory(
            user_id=user_id,
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
            scan_id=scan.id,
            nguon_ai=NguonAI.gemini,
            nhan_du_doan=object_code,
            do_tin_cay=1.0,
            mo_ta=json.dumps(payload, ensure_ascii=False),
            trang_thai=TrangThaiDuyet.cho_duyet,
        )
        db.add(prediction)
        db.flush()
        db.commit()
        return scan, prediction

    def _save_scan_image(self, image_bytes: bytes, base_url: str | None) -> str | None:
        image_url = upload_image(image_bytes)
        if image_url or not base_url:
            return image_url

        os.makedirs(UPLOAD_DIR, exist_ok=True)
        filename = f"{uuid.uuid4().hex}.jpg"
        filepath = os.path.join(UPLOAD_DIR, filename)
        with open(filepath, "wb") as f:
            f.write(image_bytes)
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
            examples = [
                ViDuResponse(
                    id=0,
                    cau_vi_du=str(sentence).strip(),
                    dich_nghia=None,
                    nguon_du_lieu="gemini",
                )
                for sentence in (t_data.get("example_sentences") or [])[:3]
                if sentence and str(sentence).strip()
            ]
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

    def _to_dto(self, db: Session, t: Translation) -> TranslationResponse:
        lang = self.lang_repo.get_by_id(db, t.ngon_ngu_id)
        lang_code = lang.ma_ngon_ngu if lang else "en"
        audio_url = t.am_thanh_url
        if not audio_url:
            audio_url = self.tts.get_audio_url(t.tu_vung, lang_code)
            if audio_url:
                t.am_thanh_url = audio_url
                db.commit()
                db.refresh(t)
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
        )
