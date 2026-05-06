from sqlalchemy.orm import Session

from app.repositories.object_repo import ObjectRepository
from app.repositories.translation_repo import TranslationRepository
from app.repositories.language_repo import LanguageRepository
from app.repositories.learning_repo import LearningProgressRepository
from app.repositories.history_repo import HistoryRepository
from app.models.category import Category
from app.schemas.common import StatsResponse


class DataService:
    def __init__(self):
        self.obj_repo = ObjectRepository()
        self.trans_repo = TranslationRepository()
        self.lang_repo = LanguageRepository()
        self.learn_repo = LearningProgressRepository()
        self.hist_repo = HistoryRepository()

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
        categories = db.query(Category).filter(Category.thoi_gian_xoa.is_(None)).all()
        return [
            {
                "id": cat.id,
                "name": cat.ten_danh_muc,
                "parent_id": cat.danh_muc_cha,
                "description": cat.mo_ta
            }
            for cat in categories
        ]

    def get_all_objects(self, db: Session, category_id: int = None):
        objects = self.obj_repo.get_all(db, category_id)
        return [
            {
                "id": obj.id, "object_code": obj.ma_doi_tuong,
                "category_id": obj.danh_muc_id,
                "category_name": obj.category.ten_danh_muc if obj.category else None,
                "difficulty_level": obj.muc_do_kho,
                "translation_count": len(obj.translations)
            }
            for obj in objects
        ]

    def get_stats(self, db: Session, user_id: int):
        return StatsResponse(
            total_objects=self.obj_repo.count_all(db),
            total_translations=self.trans_repo.count_all(db),
            total_languages=self.lang_repo.count_active(db),
            total_learned=self.learn_repo.count_by_user(db, user_id),
            due_today=self.learn_repo.count_due_today(db, user_id),
            mastered=self.learn_repo.count_mastered(db, user_id),
            total_scans=self.hist_repo.count_by_user(db, user_id)
        )
