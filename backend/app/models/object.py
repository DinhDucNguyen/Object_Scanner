from sqlalchemy import Column, Integer, String, ForeignKey, DateTime
from sqlalchemy.orm import relationship
from app.db.session import Base


class Object(Base):
    __tablename__ = "DoiTuong"

    id = Column(Integer, primary_key=True, autoincrement=True)
    danh_muc_id = Column(Integer, ForeignKey("DanhMuc.id"), nullable=True, index=True)
    ma_doi_tuong = Column(String(100), unique=True, nullable=True)
    muc_do_kho = Column(Integer, default=1)
    tao_boi = Column(Integer, ForeignKey("NguoiDung.id"), nullable=True)
    thoi_gian_xoa = Column(DateTime, nullable=True)

    category = relationship("Category", back_populates="objects")
    translations = relationship("Translation", back_populates="object", cascade="all, delete-orphan")
    media = relationship("ObjectMedia", back_populates="object", cascade="all, delete-orphan")
    scan_histories = relationship("ScanHistory", back_populates="object")
