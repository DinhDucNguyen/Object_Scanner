from sqlalchemy import (
    Column, Integer, String, DateTime, Enum, TIMESTAMP
)
from sqlalchemy.orm import relationship
from app.db.session import Base
from datetime import datetime
import enum


class UserStatus(str, enum.Enum):
    active = "active"
    banned = "banned"
    pending = "pending"


class UserRole(str, enum.Enum):
    admin = "admin"
    user = "user"


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, autoincrement=True)
    username = Column(String(50), unique=True, nullable=False)
    email = Column(String(100), unique=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    status = Column(Enum(UserStatus), default=UserStatus.active)
    role = Column(Enum(UserRole), default=UserRole.user)
    last_login_at = Column(DateTime, nullable=True)
    created_at = Column(TIMESTAMP, default=datetime.utcnow)
    updated_at = Column(TIMESTAMP, default=datetime.utcnow, onupdate=datetime.utcnow)

    profile = relationship("Profile", back_populates="user", uselist=False, cascade="all, delete-orphan")
    settings = relationship("UserSettings", back_populates="user", uselist=False, cascade="all, delete-orphan")
    learning_progress = relationship("LearningProgress", back_populates="user", cascade="all, delete-orphan")
    collections = relationship("UserCollection", back_populates="user", cascade="all, delete-orphan")
    scan_histories = relationship("ScanHistory", back_populates="user", cascade="all, delete-orphan")
    feedback_reports = relationship("AIFeedbackReport", back_populates="reporter", cascade="all, delete-orphan")
