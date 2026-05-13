from sqlalchemy import Column, Integer, String, ForeignKey
from sqlalchemy.orm import relationship
from app.db.session import Base


class UserSettings(Base):
    __tablename__ = "CaiDatNguoiDung"

    user_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="CASCADE"), primary_key=True)
    ngon_ngu_giao_dien = Column(String(10), default="vi", nullable=False)

    user = relationship("User", back_populates="settings")
