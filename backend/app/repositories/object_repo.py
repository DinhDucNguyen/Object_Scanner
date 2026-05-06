from sqlalchemy.orm import Session
from app.models.object import Object


class ObjectRepository:
    def get_by_code(self, db: Session, object_code: str):
        return db.query(Object).filter(
            Object.ma_doi_tuong == object_code.lower(),
            Object.thoi_gian_xoa.is_(None)
        ).first()

    def get_all(self, db: Session, category_id: int = None):
        query = db.query(Object).filter(Object.thoi_gian_xoa.is_(None))
        if category_id:
            query = query.filter(Object.danh_muc_id == category_id)
        return query.all()

    def count_all(self, db: Session):
        return db.query(Object).filter(Object.thoi_gian_xoa.is_(None)).count()

    def create(self, db: Session, obj: Object):
        db.add(obj)
        db.flush()
        return obj
