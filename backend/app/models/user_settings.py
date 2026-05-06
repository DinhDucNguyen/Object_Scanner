from sqlalchemy import Column, Integer, ForeignKey
from sqlalchemy.orm import relationship
from app.db.session import Base


class UserSettings(Base):
    __tablename__ = "CaiDatNguoiDung"

    user_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="CASCADE"), primary_key=True)
    ngon_ngu_me = Column(Integer, ForeignKey("NgonNgu.id"), default=1)
    ngon_ngu_hoc = Column(Integer, ForeignKey("NgonNgu.id"), default=2)

    user = relationship("User", back_populates="settings")
    native_lang = relationship("Language", foreign_keys=[ngon_ngu_me])
    target_lang = relationship("Language", foreign_keys=[ngon_ngu_hoc])
