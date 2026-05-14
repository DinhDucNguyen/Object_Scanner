from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services.user_service import UserService
from app.schemas.user import (
    UserCreate, UserLogin, ProfileResponse,
    UserSettingsResponse, UserSettingsUpdate,
    TokenResponse, RefreshRequest,
    ForgotPasswordRequest, ForgotPasswordResponse, VerifyOtpRequest,
    ResetPasswordRequest, ChangePasswordRequest,
    ProfileUpdateRequest, AvatarUploadResponse,
    MessageResponse,
)
from app.dependencies.get_current_user import get_current_user_id

router = APIRouter(prefix="/api/auth", tags=["Auth"])
user_service = UserService()


@router.post("/register", response_model=MessageResponse)
def register(data: UserCreate, db: Session = Depends(get_db)):
    return user_service.register(db, data)


@router.post("/login", response_model=TokenResponse)
def login(data: UserLogin, db: Session = Depends(get_db)):
    return user_service.login(db, data)


@router.post("/refresh", response_model=TokenResponse)
def refresh_token(data: RefreshRequest, db: Session = Depends(get_db)):
    return user_service.refresh_token(db, data.refresh_token)


@router.get("/profile", response_model=ProfileResponse)
def get_profile(
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return user_service.get_profile(db, user_id)


@router.put("/profile", response_model=ProfileResponse)
def update_profile(
    data: ProfileUpdateRequest,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id),
):
    return user_service.update_profile(db, user_id, data)


@router.post("/profile/avatar", response_model=AvatarUploadResponse)
async def upload_avatar(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id),
):
    image_bytes = await file.read()
    return user_service.upload_avatar(db, user_id, image_bytes, file.filename or "avatar.jpg")


@router.get("/settings", response_model=UserSettingsResponse)
def get_settings(
    db: Session = Depends(get_db), 
    user_id: int = Depends(get_current_user_id)
):
    return user_service.get_settings(db, user_id)


@router.put("/settings", response_model=UserSettingsResponse)
def update_settings(
    data: UserSettingsUpdate,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return user_service.update_settings(db, user_id, data)


@router.post("/forgot-password", response_model=ForgotPasswordResponse)
def forgot_password(data: ForgotPasswordRequest, db: Session = Depends(get_db)):
    try:
        return user_service.forgot_password(db, data)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.post("/verify-otp", response_model=MessageResponse)
def verify_otp(data: VerifyOtpRequest, db: Session = Depends(get_db)):
    """Kiểm tra OTP có hợp lệ không (chưa dùng, chưa hết hạn)."""
    return user_service.verify_otp(db, data)


@router.post("/reset-password", response_model=MessageResponse)
def reset_password(data: ResetPasswordRequest, db: Session = Depends(get_db)):
    """Đặt lại mật khẩu mới bằng OTP đã xác thực."""
    return user_service.reset_password(db, data)


@router.put("/change-password", response_model=MessageResponse)
def change_password(
    data: ChangePasswordRequest,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id),
):
    """Đổi mật khẩu cho user đã đăng nhập (cần JWT)."""
    return user_service.change_password(db, user_id, data)
