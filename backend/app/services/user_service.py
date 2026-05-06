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
        token_data = {"sub": str(user.id)}
        return {
            "access_token": create_access_token(token_data),
            "refresh_token": create_refresh_token(token_data),
            "token_type": "bearer",
            "user": {
                "id": user.id,
                "username": user.ten_dang_nhap,
                "email": user.email,
                "status": user.trang_thai_obj.ten_trang_thai if user.trang_thai_obj else "hoat_dong",
                "role": user.vai_tro_obj.ten_vai_tro if user.vai_tro_obj else "nguoi_dung",
            },
        }

    def register(self, db: Session, data: UserCreate):
        if self.repo.get_by_username_or_email(db, data.username, data.email):
            raise HTTPException(400, "Username hoặc email đã tồn tại")

        role = self.repo.get_role_by_name(db, "nguoi_dung")
        status = self.repo.get_status_by_name(db, "hoat_dong")
        if not role or not status:
            raise HTTPException(500, "Hệ thống chưa khởi tạo dữ liệu vai trò / trạng thái")

        user = User(
            ten_dang_nhap=data.username,
            email=data.email,
            mat_khau_ma_hoa=hash_password(data.password),
            vai_tro_id=role.id,
            trang_thai_id=status.id,
        )
        profile = Profile(ho_ten=data.full_name)
        settings = UserSettings()
        self.repo.create(db, user, profile, settings)

        return self._generate_tokens(user)

    def login(self, db: Session, data: UserLogin):
        user = self.repo.get_by_username(db, data.username)
        if not user or not verify_password(data.password, user.mat_khau_ma_hoa):
            raise HTTPException(401, "Sai tên đăng nhập hoặc mật khẩu")

        status_name = user.trang_thai_obj.ten_trang_thai if user.trang_thai_obj else ""
        if status_name != "hoat_dong":
            raise HTTPException(403, "Tài khoản đã bị khóa")

        user.lan_dang_nhap_cuoi = datetime.utcnow()
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
        return {
            "user_id": profile.user_id,
            "full_name": profile.ho_ten,
            "avatar_url": profile.anh_dai_dien,
            "bio": profile.gioi_thieu,
        }

    def get_settings(self, db: Session, user_id: int):
        settings = self.repo.get_settings(db, user_id)
        if not settings:
            raise HTTPException(404, "Settings not found")
        return {
            "user_id": settings.user_id,
            "native_lang_id": settings.ngon_ngu_me,
            "target_lang_id": settings.ngon_ngu_hoc,
            "native_lang_code": settings.native_lang.ma_ngon_ngu if settings.native_lang else None,
            "target_lang_code": settings.target_lang.ma_ngon_ngu if settings.target_lang else None,
        }

    def update_settings(self, db: Session, user_id: int, data: UserSettingsUpdate):
        settings = self.repo.get_settings(db, user_id)
        if not settings:
            raise HTTPException(404, "Settings not found")

        if data.native_lang_id is not None:
            settings.ngon_ngu_me = data.native_lang_id
        if data.target_lang_id is not None:
            settings.ngon_ngu_hoc = data.target_lang_id

        return self.repo.update_settings(db, settings)
