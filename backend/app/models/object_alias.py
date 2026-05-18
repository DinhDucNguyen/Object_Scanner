# pyrefly: ignore [missing-import]
from sqlalchemy import Column, Integer, String, ForeignKey, TIMESTAMP
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import relationship

from app.db.session import Base
from app.utils.timezone import now_vietnam


class ObjectAlias(Base):
    __tablename__ = "BiDanhDoiTuong"

    id = Column(Integer, primary_key=True, autoincrement=True)
    doi_tuong_id = Column(Integer, ForeignKey("DoiTuong.id", ondelete="CASCADE"), nullable=False, index=True)
    ma_bi_danh = Column(String(100), unique=True, nullable=False, index=True)
    ten_hien_thi = Column(String(255), nullable=True)
    ngon_ngu = Column(String(10), nullable=True)
    thoi_gian_tao = Column(TIMESTAMP, default=now_vietnam)

    object = relationship("Object", back_populates="aliases")
