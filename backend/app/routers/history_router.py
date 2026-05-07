from fastapi import APIRouter, Depends, File, Form, Request, UploadFile
from typing import Optional
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services.history_service import HistoryFeedbackService
from app.dependencies.get_current_user import get_current_user_id
from app.schemas.common import LichSuQuetResponse

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


@router.post("/lich-su-quet", response_model=LichSuQuetResponse)
async def save_lich_su_quet(
    request: Request,
    object_code: str = Form(...),
    confidence: float = Form(0.0),
    image: Optional[UploadFile] = File(None),
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id),
):
    image_bytes = await image.read() if image else None
    base_url = str(request.base_url).rstrip("/")
    return history_service.save_with_image(db, user_id, object_code, confidence, image_bytes, base_url)
