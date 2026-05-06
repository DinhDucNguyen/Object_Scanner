from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from typing import List

from app.db.session import get_db
from app.services.learning_service import LearningService
from app.schemas.common import ReviewRequest, ReviewCardResponse, ReviewResult
from app.dependencies.get_current_user import get_current_user_id

router = APIRouter(prefix="/api", tags=["Review"])
learning_service = LearningService()


@router.post("/learning/add")
def add_to_learning(
    translation_id: int, 
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return learning_service.add_to_learning(db, translation_id, user_id)


@router.get("/review", response_model=List[ReviewCardResponse])
def get_due_reviews(
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return learning_service.get_due_reviews(db, user_id)


@router.get("/analytics")
def get_analytics(
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return learning_service.get_analytics(db, user_id)


@router.post("/review/{progress_id}", response_model=ReviewResult)
def submit_review(
    progress_id: int, 
    request: ReviewRequest, 
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return learning_service.submit_review(db, progress_id, request)
