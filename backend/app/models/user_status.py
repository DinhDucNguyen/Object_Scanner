from sqlalchemy import Column, Integer, String
from sqlalchemy.orm import relationship
from app.db.session import Base


class TrangThaiNguoiDung(Base):
    __tablename__ = "TrangThaiNguoiDung"

    id = Column(Integer, primary_key=True, autoincrement=True)
    ten_trang_thai = Column(String(50), unique=True)

    users = relationship("User", back_populates="trang_thai_obj")
