from sqlalchemy import Column, Integer, String
from sqlalchemy.orm import relationship
from app.db.session import Base


class VaiTro(Base):
    __tablename__ = "VaiTro"

    id = Column(Integer, primary_key=True, autoincrement=True)
    ten_vai_tro = Column(String(50), unique=True)

    users = relationship("User", back_populates="vai_tro_obj")
