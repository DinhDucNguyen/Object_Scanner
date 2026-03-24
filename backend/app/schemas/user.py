import re
from pydantic import BaseModel, field_validator
from typing import Optional

MIN_PASSWORD_LENGTH = 6
USERNAME_PATTERN = re.compile(r"^[a-zA-Z0-9_]{3,50}$")
EMAIL_PATTERN = re.compile(r"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$")


class UserCreate(BaseModel):
    username: str
    email: str
    password: str
    full_name: Optional[str] = None
    student_id: Optional[str] = None

    @field_validator("username")
    @classmethod
    def validate_username(cls, v: str) -> str:
        if not USERNAME_PATTERN.match(v):
            raise ValueError("Username phải từ 3-50 ký tự, chỉ gồm chữ, số và underscore")
        return v

    @field_validator("email")
    @classmethod
    def validate_email(cls, v: str) -> str:
        if not EMAIL_PATTERN.match(v):
            raise ValueError("Email không hợp lệ")
        return v.lower()

    @field_validator("password")
    @classmethod
    def validate_password(cls, v: str) -> str:
        if len(v) < MIN_PASSWORD_LENGTH:
            raise ValueError(f"Mật khẩu phải có ít nhất {MIN_PASSWORD_LENGTH} ký tự")
        return v


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
    user_id: int
    full_name: Optional[str]
    avatar_url: Optional[str]
    bio: Optional[str]
    student_id: Optional[str]
    university: Optional[str]

    class Config:
        from_attributes = True


class UserSettingsResponse(BaseModel):
    user_id: int
    native_lang_code: str
    target_lang_code: str
    theme: str
    ai_precision_threshold: float

    class Config:
        from_attributes = True


class UserSettingsUpdate(BaseModel):
    native_lang_code: Optional[str] = None
    target_lang_code: Optional[str] = None
    theme: Optional[str] = None
    ai_precision_threshold: Optional[float] = None


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user: UserResponse


class RefreshRequest(BaseModel):
    refresh_token: str

