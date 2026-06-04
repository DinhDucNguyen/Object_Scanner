"""
Admin Prediction Router
=======================
REST API để admin kiểm duyệt predictions do Gemini đề xuất.

Endpoints:
  GET    /api/admin/predictions                        — Danh sách (lọc theo trạng thái)
  GET    /api/admin/predictions/export-training        — Xuất JSONL flat
  GET    /api/admin/predictions/export-training-grouped — Xuất JSONL gom nhóm
  GET    /api/admin/predictions/{id}                   — Chi tiết
  POST   /api/admin/predictions/{id}/approve           — Duyệt
  POST   /api/admin/predictions/{id}/alias             — Gán bí danh
  PATCH  /api/admin/predictions/{id}/detach-image      — Bỏ ảnh bổ sung
  PATCH  /api/admin/predictions/{id}/reassign-image    — Chuyển ảnh bổ sung
  POST   /api/admin/predictions/{id}/reject            — Từ chối
  PATCH  /api/admin/predictions/{id}/split-to-new-object — Tách sang object mới
  DELETE /api/admin/predictions/cleanup-known-classes  — Dọn predictions YOLO/COCO
  GET    /api/admin/stats                              — Thống kê kiểm duyệt
"""
import json as _json
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.dependencies.get_current_user import require_admin_nguoi_dung_id
from app.schemas.admin import (
    AliasPredictionRequest,
    AliasPredictionResponse,
    ApproveRequest,
    ApproveResponse,
    PredictionDetailResponse,
    PredictionListItem,
    RejectResponse,
    SplitToNewObjectResponse,
)
from app.services.prediction_service import PredictionService
from app.services.training_service import TrainingService

router = APIRouter(
    prefix="/admin",
    tags=["Admin — Kiểm duyệt"],
    dependencies=[Depends(require_admin_nguoi_dung_id)],
)
prediction_service = PredictionService()
training_service = TrainingService()


@router.get("/predictions", response_model=List[PredictionListItem])
def list_predictions(
    trang_thai: Optional[str] = Query(default="cho_duyet", description="cho_duyet | da_duyet | tu_choi"),
    search: Optional[str] = Query(default=None),
    limit: int = Query(default=50, ge=1),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
):
    """Danh sách predictions của Gemini, lọc theo trạng thái kiểm duyệt."""
    return prediction_service.list_predictions(db, trang_thai=trang_thai, search=search, limit=limit, offset=offset)


@router.get("/predictions/export-training")
def export_training_data(db: Session = Depends(get_db)):
    """Xuất dữ liệu training — predictions Gemini đã duyệt, định dạng JSONL."""
    records = training_service.export_training_data(db)
    content = "\n".join(_json.dumps(r, ensure_ascii=False) for r in records)
    return Response(
        content=content,
        media_type="application/x-ndjson",
        headers={"Content-Disposition": "attachment; filename=yolo_training_data.jsonl"},
    )


@router.get("/predictions/export-training-grouped")
def export_training_grouped(db: Session = Depends(get_db)):
    """Xuất training data gom nhóm theo object — mỗi object có danh sách ảnh đa góc độ."""
    records = training_service.export_training_data_grouped(db)
    content = "\n".join(_json.dumps(r, ensure_ascii=False) for r in records)
    return Response(
        content=content,
        media_type="application/x-ndjson",
        headers={"Content-Disposition": "attachment; filename=yolo_training_grouped.jsonl"},
    )


@router.delete("/predictions/cleanup-known-classes")
def cleanup_known_class_predictions(db: Session = Depends(get_db)):
    """Resolve pending predictions for known YOLO/COCO classes without leaving scan-history junk."""
    from app.models.ai_feedback_report import AIPrediction, TrangThaiDuyet
    from app.services.scan_service import YOLO_KNOWN_CLASSES
    from app.repositories.object_repo import ObjectRepository, normalize_object_code

    known_codes = {normalize_object_code(code) for code in YOLO_KNOWN_CLASSES}
    predictions = db.query(AIPrediction).filter(
        AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
    ).all()

    obj_repo = ObjectRepository()
    matched = 0
    resolved = 0
    skipped_missing_object = []
    linked_lich_su_quet_ids = []
    deleted_prediction_ids = []

    for p in predictions:
        normalized_label = normalize_object_code(p.nhan_du_doan)
        if normalized_label not in known_codes:
            continue

        matched += 1
        obj = obj_repo.get_by_code(db, normalized_label)
        if not obj:
            skipped_missing_object.append({
                "prediction_id": p.id,
                "label": p.nhan_du_doan,
            })
            continue

        if p.scan:
            p.scan.doi_tuong_id = obj.id
            prediction_service.training_images.create_candidate(
                db,
                lich_su_quet_id=p.scan.id,
                url_anh=p.scan.url_anh,
                doi_tuong_id=obj.id,
                nhan=obj.ma_doi_tuong,
                nguon_du_lieu="gemini",
                do_tin_cay=p.do_tin_cay,
                ghi_chu="Resolved known YOLO/COCO class",
            )
            linked_lich_su_quet_ids.append(p.scan.id)

        deleted_prediction_ids.append(p.id)
        db.delete(p)
        resolved += 1

    db.commit()
    return {
        "message": "Da resolve pending predictions cua cac class YOLO/COCO da co object chinh thuc",
        "matched": matched,
        "resolved": resolved,
        "linked_lich_su_quet_ids": linked_lich_su_quet_ids,
        "deleted_prediction_ids": deleted_prediction_ids,
        "skipped_missing_object": skipped_missing_object,
    }


@router.get("/predictions/{prediction_id}", response_model=PredictionDetailResponse)
def get_prediction(prediction_id: int, db: Session = Depends(get_db)):
    """Chi tiết một prediction, bao gồm `vocab_payload` (word, IPA, loại từ, nghĩa TV, 3 ví dụ EN)."""
    result = prediction_service.get_prediction_detail(db, prediction_id)
    if not result:
        raise HTTPException(404, f"Không tìm thấy prediction #{prediction_id}")
    return result


@router.post("/predictions/{prediction_id}/approve", response_model=ApproveResponse)
def approve_prediction(
    prediction_id: int,
    request: ApproveRequest,
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_nguoi_dung_id),
):
    """
    Duyệt prediction → insert vào bảng chính (DoiTuong + BanDich + ViDu).
    Có thể override bất kỳ trường nào trước khi duyệt.
    """
    result = prediction_service.approve_prediction(db, prediction_id, request, admin_id=admin_id)
    if not result.success:
        raise HTTPException(400, result.message)
    return result


@router.post("/predictions/{prediction_id}/alias", response_model=AliasPredictionResponse)
def assign_prediction_alias(
    prediction_id: int,
    request: AliasPredictionRequest,
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_nguoi_dung_id),
):
    """Gán prediction đang chờ duyệt thành bí danh của một đối tượng chính."""
    result = prediction_service.assign_prediction_alias(db, prediction_id, request, admin_id=admin_id)
    if not result.success:
        raise HTTPException(400, result.message)
    return result


@router.patch("/predictions/{prediction_id}/detach-image")
def detach_review_image(prediction_id: int, db: Session = Depends(get_db)):
    """Bỏ một ảnh bổ sung khỏi nhóm kiểm duyệt, không xóa ảnh khỏi lịch sử quét."""
    result = prediction_service.detach_review_image(db, prediction_id)
    if not result.get("success"):
        raise HTTPException(400, result.get("message", "Khong the bo anh"))
    return result


@router.patch("/predictions/{prediction_id}/reassign-image")
def reassign_review_image(
    prediction_id: int,
    target_object_code: str = Query(..., description="Mã đối tượng đích đã có trong DB"),
    db: Session = Depends(get_db),
):
    """Chuyển một ảnh bổ sung sang object chính khác đã có trong DB."""
    result = prediction_service.reassign_review_image(db, prediction_id, target_object_code)
    if not result.get("success"):
        raise HTTPException(400, result.get("message", "Khong the chuyen anh"))
    return result


@router.post("/predictions/{prediction_id}/reject", response_model=RejectResponse)
def reject_prediction(
    prediction_id: int,
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_nguoi_dung_id),
):
    """Từ chối prediction — không insert gì vào bảng chính."""
    result = prediction_service.reject_prediction(db, prediction_id, admin_id=admin_id)
    if not result.success:
        raise HTTPException(400, result.message)
    return result


@router.patch("/predictions/{prediction_id}/split-to-new-object", response_model=SplitToNewObjectResponse)
def split_to_new_object(
    prediction_id: int,
    new_object_code: str = Query(..., description="Mã đối tượng mới (vd: tofu, eraser)"),
    db: Session = Depends(get_db),
):
    """
    Tách ảnh ra khỏi nhóm kiểm duyệt hiện tại và tạo prediction mới với nhãn đúng.
    Gemini sẽ tự sinh vocab cho object_code mới.
    Prediction cũ bị đánh dấu tu_choi với audit trail.
    """
    result = prediction_service.split_to_new_object(db, prediction_id, new_object_code)
    if not result.success:
        raise HTTPException(400, result.message)
    return result


@router.get("/stats")
def moderation_stats(db: Session = Depends(get_db)):
    """Thống kê số lượng predictions theo từng trạng thái."""
    from app.models.ai_feedback_report import AIPrediction, TrangThaiDuyet, VaiTroDuDoan

    counts = {
        status.value: db.query(AIPrediction).filter(
            AIPrediction.trang_thai == status,
            AIPrediction.vai_tro == VaiTroDuDoan.chinh,
        ).count()
        for status in TrangThaiDuyet
    }
    counts["tong"] = sum(counts.values())
    return counts
