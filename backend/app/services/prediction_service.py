"""
Prediction Service
==================
Xử lý quy trình kiểm duyệt AI predictions (approve, reject, alias, split, v.v.)
"""
from typing import Optional

from sqlalchemy import or_
from sqlalchemy.orm import Session

from app.models.ai_feedback_report import AIPrediction, NguonAI, TrangThaiDuyet, VaiTroDuDoan
from app.models.category import Category
from app.models.collection_item import CollectionItem
from app.models.learning_progress import LearningProgress
from app.models.object import Object, NguonTaoDoiTuong
from app.models.object_alias import ObjectAlias
from app.models.object_media import ObjectMedia
from app.models.scan_history import ScanHistory
from app.models.training_image import (
    TrainingImage,
    TrangThaiAnhHuanLuyen,
)
from app.models.translation import Translation, NguonDuLieu
from app.models.example import ViDu
from app.models.language import Language
from app.services.tts_service import TTSService
from app.services.gemini_service import GeminiService
from app.services.prediction_payload import dump_prediction_payload, load_prediction_payload
from app.services.training_image_service import TrainingImageService
from app.repositories.object_repo import normalize_object_code
from app.utils.timezone import now_vietnam
from app.schemas.admin import (
    ApproveRequest, ApproveResponse,
    AliasPredictionRequest, AliasPredictionResponse,
    RejectResponse, SplitToNewObjectResponse,
    PredictionDetailResponse, PredictionListItem,
    VocabPayloadSchema,
)


class PredictionService:
    def __init__(self):
        self.tts = TTSService()
        self.gemini = GeminiService()
        self.training_images = TrainingImageService()

    def _resolve_lang_id(self, db: Session, ma_ngon_ngu: str | None) -> int | None:
        if not ma_ngon_ngu:
            return None
        lang = db.query(Language).filter(Language.ma_ngon_ngu == ma_ngon_ngu.strip().lower()).first()
        return lang.id if lang else None

    def _prediction_payload(self, prediction: AIPrediction) -> dict:
        return load_prediction_payload(prediction.mo_ta, prediction.nhan_du_doan)

    def _is_image_only_prediction(self, prediction: AIPrediction) -> bool:
        return prediction.vai_tro == VaiTroDuDoan.anh_bo_sung

    def _visible_prediction_count(self, db: Session, status: TrangThaiDuyet) -> int:
        return db.query(AIPrediction).filter(
            AIPrediction.trang_thai == status,
            AIPrediction.vai_tro == VaiTroDuDoan.chinh,
        ).count()

    def _append_training_image_note(self, image: TrainingImage, note: str) -> None:
        current = (image.ghi_chu or "").strip()
        if note in current:
            return
        image.ghi_chu = f"{current} | {note}" if current else note

    def _quality_failed(self, image: TrainingImage) -> bool:
        return bool(image.ghi_chu and image.ghi_chu.startswith("quality_fail:"))

    def _approve_training_images_from_prediction(
        self,
        db: Session,
        *,
        lich_su_quet_ids: set[int],
        obj: Object,
        prediction_id: int,
        admin_id: int | None,
        reviewed_at,
    ) -> None:
        if not lich_su_quet_ids:
            return

        training_images = db.query(TrainingImage).filter(
            TrainingImage.lich_su_quet_id.in_(lich_su_quet_ids),
            TrainingImage.thoi_gian_xoa.is_(None),
        ).all()
        for image in training_images:
            image.doi_tuong_id = obj.id
            image.nhan = obj.ma_doi_tuong

            if image.trang_thai == TrangThaiAnhHuanLuyen.tu_choi:
                self._append_training_image_note(
                    image,
                    f"not_auto_approved:already_rejected_via_prediction:{prediction_id}",
                )
                continue

            if self._quality_failed(image):
                self._append_training_image_note(
                    image,
                    f"not_auto_approved:quality_fail_via_prediction:{prediction_id}",
                )
                continue

            image.trang_thai = TrangThaiAnhHuanLuyen.da_duyet
            image.nguoi_duyet_id = admin_id
            image.thoi_gian_duyet = reviewed_at
            self._append_training_image_note(
                image,
                f"approved_via_prediction:{prediction_id}",
            )

    # ------------------------------------------------------------------
    # Truy vấn
    # ------------------------------------------------------------------

    def list_predictions(
        self,
        db: Session,
        trang_thai: Optional[str] = None,
        limit: int = 50,
        offset: int = 0,
        search: Optional[str] = None,
    ) -> list[PredictionListItem]:
        query = db.query(AIPrediction).filter(
            AIPrediction.vai_tro == VaiTroDuDoan.chinh,
        ).order_by(AIPrediction.thoi_gian.desc())
        if trang_thai:
            try:
                query = query.filter(AIPrediction.trang_thai == TrangThaiDuyet(trang_thai))
            except ValueError:
                pass
        if search:
            pattern = f"%{search.strip()}%"
            query = query.filter(or_(
                AIPrediction.nhan_du_doan.ilike(pattern),
                AIPrediction.mo_ta.ilike(pattern),
            ))
        predictions = query.offset(offset).limit(limit).all()
        lich_su_quet_ids = [p.lich_su_quet_id for p in predictions if p.lich_su_quet_id]
        scans = (
            {s.id: s for s in db.query(ScanHistory).filter(ScanHistory.id.in_(lich_su_quet_ids)).all()}
            if lich_su_quet_ids else {}
        )
        items = []
        for p in predictions:
            scan = scans.get(p.lich_su_quet_id)
            item = PredictionListItem(
                id=p.id,
                lich_su_quet_id=p.lich_su_quet_id,
                nhan_du_doan=p.nhan_du_doan,
                do_tin_cay=p.do_tin_cay,
                trang_thai=p.trang_thai.value,
                thoi_gian=p.thoi_gian,
                scan_image_url=scan.url_anh if scan else None,
                vai_tro=p.vai_tro.value if p.vai_tro else None,
                du_doan_goc_id=p.du_doan_goc_id,
            )
            items.append(item)
        return items

    def get_prediction_detail(self, db: Session, prediction_id: int) -> Optional[PredictionDetailResponse]:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return None

        scan = db.query(ScanHistory).filter(ScanHistory.id == p.lich_su_quet_id).first()

        vocab_payload = None
        if p.mo_ta:
            try:
                vocab_payload = VocabPayloadSchema.model_validate(self._prediction_payload(p))
            except Exception:
                pass

        return PredictionDetailResponse(
            id=p.id,
            lich_su_quet_id=p.lich_su_quet_id,
            nhan_du_doan=p.nhan_du_doan,
            do_tin_cay=p.do_tin_cay,
            trang_thai=p.trang_thai.value,
            thoi_gian=p.thoi_gian,
            scan_image_url=scan.url_anh if scan else None,
            vai_tro=p.vai_tro.value if p.vai_tro else None,
            du_doan_goc_id=p.du_doan_goc_id,
            related_images=self._related_prediction_images(db, p),
            vocab_payload=vocab_payload,
        )

    def _related_prediction_images(self, db: Session, prediction: AIPrediction) -> list[dict]:
        root_id = prediction.du_doan_goc_id or prediction.id
        related = (
            db.query(AIPrediction, ScanHistory)
            .join(ScanHistory, AIPrediction.lich_su_quet_id == ScanHistory.id)
            .filter(
                (AIPrediction.id == root_id) |
                (AIPrediction.du_doan_goc_id == root_id)
            )
            .order_by(AIPrediction.vai_tro.asc(), AIPrediction.thoi_gian.asc())
            .all()
        )
        return [
            {
                "prediction_id": pred.id,
                "lich_su_quet_id": scan.id,
                "nguoi_dung_id": scan.nguoi_dung_id,
                "image_url": scan.url_anh,
                "vai_tro": pred.vai_tro.value if pred.vai_tro else None,
                "thoi_gian": scan.thoi_gian,
            }
            for pred, scan in related
            if scan.url_anh
        ]

    # ------------------------------------------------------------------
    # Approve — Insert vào bảng chính
    # ------------------------------------------------------------------

    def approve_prediction(
        self,
        db: Session,
        prediction_id: int,
        request: ApproveRequest,
        admin_id: int | None = None,
    ) -> ApproveResponse:
        """
        Duyệt prediction:
          1. Parse vocab payload từ mo_ta
          2. Upsert DoiTuong
          3. Loop qua từng ngôn ngữ trong translations: upsert Language + BanDich + ViDu
          4. Đánh dấu da_duyet
        """
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return ApproveResponse(success=False, message="Không tìm thấy prediction", prediction_id=prediction_id)

        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return ApproveResponse(
                success=False,
                message=f"Prediction đã ở trạng thái '{p.trang_thai.value}'",
                prediction_id=prediction_id,
            )

        if not p.mo_ta:
            return ApproveResponse(success=False, message="Không có dữ liệu từ vựng (mo_ta trống)", prediction_id=prediction_id)

        raw = self._prediction_payload(p)
        if raw.get("_payload_error"):
            return ApproveResponse(success=False, message="mo_ta không phải JSON hợp lệ", prediction_id=prediction_id)

        object_code = normalize_object_code(raw.get("object_code", p.nhan_du_doan or "unknown"))
        image_url_suggestion = raw.get("image_url_suggestion")
        translations_raw = raw.get("translations", [])
        if not translations_raw:
            return ApproveResponse(success=False, message="Không có translations trong payload", prediction_id=prediction_id)

        reviewed_at = now_vietnam()

        # Upsert DoiTuong (một lần, trước khi loop ngôn ngữ)
        obj = db.query(Object).filter(
            Object.ma_doi_tuong == object_code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            obj = Object(
                ma_doi_tuong=object_code,
                danh_muc_id=request.category_id,
                nguon_tao=NguonTaoDoiTuong.gemini,
                du_doan_ai_id=prediction_id,
                nguoi_duyet_id=admin_id,
                thoi_gian_duyet=reviewed_at if admin_id is not None else None,
            )
            db.add(obj)
            db.flush()
        else:
            if request.category_id is not None:
                obj.danh_muc_id = request.category_id

        # Tự gán danh mục từ Gemini nếu admin chưa chọn và Gemini trả về đúng tên trong DB
        if obj.danh_muc_id is None:
            gemini_category = raw.get("category")
            if gemini_category:
                matched_cat = db.query(Category).filter(
                    Category.ten_danh_muc == gemini_category,
                    Category.thoi_gian_xoa.is_(None),
                ).first()
                if matched_cat:
                    obj.danh_muc_id = matched_cat.id

        training_lich_su_quet_ids: set[int] = set()
        if p.scan:
            p.scan.doi_tuong_id = obj.id
            training_lich_su_quet_ids.add(p.scan.id)

        total_examples_created = 0
        first_translation_id = None
        first_word_name = None

        for idx, t_data in enumerate(translations_raw):
            lang_code = t_data.get("lang_code", "en")

            # Admin override chỉ áp cho translation đầu tiên (single-value override)
            if idx == 0:
                word_name      = request.override_word_name      or t_data.get("word_name", object_code)
                phonetic       = request.override_phonetic       or t_data.get("phonetic")
                part_of_speech = request.override_part_of_speech or t_data.get("part_of_speech")
                definition     = request.override_definition     or t_data.get("definition")
                examples = (
                    request.override_example_sentences
                    if request.override_example_sentences is not None
                    else t_data.get("example_sentences", [])
                )
                first_word_name = word_name
            else:
                word_name      = t_data.get("word_name", object_code)
                phonetic       = t_data.get("phonetic")
                part_of_speech = t_data.get("part_of_speech")
                definition     = t_data.get("definition")
                examples       = t_data.get("example_sentences", [])

            audio_url = self.tts.get_audio_url(word_name, lang_code)

            # Upsert Language
            lang = db.query(Language).filter(Language.ma_ngon_ngu == lang_code).first()
            if not lang:
                lang_names = {"en": "English", "vi": "Vietnamese", "ja": "Japanese", "ko": "Korean"}
                lang = Language(
                    ma_ngon_ngu=lang_code,
                    ten_ngon_ngu=lang_names.get(lang_code, lang_code.upper()),
                    dang_hoat_dong=True,
                )
                db.add(lang)
                db.flush()

            # Upsert BanDich
            existing = db.query(Translation).filter(
                Translation.doi_tuong_id == obj.id,
                Translation.ngon_ngu_id == lang.id,
            ).first()

            examples_created = 0
            if existing:
                existing.tu_vung      = word_name      or existing.tu_vung
                existing.phien_am     = phonetic       or existing.phien_am
                existing.loai_tu      = part_of_speech or existing.loai_tu
                existing.dinh_nghia   = definition     or existing.dinh_nghia
                existing.am_thanh_url = existing.am_thanh_url or audio_url
                existing.da_xac_nhan  = True
                existing_source = (
                    existing.nguon_du_lieu.value
                    if hasattr(existing.nguon_du_lieu, "value")
                    else existing.nguon_du_lieu
                )
                if existing.du_doan_ai_id is None and existing_source == NguonDuLieu.gemini.value:
                    existing.du_doan_ai_id = prediction_id
                if admin_id is not None:
                    existing.nguoi_duyet_id = admin_id
                    existing.thoi_gian_duyet = reviewed_at
                translation = existing
                seen = {
                    (e.cau_vi_du or "").strip().lower()
                    for e in (translation.examples or [])
                    if e.cau_vi_du
                }
                for item in examples[:3]:
                    cau, dich = self._parse_example_item(item)
                    if cau and cau.lower() not in seen:
                        db.add(ViDu(ban_dich_id=translation.id, cau_vi_du=cau, dich_nghia=dich, nguon_du_lieu="gemini"))
                        seen.add(cau.lower())
                        examples_created += 1
            else:
                translation = Translation(
                    doi_tuong_id=obj.id,
                    ngon_ngu_id=lang.id,
                    tu_vung=word_name,
                    phien_am=phonetic,
                    loai_tu=part_of_speech,
                    dinh_nghia=definition,
                    am_thanh_url=audio_url,
                    nguon_du_lieu=NguonDuLieu.gemini,
                    da_xac_nhan=True,
                    du_doan_ai_id=prediction_id,
                    nguoi_duyet_id=admin_id,
                    thoi_gian_duyet=reviewed_at if admin_id is not None else None,
                )
                db.add(translation)
                db.flush()
                for item in examples[:3]:
                    cau, dich = self._parse_example_item(item)
                    if cau:
                        db.add(ViDu(ban_dich_id=translation.id, cau_vi_du=cau, dich_nghia=dich, nguon_du_lieu="gemini"))
                        examples_created += 1

            if first_translation_id is None:
                first_translation_id = translation.id
            total_examples_created += examples_created

        # Auto-enroll tất cả user đã quét object này (pending) vào learning
        nguoi_dung_ids_to_enroll: set[int] = set()

        if p.scan and p.scan.nguoi_dung_id:
            nguoi_dung_ids_to_enroll.add(p.scan.nguoi_dung_id)

        # Tìm các prediction khác cùng object_code còn cho_duyet → auto-resolve luôn
        related_predictions = db.query(AIPrediction).filter(
            AIPrediction.id != prediction_id,
            AIPrediction.nguon_ai == NguonAI.gemini,
            AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
            or_(
                AIPrediction.du_doan_goc_id == prediction_id,
                AIPrediction.nhan_du_doan == object_code,
            ),
        ).all()
        for rel in related_predictions:
            if rel.scan:
                rel.scan.doi_tuong_id = obj.id
                training_lich_su_quet_ids.add(rel.scan.id)
                if rel.scan.nguoi_dung_id:
                    nguoi_dung_ids_to_enroll.add(rel.scan.nguoi_dung_id)
            rel.trang_thai = TrangThaiDuyet.da_duyet

        now = now_vietnam()
        self._approve_training_images_from_prediction(
            db,
            lich_su_quet_ids=training_lich_su_quet_ids,
            obj=obj,
            prediction_id=prediction_id,
            admin_id=admin_id,
            reviewed_at=now,
        )

        for uid in nguoi_dung_ids_to_enroll:
            already = db.query(LearningProgress).filter(
                LearningProgress.nguoi_dung_id == uid,
                LearningProgress.ban_dich_id == first_translation_id,
            ).first()
            if not already:
                db.add(LearningProgress(
                    nguoi_dung_id=uid,
                    ban_dich_id=first_translation_id,
                    ngay_on_tiep=now,
                    lan_on_cuoi=now,
                ))

        # Ghi ObjectMedia nếu có ảnh gợi ý
        if image_url_suggestion:
            media_exists = db.query(ObjectMedia).filter(
                ObjectMedia.doi_tuong_id == obj.id,
                ObjectMedia.url == image_url_suggestion,
                ObjectMedia.thoi_gian_xoa.is_(None),
            ).first()
            if not media_exists:
                db.add(ObjectMedia(
                    doi_tuong_id=obj.id,
                    url=image_url_suggestion,
                    doi_tuong_chinh=True,
                ))

        p.trang_thai = TrangThaiDuyet.da_duyet
        db.commit()

        langs_approved = [t.get("lang_code", "en") for t in translations_raw]
        return ApproveResponse(
            success=True,
            message=f"Đã duyệt '{first_word_name}' — {len(langs_approved)} ngôn ngữ: {', '.join(langs_approved)}",
            prediction_id=prediction_id,
            object_id=obj.id,
            translation_id=first_translation_id,
            examples_created=total_examples_created,
            users_enrolled=len(nguoi_dung_ids_to_enroll),
        )

    # ------------------------------------------------------------------
    # Alias
    # ------------------------------------------------------------------

    def assign_prediction_alias(
        self,
        db: Session,
        prediction_id: int,
        request: AliasPredictionRequest,
        admin_id: int | None = None,
    ) -> AliasPredictionResponse:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return AliasPredictionResponse(success=False, message="Không tìm thấy prediction", prediction_id=prediction_id)

        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return AliasPredictionResponse(
                success=False,
                message=f"Prediction đã ở trạng thái '{p.trang_thai.value}'",
                prediction_id=prediction_id,
            )

        obj = db.query(Object).filter(
            Object.id == request.doi_tuong_id,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            return AliasPredictionResponse(success=False, message="Không tìm thấy đối tượng đích", prediction_id=prediction_id)

        raw = self._prediction_payload(p)
        alias_code = normalize_object_code(
            request.ma_bi_danh
            or raw.get("object_code")
            or p.nhan_du_doan
            or ""
        )
        if not alias_code:
            return AliasPredictionResponse(success=False, message="Mã bí danh không hợp lệ", prediction_id=prediction_id)

        canonical_conflict = db.query(Object).filter(
            Object.id != obj.id,
            Object.ma_doi_tuong == alias_code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if canonical_conflict:
            return AliasPredictionResponse(
                success=False,
                message=f"'{alias_code}' đang là mã đối tượng chính của object khác",
                prediction_id=prediction_id,
            )

        alias = db.query(ObjectAlias).filter(ObjectAlias.ma_bi_danh == alias_code).first()
        if alias and alias.doi_tuong_id != obj.id:
            return AliasPredictionResponse(
                success=False,
                message=f"'{alias_code}' đã là bí danh của object khác",
                prediction_id=prediction_id,
            )

        if alias_code != obj.ma_doi_tuong and not alias:
            alias = ObjectAlias(
                doi_tuong_id=obj.id,
                ma_bi_danh=alias_code,
                ten_hien_thi=(request.ten_hien_thi or "").strip() or None,
                ngon_ngu_id=self._resolve_lang_id(db, request.ngon_ngu),
            )
            db.add(alias)
            db.flush()
        elif alias:
            if request.ten_hien_thi is not None:
                alias.ten_hien_thi = request.ten_hien_thi.strip() or alias.ten_hien_thi
            if request.ngon_ngu is not None:
                alias.ngon_ngu_id = self._resolve_lang_id(db, request.ngon_ngu)

        root_id = p.du_doan_goc_id or p.id
        related_predictions = db.query(AIPrediction).filter(
            AIPrediction.nguon_ai == NguonAI.gemini,
            AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
            or_(
                AIPrediction.id == root_id,
                AIPrediction.du_doan_goc_id == root_id,
                AIPrediction.nhan_du_doan == alias_code,
            ),
        ).all()
        root_prediction = self._approved_root_prediction_for_object(db, obj)

        review_translation = self._pick_review_translation(db, obj.id)
        nguoi_dung_ids_to_enroll: set[int] = set()
        training_lich_su_quet_ids: set[int] = set()
        for rel in related_predictions:
            if rel.scan:
                rel.scan.doi_tuong_id = obj.id
                training_lich_su_quet_ids.add(rel.scan.id)
                if rel.scan.nguoi_dung_id:
                    nguoi_dung_ids_to_enroll.add(rel.scan.nguoi_dung_id)

            payload = self._prediction_payload(rel)
            payload["ma_bi_danh"] = alias_code
            payload["ma_doi_tuong_chinh"] = obj.ma_doi_tuong
            payload["doi_tuong_chinh_id"] = obj.id
            rel.mo_ta = dump_prediction_payload(payload, rel.nhan_du_doan)
            rel.nhan_du_doan = obj.ma_doi_tuong
            if root_prediction and rel.id != root_prediction.id:
                rel.vai_tro = VaiTroDuDoan.anh_bo_sung
                rel.du_doan_goc_id = root_prediction.id
            rel.trang_thai = TrangThaiDuyet.da_duyet

        users_enrolled = 0
        now = now_vietnam()
        self._approve_training_images_from_prediction(
            db,
            lich_su_quet_ids=training_lich_su_quet_ids,
            obj=obj,
            prediction_id=prediction_id,
            admin_id=admin_id,
            reviewed_at=now,
        )

        if review_translation:
            for uid in nguoi_dung_ids_to_enroll:
                already = db.query(LearningProgress).filter(
                    LearningProgress.nguoi_dung_id == uid,
                    LearningProgress.ban_dich_id == review_translation.id,
                ).first()
                if not already:
                    db.add(LearningProgress(
                        nguoi_dung_id=uid,
                        ban_dich_id=review_translation.id,
                        ngay_on_tiep=now,
                        lan_on_cuoi=now,
                    ))
                    users_enrolled += 1

        db.commit()

        return AliasPredictionResponse(
            success=True,
            message=f"Đã gán '{alias_code}' làm bí danh của '{obj.ma_doi_tuong}'",
            prediction_id=prediction_id,
            doi_tuong_id=obj.id,
            bi_danh_id=alias.id if alias else None,
            ma_bi_danh=alias_code,
            ma_doi_tuong=obj.ma_doi_tuong,
            users_enrolled=users_enrolled,
        )

    def detach_review_image(self, db: Session, prediction_id: int) -> dict:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return {"success": False, "message": "Không tìm thấy prediction", "prediction_id": prediction_id}
        if p.vai_tro != VaiTroDuDoan.anh_bo_sung:
            return {"success": False, "message": "Chỉ có thể bỏ ảnh bổ sung, không bỏ ảnh chính", "prediction_id": prediction_id}
        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return {"success": False, "message": "Chỉ có thể thao tác ảnh đang chờ duyệt", "prediction_id": prediction_id}

        root_id = p.du_doan_goc_id
        if p.scan:
            p.scan.doi_tuong_id = None

        payload = self._prediction_payload(p)
        payload["review_kind"] = "detached_image"
        payload["detached_from_prediction_id"] = root_id
        p.mo_ta = dump_prediction_payload(payload, p.nhan_du_doan)
        p.du_doan_goc_id = None
        p.trang_thai = TrangThaiDuyet.tu_choi
        db.commit()
        return {
            "success": True,
            "message": "Đã bỏ ảnh khỏi nhóm duyệt",
            "prediction_id": prediction_id,
            "root_prediction_id": root_id,
        }

    def reassign_review_image(self, db: Session, prediction_id: int, target_object_code: str) -> dict:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return {"success": False, "message": "Không tìm thấy prediction", "prediction_id": prediction_id}
        if p.vai_tro != VaiTroDuDoan.anh_bo_sung:
            return {"success": False, "message": "Chỉ có thể chuyển ảnh bổ sung, không chuyển ảnh chính", "prediction_id": prediction_id}
        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return {"success": False, "message": "Chỉ có thể thao tác ảnh đang chờ duyệt", "prediction_id": prediction_id}

        target_code = normalize_object_code(target_object_code)
        obj = db.query(Object).filter(
            Object.ma_doi_tuong == target_code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            return {"success": False, "message": f"Không tìm thấy đối tượng '{target_object_code}'", "prediction_id": prediction_id}

        root_id = p.du_doan_goc_id
        target_root = self._approved_root_prediction_for_object(db, obj)
        if p.scan:
            p.scan.doi_tuong_id = obj.id

        payload = self._prediction_payload(p)
        payload["review_kind"] = "reassigned_image"
        payload["reassigned_from_prediction_id"] = root_id
        payload["ma_doi_tuong_chinh"] = obj.ma_doi_tuong
        payload["doi_tuong_chinh_id"] = obj.id
        p.mo_ta = dump_prediction_payload(payload, p.nhan_du_doan)
        p.nhan_du_doan = obj.ma_doi_tuong
        p.du_doan_goc_id = target_root.id if target_root and target_root.id != p.id else None
        p.trang_thai = TrangThaiDuyet.da_duyet

        review_translation = self._pick_review_translation(db, obj.id)
        users_enrolled = 0
        if review_translation and p.scan and p.scan.nguoi_dung_id:
            already = db.query(LearningProgress).filter(
                LearningProgress.nguoi_dung_id == p.scan.nguoi_dung_id,
                LearningProgress.ban_dich_id == review_translation.id,
            ).first()
            if not already:
                now = now_vietnam()
                db.add(LearningProgress(
                    nguoi_dung_id=p.scan.nguoi_dung_id,
                    ban_dich_id=review_translation.id,
                    ngay_on_tiep=now,
                    lan_on_cuoi=now,
                ))
                users_enrolled = 1

        db.commit()
        return {
            "success": True,
            "message": f"Đã chuyển ảnh sang '{obj.ma_doi_tuong}'",
            "prediction_id": prediction_id,
            "root_prediction_id": root_id,
            "target_object_code": obj.ma_doi_tuong,
            "users_enrolled": users_enrolled,
        }

    def _pick_review_translation(self, db: Session, object_id: int) -> Translation | None:
        translations = (
            db.query(Translation)
            .outerjoin(Language, Language.id == Translation.ngon_ngu_id)
            .filter(
                Translation.doi_tuong_id == object_id,
                Translation.thoi_gian_xoa.is_(None),
                Translation.da_xac_nhan.is_(True),
            )
            .order_by((Language.ma_ngon_ngu == "en").desc(), Translation.id.asc())
            .all()
        )
        return translations[0] if translations else None

    def _approved_root_prediction_for_object(self, db: Session, obj: Object) -> AIPrediction | None:
        predictions = (
            db.query(AIPrediction)
            .filter(
                AIPrediction.nguon_ai == NguonAI.gemini,
                AIPrediction.trang_thai == TrangThaiDuyet.da_duyet,
                AIPrediction.vai_tro == VaiTroDuDoan.chinh,
                AIPrediction.nhan_du_doan == obj.ma_doi_tuong,
            )
            .order_by(AIPrediction.thoi_gian.asc())
            .all()
        )
        for prediction in predictions:
            payload = self._prediction_payload(prediction)
            payload_alias = normalize_object_code(payload.get("ma_bi_danh") or "")
            payload_code = normalize_object_code(payload.get("object_code") or "")
            if not payload_alias and (not payload_code or payload_code == obj.ma_doi_tuong):
                return prediction
        return predictions[0] if predictions else None

    def _rewire_alias_predictions(self, db: Session, alias_code: str, obj: Object) -> int:
        candidates = (
            db.query(AIPrediction)
            .filter(
                AIPrediction.nguon_ai == NguonAI.gemini,
                AIPrediction.mo_ta.contains(alias_code),
            )
            .all()
        )
        root_prediction = self._approved_root_prediction_for_object(db, obj)
        changed = 0
        for pred in candidates:
            payload = self._prediction_payload(pred)
            payload_alias = normalize_object_code(payload.get("ma_bi_danh") or "")
            payload_code = normalize_object_code(payload.get("object_code") or "")
            if payload_alias != alias_code and payload_code != alias_code:
                continue

            payload["ma_bi_danh"] = alias_code
            payload["ma_doi_tuong_chinh"] = obj.ma_doi_tuong
            payload["doi_tuong_chinh_id"] = obj.id
            pred.mo_ta = dump_prediction_payload(payload, pred.nhan_du_doan)
            pred.nhan_du_doan = obj.ma_doi_tuong
            if root_prediction and pred.id != root_prediction.id:
                pred.vai_tro = VaiTroDuDoan.anh_bo_sung
                pred.du_doan_goc_id = root_prediction.id

            scan = pred.scan or (
                db.query(ScanHistory).filter(ScanHistory.id == pred.lich_su_quet_id).first()
                if pred.lich_su_quet_id else None
            )
            if scan:
                scan.doi_tuong_id = obj.id
            changed += 1
        return changed

    # ------------------------------------------------------------------
    # Reject
    # ------------------------------------------------------------------

    def reject_prediction(self, db: Session, prediction_id: int, admin_id: int | None = None) -> RejectResponse:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return RejectResponse(success=False, message="Không tìm thấy prediction", prediction_id=prediction_id)

        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return RejectResponse(
                success=False,
                message=f"Prediction đã ở trạng thái '{p.trang_thai.value}'",
                prediction_id=prediction_id,
            )

        raw = self._prediction_payload(p)
        deleted_translations = 0
        deleted_object = False
        if p.mo_ta and not raw.get("_payload_error"):
            for translation_id in raw.get("temp_translation_ids") or []:
                translation = db.query(Translation).filter(Translation.id == translation_id).first()
                if translation and not translation.da_xac_nhan:
                    db.query(CollectionItem).filter(CollectionItem.ban_dich_id == translation.id).delete(synchronize_session=False)
                    db.query(LearningProgress).filter(LearningProgress.ban_dich_id == translation.id).delete(synchronize_session=False)
                    db.delete(translation)
                    deleted_translations += 1

            temp_object_id = raw.get("temp_object_id")
            if temp_object_id:
                obj = db.query(Object).filter(Object.id == temp_object_id).first()
                if obj:
                    approved_count = db.query(Translation).filter(
                        Translation.doi_tuong_id == obj.id,
                        Translation.da_xac_nhan.is_(True),
                    ).count()
                    other_predictions = db.query(AIPrediction).filter(
                        AIPrediction.id != p.id,
                        AIPrediction.nhan_du_doan == obj.ma_doi_tuong,
                    ).count()
                    if approved_count == 0 and other_predictions == 0:
                        db.delete(obj)
                        deleted_object = True

        object_code = (raw.get("object_code") or p.nhan_du_doan or "").lower()
        related_rejected = 0
        rejected_lich_su_quet_ids: set[int] = set()
        if p.lich_su_quet_id:
            rejected_lich_su_quet_ids.add(p.lich_su_quet_id)
        if object_code:
            related_predictions = db.query(AIPrediction).filter(
                AIPrediction.id != prediction_id,
                AIPrediction.nguon_ai == NguonAI.gemini,
                AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
                or_(
                    AIPrediction.du_doan_goc_id == prediction_id,
                    AIPrediction.nhan_du_doan == object_code,
                ),
            ).all()
            for rel in related_predictions:
                rel.trang_thai = TrangThaiDuyet.tu_choi
                if rel.lich_su_quet_id:
                    rejected_lich_su_quet_ids.add(rel.lich_su_quet_id)
                related_rejected += 1

        p.trang_thai = TrangThaiDuyet.tu_choi
        if rejected_lich_su_quet_ids:
            now = now_vietnam()
            training_images = db.query(TrainingImage).filter(
                TrainingImage.lich_su_quet_id.in_(rejected_lich_su_quet_ids),
                TrainingImage.thoi_gian_xoa.is_(None),
            ).all()
            for image in training_images:
                image.trang_thai = TrangThaiAnhHuanLuyen.tu_choi
                image.nguoi_duyet_id = admin_id
                image.thoi_gian_duyet = now
        db.commit()

        details = []
        if deleted_translations:
            details.append(f"xóa {deleted_translations} bản dịch tạm")
        if deleted_object:
            details.append("xóa đối tượng tạm")
        if related_rejected:
            details.append(f"từ chối {related_rejected} prediction liên quan")
        suffix = f" ({', '.join(details)})" if details else ""
        return RejectResponse(success=True, message=f"Đã từ chối prediction #{prediction_id}{suffix}", prediction_id=prediction_id)

    # ------------------------------------------------------------------
    # Split to new object
    # ------------------------------------------------------------------

    def split_to_new_object(
        self,
        db: Session,
        prediction_id: int,
        new_object_code: str,
    ) -> SplitToNewObjectResponse:
        """
        Tách một ảnh ra khỏi nhóm kiểm duyệt hiện tại và tạo một prediction mới
        với object_code do admin chỉ định.

        Hữu ích khi Gemini nhận diện sai nhãn (vd: quét đậu phụ nhưng trả kết quả là cục tẩy)
        — admin có thể cứu ảnh đó thành training data cho đối tượng đúng.
        """
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return SplitToNewObjectResponse(
                success=False, message="Không tìm thấy prediction", old_prediction_id=prediction_id
            )
        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return SplitToNewObjectResponse(
                success=False,
                message=f"Prediction đã ở trạng thái '{p.trang_thai.value}', không thể tách",
                old_prediction_id=prediction_id,
            )

        new_code = normalize_object_code(new_object_code)
        if not new_code or new_code == "unknown":
            return SplitToNewObjectResponse(
                success=False, message="Mã đối tượng mới không hợp lệ", old_prediction_id=prediction_id
            )

        # Sinh vocab mới cho object_code đúng bằng Gemini (text prompt)
        vocab_result = self.gemini.generate_vocab_for_object_code(new_code)
        vocab_generated = "_error" not in vocab_result

        if vocab_generated:
            vocab_result["object_code"] = new_code
            new_mo_ta = dump_prediction_payload(vocab_result, new_code)
        else:
            # Tạo payload tối thiểu để admin có thể approve sau
            new_mo_ta = dump_prediction_payload({
                "object_code": new_code,
                "category": None,
                "translations": [],
                "_admin_split": True,
                "_vocab_error": vocab_result.get("_message", "unknown"),
            }, new_code)

        # Tạo prediction mới cho object đúng
        new_pred = AIPrediction(
            lich_su_quet_id=p.lich_su_quet_id,
            nguon_ai=NguonAI.gemini,
            nhan_du_doan=new_code,
            do_tin_cay=p.do_tin_cay,
            mo_ta=new_mo_ta,
            trang_thai=TrangThaiDuyet.cho_duyet,
            vai_tro=VaiTroDuDoan.chinh,
            du_doan_goc_id=None,
        )
        db.add(new_pred)
        db.flush()  # lấy new_pred.id

        # Nếu tách prediction chính, chuyển ảnh bổ sung sang prediction mới
        if p.vai_tro == VaiTroDuDoan.chinh:
            supplementary_preds = db.query(AIPrediction).filter(
                AIPrediction.du_doan_goc_id == p.id,
                AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
            ).all()
            for supp in supplementary_preds:
                supp.du_doan_goc_id = new_pred.id
                supp.nhan_du_doan = new_code
                if supp.scan:
                    supp.scan.doi_tuong_id = None

        # Ghi audit trail vào prediction cũ rồi đánh dấu tu_choi
        old_payload = self._prediction_payload(p)
        old_payload["review_kind"] = "split_to_new_object"
        old_payload["original_object_code"] = p.nhan_du_doan
        old_payload["split_to_new_prediction_id"] = new_pred.id
        old_payload["corrected_by_admin"] = True
        p.mo_ta = dump_prediction_payload(old_payload, p.nhan_du_doan)
        p.trang_thai = TrangThaiDuyet.tu_choi
        p.du_doan_goc_id = None

        # Tháo liên kết scan khỏi đối tượng cũ
        if p.scan:
            p.scan.doi_tuong_id = None

        db.commit()
        return SplitToNewObjectResponse(
            success=True,
            message=f"Đã tách ảnh thành prediction mới cho '{new_code}'",
            old_prediction_id=prediction_id,
            new_prediction_id=new_pred.id,
            new_object_code=new_code,
            vocab_generated=vocab_generated,
        )

    # ------------------------------------------------------------------
    # Static helpers (dùng trong approve và create_translation)
    # ------------------------------------------------------------------

    @staticmethod
    def _parse_example_item(item) -> tuple[str, str | None]:
        """Parse example từ Gemini (dict {'en','vi'}) hoặc string thuần."""
        if isinstance(item, dict):
            return (item.get("en") or "").strip(), (item.get("vi") or "").strip() or None
        s = (item or "").strip()
        return s, None

    @staticmethod
    def _add_example(db: Session, translation_id: int, line: str) -> None:
        """Parse 'sentence | dịch nghĩa' hoặc chỉ 'sentence', lưu vào ViDu."""
        line = line.strip()
        if not line:
            return
        if '|' in line:
            parts = line.split('|', 1)
            cau_vi_du = parts[0].strip()
            dich_nghia = parts[1].strip() or None
        else:
            cau_vi_du = line
            dich_nghia = None
        if cau_vi_du:
            db.add(ViDu(ban_dich_id=translation_id, cau_vi_du=cau_vi_du, dich_nghia=dich_nghia, nguon_du_lieu="thu_cong"))
