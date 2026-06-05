import logging
# pyrefly: ignore [missing-import]
from fastapi import HTTPException
# pyrefly: ignore [missing-import]
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from datetime import timedelta
import os
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
FORGOT_PASSWORD_GENERIC_MESSAGE = (
    "Nếu email đã đăng ký và đủ điều kiện đặt lại mật khẩu, mã OTP sẽ được gửi trong ít phút."
)

# email -> {"otp": str, "expires_at": datetime}
_otp_store: dict[str, dict] = {}
_registration_otp_store: dict[str, dict] = {}

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

    def _ensure_user_can_authenticate(self, user: User) -> None:
        if not bool(user.email_da_xac_thuc):
            raise HTTPException(403, "Vui lòng xác thực email trước khi đăng nhập")

        status_name = user.trang_thai_obj.ten_trang_thai if user.trang_thai_obj else ""
        if status_name != "hoat_dong":
            raise HTTPException(403, "Tài khoản đã bị khóa")

    def register(self, db: Session, data: UserCreate) -> dict:
        existing_email = self.repo.get_by_email(db, data.email)
        if existing_email and bool(existing_email.email_da_xac_thuc):
            raise HTTPException(400, "Email đã được sử dụng")

        existing_username = self.repo.get_by_username(db, data.username)
        if existing_username and (not existing_email or existing_username.id != existing_email.id):
            raise HTTPException(400, "Tên đăng nhập đã được sử dụng")

        role = self.repo.get_role_by_name(db, "nguoi_dung")
        status = self.repo.get_status_by_name(db, "hoat_dong")
        if not role or not status:
            raise HTTPException(500, "Hệ thống chưa khởi tạo dữ liệu vai trò / trạng thái")

        if existing_email:
            # Không ghi đè — chỉ gửi lại OTP để tránh người khác chiếm tài khoản
            self._send_registration_otp(existing_email.email)
            return {"message": "Email đã đăng ký nhưng chưa xác thực. Đã gửi lại mã OTP."}

        # Gửi OTP trước — nếu thất bại thì không tạo user
        self._send_registration_otp(data.email)

        user = User(
            ten_dang_nhap=data.username,
            email=data.email,
            email_da_xac_thuc=False,
            mat_khau_ma_hoa=hash_password(data.password),
            vai_tro_id=role.id,
            trang_thai_id=status.id,
        )
        profile = Profile(ho_ten=data.full_name)
        settings = UserSettings()
        self.repo.create(db, user, profile, settings)
        return {"message": "Mã OTP đã được gửi đến email. Vui lòng xác thực để hoàn tất đăng ký."}


    def login(self, db: Session, data: UserLogin):
        user = self.repo.get_by_username(db, data.username)
        if not user or not verify_password(data.password, user.mat_khau_ma_hoa):
            raise HTTPException(401, "Sai tên đăng nhập hoặc mật khẩu")

        self._ensure_user_can_authenticate(user)

        user.lan_dang_nhap_cuoi = now_vietnam()
        db.commit()

        return self._generate_tokens(user)

    def _send_registration_otp(self, email: str) -> None:
        normalized = email.lower()
        otp_code = str(random.randint(100000, 999999))
        _registration_otp_store[normalized] = {
            "otp": otp_code,
            "expires_at": now_vietnam() + timedelta(minutes=OTP_EXPIRE_MINUTES),
        }
        ok = EmailService().send_otp(normalized, otp_code, purpose="register")
        logger.debug("register: sent OTP to %s, result=%s", normalized, ok)
        if not ok:
            _registration_otp_store.pop(normalized, None)
            raise HTTPException(500, "Không thể gửi email. Vui lòng kiểm tra lại địa chỉ email.")

    def refresh_token(self, db: Session, refresh_token: str):
        payload = decode_token(refresh_token)
        if payload is None or payload.get("type") != "refresh":
            raise HTTPException(401, "Refresh token không hợp lệ hoặc đã hết hạn")

        try:
            nguoi_dung_id = int(payload.get("sub"))
        except (TypeError, ValueError):
            raise HTTPException(401, "Refresh token không hợp lệ hoặc đã hết hạn")

        user = self.repo.get_by_id(db, nguoi_dung_id)
        if not user:
            raise HTTPException(404, "User not found")
        self._ensure_user_can_authenticate(user)

        return self._generate_tokens(user)

    def update_profile(self, db: Session, nguoi_dung_id: int, data) -> dict:
        user = self.repo.get_by_id(db, nguoi_dung_id)
        if not user:
            raise HTTPException(404, "Không tìm thấy người dùng")

        if data.username is not None:
            existing = self.repo.get_by_username(db, data.username)
            if existing and existing.id != nguoi_dung_id:
                raise HTTPException(400, "Tên đăng nhập đã được sử dụng")
            user.ten_dang_nhap = data.username

        profile = self._get_or_create_profile(db, nguoi_dung_id)
        if data.full_name is not None:
            profile.ho_ten = data.full_name
        if data.bio is not None:
            profile.gioi_thieu = data.bio
        db.commit()
        db.refresh(profile)
        db.refresh(user)
        return {
            "nguoi_dung_id": profile.nguoi_dung_id,
            "username": user.ten_dang_nhap,
            "full_name": profile.ho_ten,
            "avatar_url": profile.anh_dai_dien,
            "bio": profile.gioi_thieu,
        }

    def upload_avatar(self, db: Session, nguoi_dung_id: int, image_bytes: bytes, filename: str) -> dict:
        import os, uuid

        profile = self._get_or_create_profile(db, nguoi_dung_id)
        if not image_bytes:
            raise HTTPException(400, "File ảnh không hợp lệ")

        avatar_url = upload_image(image_bytes, folder="object_scanner/avatars")
        if avatar_url is None:
            uploads_dir = "uploads/avatars"
            os.makedirs(uploads_dir, exist_ok=True)
            ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else "jpg"
            if ext not in {"jpg", "jpeg", "png", "webp"}:
                ext = "jpg"
            new_filename = f"{nguoi_dung_id}_{uuid.uuid4().hex[:8]}.{ext}"
            filepath = os.path.join(uploads_dir, new_filename)
            with open(filepath, "wb") as f:
                f.write(image_bytes)
            avatar_url = f"/uploads/avatars/{new_filename}"

        profile.anh_dai_dien = avatar_url
        db.commit()
        return {"avatar_url": avatar_url}

    def get_profile(self, db: Session, nguoi_dung_id: int):
        profile = self._get_or_create_profile(db, nguoi_dung_id)
        user = self.repo.get_by_id(db, nguoi_dung_id)
        return {
            "nguoi_dung_id": profile.nguoi_dung_id,
            "username": user.ten_dang_nhap if user else None,
            "full_name": profile.ho_ten,
            "avatar_url": profile.anh_dai_dien,
            "bio": profile.gioi_thieu,
        }

    def _get_or_create_profile(self, db: Session, nguoi_dung_id: int) -> Profile:
        user = self.repo.get_by_id(db, nguoi_dung_id)
        if not user:
            raise HTTPException(404, "Không tìm thấy người dùng")

        profile = self.repo.get_profile(db, nguoi_dung_id)
        if profile:
            return profile

        profile = Profile(nguoi_dung_id=nguoi_dung_id)
        db.add(profile)
        db.commit()
        db.refresh(profile)
        return profile

    def get_settings(self, db: Session, nguoi_dung_id: int):
        settings = self.repo.get_settings(db, nguoi_dung_id)
        if not settings:
            settings = UserSettings(nguoi_dung_id=nguoi_dung_id)
            db.add(settings)
            db.commit()
            db.refresh(settings)
        return {
            "nguoi_dung_id": settings.nguoi_dung_id,
            "display_language": settings.ngon_ngu_giao_dien or "vi",
            "dark_mode": bool(settings.che_do_toi),
        }

    def update_settings(self, db: Session, nguoi_dung_id: int, data: UserSettingsUpdate):
        settings = self.repo.get_settings(db, nguoi_dung_id)
        if not settings:
            settings = UserSettings(nguoi_dung_id=nguoi_dung_id)
            db.add(settings)

        if data.display_language is not None:
            settings.ngon_ngu_giao_dien = data.display_language
        if data.dark_mode is not None:
            settings.che_do_toi = data.dark_mode

        self.repo.update_settings(db, settings)
        return {
            "nguoi_dung_id": settings.nguoi_dung_id,
            "display_language": settings.ngon_ngu_giao_dien or "vi",
            "dark_mode": bool(settings.che_do_toi),
        }

    def delete_account(self, db: Session, nguoi_dung_id: int, password: str | None) -> dict:
        user = self.repo.get_by_id(db, nguoi_dung_id)
        if not user:
            raise HTTPException(404, "Không tìm thấy người dùng")
        password = (password or "").strip()
        is_google_account = bool(getattr(user, "ma_dinh_danh_google", None))
        if not is_google_account and not password:
            raise HTTPException(400, "Vui lòng nhập mật khẩu để xóa tài khoản")
        if password and not verify_password(password, user.mat_khau_ma_hoa):
            raise HTTPException(400, "Mật khẩu không đúng")
        db.delete(user)
        db.commit()
        return {"message": "Tài khoản đã được xóa vĩnh viễn"}

    def forgot_password(self, db: Session, data: ForgotPasswordRequest) -> dict:
        email = data.email.lower()
        user = self.repo.get_by_email(db, data.email)
        if not user or not bool(user.email_da_xac_thuc):
            logger.debug("forgot_password: email unavailable for reset: %s", data.email)
            return {
                "message": FORGOT_PASSWORD_GENERIC_MESSAGE,
                "email": email,
                "masked_email": self._mask_email(email),
            }

        email = user.email.lower()
        otp_code = str(random.randint(100000, 999999))
        _otp_store[email] = {
            "otp": otp_code,
            "expires_at": now_vietnam() + timedelta(minutes=OTP_EXPIRE_MINUTES),
            "verified": False,
        }

        ok = EmailService().send_otp(email, otp_code, purpose="reset")
        logger.debug("forgot_password: sent OTP to %s, result=%s", email, ok)
        if not ok:
            _otp_store.pop(email, None)
            raise HTTPException(500, "Không thể gửi email. Vui lòng kiểm tra lại địa chỉ email.")
        masked = self._mask_email(email)
        return {"message": FORGOT_PASSWORD_GENERIC_MESSAGE, "email": email, "masked_email": masked}

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
        if entry.get("verified"):
            raise HTTPException(400, "OTP đã được xác thực. Vui lòng tiếp tục đặt lại mật khẩu.")
        entry["verified"] = True
        return {"message": "OTP hợp lệ", "valid": True}

    def resend_registration_otp(self, db: Session, data: ForgotPasswordRequest) -> dict:
        user = self.repo.get_by_email(db, data.email)
        if not user:
            raise HTTPException(404, "Email chưa được đăng ký")
        if bool(user.email_da_xac_thuc):
            raise HTTPException(400, "Email đã được xác thực")
        self._send_registration_otp(user.email)
        return {"message": "Đã gửi lại mã OTP"}

    def verify_registration_otp(self, db: Session, data: VerifyOtpRequest) -> dict:
        entry = _registration_otp_store.get(data.email)
        if not entry or entry["otp"] != data.otp_code or entry["expires_at"] < now_vietnam():
            raise HTTPException(400, "OTP không hợp lệ hoặc đã hết hạn")

        user = self.repo.get_by_email(db, data.email)
        if not user:
            raise HTTPException(404, "Email chưa được đăng ký")

        user.email_da_xac_thuc = True
        db.commit()
        _registration_otp_store.pop(data.email, None)
        return {"message": "Xác thực email thành công. Vui lòng đăng nhập."}

    def reset_password(self, db: Session, data: ResetPasswordRequest) -> dict:
        entry = _otp_store.get(data.email)
        if not entry or entry["otp"] != data.otp_code or entry["expires_at"] < now_vietnam():
            raise HTTPException(400, "OTP không hợp lệ hoặc đã hết hạn")
        if not entry.get("verified"):
            raise HTTPException(400, "Vui lòng xác thực OTP trước khi đặt lại mật khẩu")

        user = self.repo.get_by_email(db, data.email)
        if not user:
            raise HTTPException(400, "OTP không hợp lệ hoặc đã hết hạn")

        user.mat_khau_ma_hoa = hash_password(data.new_password)
        db.commit()
        _otp_store.pop(data.email, None)

        return {"message": "Đặt lại mật khẩu thành công"}

    def change_password(self, db: Session, nguoi_dung_id: int, data: ChangePasswordRequest) -> dict:
        user = self.repo.get_by_id(db, nguoi_dung_id)
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
        ma_dinh_danh_google = (idinfo.get("sub") or "").strip()
        email_verified = bool(idinfo.get("email_verified"))
        full_name = idinfo.get("name", "")
        if not email:
            raise HTTPException(400, "Không lấy được email từ Google")
        if not ma_dinh_danh_google:
            raise HTTPException(400, "Không lấy được mã định danh Google")

        if not email_verified:
            raise HTTPException(401, "Email Google chưa được xác thực")

        user = (
            db.query(User)
            .filter(User.ma_dinh_danh_google == ma_dinh_danh_google, User.thoi_gian_xoa.is_(None))
            .first()
        )
        if not user:
            user = self.repo.get_by_email(db, email)
            # Không link Google ID vào tài khoản email chưa xác thực — tránh account takeover
            if user and not bool(user.email_da_xac_thuc):
                user = None
        if user:
            user.email_da_xac_thuc = True
            if not getattr(user, "ma_dinh_danh_google", None):
                user.ma_dinh_danh_google = ma_dinh_danh_google
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
                email_da_xac_thuc=True,
                ma_dinh_danh_google=ma_dinh_danh_google,
                mat_khau_ma_hoa=hash_password(os.urandom(32).hex()),
                vai_tro_id=role.id,
                trang_thai_id=status.id,
            )
            profile = Profile(ho_ten=full_name)
            settings = UserSettings()
            user.profile = profile
            user.settings = settings
            db.add(user)

        user.lan_dang_nhap_cuoi = now_vietnam()
        try:
            db.commit()
        except IntegrityError:
            db.rollback()
            raise HTTPException(409, "Tài khoản Google đang được xử lý. Vui lòng thử lại.")

        return self._generate_tokens(user)
