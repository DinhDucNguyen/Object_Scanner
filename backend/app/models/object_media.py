from sqlalchemy import Column, Integer, String, Boolean, ForeignKey, Enum, TIMESTAMP
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime
import enum


class MediaType(str, enum.Enum):
    image = "image"
    audio = "audio"
    three_d = "3d"


class ObjectMedia(Base):
    __tablename__ = "object_media"

    id = Column(Integer, primary_key=True, autoincrement=True)
    object_id = Column(Integer, ForeignKey("objects.id", ondelete="CASCADE"), nullable=False)
    language_id = Column(Integer, ForeignKey("languages.id", ondelete="SET NULL"), nullable=True)
    type = Column(Enum(MediaType), nullable=False)
    url = Column(String(255), nullable=False)
    is_primary = Column(Boolean, default=False)
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)

    object = relationship("Object", back_populates="media")
