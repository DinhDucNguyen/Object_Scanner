# pyrefly: ignore [missing-import]
from sqlalchemy import Column, Integer, String, ForeignKey, DateTime, Enum
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import relationship
from app.db.session import Base
import enum


class NguonTaoDoiTuong(str, enum.Enum):
    admin = "admin"
    gemini = "gemini"


class Object(Base):
    __tablename__ = "DoiTuong"

    id = Column(Integer, primary_key=True, autoincrement=True)
    danh_muc_id = Column(Integer, ForeignKey("DanhMuc.id"), nullable=True, index=True)
    ma_doi_tuong = Column(String(100), unique=True, nullable=True)
    nguon_tao = Column(Enum(NguonTaoDoiTuong), default=NguonTaoDoiTuong.admin, nullable=False)
    nguoi_tao_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="SET NULL"), nullable=True)
    du_doan_ai_id = Column(Integer, ForeignKey("DuDoanAI.id", ondelete="SET NULL"), nullable=True, index=True)
    nguoi_duyet_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="SET NULL"), nullable=True)
    thoi_gian_duyet = Column(DateTime, nullable=True)
    thoi_gian_xoa = Column(DateTime, nullable=True)

    category = relationship("Category", back_populates="objects")
    translations = relationship("Translation", back_populates="object", cascade="all, delete-orphan")
    media = relationship("ObjectMedia", back_populates="object", cascade="all, delete-orphan")
    aliases = relationship("ObjectAlias", back_populates="object", cascade="all, delete-orphan")
    scan_histories = relationship("ScanHistory", back_populates="object")
