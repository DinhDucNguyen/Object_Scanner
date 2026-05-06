from sqlalchemy import Column, Integer, String, Boolean
from sqlalchemy.orm import relationship
from app.db.session import Base


class Language(Base):
    __tablename__ = "NgonNgu"

    id = Column(Integer, primary_key=True, autoincrement=True)
    ma_ngon_ngu = Column(String(10), unique=True, nullable=False)
    ten_ngon_ngu = Column(String(50), nullable=False)
    icon_co = Column(String(255), nullable=True)
    dang_hoat_dong = Column(Boolean, default=True)

    translations = relationship("Translation", back_populates="language")
