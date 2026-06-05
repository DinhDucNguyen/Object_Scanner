from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from sqlalchemy.orm import Session
from app.core.limiter import limiter
from app.utils.upload import read_upload_bytes

from app.db.session import get_db
from app.services.user_service import UserService
from app.schemas.user import (
    UserCreate, UserLogin, ProfileResponse,
    UserSettingsResponse, UserSettingsUpdate,
    TokenResponse, RefreshRequest,
    ForgotPasswordRequest, ForgotPasswordResponse, VerifyOtpRequest,
    ResetPasswordRequest, ChangePasswordRequest,
    ProfileUpdateRequest, AvatarUploadResponse,
    MessageResponse, GoogleLoginRequest,
)
from app.dependencies.get_current_user import get_current_nguoi_dung_id
from app.schemas.user import DeleteAccountRequest

router = APIRouter(prefix="/api/auth", tags=["Auth"])
user_service = UserService()


@router.post("/register", response_model=MessageResponse)
@limiter.limit("5/minute")
def register(request: Request, data: UserCreate, db: Session = Depends(get_db)):
    return user_service.register(db, data)


@router.post("/register/resend-otp", response_model=MessageResponse)
@limiter.limit("3/minute")
def resend_registration_otp(request: Request, data: ForgotPasswordRequest, db: Session = Depends(get_db)):
    return user_service.resend_registration_otp(db, data)


@router.post("/register/verify-otp", response_model=MessageResponse)
@limiter.limit("5/minute")
def verify_registration_otp(request: Request, data: VerifyOtpRequest, db: Session = Depends(get_db)):
    return user_service.verify_registration_otp(db, data)


@router.post("/login", response_model=TokenResponse)
@limiter.limit("10/minute")
def login(request: Request, data: UserLogin, db: Session = Depends(get_db)):
    return user_service.login(db, data)


@router.post("/refresh", response_model=TokenResponse)
def refresh_token(data: RefreshRequest, db: Session = Depends(get_db)):
    return user_service.refresh_token(db, data.refresh_token)


@router.get("/profile", response_model=ProfileResponse)
def get_profile(
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return user_service.get_profile(db, nguoi_dung_id)


@router.put("/profile", response_model=ProfileResponse)
def update_profile(
    data: ProfileUpdateRequest,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id),
):
    return user_service.update_profile(db, nguoi_dung_id, data)


@router.post("/profile/avatar", response_model=AvatarUploadResponse)
async def upload_avatar(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id),
):
    if file.content_type and not file.content_type.startswith("image/"):
        raise HTTPException(400, "File phải là ảnh")
    image_bytes = await read_upload_bytes(file)
    return user_service.upload_avatar(db, nguoi_dung_id, image_bytes, file.filename or "avatar.jpg")


@router.get("/settings", response_model=UserSettingsResponse)
def get_settings(
    db: Session = Depends(get_db), 
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return user_service.get_settings(db, nguoi_dung_id)


@router.put("/settings", response_model=UserSettingsResponse)
def update_settings(
    data: UserSettingsUpdate,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return user_service.update_settings(db, nguoi_dung_id, data)


@router.post("/forgot-password", response_model=ForgotPasswordResponse)
@limiter.limit("3/minute")
def forgot_password(request: Request, data: ForgotPasswordRequest, db: Session = Depends(get_db)):
    try:
        return user_service.forgot_password(db, data)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.post("/verify-otp", response_model=MessageResponse)
@limiter.limit("5/minute")
def verify_otp(request: Request, data: VerifyOtpRequest, db: Session = Depends(get_db)):
    """Kiểm tra OTP có hợp lệ không (chưa dùng, chưa hết hạn)."""
    return user_service.verify_otp(db, data)


@router.post("/reset-password", response_model=MessageResponse)
@limiter.limit("5/minute")
def reset_password(request: Request, data: ResetPasswordRequest, db: Session = Depends(get_db)):
    """Đặt lại mật khẩu mới bằng OTP đã xác thực."""
    return user_service.reset_password(db, data)


@router.put("/change-password", response_model=MessageResponse)
def change_password(
    data: ChangePasswordRequest,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id),
):
    """Đổi mật khẩu cho user đã đăng nhập (cần JWT)."""
    return user_service.change_password(db, nguoi_dung_id, data)


@router.post("/google", response_model=TokenResponse)
@limiter.limit("10/minute")
def google_login(request: Request, data: GoogleLoginRequest, db: Session = Depends(get_db)):
    """Đăng nhập / đăng ký bằng Google idToken từ Android."""
    return user_service.google_login(db, data.id_token)


@router.delete("/account", response_model=MessageResponse)
def delete_account(
    data: DeleteAccountRequest,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id),
):
    """Xóa tài khoản vĩnh viễn — yêu cầu xác nhận mật khẩu."""
    return user_service.delete_account(db, nguoi_dung_id, data.password)
