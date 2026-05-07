from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    # Database
    DATABASE_URL: str = "mysql+pymysql://root:@localhost:3307/HeThongHocNgonNgu"
    
    # App
    APP_NAME: str = "Object Language API"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = True
    
    # JWT — SECRET_KEY bắt buộc phải set trong .env
    SECRET_KEY: str
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7
    
    # Gemini API
    GEMINI_API_KEY: Optional[str] = None

    # Cloudinary
    CLOUDINARY_CLOUD_NAME: Optional[str] = None
    CLOUDINARY_API_KEY: Optional[str] = None
    CLOUDINARY_API_SECRET: Optional[str] = None
    
    class Config:
        env_file = ".env"
        extra = "allow"


settings = Settings()
