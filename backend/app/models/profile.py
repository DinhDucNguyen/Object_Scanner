from sqlalchemy import Column, Integer, String, Text, ForeignKey, TIMESTAMP
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime


class Profile(Base):
    __tablename__ = "profiles"

    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    full_name = Column(String(100), nullable=True)
    avatar_url = Column(String(255), default="default_avatar.png")
    bio = Column(Text, nullable=True)
    student_id = Column(String(20), nullable=True)
    university = Column(String(100), default="UNIVERSITY OF DA NANG")
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)

    user = relationship("User", back_populates="profile")
