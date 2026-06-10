from sqlalchemy.orm import Session
from app.models.user_collection import UserCollection
from app.models.collection_item import CollectionItem


class CollectionRepository:
    def get_by_user(self, db: Session, nguoi_dung_id: int):
        return db.query(UserCollection).filter(
            UserCollection.nguoi_dung_id == nguoi_dung_id,
            UserCollection.thoi_gian_xoa.is_(None),
        ).all()

    def get_by_id(self, db: Session, collection_id: int):
        return db.query(UserCollection).filter(
            UserCollection.id == collection_id,
            UserCollection.thoi_gian_xoa.is_(None),
        ).first()

    def count_items(self, db: Session, collection_id: int):
        return db.query(CollectionItem).filter(CollectionItem.bo_suu_tap_id == collection_id).count()

    def create_collection(self, db: Session, collection: UserCollection):
        db.add(collection)
        db.flush()
        return collection

    def delete_collection(self, db: Session, collection_id: int):
        db.query(CollectionItem).filter(CollectionItem.bo_suu_tap_id == collection_id).delete()
        db.query(UserCollection).filter(UserCollection.id == collection_id).delete()
        db.flush()

    def get_item(self, db: Session, collection_id: int, translation_id: int):
        return db.query(CollectionItem).filter(
            CollectionItem.bo_suu_tap_id == collection_id,
            CollectionItem.ban_dich_id == translation_id
        ).first()

    def add_item(self, db: Session, item: CollectionItem):
        db.add(item)
        db.flush()
        return item

    def get_public(self, db: Session, nguoi_dung_id: int):
        return (
            db.query(UserCollection)
            .join(CollectionItem, CollectionItem.bo_suu_tap_id == UserCollection.id)
            .filter(
                UserCollection.cong_khai == True,
                UserCollection.nguoi_dung_id != nguoi_dung_id,
                UserCollection.thoi_gian_xoa.is_(None),
            )
            .distinct()
            .all()
        )

    def remove_item(self, db: Session, collection_id: int, translation_id: int):
        db.query(CollectionItem).filter(
            CollectionItem.bo_suu_tap_id == collection_id,
            CollectionItem.ban_dich_id == translation_id
        ).delete()
        db.flush()
