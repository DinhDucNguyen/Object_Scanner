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
from typing import Optional, List

from sqlalchemy import func, case, or_
from sqlalchemy.orm import Session, joinedload

from app.models.ai_feedback_report import AIPrediction, TrangThaiDuyet, VaiTroDuDoan
from app.models.category import Category
from app.models.object import Object, NguonTaoDoiTuong
from app.models.object_alias import ObjectAlias
from app.models.object_media import ObjectMedia
from app.models.scan_history import ScanHistory
from app.models.translation import Translation, NguonDuLieu
from app.models.example import ViDu
from app.models.language import Language
from app.models.user import User
from app.models.review_log import ReviewLog
from app.models.learning_progress import LearningProgress
from app.services.tts_service import TTSService
from app.services.prediction_service import PredictionService
from app.repositories.object_repo import normalize_object_code
from app.utils.timezone import now_vietnam
from app.utils.security import hash_password
from app.schemas.admin import (
    CategoryAdminResponse, CategoryCreateRequest, CategoryUpdateRequest,
    ObjectAliasItem, ObjectAliasUpsertRequest,
    ObjectListItem, ObjectDetailResponse, ObjectCreateRequest, ObjectUpdateRequest,
    TranslationAdminResponse, TranslationCreateRequest, TranslationUpdateRequest,
    UserAdminResponse, UserRoleUpdate, UserStatusUpdate,
    DashboardStats,
    ScanHistoryAdminItem, UserStatsAdminResponse,
    ExampleItem,
)


class AdminService:
    def __init__(self):
        self.tts = TTSService()

    def _object_translation_counts(self, db: Session, object_id: int) -> tuple[int, int]:
        base = db.query(Translation).filter(
            Translation.doi_tuong_id == object_id,
            Translation.thoi_gian_xoa.is_(None),
        )
        approved_count = base.filter(Translation.da_xac_nhan.is_(True)).count()
        pending_count = base.filter(Translation.da_xac_nhan.is_(False)).count()
        return approved_count, pending_count

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

        def _visible_count(status: TrangThaiDuyet) -> int:
            return db.query(AIPrediction).filter(
                AIPrediction.trang_thai == status,
                AIPrediction.vai_tro == VaiTroDuDoan.chinh,
            ).count()

        return DashboardStats(
            total_users=db.query(User).filter(User.thoi_gian_xoa.is_(None)).count(),
            total_objects=db.query(Object).filter(Object.thoi_gian_xoa.is_(None)).count(),
            total_translations=db.query(Translation).filter(Translation.thoi_gian_xoa.is_(None)).count(),
            total_scans=db.query(ScanHistory).count(),
            pending_predictions=_visible_count(TrangThaiDuyet.cho_duyet),
            approved_predictions=_visible_count(TrangThaiDuyet.da_duyet),
            rejected_predictions=_visible_count(TrangThaiDuyet.tu_choi),
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
        query = (
            db.query(Object)
            .options(joinedload(Object.category), joinedload(Object.aliases))
            .filter(Object.thoi_gian_xoa.is_(None))
        )
        if search:
            pattern = f"%{search}%"
            query = (
                query.outerjoin(ObjectAlias, ObjectAlias.doi_tuong_id == Object.id)
                .filter(or_(
                    Object.ma_doi_tuong.ilike(pattern),
                    ObjectAlias.ma_bi_danh.ilike(pattern),
                ))
                .distinct()
            )
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
        if not objs:
            return []

        obj_ids = [obj.id for obj in objs]

        trans_rows = (
            db.query(
                Translation.doi_tuong_id,
                func.sum(case((Translation.da_xac_nhan == True, 1), else_=0)).label("approved"),  # noqa: E712
                func.sum(case((Translation.da_xac_nhan == False, 1), else_=0)).label("pending"),  # noqa: E712
            )
            .filter(Translation.doi_tuong_id.in_(obj_ids), Translation.thoi_gian_xoa.is_(None))
            .group_by(Translation.doi_tuong_id)
            .all()
        )
        trans_counts = {row.doi_tuong_id: (int(row.approved or 0), int(row.pending or 0)) for row in trans_rows}

        has_img_ids = {
            row[0]
            for row in db.query(ObjectMedia.doi_tuong_id)
            .filter(ObjectMedia.doi_tuong_id.in_(obj_ids), ObjectMedia.thoi_gian_xoa.is_(None))
            .distinct()
            .all()
        }

        result = []
        for obj in objs:
            approved_count, pending_count = trans_counts.get(obj.id, (0, 0))
            result.append(ObjectListItem(
                id=obj.id,
                ma_doi_tuong=obj.ma_doi_tuong,
                danh_muc_id=obj.danh_muc_id,
                category_name=obj.category.ten_danh_muc if obj.category else None,
                translation_count=approved_count,
                pending_translation_count=pending_count,
                has_image=obj.id in has_img_ids,
                aliases=[ObjectAliasItem.model_validate(alias) for alias in (obj.aliases or [])],
                nguon_tao=obj.nguon_tao.value if hasattr(obj.nguon_tao, "value") else obj.nguon_tao,
                nguoi_tao_id=obj.nguoi_tao_id,
                du_doan_ai_id=obj.du_doan_ai_id,
                nguoi_duyet_id=obj.nguoi_duyet_id,
                thoi_gian_duyet=obj.thoi_gian_duyet,
            ))
        return result

    def get_object(self, db: Session, object_id: int) -> Optional[ObjectDetailResponse]:
        from sqlalchemy import exists
        obj = (
            db.query(Object)
            .options(joinedload(Object.aliases))
            .filter(Object.id == object_id, Object.thoi_gian_xoa.is_(None))
            .first()
        )
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
            aliases=[ObjectAliasItem.model_validate(alias) for alias in (obj.aliases or [])],
            nguon_tao=obj.nguon_tao.value if hasattr(obj.nguon_tao, "value") else obj.nguon_tao,
            nguoi_tao_id=obj.nguoi_tao_id,
            du_doan_ai_id=obj.du_doan_ai_id,
            nguoi_duyet_id=obj.nguoi_duyet_id,
            thoi_gian_duyet=obj.thoi_gian_duyet,
        )

    def create_object(self, db: Session, req: ObjectCreateRequest, admin_id: int | None = None) -> ObjectDetailResponse:
        obj = Object(
            ma_doi_tuong=normalize_object_code(req.ma_doi_tuong),
            danh_muc_id=req.danh_muc_id,
            nguon_tao=NguonTaoDoiTuong.admin,
            nguoi_tao_id=admin_id,
        )
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

    def upsert_object_alias(self, db: Session, req: ObjectAliasUpsertRequest) -> ObjectAliasItem | None:
        from app.services.prediction_service import PredictionService
        obj = db.query(Object).filter(
            Object.id == req.doi_tuong_id,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            return None

        alias_code = normalize_object_code(req.ma_bi_danh)
        if not alias_code:
            return None
        if alias_code == obj.ma_doi_tuong:
            raise ValueError("Bí danh không được trùng mã đối tượng chính")

        canonical_conflict = db.query(Object).filter(
            Object.id != obj.id,
            Object.ma_doi_tuong == alias_code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if canonical_conflict:
            raise ValueError(f"'{alias_code}' đang là mã đối tượng chính của object khác")

        alias = db.query(ObjectAlias).filter(ObjectAlias.ma_bi_danh == alias_code).first()
        if alias:
            alias.doi_tuong_id = obj.id
            if req.ten_hien_thi is not None:
                alias.ten_hien_thi = req.ten_hien_thi.strip() or None
            if req.ngon_ngu is not None:
                alias.ngon_ngu = req.ngon_ngu.strip() or None
        else:
            alias = ObjectAlias(
                doi_tuong_id=obj.id,
                ma_bi_danh=alias_code,
                ten_hien_thi=(req.ten_hien_thi or "").strip() or None,
                ngon_ngu=(req.ngon_ngu or "").strip() or None,
            )
            db.add(alias)

        prediction_svc = PredictionService()
        prediction_svc._rewire_alias_predictions(db, alias_code, obj)
        db.commit()
        db.refresh(alias)
        return ObjectAliasItem.model_validate(alias)

    def update_object_alias(self, db: Session, alias_id: int, ma_bi_danh: str, ten_hien_thi: str | None, ngon_ngu: str | None) -> ObjectAliasItem | None:
        alias = db.query(ObjectAlias).filter(ObjectAlias.id == alias_id).first()
        if not alias:
            return None

        new_code = normalize_object_code(ma_bi_danh)
        if not new_code:
            raise ValueError("Mã bí danh không hợp lệ")

        obj = db.query(Object).filter(Object.id == alias.doi_tuong_id, Object.thoi_gian_xoa.is_(None)).first()
        if obj and new_code == obj.ma_doi_tuong:
            raise ValueError("Bí danh không được trùng mã đối tượng chính")

        conflict = db.query(ObjectAlias).filter(
            ObjectAlias.ma_bi_danh == new_code,
            ObjectAlias.id != alias_id,
        ).first()
        if conflict:
            raise ValueError(f"'{new_code}' đã tồn tại dưới dạng bí danh khác")

        alias.ma_bi_danh = new_code
        alias.ten_hien_thi = (ten_hien_thi or "").strip() or None
        if ngon_ngu is not None:
            alias.ngon_ngu = ngon_ngu.strip() or None
        db.commit()
        db.refresh(alias)
        return ObjectAliasItem.model_validate(alias)

    def delete_object_alias(self, db: Session, alias_id: int) -> bool:
        alias = db.query(ObjectAlias).filter(ObjectAlias.id == alias_id).first()
        if not alias:
            return False
        db.delete(alias)
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
                du_doan_ai_id=t.du_doan_ai_id,
                nguoi_tao_id=t.nguoi_tao_id,
                nguoi_duyet_id=t.nguoi_duyet_id,
                thoi_gian_duyet=t.thoi_gian_duyet,
            ))
        return result

    def create_translation(self, db: Session, req: TranslationCreateRequest, admin_id: int | None = None) -> TranslationAdminResponse:
        from app.services.prediction_service import PredictionService
        lang = db.query(Language).filter(Language.ma_ngon_ngu == req.lang_code).first()
        if not lang:
            lang_names = {"en": "English", "vi": "Vietnamese", "ja": "Japanese", "ko": "Korean"}
            lang = Language(ma_ngon_ngu=req.lang_code, ten_ngon_ngu=lang_names.get(req.lang_code, req.lang_code.upper()), dang_hoat_dong=True)
            db.add(lang)
            db.flush()
        audio_url = self.tts.get_audio_url(req.tu_vung, req.lang_code)
        now = now_vietnam()
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
            nguoi_tao_id=admin_id,
            nguoi_duyet_id=admin_id,
            thoi_gian_duyet=now if admin_id is not None else None,
        )
        db.add(t)
        db.flush()
        for sentence in req.example_sentences[:5]:
            PredictionService._add_example(db, t.id, sentence)
        db.commit()
        db.refresh(t)
        return self.list_translations(db, object_id=t.doi_tuong_id, limit=1, offset=0)[0]

    def update_translation(
        self,
        db: Session,
        translation_id: int,
        req: TranslationUpdateRequest,
        admin_id: int | None = None,
    ) -> Optional[TranslationAdminResponse]:
        from app.services.prediction_service import PredictionService
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
            was_confirmed = bool(t.da_xac_nhan)
            t.da_xac_nhan = req.da_xac_nhan
            if req.da_xac_nhan and (not was_confirmed or not t.nguoi_duyet_id):
                if admin_id is not None:
                    t.nguoi_duyet_id = admin_id
                    t.thoi_gian_duyet = now_vietnam()
            elif not req.da_xac_nhan:
                t.nguoi_duyet_id = None
                t.thoi_gian_duyet = None
        if req.example_sentences is not None:
            db.query(ViDu).filter(ViDu.ban_dich_id == t.id).delete(synchronize_session=False)
            for sentence in req.example_sentences[:5]:
                PredictionService._add_example(db, t.id, sentence)
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

    def update_user_role(self, db: Session, nguoi_dung_id: int, req: UserRoleUpdate) -> bool:
        u = db.query(User).filter(User.id == nguoi_dung_id, User.thoi_gian_xoa.is_(None)).first()
        if not u:
            return False
        u.vai_tro_id = req.vai_tro_id
        db.commit()
        return True

    def update_user_status(self, db: Session, nguoi_dung_id: int, req: UserStatusUpdate) -> bool:
        u = db.query(User).filter(User.id == nguoi_dung_id, User.thoi_gian_xoa.is_(None)).first()
        if not u:
            return False
        u.trang_thai_id = req.trang_thai_id
        db.commit()
        return True

    def delete_user(self, db: Session, nguoi_dung_id: int) -> bool:
        u = db.query(User).filter(User.id == nguoi_dung_id, User.thoi_gian_xoa.is_(None)).first()
        if not u:
            return False
        u.thoi_gian_xoa = now_vietnam()
        db.commit()
        return True

    def reset_user_password(self, db: Session, nguoi_dung_id: int, new_password: str) -> bool:
        u = db.query(User).filter(User.id == nguoi_dung_id, User.thoi_gian_xoa.is_(None)).first()
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
        nguoi_dung_id: Optional[int] = None,
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
            .outerjoin(User, ScanHistory.nguoi_dung_id == User.id)
            .outerjoin(Object, ScanHistory.doi_tuong_id == Object.id)
            .order_by(ScanHistory.thoi_gian.desc())
        )
        if nguoi_dung_id:
            query = query.filter(ScanHistory.nguoi_dung_id == nguoi_dung_id)
        if username:
            query = query.filter(User.ten_dang_nhap.ilike(f"%{username}%"))
        if object_code:
            from sqlalchemy import exists
            pattern = f"%{object_code}%"
            alias_match = exists().where(
                (ObjectAlias.doi_tuong_id == Object.id) &
                ObjectAlias.ma_bi_danh.ilike(pattern)
            )
            query = query.filter(or_(
                Object.ma_doi_tuong.ilike(pattern),
                alias_match,
            ))
        if date_from:
            query = query.filter(ScanHistory.thoi_gian >= dt.fromisoformat(date_from))
        if date_to:
            query = query.filter(ScanHistory.thoi_gian <= dt.fromisoformat(date_to + "T23:59:59"))
        rows = query.offset(offset).limit(limit).all()
        lich_su_quet_ids = [scan.id for scan, _, _ in rows]
        pending_lich_su_quet_ids = set(
            r[0] for r in db.query(AIPrediction.lich_su_quet_id)
            .filter(
                AIPrediction.lich_su_quet_id.in_(lich_su_quet_ids),
                AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
            ).all()
        ) if lich_su_quet_ids else set()
        result = []
        for scan, user, obj in rows:
            result.append(ScanHistoryAdminItem(
                id=scan.id,
                nguoi_dung_id=scan.nguoi_dung_id,
                username=user.ten_dang_nhap if user else None,
                doi_tuong_id=scan.doi_tuong_id,
                object_code=obj.ma_doi_tuong if obj else None,
                url_anh=scan.url_anh,
                do_tin_cay=scan.do_tin_cay,
                thoi_gian=scan.thoi_gian,
                has_pending_prediction=scan.id in pending_lich_su_quet_ids,
            ))
        return result

    # ------------------------------------------------------------------
    # User Stats
    # ------------------------------------------------------------------

    def get_user_stats(self, db: Session, nguoi_dung_id: int) -> Optional[UserStatsAdminResponse]:
        from sqlalchemy import func
        from datetime import date, timedelta

        user = db.query(User).filter(User.id == nguoi_dung_id, User.thoi_gian_xoa.is_(None)).first()
        if not user:
            return None

        total_scans = db.query(ScanHistory).filter(ScanHistory.nguoi_dung_id == nguoi_dung_id).count()
        total_reviews = db.query(ReviewLog).filter(ReviewLog.nguoi_dung_id == nguoi_dung_id).count()
        total_learned = db.query(LearningProgress).filter(LearningProgress.nguoi_dung_id == nguoi_dung_id).count()

        review_date_rows = (
            db.query(func.date(ReviewLog.thoi_diem_on))
            .filter(ReviewLog.nguoi_dung_id == nguoi_dung_id)
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
            .filter(ScanHistory.nguoi_dung_id == nguoi_dung_id)
            .order_by(ScanHistory.thoi_gian.desc())
            .first()
        )
        last_review = (
            db.query(ReviewLog.thoi_diem_on)
            .filter(ReviewLog.nguoi_dung_id == nguoi_dung_id)
            .order_by(ReviewLog.thoi_diem_on.desc())
            .first()
        )

        return UserStatsAdminResponse(
            nguoi_dung_id=nguoi_dung_id,
            total_scans=total_scans,
            total_reviews=total_reviews,
            total_learned=total_learned,
            streak_hien_tai=streak_hien_tai,
            streak_dai_nhat=streak_dai_nhat,
            last_scan_at=last_scan[0] if last_scan else None,
            last_review_at=last_review[0] if last_review else None,
        )
