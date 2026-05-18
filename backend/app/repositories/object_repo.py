from sqlalchemy.orm import Session
from app.models.object import Object
from app.models.object_alias import ObjectAlias


def normalize_object_code(value: str | None) -> str:
    return "_".join((value or "").lower().strip().replace("-", " ").split())


class ObjectRepository:
    def get_by_code(self, db: Session, object_code: str):
        code = normalize_object_code(object_code)
        if not code:
            return None

        obj = db.query(Object).filter(
            Object.ma_doi_tuong == code,
            Object.thoi_gian_xoa.is_(None)
        ).first()
        if obj:
            return obj

        alias = (
            db.query(ObjectAlias)
            .join(Object, ObjectAlias.doi_tuong_id == Object.id)
            .filter(
                ObjectAlias.ma_bi_danh == code,
                Object.thoi_gian_xoa.is_(None),
            )
            .first()
        )
        return alias.object if alias else None

    def get_by_canonical_code(self, db: Session, object_code: str):
        code = normalize_object_code(object_code)
        if not code:
            return None
        return db.query(Object).filter(
            Object.ma_doi_tuong == code,
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
