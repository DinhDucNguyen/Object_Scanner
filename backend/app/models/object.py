from sqlalchemy import (
    Column, Integer, String, Boolean, ForeignKey, CheckConstraint, TIMESTAMP
)
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime


class Object(Base):
    __tablename__ = "objects"

    id = Column(Integer, primary_key=True, autoincrement=True)
    category_id = Column(Integer, ForeignKey("categories.id"), nullable=True, index=True)
    object_code = Column(String(100), unique=True, nullable=False)
    difficulty_level = Column(Integer, default=1)
    created_by = Column(Integer, ForeignKey("users.id"), nullable=True)
    is_deleted = Column(Boolean, default=False)
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)

    __table_args__ = (
        CheckConstraint("difficulty_level BETWEEN 1 AND 5", name="chk_difficulty"),
    )

    category = relationship("Category", back_populates="objects")
    translations = relationship("Translation", back_populates="object", cascade="all, delete-orphan")
    media = relationship("ObjectMedia", back_populates="object", cascade="all, delete-orphan")
    scan_histories = relationship("ScanHistory", back_populates="object")
