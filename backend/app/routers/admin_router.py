"""
Admin Router
============
REST API quản trị chung: dashboard, danh mục, đối tượng, bản dịch, người dùng,
ảnh đối tượng và lịch sử quét.

Endpoints:
  GET  /api/admin/dashboard
  CRUD /api/admin/categories
  CRUD /api/admin/objects
  CRUD /api/admin/translations
  CRUD /api/admin/users
  CRUD /api/admin/scan-history
"""
import uuid
from pathlib import Path

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile
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
from app.utils.upload import read_upload_bytes
from app.dependencies.get_current_user import require_admin_nguoi_dung_id
from app.schemas.admin import (
    CategoryAdminResponse, CategoryCreateRequest, CategoryUpdateRequest,
    ObjectAliasItem, ObjectAliasUpsertRequest, ObjectAliasUpdateRequest,
    ObjectListItem, ObjectDetailResponse, ObjectCreateRequest, ObjectUpdateRequest,
    TranslationAdminResponse, TranslationCreateRequest, TranslationUpdateRequest,
    UserAdminResponse, UserRoleUpdate, UserStatusUpdate, UserPasswordReset,
    DashboardStats,
    ScanHistoryAdminItem, UserStatsAdminResponse,
)

router = APIRouter(
    prefix="/admin",
    tags=["Admin — Kiểm duyệt từ vựng"],
    dependencies=[Depends(require_admin_nguoi_dung_id)],
)
admin_service = AdminService()
MAX_BULK_SCAN_HISTORY_DELETE = 500
OBJECT_UPLOAD_DIR = Path(__file__).resolve().parents[2] / "uploads" / "objects"


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
        image_bytes = await read_upload_bytes(image)
        final_url = upload_image(image_bytes, folder="object_scanner/objects")
        if final_url is None:
            OBJECT_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
            extension = _extension_from_content_type(image.content_type)
            filename = f"{uuid.uuid4().hex}{extension}"
            filepath = OBJECT_UPLOAD_DIR / filename
            filepath.write_bytes(image_bytes)
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
def create_object(
    req: ObjectCreateRequest,
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_nguoi_dung_id),
):
    return admin_service.create_object(db, req, admin_id=admin_id)


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


@router.put("/object-aliases/{alias_id}", response_model=ObjectAliasItem)
def update_object_alias(alias_id: int, req: ObjectAliasUpdateRequest, db: Session = Depends(get_db)):
    try:
        result = admin_service.update_object_alias(db, alias_id, req.ma_bi_danh, req.ten_hien_thi, req.ngon_ngu)
    except ValueError as exc:
        raise HTTPException(400, str(exc))
    if not result:
        raise HTTPException(404, "Không tìm thấy bí danh")
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
def create_translation(
    req: TranslationCreateRequest,
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_nguoi_dung_id),
):
    return admin_service.create_translation(db, req, admin_id=admin_id)


@router.put("/translations/{translation_id}", response_model=TranslationAdminResponse)
def update_translation(
    translation_id: int,
    req: TranslationUpdateRequest,
    db: Session = Depends(get_db),
    admin_id: int = Depends(require_admin_nguoi_dung_id),
):
    result = admin_service.update_translation(db, translation_id, req, admin_id=admin_id)
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


@router.put("/users/{nguoi_dung_id}/role")
def update_user_role(nguoi_dung_id: int, req: UserRoleUpdate, db: Session = Depends(get_db)):
    if not admin_service.update_user_role(db, nguoi_dung_id, req):
        raise HTTPException(404, "Không tìm thấy người dùng")
    return {"message": "Đã cập nhật vai trò"}


@router.put("/users/{nguoi_dung_id}/status")
def update_user_status(nguoi_dung_id: int, req: UserStatusUpdate, db: Session = Depends(get_db)):
    if not admin_service.update_user_status(db, nguoi_dung_id, req):
        raise HTTPException(404, "Không tìm thấy người dùng")
    return {"message": "Đã cập nhật trạng thái"}


@router.delete("/users/{nguoi_dung_id}")
def delete_user(nguoi_dung_id: int, db: Session = Depends(get_db)):
    if not admin_service.delete_user(db, nguoi_dung_id):
        raise HTTPException(404, "Không tìm thấy người dùng")
    return {"message": "Đã xoá người dùng"}


@router.post("/users/{nguoi_dung_id}/reset-password")
def reset_user_password(nguoi_dung_id: int, req: UserPasswordReset, db: Session = Depends(get_db)):
    if not admin_service.reset_user_password(db, nguoi_dung_id, req.new_password):
        raise HTTPException(404, "Không tìm thấy người dùng")
    return {"message": "Đã đặt lại mật khẩu"}


@router.get("/users/{nguoi_dung_id}/stats", response_model=UserStatsAdminResponse)
def get_user_stats(nguoi_dung_id: int, db: Session = Depends(get_db)):
    """Stats của một user: tổng quét, tổng ôn, từ đã học, streak."""
    result = admin_service.get_user_stats(db, nguoi_dung_id)
    if not result:
        raise HTTPException(404, "Không tìm thấy người dùng")
    return result


# ==========================================================================
# Scan History
# ==========================================================================

@router.get("/scan-history", response_model=List[ScanHistoryAdminItem])
def list_scan_history(
    nguoi_dung_id: Optional[int] = Query(default=None),
    username: Optional[str] = Query(default=None),
    object_code: Optional[str] = Query(default=None),
    date_from: Optional[str] = Query(default=None),
    date_to: Optional[str] = Query(default=None),
    limit: int = Query(default=50, ge=1),
    offset: int = Query(default=0, ge=0),
    db: Session = Depends(get_db),
):
    """Lịch sử quét toàn bộ hệ thống, có thể lọc theo user, mã đối tượng và khoảng ngày."""
    try:
        return admin_service.list_scan_history(
            db, nguoi_dung_id=nguoi_dung_id, username=username, object_code=object_code,
            date_from=date_from, date_to=date_to, limit=limit, offset=offset
        )
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@router.delete("/scan-history/bulk")
def bulk_delete_scan_history(ids: List[int] = Query(...), db: Session = Depends(get_db)):
    """Xóa nhiều bản ghi lịch sử quét cùng lúc."""
    unique_ids = list(dict.fromkeys(ids))
    if len(unique_ids) > MAX_BULK_SCAN_HISTORY_DELETE:
        raise HTTPException(400, f"Chi duoc xoa toi da {MAX_BULK_SCAN_HISTORY_DELETE} ban ghi moi lan")
    count = db.query(ScanHistory).filter(ScanHistory.id.in_(unique_ids)).delete(synchronize_session=False)
    db.commit()
    return {"message": f"Da xoa {count} ban ghi", "count": count}


@router.delete("/scan-history/{lich_su_quet_id}")
def delete_scan_history(lich_su_quet_id: int, db: Session = Depends(get_db)):
    """Xóa một bản ghi lịch sử quét."""
    scan = db.query(ScanHistory).filter(ScanHistory.id == lich_su_quet_id).first()
    if not scan:
        raise HTTPException(404, "Không tìm thấy bản ghi")
    db.delete(scan)
    db.commit()
    return {"message": "Đã xóa", "lich_su_quet_id": lich_su_quet_id}
