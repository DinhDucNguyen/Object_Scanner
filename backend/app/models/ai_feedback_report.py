from sqlalchemy import (
    Column, Integer, BigInteger, String, Boolean, ForeignKey,
    TIMESTAMP, Enum, Text
)
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime
import enum


class ErrorType(str, enum.Enum):
    wrong_object = "wrong_object"
    wrong_translation = "wrong_translation"
    bad_image = "bad_image"


class AIFeedbackReport(Base):
    __tablename__ = "ai_feedback_reports"

    id = Column(Integer, primary_key=True, autoincrement=True)
    scan_id = Column(BigInteger, ForeignKey("scan_history.id", ondelete="CASCADE"), nullable=False)
    reported_by = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    error_type = Column(Enum(ErrorType), nullable=True)
    correct_label = Column(String(100), nullable=True)
    user_note = Column(Text, nullable=True)
    is_resolved = Column(Boolean, default=False)
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)

    scan = relationship("ScanHistory", back_populates="feedback")
    reporter = relationship("User", back_populates="feedback_reports")
