from sqlalchemy import (
    Column, Integer, String, Text, ForeignKey, UniqueConstraint, TIMESTAMP
)
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime


class Translation(Base):
    __tablename__ = "translations"

    id = Column(Integer, primary_key=True, autoincrement=True)
    object_id = Column(Integer, ForeignKey("objects.id", ondelete="CASCADE"), nullable=False, index=True)
    language_id = Column(Integer, ForeignKey("languages.id", ondelete="CASCADE"), nullable=False, index=True)
    word_name = Column(String(255), nullable=False)
    phonetic = Column(String(255), nullable=True)
    definition = Column(Text, nullable=True)
    example_sentence = Column(Text, nullable=True)
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)

    __table_args__ = (
        UniqueConstraint("object_id", "language_id", name="unique_trans"),
    )

    object = relationship("Object", back_populates="translations")
    language = relationship("Language", back_populates="translations")
    learning_progress = relationship("LearningProgress", back_populates="translation", cascade="all, delete-orphan")
