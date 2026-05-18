# pyrefly: ignore [missing-import]
from sqlalchemy import (
    Column, Integer, String, Float, ForeignKey,
    TIMESTAMP, Enum, Text
)
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import relationship
from app.db.session import Base
from app.utils.timezone import now_vietnam
import enum


class NguonAI(str, enum.Enum):
    yolo = "yolo"
    gemini = "gemini"


class TrangThaiDuyet(str, enum.Enum):
    cho_duyet = "cho_duyet"
    da_duyet  = "da_duyet"
    tu_choi   = "tu_choi"


class VaiTroDuDoan(str, enum.Enum):
    chinh = "chinh"
    anh_bo_sung = "anh_bo_sung"


class AIPrediction(Base):
    __tablename__ = "DuDoanAI"

    id          = Column(Integer, primary_key=True, autoincrement=True)
    scan_id     = Column(Integer, ForeignKey("LichSuQuet.id", ondelete="CASCADE"), nullable=False)
    nguon_ai    = Column(Enum(NguonAI), nullable=False)
    nhan_du_doan = Column(String(255), nullable=True)
    do_tin_cay  = Column(Float, nullable=True)

    mo_ta       = Column(Text, nullable=True)

    trang_thai  = Column(
        Enum(TrangThaiDuyet),
        default=TrangThaiDuyet.cho_duyet,
        nullable=False,
        index=True,
    )
    vai_tro = Column(
        Enum(VaiTroDuDoan),
        default=VaiTroDuDoan.chinh,
        nullable=False,
        index=True,
    )
    du_doan_goc_id = Column(
        Integer,
        ForeignKey("DuDoanAI.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )
    thoi_gian   = Column(TIMESTAMP, default=now_vietnam)

    scan = relationship("ScanHistory", back_populates="predictions")
    du_doan_goc = relationship("AIPrediction", remote_side=[id], back_populates="anh_bo_sung")
    anh_bo_sung = relationship("AIPrediction", back_populates="du_doan_goc")
