from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile
from datetime import date
from typing import Optional
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.services.history_service import HistoryFeedbackService
from app.dependencies.get_current_user import get_current_nguoi_dung_id
from app.schemas.common import LichSuQuetResponse, HistoryPaginatedResponse
from app.utils.upload import read_upload_bytes

router = APIRouter(prefix="/api", tags=["History"])
history_service = HistoryFeedbackService()


@router.get("/history")
def get_history(
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
    keyword: Optional[str] = Query(None),
    period: Optional[str] = Query("all"),
    from_date: Optional[date] = Query(None),
    to_date: Optional[date] = Query(None),
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return history_service.get_history(
        db, nguoi_dung_id, limit, offset, keyword, period, from_date, to_date
    )


@router.get("/history/{lich_su_quet_id}")
def get_history_detail(
    lich_su_quet_id: int,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id),
):
    detail = history_service.get_history_detail(db, nguoi_dung_id, lich_su_quet_id)
    if detail is None:
        raise HTTPException(404, "History item not found")
    return detail


@router.delete("/history/{lich_su_quet_id}")
def delete_history(
    lich_su_quet_id: int,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id),
):
    deleted = history_service.delete_history(db, nguoi_dung_id, lich_su_quet_id)
    if not deleted:
        raise HTTPException(404, "History item not found")
    return {"message": "Đã xóa lịch sử quét"}


@router.get("/predictions/{lich_su_quet_id}")
def get_predictions(
    lich_su_quet_id: int,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id),
):
    predictions = history_service.get_predictions(db, lich_su_quet_id, nguoi_dung_id)
    if predictions is None:
        raise HTTPException(404, "History item not found")
    return predictions


@router.post("/lich-su-quet", response_model=LichSuQuetResponse)
async def save_lich_su_quet(
    request: Request,
    object_code: str = Form(...),
    confidence: float = Form(0.0),
    training_source: str = Form("yolo"),
    image: Optional[UploadFile] = File(None),
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id),
):
    if image and image.content_type and not image.content_type.startswith("image/"):
        raise HTTPException(400, "File phải là ảnh")
    image_bytes = await read_upload_bytes(image) if image else None
    base_url = str(request.base_url).rstrip("/")
    return history_service.save_with_image(
        db,
        nguoi_dung_id,
        object_code,
        confidence,
        image_bytes,
        base_url,
        training_source,
    )
