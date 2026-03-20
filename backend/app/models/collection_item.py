from sqlalchemy import Column, Integer, ForeignKey, TIMESTAMP, UniqueConstraint
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime


class CollectionItem(Base):
    __tablename__ = "collection_items"

    id = Column(Integer, primary_key=True, autoincrement=True)
    collection_id = Column(Integer, ForeignKey("user_collections.id", ondelete="CASCADE"), nullable=False)
    translation_id = Column(Integer, ForeignKey("translations.id", ondelete="CASCADE"), nullable=False)
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)

    __table_args__ = (
        UniqueConstraint("collection_id", "translation_id", name="unique_collection_item"),
    )

    collection = relationship("UserCollection", back_populates="items")
    translation = relationship("Translation")
