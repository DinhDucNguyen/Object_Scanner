from sqlalchemy import BigInteger, Column, DateTime, DECIMAL, ForeignKey, Integer
from sqlalchemy.orm import relationship

from app.db.session import Base
from app.utils.timezone import now_vietnam


class ReviewLog(Base):
    __tablename__ = "LichSuOnTap"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="CASCADE"), nullable=False)
    tien_do_hoc_id = Column(BigInteger, ForeignKey("TienDoHoc.id", ondelete="CASCADE"), nullable=False)
    ban_dich_id = Column(Integer, ForeignKey("BanDich.id", ondelete="CASCADE"), nullable=False)
    chat_luong = Column(Integer, nullable=False)
    thoi_diem_on = Column(DateTime, default=now_vietnam, nullable=False)
    khoang_lap_cu = Column(Integer, nullable=True)
    khoang_lap_moi = Column(Integer, nullable=True)
    do_de_nho_cu = Column(DECIMAL(5, 2), nullable=True)
    do_de_nho_moi = Column(DECIMAL(5, 2), nullable=True)
    so_lan_lap_cu = Column(Integer, nullable=True)
    so_lan_lap_moi = Column(Integer, nullable=True)
    ngay_on_tiep = Column(DateTime, nullable=True)

    user = relationship("User")
    progress = relationship("LearningProgress")
    translation = relationship("Translation")
