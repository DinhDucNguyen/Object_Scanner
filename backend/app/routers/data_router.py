# pyrefly: ignore [missing-import]
from fastapi import APIRouter, Depends
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import Session
# pyrefly: ignore [missing-import]
from typing import List, Optional

from app.db.session import get_db
from app.services.data_service import DataService
from app.schemas.common import StatsResponse, LanguageResponse, CategoryResponse
from app.dependencies.get_current_user import get_current_nguoi_dung_id

router = APIRouter(prefix="/api", tags=["Data"])
data_service = DataService()


@router.get("/languages", response_model=List[LanguageResponse])
def get_languages(db: Session = Depends(get_db)):
    return data_service.get_languages(db)


@router.get("/categories", response_model=List[CategoryResponse])
def get_categories(db: Session = Depends(get_db)):
    return data_service.get_categories(db)


@router.get("/objects")
def get_all_objects(category_id: Optional[int] = None, db: Session = Depends(get_db)):
    return data_service.get_all_objects(db, category_id)


@router.get("/stats", response_model=StatsResponse)
def get_stats(
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return data_service.get_stats(db, nguoi_dung_id)
