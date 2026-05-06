from sqlalchemy import (
    Column, Integer, String, Float, ForeignKey,
    TIMESTAMP, Enum, Text
)
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime
import enum


class NguonAI(str, enum.Enum):
    yolo = "yolo"
    gemini = "gemini"


class TrangThaiDuyet(str, enum.Enum):
    cho_duyet = "cho_duyet"  # Chờ admin kiểm duyệt
    da_duyet  = "da_duyet"   # Đã duyệt → đã insert vào bảng chính
    tu_choi   = "tu_choi"    # Bị từ chối


class AIPrediction(Base):
    __tablename__ = "DuDoanAI"

    id          = Column(Integer, primary_key=True, autoincrement=True)
    scan_id     = Column(Integer, ForeignKey("LichSuQuet.id", ondelete="CASCADE"), nullable=False)
    nguon_ai    = Column(Enum(NguonAI), nullable=False)
    nhan_du_doan = Column(String(255), nullable=True)
    do_tin_cay  = Column(Float, nullable=True)

    # Payload từ vựng Gemini (JSON string).
    # Format: {"object_code": "...", "category": "...", "translations": [...]}
    # Chỉ chuyển sang BanDich/ViDu sau khi admin duyệt.
    mo_ta       = Column(Text, nullable=True)

    # Thay thế ket_qua_dung (Boolean) bằng trang_thai (Enum) rõ nghĩa hơn.
    trang_thai  = Column(
        Enum(TrangThaiDuyet),
        default=TrangThaiDuyet.cho_duyet,
        nullable=False,
        index=True,
    )
    thoi_gian   = Column(TIMESTAMP, default=datetime.utcnow)

    scan = relationship("ScanHistory", back_populates="predictions")
