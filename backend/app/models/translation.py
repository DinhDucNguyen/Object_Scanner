from sqlalchemy import (
    Column, Integer, String, Text, Boolean, ForeignKey, UniqueConstraint, TIMESTAMP, Enum, DateTime
)
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime
import enum


class NguonDuLieu(str, enum.Enum):
    thu_cong = "thu_cong"
    gemini = "gemini"


class Translation(Base):
    __tablename__ = "BanDich"

    id = Column(Integer, primary_key=True, autoincrement=True)
    doi_tuong_id = Column(Integer, ForeignKey("DoiTuong.id", ondelete="CASCADE"), nullable=True, index=True)
    ngon_ngu_id = Column(Integer, ForeignKey("NgonNgu.id", ondelete="CASCADE"), nullable=True, index=True)
    tu_vung = Column(String(255), nullable=True)
    phien_am = Column(String(255), nullable=True)
    loai_tu = Column(String(50), nullable=True)
    dinh_nghia = Column(Text, nullable=True)
    am_thanh_url = Column(String(255), nullable=True)
    nguon_du_lieu = Column(Enum(NguonDuLieu), default=NguonDuLieu.thu_cong)
    da_xac_nhan = Column(Boolean, default=False)
    ngay_tao = Column(TIMESTAMP, default=datetime.utcnow)
    thoi_gian_xoa = Column(DateTime, nullable=True)

    __table_args__ = (
        UniqueConstraint("doi_tuong_id", "ngon_ngu_id", name="unique_trans"),
    )

    object = relationship("Object", back_populates="translations")
    language = relationship("Language", back_populates="translations")
    examples = relationship("ViDu", back_populates="translation", cascade="all, delete-orphan")
    learning_progress = relationship("LearningProgress", back_populates="translation", cascade="all, delete-orphan")
