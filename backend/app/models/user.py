from sqlalchemy import Column, Integer, String, DateTime, TIMESTAMP, ForeignKey
from sqlalchemy.orm import relationship
from app.db.session import Base
from app.utils.timezone import now_vietnam


class User(Base):
    __tablename__ = "NguoiDung"

    id = Column(Integer, primary_key=True, autoincrement=True)
    ten_dang_nhap = Column(String(50), unique=True, nullable=False)
    email = Column(String(100), unique=True, nullable=False)
    mat_khau_ma_hoa = Column(String(255), nullable=False)
    vai_tro_id = Column(Integer, ForeignKey("VaiTro.id"), nullable=False)
    trang_thai_id = Column(Integer, ForeignKey("TrangThaiNguoiDung.id"), nullable=False)
    lan_dang_nhap_cuoi = Column(DateTime, nullable=True)
    ngay_tao = Column(TIMESTAMP, default=now_vietnam)
    ngay_cap_nhat = Column(TIMESTAMP, default=now_vietnam, onupdate=now_vietnam)
    thoi_gian_xoa = Column(DateTime, nullable=True)

    vai_tro_obj = relationship("VaiTro", back_populates="users")
    trang_thai_obj = relationship("TrangThaiNguoiDung", back_populates="users")
    profile = relationship("Profile", back_populates="user", uselist=False, cascade="all, delete-orphan")
    settings = relationship("UserSettings", back_populates="user", uselist=False, cascade="all, delete-orphan")
    learning_progress = relationship("LearningProgress", back_populates="user", cascade="all, delete-orphan")
    collections = relationship("UserCollection", back_populates="user", cascade="all, delete-orphan")
    scan_histories = relationship("ScanHistory", back_populates="user", cascade="all, delete-orphan")
