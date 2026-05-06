"""
Admin Moderation Router
=======================
REST API để admin kiểm duyệt từ vựng do Gemini đề xuất.

Endpoints:
  GET  /api/admin/predictions             — Danh sách (lọc theo trạng thái)
  GET  /api/admin/predictions/{id}        — Chi tiết kèm vocab_payload
  POST /api/admin/predictions/{id}/approve — Duyệt → insert DoiTuong/BanDich/ViDu
  POST /api/admin/predictions/{id}/reject  — Từ chối
  GET  /api/admin/stats                   — Thống kê nhanh
"""
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Optional

from app.db.session import get_db
from app.services.admin_service import AdminService
from app.schemas.admin import (
    PredictionListItem,
    PredictionDetailResponse,
    ApproveRequest,
    ApproveResponse,
    RejectResponse,
)

router = APIRouter(prefix="/admin", tags=["Admin — Kiểm duyệt từ vựng"])
admin_service = AdminService()


@router.get("/predictions", response_model=List[PredictionListItem])
def list_predictions(
    trang_thai: Optional[str] = Query(default="cho_duyet", description="cho_duyet | da_duyet | tu_choi"),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
):
    """Danh sách predictions của Gemini, lọc theo trạng thái kiểm duyệt."""
    return admin_service.list_predictions(db, trang_thai=trang_thai, limit=limit, offset=offset)


@router.get("/predictions/{prediction_id}", response_model=PredictionDetailResponse)
def get_prediction(prediction_id: int, db: Session = Depends(get_db)):
    """Chi tiết một prediction, bao gồm `vocab_payload` (word, IPA, loại từ, nghĩa TV, 3 ví dụ EN)."""
    result = admin_service.get_prediction_detail(db, prediction_id)
    if not result:
        raise HTTPException(404, f"Không tìm thấy prediction #{prediction_id}")
    return result


@router.post("/predictions/{prediction_id}/approve", response_model=ApproveResponse)
def approve_prediction(
    prediction_id: int,
    request: ApproveRequest,
    db: Session = Depends(get_db),
):
    """
    Duyệt prediction → insert vào bảng chính (DoiTuong + BanDich + ViDu).
    Có thể override bất kỳ trường nào trước khi duyệt.
    """
    result = admin_service.approve_prediction(db, prediction_id, request)
    if not result.success:
        raise HTTPException(400, result.message)
    return result


@router.post("/predictions/{prediction_id}/reject", response_model=RejectResponse)
def reject_prediction(prediction_id: int, db: Session = Depends(get_db)):
    """Từ chối prediction — không insert gì vào bảng chính."""
    result = admin_service.reject_prediction(db, prediction_id)
    if not result.success:
        raise HTTPException(400, result.message)
    return result


@router.get("/stats")
def moderation_stats(db: Session = Depends(get_db)):
    """Thống kê số lượng predictions theo từng trạng thái."""
    from app.models.ai_feedback_report import AIPrediction, TrangThaiDuyet

    counts = {
        status.value: db.query(AIPrediction).filter(AIPrediction.trang_thai == status).count()
        for status in TrangThaiDuyet
    }
    counts["tong"] = sum(counts.values())
    return counts
