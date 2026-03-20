from pydantic_settings import BaseSettings
from typing import Optional
import os


class Settings(BaseSettings):
    # Database
    DATABASE_URL: str = "mysql+pymysql://root:@localhost:3306/language_learning_db"
    
    # App
    APP_NAME: str = "Object Language API"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = True
    
    # JWT (cho tương lai)
    SECRET_KEY: str = "your-secret-key-change-in-production"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    
    # Gemini API (cho fallback translation)
    GEMINI_API_KEY: Optional[str] = None
    
    class Config:
        env_file = ".env"
        extra = "allow"


settings = Settings()
