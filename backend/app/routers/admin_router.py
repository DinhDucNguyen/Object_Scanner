"""
Admin Moderation Router
=======================
REST API để admin kiểm duyệt từ vựng do Gemini đề xuất.

Endpoints:
  GET  /api/admin/predictions                    — Danh sách (lọc theo trạng thái)
  GET  /api/admin/predictions/export-training         — Xuất JSONL flat (1 dòng/prediction)
  GET  /api/admin/predictions/export-training-grouped — Xuất JSONL gom nhóm theo object + ảnh đa góc
  GET  /api/admin/predictions/{id}               — Chi tiết kèm vocab_payload
  PATCH /api/admin/training-images/{scan_id}/unlink   — Bỏ ảnh khỏi pool training
  PATCH /api/admin/training-images/{scan_id}/reassign — Chuyển ảnh sang đối tượng khác
  POST /api/admin/predictions/{id}/approve       — Duyệt → insert DoiTuong/BanDich/ViDu
  POST /api/admin/predictions/{id}/reject        — Từ chối
  GET  /api/admin/stats                          — Thống kê nhanh
"""
import os
import uuid

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, Response, UploadFile
from sqlalchemy.orm import Session
from typing import List, Optional

from app.db.session import get_db
from app.models.object import Object
from app.models.object_media import ObjectMedia
from app.models.scan_history import ScanHistory
from app.services.admin_service import AdminService
from app.services.object_media_service import set_primary_object_image
from app.utils.timezone import now_vietnam
from app.utils.cloudinary_helper import upload_image
from app.dependencies.get_current_user import require_admin_user_id
from app.schemas.admin import (
    PredictionListItem,
    PredictionDetailResponse,
    ApproveRequest,
    ApproveResponse,
    AliasPredictionRequest,
    AliasPredictionResponse,
    RejectResponse, SplitToNewObjectResponse,
    CategoryAdminResponse, CategoryCreateRequest, CategoryUpdateRequest,
    ObjectAliasItem, ObjectAliasUpsertRequest,
    ObjectListItem, ObjectDetailResponse, ObjectCreateRequest, ObjectUpdateRequest,
    TranslationAdminResponse, TranslationCreateRequest, TranslationUpdateRequest,
    UserAdminResponse, UserRoleUpdate, UserStatusUpdate, UserPasswordReset,
    DashboardStats,
    ScanHistoryAdminItem, UserStatsAdminResponse,
)

router = APIRouter(
    prefix="/admin",
    tags=["Admin — Kiểm duyệt từ vựng"],
    dependencies=[Depends(require_admin_user_id)],
)
admin_service = AdminService()
OBJECT_UPLOAD_DIR = "uploads/objects"


@router.get("/predictions", response_model=List[PredictionListItem])
def list_predictions(
    trang_thai: Optional[str] = Query(default="cho_duyet", description="cho_duyet | da_duyet | tu_choi"),
    search: Optional[str] = Query(default=None),
    limit: int = Query(default=50, ge=1),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
):
    """Danh sách predictions của Gemini, lọc theo trạng thái kiểm duyệt."""
    return admin_service.list_predictions(db, trang_thai=trang_thai, search=search, limit=limit, offset=offset)


@router.get("/training-summary")
def training_summary(db: Session = Depends(get_db)):
    """Trả JSON tóm tắt dữ liệu training — dùng cho UI dashboard."""
    return admin_service.training_summary(db)


@router.get("/predictions/export-training")
def export_training_data(db: Session = Depends(get_db)):
    """Xuất dữ liệu training — predictions Gemini đã duyệt, định dạng JSONL."""
    import json as _json
    records = admin_service.export_training_data(db)
    content = "\n".join(_json.dumps(r, ensure_ascii=False) for r in records)
    return Response(
        content=content,
        media_type="application/x-ndjson",
        headers={"Content-Disposition": "attachment; filename=yolo_training_data.jsonl"},
    )


@router.get("/predictions/export-training-grouped")
def export_training_grouped(db: Session = Depends(get_db)):
    """Xuất training data gom nhóm theo object — mỗi object có danh sách ảnh đa góc độ."""
    import json as _json
    records = admin_service.export_training_data_grouped(db)
    content = "\n".join(_json.dumps(r, ensure_ascii=False) for r in records)
    return Response(
        content=content,
        media_type="application/x-ndjson",
        headers={"Content-Disposition": "attachment; filename=yolo_training_grouped.jsonl"},
    )


@router.patch("/training-images/{scan_id}/approve")
def approve_training_image(
    scan_id: int,
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_user_id),
):
    """Duyet anh training ma khong thay doi logic lich su quet."""
    item = admin_service.approve_training_image_by_scan(db, scan_id, admin_id=admin_id)
    if not item:
        raise HTTPException(404, "Khong tim thay anh training")
    return {"message": "Da duyet anh training", "scan_id": scan_id, "training_image_id": item.id}


@router.patch("/training-images/{scan_id}/unlink")
def unlink_training_image(
    scan_id: int,
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_user_id),
):
    """Từ chối ảnh training; lịch sử quét vẫn giữ đối tượng."""
    item = admin_service.reject_training_image_by_scan(db, scan_id, admin_id=admin_id)
    if not item:
        raise HTTPException(404, "Khong tim thay anh training")
    return {"message": "Da tu choi anh training", "scan_id": scan_id, "training_image_id": item.id}


@router.delete("/training-images/unlink-all")
def unlink_all_training_images(
    object_code: str = Query(..., description="Mã đối tượng cần xoá toàn bộ ảnh training"),
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_user_id),
):
    """Từ chối toàn bộ ảnh training của một nhãn; không sửa lịch sử quét."""
    count = admin_service.reject_training_images_for_object(db, object_code, admin_id=admin_id)
    return {"message": f"Da loai {count} anh khoi pool training cua '{object_code}'", "count": count}


@router.post("/training-datasets")
def create_training_dataset(
    ma_phien_ban: str = Query(..., description="Ma phien ban dataset, vi du v1"),
    ghi_chu: Optional[str] = Query(default=None),
    db: Session = Depends(get_db),
):
    """Chot mot phien ban dataset gom toan bo anh training da duyet hien tai."""
    try:
        dataset = admin_service.create_dataset_version(db, ma_phien_ban=ma_phien_ban, ghi_chu=ghi_chu)
    except ValueError as exc:
        raise HTTPException(400, str(exc))
    return {
        "message": f"Da tao dataset {dataset.ma_phien_ban}",
        "id": dataset.id,
        "ma_phien_ban": dataset.ma_phien_ban,
        "tong_anh": dataset.tong_anh,
        "tong_nhan": dataset.tong_nhan,
    }


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
    linked_scan_ids = []
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
            admin_service.training_images.create_candidate(
                db,
                scan_id=p.scan.id,
                url_anh=p.scan.url_anh,
                doi_tuong_id=obj.id,
                nhan=obj.ma_doi_tuong,
                nguon_du_lieu="gemini",
                do_tin_cay=p.do_tin_cay,
                ghi_chu="Resolved known YOLO/COCO class",
            )
            linked_scan_ids.append(p.scan.id)

        deleted_prediction_ids.append(p.id)
        db.delete(p)
        resolved += 1

    db.commit()
    return {
        "message": "Da resolve pending predictions cua cac class YOLO/COCO da co object chinh thuc",
        "matched": matched,
        "resolved": resolved,
        "linked_scan_ids": linked_scan_ids,
        "deleted_prediction_ids": deleted_prediction_ids,
        "skipped_missing_object": skipped_missing_object,
    }


@router.patch("/training-images/{scan_id}/reassign")
def reassign_training_image(
    scan_id: int,
    target_object_code: str = Query(..., description="Mã đối tượng đích"),
    db: Session = Depends(get_db),
):
    """Chuyển ảnh scan sang đối tượng khác trong pool training."""
    item, target_obj = admin_service.reassign_training_image_by_scan(db, scan_id, target_object_code)
    if not target_obj:
        scan_exists = db.query(ScanHistory.id).filter(ScanHistory.id == scan_id).first()
        if not scan_exists:
            raise HTTPException(404, "Khong tim thay scan")
        raise HTTPException(404, f"Khong tim thay doi tuong '{target_object_code}'")
    return {
        "message": f"Da chuyen anh sang '{target_object_code}'",
        "scan_id": scan_id,
        "training_image_id": item.id if item else None,
        "new_object_id": target_obj.id,
    }


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


@router.post("/predictions/{prediction_id}/alias", response_model=AliasPredictionResponse)
def assign_prediction_alias(
    prediction_id: int,
    request: AliasPredictionRequest,
    db: Session = Depends(get_db),
):
    """Gán prediction đang chờ duyệt thành bí danh của một đối tượng chính."""
    result = admin_service.assign_prediction_alias(db, prediction_id, request)
    if not result.success:
        raise HTTPException(400, result.message)
    return result


@router.patch("/predictions/{prediction_id}/detach-image")
def detach_review_image(prediction_id: int, db: Session = Depends(get_db)):
    """Bỏ một ảnh bổ sung khỏi nhóm kiểm duyệt, không xóa ảnh khỏi lịch sử quét."""
    result = admin_service.detach_review_image(db, prediction_id)
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
    result = admin_service.reassign_review_image(db, prediction_id, target_object_code)
    if not result.get("success"):
        raise HTTPException(400, result.get("message", "Khong the chuyen anh"))
    return result


@router.post("/predictions/{prediction_id}/reject", response_model=RejectResponse)
def reject_prediction(prediction_id: int, db: Session = Depends(get_db)):
    """Từ chối prediction — không insert gì vào bảng chính."""
    result = admin_service.reject_prediction(db, prediction_id)
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
    result = admin_service.split_to_new_object(db, prediction_id, new_object_code)
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

    Images uploaded here are curated object images for explore/review,
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


@router.post("/objects/media/{media_id}/unset-primary")
def unset_media_primary(media_id: int, db: Session = Depends(get_db)):
    media = db.query(ObjectMedia).filter(
        ObjectMedia.id == media_id,
        ObjectMedia.thoi_gian_xoa.is_(None),
    ).first()
    if not media:
        raise HTTPException(404, "Khong tim thay anh doi tuong")
    media.doi_tuong_chinh = False
    db.commit()
    return {"message": "Da bo anh chinh", "media_id": media.id}


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


# ==========================================================================
# Dashboard
# ==========================================================================

@router.get("/dashboard", response_model=DashboardStats)
def get_dashboard(db: Session = Depends(get_db)):
    """Thống kê tổng quan cho trang chủ admin."""
    return admin_service.get_dashboard_stats(db)


# ==========================================================================
# Category CRUD
# ==========================================================================

@router.get("/categories", response_model=List[CategoryAdminResponse])
def list_categories(db: Session = Depends(get_db)):
    return admin_service.list_categories(db)


@router.post("/categories", response_model=CategoryAdminResponse)
def create_category(req: CategoryCreateRequest, db: Session = Depends(get_db)):
    return admin_service.create_category(db, req)


@router.put("/categories/{category_id}", response_model=CategoryAdminResponse)
def update_category(category_id: int, req: CategoryUpdateRequest, db: Session = Depends(get_db)):
    result = admin_service.update_category(db, category_id, req)
    if not result:
        raise HTTPException(404, "Không tìm thấy danh mục")
    return result


@router.delete("/categories/{category_id}")
def delete_category(category_id: int, db: Session = Depends(get_db)):
    if not admin_service.delete_category(db, category_id):
        raise HTTPException(404, "Không tìm thấy danh mục")
    return {"message": "Đã xoá danh mục"}


# ==========================================================================
# Object CRUD
# ==========================================================================

@router.get("/objects", response_model=List[ObjectListItem])
def list_objects(
    search: Optional[str] = Query(default=None),
    category_id: Optional[int] = Query(default=None),
    no_image: Optional[bool] = Query(default=None, description="true = chỉ hiện đối tượng chưa có ảnh"),
    limit: int = Query(default=50, ge=1),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
):
    return admin_service.list_objects(db, limit=limit, offset=offset, search=search, category_id=category_id, no_image=no_image)


@router.get("/objects/{object_id}", response_model=ObjectDetailResponse)
def get_object(object_id: int, db: Session = Depends(get_db)):
    result = admin_service.get_object(db, object_id)
    if not result:
        raise HTTPException(404, "Không tìm thấy đối tượng")
    return result


@router.post("/objects", response_model=ObjectDetailResponse)
def create_object(req: ObjectCreateRequest, db: Session = Depends(get_db)):
    return admin_service.create_object(db, req)


@router.put("/objects/{object_id}", response_model=ObjectDetailResponse)
def update_object(object_id: int, req: ObjectUpdateRequest, db: Session = Depends(get_db)):
    result = admin_service.update_object(db, object_id, req)
    if not result:
        raise HTTPException(404, "Không tìm thấy đối tượng")
    return result


@router.delete("/objects/{object_id}")
def delete_object(object_id: int, db: Session = Depends(get_db)):
    if not admin_service.delete_object(db, object_id):
        raise HTTPException(404, "Không tìm thấy đối tượng")
    return {"message": "Đã xoá đối tượng"}


@router.post("/object-aliases", response_model=ObjectAliasItem)
def upsert_object_alias(req: ObjectAliasUpsertRequest, db: Session = Depends(get_db)):
    try:
        result = admin_service.upsert_object_alias(db, req)
    except ValueError as exc:
        raise HTTPException(400, str(exc))
    if not result:
        raise HTTPException(404, "Khong tim thay doi tuong hoac ma bi danh khong hop le")
    return result


@router.delete("/object-aliases/{alias_id}")
def delete_object_alias(alias_id: int, db: Session = Depends(get_db)):
    if not admin_service.delete_object_alias(db, alias_id):
        raise HTTPException(404, "Khong tim thay bi danh")
    return {"message": "Da xoa bi danh", "alias_id": alias_id}


# ==========================================================================
# Translation CRUD
# ==========================================================================

@router.get("/translations", response_model=List[TranslationAdminResponse])
def list_translations(
    object_id: Optional[int] = Query(default=None),
    search: Optional[str] = Query(default=None),
    lang_code: Optional[str] = Query(default=None),
    approved: Optional[bool] = Query(default=None),
    limit: int = Query(default=50, ge=1),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
):
    return admin_service.list_translations(
        db, object_id=object_id, search=search, lang_code=lang_code, approved=approved, limit=limit, offset=offset
    )


@router.post("/translations", response_model=TranslationAdminResponse)
def create_translation(req: TranslationCreateRequest, db: Session = Depends(get_db)):
    return admin_service.create_translation(db, req)


@router.put("/translations/{translation_id}", response_model=TranslationAdminResponse)
def update_translation(translation_id: int, req: TranslationUpdateRequest, db: Session = Depends(get_db)):
    result = admin_service.update_translation(db, translation_id, req)
    if not result:
        raise HTTPException(404, "Không tìm thấy bản dịch")
    return result


@router.delete("/translations/{translation_id}")
def delete_translation(translation_id: int, db: Session = Depends(get_db)):
    if not admin_service.delete_translation(db, translation_id):
        raise HTTPException(404, "Không tìm thấy bản dịch")
    return {"message": "Đã xoá bản dịch"}


# ==========================================================================
# User management
# ==========================================================================

@router.get("/users", response_model=List[UserAdminResponse])
def list_users(
    search: Optional[str] = Query(default=None),
    limit: int = Query(default=50, ge=1),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
):
    return admin_service.list_users(db, limit=limit, offset=offset, search=search)


@router.put("/users/{user_id}/role")
def update_user_role(user_id: int, req: UserRoleUpdate, db: Session = Depends(get_db)):
    if not admin_service.update_user_role(db, user_id, req):
        raise HTTPException(404, "Không tìm thấy người dùng")
    return {"message": "Đã cập nhật vai trò"}


@router.put("/users/{user_id}/status")
def update_user_status(user_id: int, req: UserStatusUpdate, db: Session = Depends(get_db)):
    if not admin_service.update_user_status(db, user_id, req):
        raise HTTPException(404, "Không tìm thấy người dùng")
    return {"message": "Đã cập nhật trạng thái"}


@router.delete("/users/{user_id}")
def delete_user(user_id: int, db: Session = Depends(get_db)):
    if not admin_service.delete_user(db, user_id):
        raise HTTPException(404, "Không tìm thấy người dùng")
    return {"message": "Đã xoá người dùng"}


@router.post("/users/{user_id}/reset-password")
def reset_user_password(user_id: int, req: UserPasswordReset, db: Session = Depends(get_db)):
    if not admin_service.reset_user_password(db, user_id, req.new_password):
        raise HTTPException(404, "Không tìm thấy người dùng")
    return {"message": "Đã đặt lại mật khẩu"}


@router.get("/users/{user_id}/stats", response_model=UserStatsAdminResponse)
def get_user_stats(user_id: int, db: Session = Depends(get_db)):
    """Stats của một user: tổng quét, tổng ôn, từ đã học, streak."""
    result = admin_service.get_user_stats(db, user_id)
    if not result:
        raise HTTPException(404, "Không tìm thấy người dùng")
    return result


# ==========================================================================
# Scan History
# ==========================================================================

@router.get("/scan-history", response_model=List[ScanHistoryAdminItem])
def list_scan_history(
    user_id: Optional[int] = Query(default=None),
    username: Optional[str] = Query(default=None),
    object_code: Optional[str] = Query(default=None),
    date_from: Optional[str] = Query(default=None),
    date_to: Optional[str] = Query(default=None),
    limit: int = Query(default=50, ge=1),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
):
    """Lịch sử quét toàn bộ hệ thống, có thể lọc theo user, mã đối tượng và khoảng ngày."""
    return admin_service.list_scan_history(
        db, user_id=user_id, username=username, object_code=object_code,
        date_from=date_from, date_to=date_to, limit=limit, offset=offset
    )


@router.delete("/scan-history/bulk")
def bulk_delete_scan_history(ids: List[int] = Query(...), db: Session = Depends(get_db)):
    """Xóa nhiều bản ghi lịch sử quét cùng lúc."""
    count = db.query(ScanHistory).filter(ScanHistory.id.in_(ids)).delete(synchronize_session=False)
    db.commit()
    return {"message": f"Da xoa {count} ban ghi", "count": count}


@router.delete("/scan-history/{scan_id}")
def delete_scan_history(scan_id: int, db: Session = Depends(get_db)):
    """Xóa một bản ghi lịch sử quét."""
    scan = db.query(ScanHistory).filter(ScanHistory.id == scan_id).first()
    if not scan:
        raise HTTPException(404, "Không tìm thấy bản ghi")
    db.delete(scan)
    db.commit()
    return {"message": "Đã xóa", "scan_id": scan_id}
