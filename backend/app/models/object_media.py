from sqlalchemy import Column, Integer, String, Boolean, ForeignKey, DateTime
from sqlalchemy.orm import relationship
from app.db.session import Base


class ObjectMedia(Base):
    __tablename__ = "AnhDoiTuong"

    id = Column(Integer, primary_key=True, autoincrement=True)
    doi_tuong_id = Column(Integer, ForeignKey("DoiTuong.id", ondelete="CASCADE"), nullable=False)
    url = Column(String(255), nullable=False)
    doi_tuong_chinh = Column(Boolean, default=False)
    thoi_gian_xoa = Column(DateTime, nullable=True)

    object = relationship("Object", back_populates="media")
