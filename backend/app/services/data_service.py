from sqlalchemy.orm import Session
from datetime import datetime

from app.repositories.object_repo import ObjectRepository
from app.repositories.translation_repo import TranslationRepository
from app.repositories.language_repo import LanguageRepository
from app.repositories.learning_repo import LearningProgressRepository
from app.repositories.history_repo import HistoryRepository
from app.models.language import Language
from app.models.category import Category
from app.models.data_version import DataVersion
from app.schemas.common import StatsResponse


class DataService:
    def __init__(self):
        self.obj_repo = ObjectRepository()
        self.trans_repo = TranslationRepository()
        self.lang_repo = LanguageRepository()
        self.learn_repo = LearningProgressRepository()
        self.hist_repo = HistoryRepository()

    def get_languages(self, db: Session):
        return self.lang_repo.get_active(db)

    def get_categories(self, db: Session):
        return db.query(Category).all()

    def get_all_objects(self, db: Session, category_id: int = None):
        objects = self.obj_repo.get_all(db, category_id)
        return [
            {
                "id": obj.id, "object_code": obj.object_code,
                "category_id": obj.category_id,
                "category_name": obj.category.name if obj.category else None,
                "difficulty_level": obj.difficulty_level,
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

    def get_data_versions(self, db: Session):
        versions = db.query(DataVersion).all()
        return {v.table_name: {"version": v.version_number, "updated_at": str(v.last_updated_at)} for v in versions}
