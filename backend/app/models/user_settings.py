from sqlalchemy import Column, Integer, String, Float, ForeignKey, TIMESTAMP
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime


class UserSettings(Base):
    __tablename__ = "user_settings"

    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    native_lang_code = Column(String(10), ForeignKey("languages.code"), nullable=True)
    target_lang_code = Column(String(10), ForeignKey("languages.code"), nullable=True)
    theme = Column(String(10), default="light")
    ai_precision_threshold = Column(Float, default=0.75)
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)

    user = relationship("User", back_populates="settings")
