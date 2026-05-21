import enum

from sqlalchemy import (
    Column,
    DateTime,
    Enum,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
    TIMESTAMP,
    UniqueConstraint,
)
from sqlalchemy.orm import relationship

from app.db.session import Base
from app.utils.timezone import now_vietnam


class NguonAnhHuanLuyen(str, enum.Enum):
    yolo = "yolo"
    gemini = "gemini"
    admin = "admin"


class TrangThaiAnhHuanLuyen(str, enum.Enum):
    cho_duyet = "cho_duyet"
    da_duyet = "da_duyet"
    tu_choi = "tu_choi"


class TrainingImage(Base):
    __tablename__ = "AnhHuanLuyen"

    id = Column(Integer, primary_key=True, autoincrement=True)
    scan_id = Column(Integer, ForeignKey("LichSuQuet.id", ondelete="SET NULL"), nullable=True, index=True)
    du_doan_id = Column(Integer, ForeignKey("DuDoanAI.id", ondelete="SET NULL"), nullable=True, index=True)
    doi_tuong_id = Column(Integer, ForeignKey("DoiTuong.id", ondelete="SET NULL"), nullable=True, index=True)
    url_anh = Column(String(255), nullable=False)
    nhan = Column(String(255), nullable=True, index=True)
    nguon_du_lieu = Column(Enum(NguonAnhHuanLuyen), nullable=False)
    do_tin_cay = Column(Float, nullable=True)
    diem_chat_luong = Column(Integer, nullable=True)
    trang_thai = Column(
        Enum(TrangThaiAnhHuanLuyen),
        default=TrangThaiAnhHuanLuyen.cho_duyet,
        nullable=False,
        index=True,
    )
    nguoi_duyet_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="SET NULL"), nullable=True)
    thoi_gian_tao = Column(TIMESTAMP, default=now_vietnam, nullable=False)
    thoi_gian_duyet = Column(DateTime, nullable=True)
    ghi_chu = Column(Text, nullable=True)
    thoi_gian_xoa = Column(DateTime, nullable=True)

    scan = relationship("ScanHistory")
    prediction = relationship("AIPrediction")
    object = relationship("Object")
    reviewer = relationship("User")
    dataset_links = relationship("TrainingDatasetImage", back_populates="training_image", cascade="all, delete-orphan")


class TrainingDatasetVersion(Base):
    __tablename__ = "PhienBanDataset"

    id = Column(Integer, primary_key=True, autoincrement=True)
    ma_phien_ban = Column(String(100), unique=True, nullable=False)
    mo_ta = Column(Text, nullable=True)
    tong_anh = Column(Integer, default=0, nullable=False)
    tong_nhan = Column(Integer, default=0, nullable=False)
    thoi_gian_tao = Column(TIMESTAMP, default=now_vietnam, nullable=False)
    ghi_chu = Column(Text, nullable=True)

    images = relationship("TrainingDatasetImage", back_populates="dataset", cascade="all, delete-orphan")


class TrainingDatasetImage(Base):
    __tablename__ = "AnhHuanLuyenDataset"
    __table_args__ = (
        UniqueConstraint("dataset_id", "anh_huan_luyen_id", name="uq_dataset_training_image"),
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    dataset_id = Column(Integer, ForeignKey("PhienBanDataset.id", ondelete="CASCADE"), nullable=False, index=True)
    anh_huan_luyen_id = Column(Integer, ForeignKey("AnhHuanLuyen.id", ondelete="CASCADE"), nullable=False, index=True)
    thoi_gian_tao = Column(TIMESTAMP, default=now_vietnam, nullable=False)

    dataset = relationship("TrainingDatasetVersion", back_populates="images")
    training_image = relationship("TrainingImage", back_populates="dataset_links")
