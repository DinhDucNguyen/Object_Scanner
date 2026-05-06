from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services.history_service import HistoryFeedbackService
from app.dependencies.get_current_user import get_current_user_id

router = APIRouter(prefix="/api", tags=["History"])
history_service = HistoryFeedbackService()


@router.get("/history")
def get_history(
    limit: int = 50,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return history_service.get_history(db, user_id, limit)


@router.get("/predictions/{scan_id}")
def get_predictions(scan_id: int, db: Session = Depends(get_db)):
    return history_service.get_predictions(db, scan_id)
