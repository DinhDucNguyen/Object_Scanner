from sqlalchemy import Column, Integer, String, Boolean, ForeignKey, DateTime
from sqlalchemy.orm import relationship
from app.db.session import Base
from app.utils.timezone import now_vietnam


class PasswordResetOTP(Base):
    __tablename__ = "OTPDatLaiMatKhau"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="CASCADE"), nullable=False, index=True)
    otp_code = Column(String(6), nullable=False)
    expires_at = Column(DateTime, nullable=False)
    da_su_dung = Column(Boolean, default=False)
    ngay_tao = Column(DateTime, default=now_vietnam)

    user = relationship("User")
