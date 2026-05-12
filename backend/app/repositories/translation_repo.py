from sqlalchemy.orm import Session
from app.models.translation import Translation


class TranslationRepository:
    def get_by_id(self, db: Session, translation_id: int):
        return db.query(Translation).filter(Translation.id == translation_id).first()

    def get_by_object_id(self, db: Session, object_id: int):
        return db.query(Translation).filter(Translation.doi_tuong_id == object_id).all()

    def count_all(self, db: Session):
        return db.query(Translation).count()
