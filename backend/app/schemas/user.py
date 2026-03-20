from pydantic import BaseModel
from typing import Optional


class UserCreate(BaseModel):
    username: str
    email: str
    password: str
    full_name: Optional[str] = None
    student_id: Optional[str] = None


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

