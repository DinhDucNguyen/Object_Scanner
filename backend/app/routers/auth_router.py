from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services.user_service import UserService
from app.schemas.user import (
    UserCreate, UserLogin, ProfileResponse,
    UserSettingsResponse, UserSettingsUpdate,
    TokenResponse, RefreshRequest
)
from app.dependencies.get_current_user import get_current_user_id

router = APIRouter(prefix="/api/auth", tags=["Auth"])
user_service = UserService()


@router.post("/register", response_model=TokenResponse)
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
