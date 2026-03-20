from fastapi import HTTPException
from sqlalchemy.orm import Session
from datetime import datetime

from app.models.user import User
from app.models.profile import Profile
from app.models.user_settings import UserSettings
from app.repositories.user_repo import UserRepository
from app.schemas.user import UserCreate, UserLogin, UserSettingsUpdate
from app.utils.security import hash_password, verify_password, create_access_token, create_refresh_token, decode_token


class UserService:
    def __init__(self):
        self.repo = UserRepository()

    def _generate_tokens(self, user: User) -> dict:
        """Tạo access + refresh token cho user."""
        token_data = {"sub": str(user.id)}
        return {
            "access_token": create_access_token(token_data),
            "refresh_token": create_refresh_token(token_data),
            "token_type": "bearer",
            "user": user,
        }

    def register(self, db: Session, data: UserCreate):
        if self.repo.get_by_username_or_email(db, data.username, data.email):
            raise HTTPException(400, "Username hoặc email đã tồn tại")

        user = User(
            username=data.username,
            email=data.email,
            password_hash=hash_password(data.password),
        )
        profile = Profile(full_name=data.full_name, student_id=data.student_id)
        settings = UserSettings()
        self.repo.create(db, user, profile, settings)

        return self._generate_tokens(user)

    def login(self, db: Session, data: UserLogin):
        user = self.repo.get_by_username(db, data.username)
        if not user or not verify_password(data.password, user.password_hash):
            raise HTTPException(401, "Sai tên đăng nhập hoặc mật khẩu")
        if user.status != "active":
            raise HTTPException(403, "Tài khoản đã bị khóa")

        user.last_login_at = datetime.utcnow()
        db.commit()

        return self._generate_tokens(user)

    def refresh_token(self, db: Session, refresh_token: str):
        payload = decode_token(refresh_token)
        if payload is None or payload.get("type") != "refresh":
            raise HTTPException(401, "Refresh token không hợp lệ hoặc đã hết hạn")

        user_id = payload.get("sub")
        user = self.repo.get_by_id(db, int(user_id))
        if not user:
            raise HTTPException(404, "User not found")

        return self._generate_tokens(user)

    def get_profile(self, db: Session, user_id: int):
        profile = self.repo.get_profile(db, user_id)
        if not profile:
            raise HTTPException(404, "Profile not found")
        return profile

    def get_settings(self, db: Session, user_id: int):
        settings = self.repo.get_settings(db, user_id)
        if not settings:
            raise HTTPException(404, "Settings not found")
        return settings

    def update_settings(self, db: Session, user_id: int, data: UserSettingsUpdate):
        settings = self.repo.get_settings(db, user_id)
        if not settings:
            raise HTTPException(404, "Settings not found")

        for field, value in data.model_dump(exclude_unset=True).items():
            setattr(settings, field, value)

        return self.repo.update_settings(db, settings)
