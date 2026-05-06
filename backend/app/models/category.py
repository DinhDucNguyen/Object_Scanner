from sqlalchemy import Column, Integer, String, Text, ForeignKey, DateTime
from sqlalchemy.orm import relationship
from app.db.session import Base


class Category(Base):
    __tablename__ = "DanhMuc"

    id = Column(Integer, primary_key=True, autoincrement=True)
    ten_danh_muc = Column(String(100), nullable=True)
    danh_muc_cha = Column(Integer, ForeignKey("DanhMuc.id", ondelete="SET NULL"), nullable=True)
    mo_ta = Column(Text, nullable=True)
    thoi_gian_xoa = Column(DateTime, nullable=True)

    parent = relationship("Category", remote_side=[id])
    objects = relationship("Object", back_populates="category")
