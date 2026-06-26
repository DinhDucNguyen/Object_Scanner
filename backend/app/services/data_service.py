from sqlalchemy.orm import Session
from sqlalchemy import func
from typing import Optional

from app.repositories.object_repo import ObjectRepository
from app.repositories.translation_repo import TranslationRepository
from app.repositories.language_repo import LanguageRepository
from app.repositories.learning_repo import LearningProgressRepository
from app.repositories.history_repo import HistoryRepository
from app.models.category import Category
from app.models.object import Object
from app.schemas.common import StatsResponse
from app.services.object_media_service import pick_primary_object_image
from app.services.streak_service import StreakService


class DataService:
    def __init__(self):
        self.obj_repo = ObjectRepository()
        self.trans_repo = TranslationRepository()
        self.lang_repo = LanguageRepository()
        self.learn_repo = LearningProgressRepository()
        self.hist_repo = HistoryRepository()
        self.streak_service = StreakService()

    def get_languages(self, db: Session):
        langs = self.lang_repo.get_active(db)
        return [
            {
                "id": lang.id,
                "code": lang.ma_ngon_ngu,
                "name": lang.ten_ngon_ngu,
                "flag_icon_url": lang.icon_co,
                "is_active": lang.dang_hoat_dong
            }
            for lang in langs
        ]

    def get_categories(self, db: Session):
        counts = dict(
            db.query(Object.danh_muc_id, func.count(Object.id))
            .filter(Object.thoi_gian_xoa.is_(None))
            .group_by(Object.danh_muc_id)
            .all()
        )
        categories = db.query(Category).filter(Category.thoi_gian_xoa.is_(None)).all()
        return [
            {
                "id": cat.id,
                "name": cat.ten_danh_muc,
                "parent_id": cat.danh_muc_cha,
                "description": cat.mo_ta,
                "object_count": counts.get(cat.id, 0)
            }
            for cat in categories
        ]

    def get_all_objects(self, db: Session, category_id: Optional[int] = None):
        objects = self.obj_repo.get_all(db, category_id)
        result = []
        from app.models.scan_history import ScanHistory
        for obj in objects:
            primary_translation = next(
                (t for t in obj.translations if getattr(t, "thoi_gian_xoa", None) is None),
                None,
            )
            
            image_url = pick_primary_object_image(obj)
            is_temporary_image = False
            if not image_url:
                latest_scan = db.query(ScanHistory).filter(
                    ScanHistory.doi_tuong_id == obj.id,
                    ScanHistory.url_anh.isnot(None)
                ).order_by(ScanHistory.thoi_gian.desc()).first()
                if latest_scan:
                    image_url = latest_scan.url_anh
                    is_temporary_image = True

            result.append({
                "id": obj.id,
                "object_code": obj.ma_doi_tuong,
                "category_id": obj.danh_muc_id,
                "category_name": obj.category.ten_danh_muc if obj.category else None,
                "translation_count": len(obj.translations),
                "word_name": primary_translation.tu_vung if primary_translation else obj.ma_doi_tuong.replace("_", " ").title(),
                "phonetic": primary_translation.phien_am if primary_translation else None,
                "definition": primary_translation.dinh_nghia if primary_translation else None,
                "translation_id": primary_translation.id if primary_translation else None,
                "image_url": image_url,
                "is_temporary_image": is_temporary_image,
            })
        return result

    def get_stats(self, db: Session, nguoi_dung_id: int):
        streak = self.streak_service.get_streak(db, nguoi_dung_id)
        return StatsResponse(
            total_objects=self.obj_repo.count_all(db),
            total_translations=self.trans_repo.count_all(db),
            total_languages=self.lang_repo.count_active(db),
            total_learned=self.learn_repo.count_by_user(db, nguoi_dung_id),
            due_today=self.learn_repo.count_due_today(db, nguoi_dung_id),
            mastered=self.learn_repo.count_mastered(db, nguoi_dung_id),
            total_scans=self.hist_repo.count_by_user(db, nguoi_dung_id),
            current_streak=streak["streak_hien_tai"],
            longest_streak=streak["streak_dai_nhat"],
            total_reviews=streak["tong_luot_on"],
        )
