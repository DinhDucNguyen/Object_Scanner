from sqlalchemy import (
    Column, Integer, BigInteger, ForeignKey, DECIMAL, TIMESTAMP,
    UniqueConstraint, Index
)
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime


class LearningProgress(Base):
    __tablename__ = "learning_progress"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    translation_id = Column(Integer, ForeignKey("translations.id", ondelete="CASCADE"), nullable=False)
    easiness_factor = Column(DECIMAL(5, 2), default=2.50)
    interval = Column(Integer, default=0)
    repetitions = Column(Integer, default=0)
    next_review_date = Column(TIMESTAMP, nullable=True)
    last_reviewed_at = Column(TIMESTAMP, default=datetime.utcnow)
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)

    __table_args__ = (
        UniqueConstraint("user_id", "translation_id", name="unique_user_translation"),
        Index("idx_next_review", "next_review_date"),
    )

    user = relationship("User", back_populates="learning_progress")
    translation = relationship("Translation", back_populates="learning_progress")
