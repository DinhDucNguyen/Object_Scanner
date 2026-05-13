# pyrefly: ignore [missing-import]
from sqlalchemy import Column, Integer, ForeignKey
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import relationship
from app.db.session import Base


class CollectionItem(Base):
    __tablename__ = "ChiTietBoSuuTap"

    bo_suu_tap_id = Column(Integer, ForeignKey("BoSuuTap.id", ondelete="CASCADE"), primary_key=True)
    ban_dich_id = Column(Integer, ForeignKey("BanDich.id", ondelete="CASCADE"), primary_key=True)

    collection = relationship("UserCollection", back_populates="items")
    translation = relationship("Translation")
