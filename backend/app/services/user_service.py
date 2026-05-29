import logging
# pyrefly: ignore [missing-import]
from fastapi import HTTPException
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import Session
from datetime import timedelta
import random

logger = logging.getLogger(__name__)

# pyrefly: ignore [missing-import]
from app.models.user import User
# pyrefly: ignore [missing-import]
from app.models.profile import Profile
from app.models.user_settings import UserSettings
from app.repositories.user_repo import UserRepository
from app.schemas.user import (
    UserCreate, UserLogin, UserSettingsUpdate,
    ForgotPasswordRequest, VerifyOtpRequest,
    ResetPasswordRequest, ChangePasswordRequest,
)
from app.utils.security import hash_password, verify_password, create_access_token, create_refresh_token, decode_token
from app.services.email_service import EmailService
from app.utils.cloudinary_helper import upload_image
from app.utils.timezone import now_vietnam

OTP_EXPIRE_MINUTES = 5

# email -> {"otp": str, "expires_at": datetime}
_otp_store: dict[str, dict] = {}

from app.core.config import settings as _settings
GOOGLE_CLIENT_ID = _settings.GOOGLE_CLIENT_ID or ""


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

    def register(self, db: Session, data: UserCreate) -> dict:
        if self.repo.get_by_email(db, data.email):
            raise HTTPException(400, "Email đã được sử dụng")

        if self.repo.get_by_username(db, data.username):
            raise HTTPException(400, "Tên đăng nhập đã được sử dụng")

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

        return {"message": "Đăng ký thành công"}

    def login(self, db: Session, data: UserLogin):
        user = self.repo.get_by_username(db, data.username)
        if not user or not verify_password(data.password, user.mat_khau_ma_hoa):
            raise HTTPException(401, "Sai tên đăng nhập hoặc mật khẩu")

        status_name = user.trang_thai_obj.ten_trang_thai if user.trang_thai_obj else ""
        if status_name != "hoat_dong":
            raise HTTPException(403, "Tài khoản đã bị khóa")

        user.lan_dang_nhap_cuoi = now_vietnam()
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

    def update_profile(self, db: Session, user_id: int, data) -> dict:
        user = self.repo.get_by_id(db, user_id)
        if not user:
            raise HTTPException(404, "Không tìm thấy người dùng")

        if data.username is not None:
            existing = self.repo.get_by_username(db, data.username)
            if existing and existing.id != user_id:
                raise HTTPException(400, "Tên đăng nhập đã được sử dụng")
            user.ten_dang_nhap = data.username

        profile = self._get_or_create_profile(db, user_id)
        if data.full_name is not None:
            profile.ho_ten = data.full_name
        if data.bio is not None:
            profile.gioi_thieu = data.bio
        db.commit()
        db.refresh(profile)
        db.refresh(user)
        return {
            "user_id": profile.user_id,
            "username": user.ten_dang_nhap,
            "full_name": profile.ho_ten,
            "avatar_url": profile.anh_dai_dien,
            "bio": profile.gioi_thieu,
        }

    def upload_avatar(self, db: Session, user_id: int, image_bytes: bytes, filename: str) -> dict:
        import os, uuid

        profile = self._get_or_create_profile(db, user_id)
        if not image_bytes:
            raise HTTPException(400, "File ảnh không hợp lệ")

        avatar_url = upload_image(image_bytes, folder="object_scanner/avatars")
        if avatar_url is None:
            uploads_dir = "uploads/avatars"
            os.makedirs(uploads_dir, exist_ok=True)
            ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else "jpg"
            if ext not in {"jpg", "jpeg", "png", "webp"}:
                ext = "jpg"
            new_filename = f"{user_id}_{uuid.uuid4().hex[:8]}.{ext}"
            filepath = os.path.join(uploads_dir, new_filename)
            with open(filepath, "wb") as f:
                f.write(image_bytes)
            avatar_url = f"/uploads/avatars/{new_filename}"

        profile.anh_dai_dien = avatar_url
        db.commit()
        return {"avatar_url": avatar_url}

    def get_profile(self, db: Session, user_id: int):
        profile = self._get_or_create_profile(db, user_id)
        user = self.repo.get_by_id(db, user_id)
        return {
            "user_id": profile.user_id,
            "username": user.ten_dang_nhap if user else None,
            "full_name": profile.ho_ten,
            "avatar_url": profile.anh_dai_dien,
            "bio": profile.gioi_thieu,
        }

    def _get_or_create_profile(self, db: Session, user_id: int) -> Profile:
        user = self.repo.get_by_id(db, user_id)
        if not user:
            raise HTTPException(404, "Không tìm thấy người dùng")

        profile = self.repo.get_profile(db, user_id)
        if profile:
            return profile

        profile = Profile(user_id=user_id)
        db.add(profile)
        db.commit()
        db.refresh(profile)
        return profile

    def get_settings(self, db: Session, user_id: int):
        settings = self.repo.get_settings(db, user_id)
        if not settings:
            settings = UserSettings(user_id=user_id)
            db.add(settings)
            db.commit()
            db.refresh(settings)
        return {
            "user_id": settings.user_id,
            "display_language": settings.ngon_ngu_giao_dien or "vi",
            "dark_mode": bool(settings.che_do_toi),
        }

    def update_settings(self, db: Session, user_id: int, data: UserSettingsUpdate):
        settings = self.repo.get_settings(db, user_id)
        if not settings:
            settings = UserSettings(user_id=user_id)
            db.add(settings)

        if data.display_language is not None:
            settings.ngon_ngu_giao_dien = data.display_language
        if data.dark_mode is not None:
            settings.che_do_toi = data.dark_mode

        self.repo.update_settings(db, settings)
        return {
            "user_id": settings.user_id,
            "display_language": settings.ngon_ngu_giao_dien or "vi",
            "dark_mode": bool(settings.che_do_toi),
        }

    def forgot_password(self, db: Session, data: ForgotPasswordRequest) -> dict:
        user = self.repo.get_by_email(db, data.email)
        if not user:
            logger.debug("forgot_password: email not found: %s", data.email)
            raise ValueError("Email không tồn tại")

        email = user.email.lower()
        otp_code = str(random.randint(100000, 999999))
        _otp_store[email] = {
            "otp": otp_code,
            "expires_at": now_vietnam() + timedelta(minutes=OTP_EXPIRE_MINUTES),
        }

        ok = EmailService().send_otp(email, otp_code)
        logger.debug("forgot_password: sent OTP to %s, result=%s", email, ok)
        masked = self._mask_email(email)
        return {"message": "Mã OTP đã được gửi", "email": email, "masked_email": masked}

    @staticmethod
    def _mask_email(email: str) -> str:
        at = email.find("@")
        if at < 2:
            return email
        local = email[:at]
        domain = email[at:]
        return local[0] + "*" * (len(local) - 2) + local[-1] + domain

    def verify_otp(self, _db: Session, data: VerifyOtpRequest) -> dict:
        entry = _otp_store.get(data.email)
        if not entry or entry["otp"] != data.otp_code or entry["expires_at"] < now_vietnam():
            raise HTTPException(400, "OTP không hợp lệ hoặc đã hết hạn")
        return {"message": "OTP hợp lệ", "valid": True}

    def reset_password(self, db: Session, data: ResetPasswordRequest) -> dict:
        entry = _otp_store.get(data.email)
        if not entry or entry["otp"] != data.otp_code or entry["expires_at"] < now_vietnam():
            raise HTTPException(400, "OTP không hợp lệ hoặc đã hết hạn")

        user = self.repo.get_by_email(db, data.email)
        if not user:
            raise HTTPException(400, "OTP không hợp lệ hoặc đã hết hạn")

        user.mat_khau_ma_hoa = hash_password(data.new_password)
        db.commit()
        _otp_store.pop(data.email, None)

        return {"message": "Đặt lại mật khẩu thành công"}

    def change_password(self, db: Session, user_id: int, data: ChangePasswordRequest) -> dict:
        user = self.repo.get_by_id(db, user_id)
        if not user:
            raise HTTPException(404, "Không tìm thấy người dùng")

        if not verify_password(data.current_password, user.mat_khau_ma_hoa):
            raise HTTPException(400, "Mật khẩu hiện tại không đúng")

        user.mat_khau_ma_hoa = hash_password(data.new_password)
        db.commit()

        return {"message": "Đổi mật khẩu thành công"}

    def google_login(self, db: Session, id_token: str) -> dict:
        try:
            from google.oauth2 import id_token as google_id_token
            from google.auth.transport import requests as google_requests
            idinfo = google_id_token.verify_oauth2_token(
                id_token,
                google_requests.Request(),
                GOOGLE_CLIENT_ID,
            )
        except Exception as e:
            logger.warning("google_login: invalid token: %s", e)
            raise HTTPException(401, "Google token không hợp lệ")

        email = idinfo.get("email", "").lower().strip()
        full_name = idinfo.get("name", "")
        if not email:
            raise HTTPException(400, "Không lấy được email từ Google")

        user = self.repo.get_by_email(db, email)
        if user:
            status_name = user.trang_thai_obj.ten_trang_thai if user.trang_thai_obj else ""
            if status_name != "hoat_dong":
                raise HTTPException(403, "Tài khoản đã bị khóa")
        else:
            role = self.repo.get_role_by_name(db, "nguoi_dung")
            status = self.repo.get_status_by_name(db, "hoat_dong")
            if not role or not status:
                raise HTTPException(500, "Hệ thống chưa khởi tạo dữ liệu vai trò / trạng thái")

            base_username = email.split("@")[0].replace(".", "_")[:45]
            username = base_username
            counter = 1
            while self.repo.get_by_username(db, username):
                username = f"{base_username}_{counter}"
                counter += 1

            user = User(
                ten_dang_nhap=username,
                email=email,
                mat_khau_ma_hoa=hash_password(__import__('os').urandom(32).hex()),
                vai_tro_id=role.id,
                trang_thai_id=status.id,
            )
            profile = Profile(ho_ten=full_name)
            settings = UserSettings()
            self.repo.create(db, user, profile, settings)

        user.lan_dang_nhap_cuoi = now_vietnam()
        db.commit()

        return self._generate_tokens(user)
