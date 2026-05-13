# pyrefly: ignore [missing-import]
from sqlalchemy import (
    Column, Integer, BigInteger, ForeignKey, DECIMAL, DateTime,
    UniqueConstraint
)
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import relationship
from app.db.session import Base
from app.utils.timezone import now_vietnam


class LearningProgress(Base):
    __tablename__ = "TienDoHoc"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="CASCADE"), nullable=False)
    ban_dich_id = Column(Integer, ForeignKey("BanDich.id", ondelete="CASCADE"), nullable=False)
    do_de_nho = Column(DECIMAL(5, 2), default=2.50)
    khoang_lap = Column(Integer, default=0)
    so_lan_lap = Column(Integer, default=0)
    ngay_on_tiep = Column(DateTime, nullable=True)
    lan_on_cuoi = Column(DateTime, default=now_vietnam)

    __table_args__ = (
        UniqueConstraint("user_id", "ban_dich_id", name="unique_user_translation"),
    )

    user = relationship("User", back_populates="learning_progress")
    translation = relationship("Translation", back_populates="learning_progress")
