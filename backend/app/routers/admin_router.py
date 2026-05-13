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
import os
import uuid

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile
from sqlalchemy.orm import Session
from typing import List, Optional

from app.db.session import get_db
from app.models.object import Object
from app.models.object_media import ObjectMedia
from app.services.admin_service import AdminService
from app.services.object_media_service import set_primary_object_image
from app.utils.timezone import now_vietnam
from app.utils.cloudinary_helper import upload_image
from app.schemas.admin import (
    PredictionListItem,
    PredictionDetailResponse,
    ApproveRequest,
    ApproveResponse,
    RejectResponse,
)

router = APIRouter(prefix="/admin", tags=["Admin — Kiểm duyệt từ vựng"])
admin_service = AdminService()
OBJECT_UPLOAD_DIR = "uploads/objects"


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


@router.get("/objects/{object_code}/media")
def list_object_media(object_code: str, db: Session = Depends(get_db)):
    obj = _get_object_or_404(db, object_code)
    return [
        {
            "id": media.id,
            "object_id": media.doi_tuong_id,
            "object_code": obj.ma_doi_tuong,
            "url": media.url,
            "is_primary": bool(media.doi_tuong_chinh),
        }
        for media in obj.media
        if media.thoi_gian_xoa is None
    ]


@router.post("/objects/{object_code}/media")
async def add_object_media(
    object_code: str,
    request: Request,
    image_url: Optional[str] = Form(default=None),
    is_primary: bool = Form(default=True),
    image: Optional[UploadFile] = File(default=None),
    db: Session = Depends(get_db),
):
    """
    Add a canonical vocabulary image into AnhDoiTuong.

    Images uploaded here are curated object images for flashcards/explore/review,
    not user scan-history photos from LichSuQuet.url_anh.
    """
    obj = _get_object_or_404(db, object_code)

    final_url = (image_url or "").strip() or None
    if image:
        if image.content_type and not image.content_type.startswith("image/"):
            raise HTTPException(400, "File phai la anh")
        image_bytes = await image.read()
        final_url = upload_image(image_bytes, folder="object_scanner/objects")
        if final_url is None:
            os.makedirs(OBJECT_UPLOAD_DIR, exist_ok=True)
            extension = _extension_from_content_type(image.content_type)
            filename = f"{uuid.uuid4().hex}{extension}"
            filepath = os.path.join(OBJECT_UPLOAD_DIR, filename)
            with open(filepath, "wb") as f:
                f.write(image_bytes)
            final_url = f"{str(request.base_url).rstrip('/')}/uploads/objects/{filename}"

    if not final_url:
        raise HTTPException(400, "Can gui image_url hoac file image")

    media = ObjectMedia(
        doi_tuong_id=obj.id,
        url=final_url,
        doi_tuong_chinh=is_primary,
    )
    db.add(media)
    db.flush()
    if is_primary:
        set_primary_object_image(db, obj, media)
    db.commit()
    db.refresh(media)

    return {
        "id": media.id,
        "object_id": media.doi_tuong_id,
        "object_code": obj.ma_doi_tuong,
        "url": media.url,
        "is_primary": bool(media.doi_tuong_chinh),
    }


@router.post("/objects/media/{media_id}/primary")
def set_media_primary(media_id: int, db: Session = Depends(get_db)):
    media = db.query(ObjectMedia).filter(
        ObjectMedia.id == media_id,
        ObjectMedia.thoi_gian_xoa.is_(None),
    ).first()
    if not media:
        raise HTTPException(404, "Khong tim thay anh doi tuong")
    set_primary_object_image(db, media.object, media)
    db.commit()
    return {"message": "Da dat anh chinh", "media_id": media.id}


@router.delete("/objects/media/{media_id}")
def delete_object_media(media_id: int, db: Session = Depends(get_db)):
    media = db.query(ObjectMedia).filter(
        ObjectMedia.id == media_id,
        ObjectMedia.thoi_gian_xoa.is_(None),
    ).first()
    if not media:
        raise HTTPException(404, "Khong tim thay anh doi tuong")
    media.thoi_gian_xoa = now_vietnam()
    db.commit()
    return {"message": "Da xoa anh doi tuong", "media_id": media.id}


def _get_object_or_404(db: Session, object_code: str) -> Object:
    obj = db.query(Object).filter(
        Object.ma_doi_tuong == object_code.lower().strip(),
        Object.thoi_gian_xoa.is_(None),
    ).first()
    if not obj:
        raise HTTPException(404, "Khong tim thay doi tuong")
    return obj


def _extension_from_content_type(content_type: str | None) -> str:
    mapping = {
        "image/png": ".png",
        "image/webp": ".webp",
        "image/gif": ".gif",
        "image/jpeg": ".jpg",
        "image/jpg": ".jpg",
    }
    return mapping.get((content_type or "").lower(), ".jpg")
