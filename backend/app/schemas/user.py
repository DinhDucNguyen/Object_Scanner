import re
from pydantic import BaseModel, field_validator
from typing import Optional

MIN_PASSWORD_LENGTH = 8
LOWERCASE_PATTERN = re.compile(r"[a-z]")
UPPERCASE_PATTERN = re.compile(r"[A-Z]")
DIGIT_PATTERN = re.compile(r"\d")
USERNAME_PATTERN = re.compile(r"^[a-zA-Z0-9_]{3,50}$")
EMAIL_PATTERN = re.compile(r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$")


def normalize_email(v: str) -> str:
    email = v.strip().lower()
    if not EMAIL_PATTERN.match(email):
        raise ValueError("Email không hợp lệ")
    return email


def validate_password_strength(v: str, field_name: str = "Mật khẩu") -> str:
    if len(v) < MIN_PASSWORD_LENGTH:
        raise ValueError(f"{field_name} phải có ít nhất {MIN_PASSWORD_LENGTH} ký tự")
    if not LOWERCASE_PATTERN.search(v):
        raise ValueError(f"{field_name} phải có ít nhất 1 chữ thường")
    if not UPPERCASE_PATTERN.search(v):
        raise ValueError(f"{field_name} phải có ít nhất 1 chữ viết hoa")
    if not DIGIT_PATTERN.search(v):
        raise ValueError(f"{field_name} phải có ít nhất 1 chữ số")
    return v


class UserCreate(BaseModel):
    username: str
    email: str
    password: str
    full_name: Optional[str] = None

    @field_validator("username")
    @classmethod
    def validate_username(cls, v: str) -> str:
        username = v.strip()
        if not USERNAME_PATTERN.match(username):
            raise ValueError("Username phải từ 3-50 ký tự, chỉ gồm chữ, số và underscore")
        return username

    @field_validator("email")
    @classmethod
    def validate_email(cls, v: str) -> str:
        return normalize_email(v)

    @field_validator("password")
    @classmethod
    def validate_password(cls, v: str) -> str:
        return validate_password_strength(v)


class UserLogin(BaseModel):
    username: str
    password: str


class UserResponse(BaseModel):
    id: int
    username: str
    email: str
    status: str
    role: str

    class Config:
        from_attributes = True


class ProfileResponse(BaseModel):
    nguoi_dung_id: int
    username: Optional[str] = None
    full_name: Optional[str] = None
    avatar_url: Optional[str] = None
    bio: Optional[str] = None

    class Config:
        from_attributes = True


class UserSettingsResponse(BaseModel):
    nguoi_dung_id: int
    display_language: str = "vi"
    dark_mode: bool = False

    class Config:
        from_attributes = True


class UserSettingsUpdate(BaseModel):
    display_language: Optional[str] = None
    dark_mode: Optional[bool] = None

    @field_validator("display_language")
    @classmethod
    def validate_display_language(cls, value: Optional[str]) -> Optional[str]:
        if value is None:
            return value
        normalized = value.strip().lower()
        if normalized not in {"vi", "en"}:
            raise ValueError("display_language chỉ hỗ trợ 'vi' hoặc 'en'")
        return normalized


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user: UserResponse


class RefreshRequest(BaseModel):
    refresh_token: str


class ForgotPasswordRequest(BaseModel):
    email: str

    @field_validator("email")
    @classmethod
    def validate_email(cls, v: str) -> str:
        return normalize_email(v)


class ForgotPasswordResponse(BaseModel):
    message: str
    email: str
    masked_email: str


class VerifyOtpRequest(BaseModel):
    email: str
    otp_code: str

    @field_validator("email")
    @classmethod
    def validate_email(cls, v: str) -> str:
        return normalize_email(v)


class ResetPasswordRequest(BaseModel):
    email: str
    otp_code: str
    new_password: str

    @field_validator("email")
    @classmethod
    def validate_email(cls, v: str) -> str:
        return normalize_email(v)

    @field_validator("new_password")
    @classmethod
    def validate_new_password(cls, v: str) -> str:
        return validate_password_strength(v, "Mật khẩu mới")


class ChangePasswordRequest(BaseModel):
    current_password: str
    new_password: str

    @field_validator("new_password")
    @classmethod
    def validate_new_password(cls, v: str) -> str:
        return validate_password_strength(v, "Mật khẩu mới")


class ProfileUpdateRequest(BaseModel):
    username: Optional[str] = None
    full_name: Optional[str] = None
    bio: Optional[str] = None

    @field_validator("username")
    @classmethod
    def validate_username(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return v
        username = v.strip()
        if not USERNAME_PATTERN.match(username):
            raise ValueError("Username phải từ 3-50 ký tự, chỉ gồm chữ, số và underscore")
        return username


class AvatarUploadResponse(BaseModel):
    avatar_url: str


class MessageResponse(BaseModel):
    message: str


class GoogleLoginRequest(BaseModel):
    id_token: str


class DeleteAccountRequest(BaseModel):
    password: Optional[str] = None


class GoogleDeleteAccountRequest(BaseModel):
    id_token: str
