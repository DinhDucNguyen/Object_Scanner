# pyrefly: ignore [missing-import]
from sqlalchemy import Column, Integer, String, ForeignKey, TIMESTAMP, Enum
# pyrefly: ignore [missing-import]
from app.db.session import Base
from app.utils.timezone import now_vietnam
import enum


class NguonTraCuu(str, enum.Enum):
    he_thong = "he_thong"
    gemini = "gemini"


class DictionaryLookup(Base):
    __tablename__ = "TraTuDien"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("NguoiDung.id", ondelete="CASCADE"), nullable=True)
    tu_tra = Column(String(255), nullable=False)
    ngon_ngu_tra_id = Column(Integer, ForeignKey("NgonNgu.id", ondelete="CASCADE"), nullable=True)
    doi_tuong_id = Column(Integer, ForeignKey("DoiTuong.id", ondelete="SET NULL"), nullable=True)
    nguon_du_lieu = Column(Enum(NguonTraCuu), nullable=True)
    lan_tra_cuoi = Column(TIMESTAMP, default=now_vietnam)
