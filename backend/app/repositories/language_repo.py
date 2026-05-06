from sqlalchemy.orm import Session
from app.models.language import Language


class LanguageRepository:
    def get_by_id(self, db: Session, language_id: int):
        return db.query(Language).filter(Language.id == language_id).first()

    def get_active(self, db: Session):
        return db.query(Language).filter(Language.dang_hoat_dong.is_(True)).all()

    def count_active(self, db: Session):
        return db.query(Language).filter(Language.dang_hoat_dong.is_(True)).count()
