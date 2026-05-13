# pyrefly: ignore [missing-import]
from sqlalchemy import Column, Integer, String, Boolean, ForeignKey, TIMESTAMP, DateTime
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import relationship
from app.db.session import Base
from app.utils.timezone import now_vietnam


class UserCollection(Base):
    __tablename__ = "BoSuuTap"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="CASCADE"), nullable=False)
    ten_bo_suu_tap = Column(String(100), nullable=False)
    cong_khai = Column(Boolean, default=False)
    ngay_tao = Column(TIMESTAMP, default=now_vietnam)
    thoi_gian_xoa = Column(DateTime, nullable=True)

    user = relationship("User", back_populates="collections")
    items = relationship("CollectionItem", back_populates="collection", cascade="all, delete-orphan")
