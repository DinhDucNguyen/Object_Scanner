from sqlalchemy.orm import Session
from app.models.object import Object


class ObjectRepository:
    def get_by_code(self, db: Session, object_code: str):
        return db.query(Object).filter(
            Object.object_code == object_code.lower(),
            Object.is_deleted.is_(False)
        ).first()

    def get_all(self, db: Session, category_id: int = None):
        query = db.query(Object).filter(Object.is_deleted == False)
        if category_id:
            query = query.filter(Object.category_id == category_id)
        return query.all()

    def count_all(self, db: Session):
        return db.query(Object).filter(Object.is_deleted == False).count()

    def create(self, db: Session, obj: Object):
        db.add(obj)
        db.flush()
        return obj
