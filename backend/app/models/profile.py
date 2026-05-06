from sqlalchemy import Column, Integer, String, ForeignKey, Date
from sqlalchemy.orm import relationship
from app.db.session import Base


class Profile(Base):
    __tablename__ = "HoSo"

    user_id         = Column(Integer, ForeignKey("NguoiDung.id", ondelete="CASCADE"), primary_key=True)
    ho_ten          = Column(String(100), nullable=True)
    anh_dai_dien    = Column(String(255), default="default_avatar.png")
    gioi_thieu      = Column(String(500), nullable=True)

    # ── Streak tracking ──────────────────────────────────────────────────────
    streak_hien_tai = Column(Integer, default=0, nullable=False)   # chuỗi ngày hiện tại
    streak_dai_nhat = Column(Integer, default=0, nullable=False)   # kỷ lục cá nhân
    tong_luot_on    = Column(Integer, default=0, nullable=False)   # tổng số lần ôn tập
    ngay_on_cuoi    = Column(Date, nullable=True)                  # ngày ôn gần nhất

    user = relationship("User", back_populates="profile")
