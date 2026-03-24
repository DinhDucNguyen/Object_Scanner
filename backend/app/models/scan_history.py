from sqlalchemy import (
    Column, Integer, BigInteger, String, Float, ForeignKey,
    DECIMAL, TIMESTAMP
)
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime


class ScanHistory(Base):
    __tablename__ = "scan_history"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    object_id = Column(Integer, ForeignKey("objects.id", ondelete="SET NULL"), nullable=True)
    image_captured_url = Column(String(255), nullable=True)
    confidence_score = Column(Float, nullable=True)
    gps_latitude = Column(DECIMAL(10, 8), nullable=True)
    gps_longitude = Column(DECIMAL(11, 8), nullable=True)
    device_model = Column(String(50), nullable=True)
    scanned_at = Column(TIMESTAMP, default=datetime.utcnow)
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)

    user = relationship("User", back_populates="scan_histories")
    object = relationship("Object", back_populates="scan_histories")
    feedback = relationship("AIFeedbackReport", back_populates="scan", uselist=False)
