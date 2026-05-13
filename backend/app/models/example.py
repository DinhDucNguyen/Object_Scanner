# pyrefly: ignore [missing-import]
from sqlalchemy import Column, Integer, String, Text, ForeignKey
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import relationship
from app.db.session import Base


class ViDu(Base):
    __tablename__ = "ViDu"

    id = Column(Integer, primary_key=True, autoincrement=True)
    ban_dich_id = Column(Integer, ForeignKey("BanDich.id", ondelete="CASCADE"), nullable=True, index=True)
    cau_vi_du = Column(Text, nullable=True)
    dich_nghia = Column(Text, nullable=True)
    nguon_du_lieu = Column(String(50), nullable=True)

    translation = relationship("Translation", back_populates="examples")
