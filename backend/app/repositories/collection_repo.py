from sqlalchemy.orm import Session
from app.models.user_collection import UserCollection
from app.models.collection_item import CollectionItem


class CollectionRepository:
    def get_by_user(self, db: Session, user_id: int):
        return db.query(UserCollection).filter(UserCollection.user_id == user_id).all()

    def get_by_id(self, db: Session, collection_id: int):
        return db.query(UserCollection).filter(UserCollection.id == collection_id).first()

    def count_items(self, db: Session, collection_id: int):
        return db.query(CollectionItem).filter(CollectionItem.collection_id == collection_id).count()

    def create_collection(self, db: Session, collection: UserCollection):
        db.add(collection)
        db.flush()
        return collection

    def delete_collection(self, db: Session, collection_id: int):
        db.query(CollectionItem).filter(CollectionItem.collection_id == collection_id).delete()
        db.query(UserCollection).filter(UserCollection.id == collection_id).delete()
        db.flush()

    def get_item(self, db: Session, collection_id: int, translation_id: int):
        return db.query(CollectionItem).filter(
            CollectionItem.collection_id == collection_id,
            CollectionItem.translation_id == translation_id
        ).first()

    def add_item(self, db: Session, item: CollectionItem):
        db.add(item)
        db.flush()
        return item

    def remove_item(self, db: Session, item_id: int):
        db.query(CollectionItem).filter(CollectionItem.id == item_id).delete()
        db.flush()

