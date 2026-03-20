from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from typing import List, Optional

from app.db.session import get_db
from app.services.history_service import HistoryFeedbackService
from app.schemas.common import FeedbackCreate, FeedbackResponse
from app.dependencies.get_current_user import get_current_user_id

router = APIRouter(prefix="/api", tags=["History & Feedback"])
history_service = HistoryFeedbackService()


@router.get("/history")
def get_history(
    limit: int = 50, 
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return history_service.get_history(db, user_id, limit)


@router.post("/feedback", response_model=FeedbackResponse)
def submit_feedback(
    data: FeedbackCreate, 
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return history_service.submit_feedback(db, user_id, data)


@router.get("/feedback", response_model=List[FeedbackResponse])
def get_feedback(
    is_resolved: Optional[bool] = None, 
    db: Session = Depends(get_db)
):
    return history_service.get_feedback(db, is_resolved)
