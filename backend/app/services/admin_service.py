"""
Admin Moderation Service
========================
Xử lý quy trình kiểm duyệt từ vựng do Gemini đề xuất.

Flow:
  DuDoanAI (cho_duyet)
      ↓ admin approve
  DoiTuong (upsert) + BanDich (insert) + ViDu ×3 (insert)
  DuDoanAI.trang_thai = da_duyet

  Hoặc:
      ↓ admin reject
  DuDoanAI.trang_thai = tu_choi
"""
import json
from typing import Optional, List

from sqlalchemy.orm import Session

from app.models.ai_feedback_report import AIPrediction, TrangThaiDuyet
from app.models.category import Category
from app.models.collection_item import CollectionItem
from app.models.learning_progress import LearningProgress
from app.models.object import Object
from app.models.object_media import ObjectMedia
from app.models.scan_history import ScanHistory
from app.models.translation import Translation, NguonDuLieu
from app.models.example import ViDu
from app.models.language import Language
from app.models.user import User
from app.models.review_log import ReviewLog
from app.services.tts_service import TTSService
from app.utils.timezone import now_vietnam
from app.utils.security import hash_password
from app.schemas.admin import (
    ApproveRequest, ApproveResponse,
    RejectResponse,
    PredictionDetailResponse, PredictionListItem,
    VocabPayloadSchema,
    CategoryAdminResponse, CategoryCreateRequest, CategoryUpdateRequest,
    ObjectListItem, ObjectDetailResponse, ObjectCreateRequest, ObjectUpdateRequest,
    TranslationAdminResponse, TranslationCreateRequest, TranslationUpdateRequest,
    UserAdminResponse, UserRoleUpdate, UserStatusUpdate,
    DashboardStats,
    ScanHistoryAdminItem, UserStatsAdminResponse,
)


class AdminService:
    def __init__(self):
        self.tts = TTSService()

    # ------------------------------------------------------------------
    # Truy vấn
    # ------------------------------------------------------------------

    def list_predictions(
        self,
        db: Session,
        trang_thai: Optional[str] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> list[PredictionListItem]:
        query = db.query(AIPrediction).order_by(AIPrediction.thoi_gian.desc())
        if trang_thai:
            try:
                query = query.filter(AIPrediction.trang_thai == TrangThaiDuyet(trang_thai))
            except ValueError:
                pass
        predictions = query.offset(offset).limit(limit).all()
        items = []
        for p in predictions:
            scan = db.query(ScanHistory).filter(ScanHistory.id == p.scan_id).first()
            item = PredictionListItem(
                id=p.id,
                scan_id=p.scan_id,
                nhan_du_doan=p.nhan_du_doan,
                do_tin_cay=p.do_tin_cay,
                trang_thai=p.trang_thai.value,
                thoi_gian=p.thoi_gian,
                scan_image_url=scan.url_anh if scan else None,
            )
            items.append(item)
        return items

    def get_prediction_detail(self, db: Session, prediction_id: int) -> Optional[PredictionDetailResponse]:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return None

        scan = db.query(ScanHistory).filter(ScanHistory.id == p.scan_id).first()

        vocab_payload = None
        if p.mo_ta:
            try:
                vocab_payload = VocabPayloadSchema.model_validate(json.loads(p.mo_ta))
            except Exception:
                pass

        return PredictionDetailResponse(
            id=p.id,
            scan_id=p.scan_id,
            nhan_du_doan=p.nhan_du_doan,
            do_tin_cay=p.do_tin_cay,
            trang_thai=p.trang_thai.value,
            thoi_gian=p.thoi_gian,
            scan_image_url=scan.url_anh if scan else None,
            vocab_payload=vocab_payload,
        )

    def export_training_data(self, db: Session) -> list[dict]:
        """
        Xuất toàn bộ predictions đã duyệt (da_duyet, nguon_ai=gemini)
        dưới dạng list dict — mỗi phần tử là 1 record training.
        """
        from app.models.ai_feedback_report import NguonAI
        predictions = (
            db.query(AIPrediction)
            .filter(
                AIPrediction.trang_thai == TrangThaiDuyet.da_duyet,
                AIPrediction.nguon_ai == NguonAI.gemini,
            )
            .order_by(AIPrediction.thoi_gian.desc())
            .all()
        )
        records = []
        for p in predictions:
            try:
                payload = json.loads(p.mo_ta or "{}")
            except Exception:
                payload = {}
            records.append({
                "prediction_id": p.id,
                "scan_id": p.scan_id,
                "object_code": p.nhan_du_doan,
                "image_url": payload.get("scan_image_url"),
                "approved_at": p.thoi_gian.isoformat() if p.thoi_gian else None,
                "translations": payload.get("translations", []),
                "category": payload.get("category"),
            })
        return records

    # ------------------------------------------------------------------
    # Approve — Insert vào bảng chính
    # ------------------------------------------------------------------

    def approve_prediction(
        self,
        db: Session,
        prediction_id: int,
        request: ApproveRequest,
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

        try:
            raw = json.loads(p.mo_ta)
        except Exception:
            return ApproveResponse(success=False, message="mo_ta không phải JSON hợp lệ", prediction_id=prediction_id)

        object_code = raw.get("object_code", p.nhan_du_doan or "unknown").lower()
        image_url_suggestion = raw.get("image_url_suggestion")
        translations_raw = raw.get("translations", [])
        if not translations_raw:
            return ApproveResponse(success=False, message="Không có translations trong payload", prediction_id=prediction_id)

        # Upsert DoiTuong (một lần, trước khi loop ngôn ngữ)
        obj = db.query(Object).filter(
            Object.ma_doi_tuong == object_code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            obj = Object(
                ma_doi_tuong=object_code,
                danh_muc_id=request.category_id,
            )
            db.add(obj)
            db.flush()
        else:
            if request.category_id is not None:
                obj.danh_muc_id = request.category_id

        if p.scan and p.scan.doi_tuong_id is None:
            p.scan.doi_tuong_id = obj.id

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
        user_ids_to_enroll: set[int] = set()

        if p.scan and p.scan.user_id:
            user_ids_to_enroll.add(p.scan.user_id)

        # Tìm các prediction khác cùng object_code còn cho_duyet → auto-resolve luôn
        related_predictions = db.query(AIPrediction).filter(
            AIPrediction.id != prediction_id,
            AIPrediction.nhan_du_doan == object_code,
            AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
        ).all()
        for rel in related_predictions:
            if rel.scan and rel.scan.user_id:
                user_ids_to_enroll.add(rel.scan.user_id)
            rel.trang_thai = TrangThaiDuyet.da_duyet

        now = now_vietnam()
        for uid in user_ids_to_enroll:
            already = db.query(LearningProgress).filter(
                LearningProgress.user_id == uid,
                LearningProgress.ban_dich_id == first_translation_id,
            ).first()
            if not already:
                db.add(LearningProgress(
                    user_id=uid,
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
            users_enrolled=len(user_ids_to_enroll),
        )

    # ------------------------------------------------------------------
    # Reject
    # ------------------------------------------------------------------

    def reject_prediction(self, db: Session, prediction_id: int) -> RejectResponse:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return RejectResponse(success=False, message="Không tìm thấy prediction", prediction_id=prediction_id)

        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return RejectResponse(
                success=False,
                message=f"Prediction đã ở trạng thái '{p.trang_thai.value}'",
                prediction_id=prediction_id,
            )

        deleted_translations = 0
        deleted_object = False
        if p.mo_ta:
            try:
                raw = json.loads(p.mo_ta)
            except Exception:
                raw = {}

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

        p.trang_thai = TrangThaiDuyet.tu_choi
        db.commit()

        details = []
        if deleted_translations:
            details.append(f"xoa {deleted_translations} ban dich tam")
        if deleted_object:
            details.append("xoa doi tuong tam")
        suffix = f" ({', '.join(details)})" if details else ""
        return RejectResponse(success=True, message=f"Da tu choi prediction #{prediction_id}{suffix}", prediction_id=prediction_id)

    # ------------------------------------------------------------------
    # Dashboard
    # ------------------------------------------------------------------

    def get_dashboard_stats(self, db: Session) -> DashboardStats:
        from sqlalchemy import exists
        has_media_subq = exists().where(
            (ObjectMedia.doi_tuong_id == Object.id) &
            ObjectMedia.thoi_gian_xoa.is_(None)
        )
        objects_without_images = db.query(Object).filter(
            Object.thoi_gian_xoa.is_(None),
            ~has_media_subq,
        ).count()
        return DashboardStats(
            total_users=db.query(User).filter(User.thoi_gian_xoa.is_(None)).count(),
            total_objects=db.query(Object).filter(Object.thoi_gian_xoa.is_(None)).count(),
            total_translations=db.query(Translation).filter(Translation.thoi_gian_xoa.is_(None)).count(),
            total_scans=db.query(ScanHistory).count(),
            pending_predictions=db.query(AIPrediction).filter(AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet).count(),
            approved_predictions=db.query(AIPrediction).filter(AIPrediction.trang_thai == TrangThaiDuyet.da_duyet).count(),
            rejected_predictions=db.query(AIPrediction).filter(AIPrediction.trang_thai == TrangThaiDuyet.tu_choi).count(),
            objects_without_images=objects_without_images,
        )

    # ------------------------------------------------------------------
    # Category CRUD
    # ------------------------------------------------------------------

    def list_categories(self, db: Session) -> List[CategoryAdminResponse]:
        from sqlalchemy import func
        cats = db.query(Category).filter(Category.thoi_gian_xoa.is_(None)).order_by(Category.id).all()
        counts = dict(
            db.query(Object.danh_muc_id, func.count(Object.id))
            .filter(Object.thoi_gian_xoa.is_(None), Object.danh_muc_id.isnot(None))
            .group_by(Object.danh_muc_id)
            .all()
        )
        result = []
        for c in cats:
            item = CategoryAdminResponse.model_validate(c)
            item.object_count = counts.get(c.id, 0)
            result.append(item)
        return result

    def create_category(self, db: Session, req: CategoryCreateRequest) -> CategoryAdminResponse:
        cat = Category(ten_danh_muc=req.ten_danh_muc, danh_muc_cha=req.danh_muc_cha, mo_ta=req.mo_ta)
        db.add(cat)
        db.commit()
        db.refresh(cat)
        return CategoryAdminResponse.model_validate(cat)

    def update_category(self, db: Session, category_id: int, req: CategoryUpdateRequest) -> Optional[CategoryAdminResponse]:
        cat = db.query(Category).filter(Category.id == category_id, Category.thoi_gian_xoa.is_(None)).first()
        if not cat:
            return None
        if req.ten_danh_muc is not None:
            cat.ten_danh_muc = req.ten_danh_muc
        if req.danh_muc_cha is not None:
            cat.danh_muc_cha = req.danh_muc_cha
        if req.mo_ta is not None:
            cat.mo_ta = req.mo_ta
        db.commit()
        db.refresh(cat)
        return CategoryAdminResponse.model_validate(cat)

    def delete_category(self, db: Session, category_id: int) -> bool:
        cat = db.query(Category).filter(Category.id == category_id, Category.thoi_gian_xoa.is_(None)).first()
        if not cat:
            return False
        cat.thoi_gian_xoa = now_vietnam()
        db.commit()
        return True

    # ------------------------------------------------------------------
    # Object CRUD
    # ------------------------------------------------------------------

    def list_objects(self, db: Session, limit: int = 50, offset: int = 0, search: Optional[str] = None, category_id: Optional[int] = None, no_image: Optional[bool] = None) -> List[ObjectListItem]:
        from sqlalchemy import exists
        query = db.query(Object).filter(Object.thoi_gian_xoa.is_(None))
        if search:
            query = query.filter(Object.ma_doi_tuong.ilike(f"%{search}%"))
        if category_id is not None:
            query = query.filter(Object.danh_muc_id == category_id)
        has_media_subq = exists().where(
            (ObjectMedia.doi_tuong_id == Object.id) &
            ObjectMedia.thoi_gian_xoa.is_(None)
        )
        if no_image is True:
            query = query.filter(~has_media_subq)
        elif no_image is False:
            query = query.filter(has_media_subq)
        objs = query.order_by(Object.id.desc()).offset(offset).limit(limit).all()
        result = []
        for obj in objs:
            cat_name = obj.category.ten_danh_muc if obj.category else None
            approved_count, pending_count = self._object_translation_counts(db, obj.id)
            has_img = db.query(exists().where(
                (ObjectMedia.doi_tuong_id == obj.id) &
                ObjectMedia.thoi_gian_xoa.is_(None)
            )).scalar()
            result.append(ObjectListItem(
                id=obj.id,
                ma_doi_tuong=obj.ma_doi_tuong,
                danh_muc_id=obj.danh_muc_id,
                category_name=cat_name,
                translation_count=approved_count,
                pending_translation_count=pending_count,
                has_image=bool(has_img),
            ))
        return result

    def get_object(self, db: Session, object_id: int) -> Optional[ObjectDetailResponse]:
        from sqlalchemy import exists
        obj = db.query(Object).filter(Object.id == object_id, Object.thoi_gian_xoa.is_(None)).first()
        if not obj:
            return None
        cat_name = obj.category.ten_danh_muc if obj.category else None
        approved_count, pending_count = self._object_translation_counts(db, obj.id)
        has_img = db.query(exists().where(
            (ObjectMedia.doi_tuong_id == obj.id) &
            ObjectMedia.thoi_gian_xoa.is_(None)
        )).scalar()
        return ObjectDetailResponse(
            id=obj.id,
            ma_doi_tuong=obj.ma_doi_tuong,
            danh_muc_id=obj.danh_muc_id,
            category_name=cat_name,
            translation_count=approved_count,
            pending_translation_count=pending_count,
            has_image=bool(has_img),
        )

    def _object_translation_counts(self, db: Session, object_id: int) -> tuple[int, int]:
        base = db.query(Translation).filter(
            Translation.doi_tuong_id == object_id,
            Translation.thoi_gian_xoa.is_(None),
        )
        approved_count = base.filter(Translation.da_xac_nhan.is_(True)).count()
        pending_count = base.filter(Translation.da_xac_nhan.is_(False)).count()
        return approved_count, pending_count

    def create_object(self, db: Session, req: ObjectCreateRequest) -> ObjectDetailResponse:
        obj = Object(ma_doi_tuong=req.ma_doi_tuong.lower().strip(), danh_muc_id=req.danh_muc_id)
        db.add(obj)
        db.commit()
        db.refresh(obj)
        return self.get_object(db, obj.id)

    def update_object(self, db: Session, object_id: int, req: ObjectUpdateRequest) -> Optional[ObjectDetailResponse]:
        obj = db.query(Object).filter(Object.id == object_id, Object.thoi_gian_xoa.is_(None)).first()
        if not obj:
            return None
        if req.danh_muc_id is not None:
            obj.danh_muc_id = req.danh_muc_id
        db.commit()
        return self.get_object(db, obj.id)

    def delete_object(self, db: Session, object_id: int) -> bool:
        obj = db.query(Object).filter(Object.id == object_id, Object.thoi_gian_xoa.is_(None)).first()
        if not obj:
            return False
        obj.thoi_gian_xoa = now_vietnam()
        db.commit()
        return True

    # ------------------------------------------------------------------
    # Translation CRUD
    # ------------------------------------------------------------------

    def list_translations(
        self,
        db: Session,
        object_id: Optional[int] = None,
        search: Optional[str] = None,
        lang_code: Optional[str] = None,
        approved: Optional[bool] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> List[TranslationAdminResponse]:
        query = db.query(Translation).filter(Translation.thoi_gian_xoa.is_(None))
        if object_id is not None:
            query = query.filter(Translation.doi_tuong_id == object_id)
        if search:
            query = query.join(Translation.object).filter(Object.ma_doi_tuong.ilike(f"%{search}%"))
        if lang_code:
            from app.models.language import Language as LangModel
            query = query.join(Translation.language).filter(LangModel.ma_ngon_ngu == lang_code)
        if approved is not None:
            query = query.filter(Translation.da_xac_nhan == approved)
        trans = query.order_by(Translation.id.desc()).offset(offset).limit(limit).all()
        result = []
        for t in trans:
            obj_code = t.object.ma_doi_tuong if t.object else None
            lang_code = t.language.ma_ngon_ngu if t.language else None
            ex_count = len(t.examples) if t.examples else 0
            from app.schemas.admin import ExampleItem
            result.append(TranslationAdminResponse(
                id=t.id,
                doi_tuong_id=t.doi_tuong_id,
                object_code=obj_code,
                lang_code=lang_code,
                tu_vung=t.tu_vung,
                phien_am=t.phien_am,
                loai_tu=t.loai_tu,
                dinh_nghia=t.dinh_nghia,
                am_thanh_url=t.am_thanh_url,
                da_xac_nhan=bool(t.da_xac_nhan),
                example_count=ex_count,
                examples=[
                    ExampleItem(id=ex.id, cau_vi_du=ex.cau_vi_du, dich_nghia=ex.dich_nghia)
                    for ex in (t.examples or [])
                ],
            ))
        return result

    def create_translation(self, db: Session, req: TranslationCreateRequest) -> TranslationAdminResponse:
        lang = db.query(Language).filter(Language.ma_ngon_ngu == req.lang_code).first()
        if not lang:
            lang_names = {"en": "English", "vi": "Vietnamese", "ja": "Japanese", "ko": "Korean"}
            lang = Language(ma_ngon_ngu=req.lang_code, ten_ngon_ngu=lang_names.get(req.lang_code, req.lang_code.upper()), dang_hoat_dong=True)
            db.add(lang)
            db.flush()
        audio_url = self.tts.get_audio_url(req.tu_vung, req.lang_code)
        t = Translation(
            doi_tuong_id=req.doi_tuong_id,
            ngon_ngu_id=lang.id,
            tu_vung=req.tu_vung,
            phien_am=req.phien_am,
            loai_tu=req.loai_tu,
            dinh_nghia=req.dinh_nghia,
            am_thanh_url=audio_url,
            nguon_du_lieu=NguonDuLieu.thu_cong,
            da_xac_nhan=True,
        )
        db.add(t)
        db.flush()
        for sentence in req.example_sentences[:5]:
            self._add_example(db, t.id, sentence)
        db.commit()
        db.refresh(t)
        return self.list_translations(db, object_id=t.doi_tuong_id, limit=1, offset=0)[0]

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

    def update_translation(self, db: Session, translation_id: int, req: TranslationUpdateRequest) -> Optional[TranslationAdminResponse]:
        t = db.query(Translation).filter(Translation.id == translation_id, Translation.thoi_gian_xoa.is_(None)).first()
        if not t:
            return None
        if req.tu_vung is not None:
            t.tu_vung = req.tu_vung
        if req.phien_am is not None:
            t.phien_am = req.phien_am
        if req.loai_tu is not None:
            t.loai_tu = req.loai_tu
        if req.dinh_nghia is not None:
            t.dinh_nghia = req.dinh_nghia
        if req.da_xac_nhan is not None:
            t.da_xac_nhan = req.da_xac_nhan
        if req.example_sentences is not None:
            db.query(ViDu).filter(ViDu.ban_dich_id == t.id).delete(synchronize_session=False)
            for sentence in req.example_sentences[:5]:
                self._add_example(db, t.id, sentence)
        db.commit()
        db.refresh(t)
        trans_list = self.list_translations(db, object_id=t.doi_tuong_id, limit=200, offset=0)
        return next((x for x in trans_list if x.id == t.id), None)

    def delete_translation(self, db: Session, translation_id: int) -> bool:
        t = db.query(Translation).filter(Translation.id == translation_id, Translation.thoi_gian_xoa.is_(None)).first()
        if not t:
            return False
        t.thoi_gian_xoa = now_vietnam()
        db.commit()
        return True

    # ------------------------------------------------------------------
    # User management
    # ------------------------------------------------------------------

    def list_users(self, db: Session, limit: int = 50, offset: int = 0, search: Optional[str] = None) -> List[UserAdminResponse]:
        query = db.query(User).filter(User.thoi_gian_xoa.is_(None))
        if search:
            query = query.filter(
                (User.ten_dang_nhap.ilike(f"%{search}%")) | (User.email.ilike(f"%{search}%"))
            )
        users = query.order_by(User.id.desc()).offset(offset).limit(limit).all()
        result = []
        for u in users:
            ho_ten = u.profile.ho_ten if u.profile else None
            vai_tro = u.vai_tro_obj.ten_vai_tro if u.vai_tro_obj else None
            trang_thai = u.trang_thai_obj.ten_trang_thai if u.trang_thai_obj else None
            result.append(UserAdminResponse(
                id=u.id,
                ten_dang_nhap=u.ten_dang_nhap,
                email=u.email,
                ho_ten=ho_ten,
                vai_tro=vai_tro,
                trang_thai=trang_thai,
                ngay_tao=u.ngay_tao,
                lan_dang_nhap_cuoi=u.lan_dang_nhap_cuoi,
            ))
        return result

    def update_user_role(self, db: Session, user_id: int, req: UserRoleUpdate) -> bool:
        u = db.query(User).filter(User.id == user_id, User.thoi_gian_xoa.is_(None)).first()
        if not u:
            return False
        u.vai_tro_id = req.vai_tro_id
        db.commit()
        return True

    def update_user_status(self, db: Session, user_id: int, req: UserStatusUpdate) -> bool:
        u = db.query(User).filter(User.id == user_id, User.thoi_gian_xoa.is_(None)).first()
        if not u:
            return False
        u.trang_thai_id = req.trang_thai_id
        db.commit()
        return True

    def delete_user(self, db: Session, user_id: int) -> bool:
        u = db.query(User).filter(User.id == user_id, User.thoi_gian_xoa.is_(None)).first()
        if not u:
            return False
        u.thoi_gian_xoa = now_vietnam()
        db.commit()
        return True

    def reset_user_password(self, db: Session, user_id: int, new_password: str) -> bool:
        u = db.query(User).filter(User.id == user_id, User.thoi_gian_xoa.is_(None)).first()
        if not u:
            return False
        u.mat_khau_ma_hoa = hash_password(new_password)
        db.commit()
        return True

    # ------------------------------------------------------------------
    # Scan History
    # ------------------------------------------------------------------

    def list_scan_history(
        self,
        db: Session,
        user_id: Optional[int] = None,
        username: Optional[str] = None,
        object_code: Optional[str] = None,
        date_from: Optional[str] = None,
        date_to: Optional[str] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> List[ScanHistoryAdminItem]:
        from datetime import datetime as dt
        query = (
            db.query(ScanHistory, User, Object)
            .outerjoin(User, ScanHistory.user_id == User.id)
            .outerjoin(Object, ScanHistory.doi_tuong_id == Object.id)
            .order_by(ScanHistory.thoi_gian.desc())
        )
        if user_id:
            query = query.filter(ScanHistory.user_id == user_id)
        if username:
            query = query.filter(User.ten_dang_nhap.ilike(f"%{username}%"))
        if object_code:
            query = query.filter(Object.ma_doi_tuong.ilike(f"%{object_code}%"))
        if date_from:
            query = query.filter(ScanHistory.thoi_gian >= dt.fromisoformat(date_from))
        if date_to:
            query = query.filter(ScanHistory.thoi_gian <= dt.fromisoformat(date_to + "T23:59:59"))
        rows = query.offset(offset).limit(limit).all()
        result = []
        for scan, user, obj in rows:
            result.append(ScanHistoryAdminItem(
                id=scan.id,
                user_id=scan.user_id,
                username=user.ten_dang_nhap if user else None,
                doi_tuong_id=scan.doi_tuong_id,
                object_code=obj.ma_doi_tuong if obj else None,
                url_anh=scan.url_anh,
                do_tin_cay=scan.do_tin_cay,
                thoi_gian=scan.thoi_gian,
            ))
        return result

    # ------------------------------------------------------------------
    # User Stats
    # ------------------------------------------------------------------

    def get_user_stats(self, db: Session, user_id: int) -> Optional[UserStatsAdminResponse]:
        from sqlalchemy import func
        from datetime import date, timedelta

        user = db.query(User).filter(User.id == user_id, User.thoi_gian_xoa.is_(None)).first()
        if not user:
            return None

        total_scans = db.query(ScanHistory).filter(ScanHistory.user_id == user_id).count()
        total_reviews = db.query(ReviewLog).filter(ReviewLog.user_id == user_id).count()
        total_learned = db.query(LearningProgress).filter(LearningProgress.user_id == user_id).count()

        review_date_rows = (
            db.query(func.date(ReviewLog.thoi_diem_on))
            .filter(ReviewLog.user_id == user_id)
            .distinct()
            .order_by(func.date(ReviewLog.thoi_diem_on).desc())
            .all()
        )
        dates = [r[0] for r in review_date_rows]

        streak_hien_tai = 0
        streak_dai_nhat = 0
        if dates:
            today = date.today()
            cur = 0
            if dates[0] == today or dates[0] == today - timedelta(days=1):
                check = dates[0]
                for d in dates:
                    if d == check:
                        cur += 1
                        check -= timedelta(days=1)
                    else:
                        break
            streak_hien_tai = cur

            longest = 1
            run = 1
            for i in range(1, len(dates)):
                if (dates[i - 1] - dates[i]).days == 1:
                    run += 1
                    if run > longest:
                        longest = run
                else:
                    run = 1
            streak_dai_nhat = longest

        last_scan = (
            db.query(ScanHistory.thoi_gian)
            .filter(ScanHistory.user_id == user_id)
            .order_by(ScanHistory.thoi_gian.desc())
            .first()
        )
        last_review = (
            db.query(ReviewLog.thoi_diem_on)
            .filter(ReviewLog.user_id == user_id)
            .order_by(ReviewLog.thoi_diem_on.desc())
            .first()
        )

        return UserStatsAdminResponse(
            user_id=user_id,
            total_scans=total_scans,
            total_reviews=total_reviews,
            total_learned=total_learned,
            streak_hien_tai=streak_hien_tai,
            streak_dai_nhat=streak_dai_nhat,
            last_scan_at=last_scan[0] if last_scan else None,
            last_review_at=last_review[0] if last_review else None,
        )
