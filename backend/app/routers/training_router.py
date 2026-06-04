"""
Admin Training Router
=====================
REST API for managing training images and dataset versions.
"""
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.dependencies.get_current_user import require_admin_nguoi_dung_id
from app.services.training_service import TrainingService

router = APIRouter(
    prefix="/admin",
    tags=["Admin - Training Data"],
    dependencies=[Depends(require_admin_nguoi_dung_id)],
)
training_service = TrainingService()


@router.get("/training-summary")
def training_summary(
    model_coverage: Optional[str] = Query(default=None, description="custom_yolo | coco_known | db_only | new_gemini"),
    recommendation: Optional[str] = Query(default=None, description="high_priority | recommended | optional | not_needed"),
    status: Optional[str] = Query(default=None, description="cho_duyet | da_duyet | tu_choi"),
    source: Optional[str] = Query(default=None, description="yolo | gemini | admin"),
    search: Optional[str] = Query(default=None),
    db: Session = Depends(get_db),
):
    return training_service.training_summary(
        db,
        model_coverage=model_coverage,
        recommendation=recommendation,
        status=status,
        source=source,
        search=search,
    )


@router.patch("/training-images/{training_image_id}/approve")
def approve_training_image(
    training_image_id: int,
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_nguoi_dung_id),
):
    item = training_service.approve_training_image(db, training_image_id, admin_id=admin_id)
    if not item:
        raise HTTPException(404, "Khong tim thay anh training")
    return {
        "message": "Da duyet anh training",
        "lich_su_quet_id": item.lich_su_quet_id,
        "training_image_id": item.id,
    }


@router.patch("/training-images/{training_image_id}/unlink")
def unlink_training_image(
    training_image_id: int,
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_nguoi_dung_id),
):
    item = training_service.reject_training_image(db, training_image_id, admin_id=admin_id)
    if not item:
        raise HTTPException(404, "Khong tim thay anh training")
    return {
        "message": "Da tu choi anh training",
        "lich_su_quet_id": item.lich_su_quet_id,
        "training_image_id": item.id,
    }


@router.delete("/training-images/unlink-all")
def unlink_all_training_images(
    object_code: str = Query(..., description="Object code to reject from the training pool"),
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_nguoi_dung_id),
):
    count = training_service.reject_training_images_for_object(db, object_code, admin_id=admin_id)
    return {"message": f"Da loai {count} anh khoi pool training cua '{object_code}'", "count": count}


@router.delete("/training-images/{training_image_id}")
def delete_training_image(
    training_image_id: int,
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_nguoi_dung_id),
):
    item = training_service.delete_training_image(db, training_image_id, admin_id=admin_id)
    if not item:
        raise HTTPException(404, "Khong tim thay anh training")
    return {"message": "Da xoa anh training", "training_image_id": item.id}


@router.post("/training-datasets")
def create_training_dataset(
    ma_phien_ban: str = Query(..., description="Ma phien ban dataset, vi du v1"),
    ghi_chu: Optional[str] = Query(default=None),
    db: Session = Depends(get_db),
):
    try:
        dataset = training_service.create_dataset_version(db, ma_phien_ban=ma_phien_ban, ghi_chu=ghi_chu)
    except ValueError as exc:
        raise HTTPException(400, str(exc))
    return {
        "message": f"Da tao dataset {dataset.ma_phien_ban}",
        "id": dataset.id,
        "ma_phien_ban": dataset.ma_phien_ban,
        "tong_anh": dataset.tong_anh,
        "tong_nhan": dataset.tong_nhan,
    }


@router.patch("/training-images/{training_image_id}/reassign")
def reassign_training_image(
    training_image_id: int,
    target_object_code: str = Query(..., description="Ma doi tuong dich"),
    db: Session = Depends(get_db),
):
    item, target_obj = training_service.reassign_training_image(db, training_image_id, target_object_code)
    if not item:
        raise HTTPException(404, "Khong tim thay anh training")
    if not target_obj:
        raise HTTPException(404, f"Khong tim thay doi tuong '{target_object_code}'")
    return {
        "message": f"Da chuyen anh sang '{target_object_code}'",
        "lich_su_quet_id": item.lich_su_quet_id,
        "training_image_id": item.id,
        "new_object_id": target_obj.id,
    }
