# pyrefly: ignore [missing-import]
from sqlalchemy import (
    Column, Integer, String, Float, ForeignKey, TIMESTAMP
)
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import relationship
from app.db.session import Base
from app.utils.timezone import now_vietnam


class ScanHistory(Base):
    __tablename__ = "LichSuQuet"

    id = Column(Integer, primary_key=True, autoincrement=True)
    nguoi_dung_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="CASCADE"), nullable=True)
    doi_tuong_id = Column(Integer, ForeignKey("DoiTuong.id", ondelete="SET NULL"), nullable=True)
    url_anh = Column(String(255), nullable=True)
    do_tin_cay = Column(Float, nullable=True)
    thoi_gian = Column(TIMESTAMP, default=now_vietnam)

    user = relationship("User", back_populates="scan_histories")
    object = relationship("Object", back_populates="scan_histories")
    predictions = relationship("AIPrediction", back_populates="scan", cascade="all, delete-orphan")
