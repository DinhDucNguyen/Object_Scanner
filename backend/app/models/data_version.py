from sqlalchemy import Column, Integer, String, TIMESTAMP
from app.db.session import Base
from datetime import datetime


class DataVersion(Base):
    __tablename__ = "data_versions"

    id = Column(Integer, primary_key=True, autoincrement=True)
    table_name = Column(String(50), unique=True, nullable=False)
    version_number = Column(Integer, default=1)
    last_updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)
