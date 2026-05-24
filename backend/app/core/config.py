from pydantic import field_validator
from pydantic_settings import BaseSettings
from pathlib import Path
from typing import Optional


class Settings(BaseSettings):
    # Database
    DATABASE_URL: str = "mysql+pymysql://object_app:ObjectApp123456@localhost:3306/HeThongHocNgonNgu?charset=utf8mb4"
    
    # App
    APP_NAME: str = "LengoLens API"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    CORS_ALLOW_ORIGINS: list[str] = []
    ADMIN_ROLE_NAMES: list[str] = ["admin", "quan_tri", "quan_tri_vien"]
    ADMIN_USER_IDS: list[int] = []

    # JWT — SECRET_KEY bắt buộc phải set trong .env
    SECRET_KEY: str
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 480
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7
    
    # Gemini API
    GEMINI_API_KEY: Optional[str] = None

    # Cloudinary
    CLOUDINARY_CLOUD_NAME: Optional[str] = None
    CLOUDINARY_API_KEY: Optional[str] = None
    CLOUDINARY_API_SECRET: Optional[str] = None

    # SMTP Email
    SMTP_HOST: str = "smtp.gmail.com"
    SMTP_PORT: int = 587
    SMTP_USER: Optional[str] = None
    SMTP_PASSWORD: Optional[str] = None
    SMTP_FROM: str = "no-reply@objectscanner.app"

    @field_validator("DEBUG", mode="before")
    @classmethod
    def parse_debug_mode(cls, value):
        if isinstance(value, str):
            normalized = value.strip().lower()
            if normalized in {"release", "production", "prod"}:
                return False
            if normalized in {"debug", "development", "dev"}:
                return True
        return value

    @field_validator("CORS_ALLOW_ORIGINS", mode="before")
    @classmethod
    def parse_cors_origins(cls, value):
        if isinstance(value, str):
            value = value.strip()
            if not value:
                return []
            return [origin.strip() for origin in value.split(",") if origin.strip()]
        return value

    @field_validator("ADMIN_ROLE_NAMES", mode="before")
    @classmethod
    def parse_admin_role_names(cls, value):
        if isinstance(value, str):
            value = value.strip()
            if not value:
                return []
            return [role.strip() for role in value.split(",") if role.strip()]
        return value

    @field_validator("ADMIN_USER_IDS", mode="before")
    @classmethod
    def parse_admin_user_ids(cls, value):
        if isinstance(value, str):
            value = value.strip()
            if not value:
                return []
            return [int(user_id.strip()) for user_id in value.split(",") if user_id.strip()]
        return value
    
    class Config:
        env_file = str(Path(__file__).resolve().parents[2] / ".env")
        extra = "allow"


settings = Settings()
