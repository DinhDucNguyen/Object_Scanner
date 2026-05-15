# pyrefly: ignore [missing-import]
from sqlalchemy import Column, Integer, String, ForeignKey
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import relationship

from app.db.session import Base


class Profile(Base):
    __tablename__ = "HoSo"

    user_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="CASCADE"), primary_key=True)
    ho_ten = Column(String(100), nullable=True)
    anh_dai_dien = Column(String(255), default="default_avatar.png")
    gioi_thieu = Column(String(500), nullable=True)

    user = relationship("User", back_populates="profile")
