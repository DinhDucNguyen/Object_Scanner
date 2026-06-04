from fastapi import Request, HTTPException, Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session
from typing import Optional

from app.core.config import settings
from app.db.session import get_db
from app.models.user import User
from app.utils.security import decode_token

security_scheme = HTTPBearer()
optional_security_scheme = HTTPBearer(auto_error=False)


def get_current_nguoi_dung_id(
    credentials: HTTPAuthorizationCredentials = Depends(security_scheme),
    db: Session = Depends(get_db),
) -> int:
    """
    Parse JWT token từ header Authorization: Bearer <token>.
    Trả về nguoi_dung_id nếu token hợp lệ, raise 401 nếu không.
    """
    return _get_authenticated_user(db, credentials).id


def get_current_user(
    db: Session = Depends(get_db),
    credentials: HTTPAuthorizationCredentials = Depends(security_scheme),
) -> User:
    return _get_authenticated_user(db, credentials)


def _get_authenticated_user(db: Session, credentials: HTTPAuthorizationCredentials) -> User:
    nguoi_dung_id = _extract_nguoi_dung_id(credentials)
    user = db.query(User).filter(
        User.id == nguoi_dung_id,
        User.thoi_gian_xoa.is_(None),
    ).first()
    if not user:
        raise HTTPException(status_code=401, detail="User not found")

    if not bool(user.email_da_xac_thuc):
        raise HTTPException(status_code=403, detail="Vui lòng xác thực email trước khi đăng nhập")

    status_name = (user.trang_thai_obj.ten_trang_thai if user.trang_thai_obj else "").strip()
    if status_name != "hoat_dong":
        raise HTTPException(status_code=403, detail="Tài khoản đã bị khóa")

    return user


def require_admin_nguoi_dung_id(user: User = Depends(get_current_user)) -> int:
    role_name = (user.vai_tro_obj.ten_vai_tro if user.vai_tro_obj else "").strip().lower()
    admin_roles = {role.strip().lower() for role in settings.ADMIN_ROLE_NAMES}
    is_admin = role_name in admin_roles or user.id in settings.ADMIN_USER_IDS
    if not is_admin:
        raise HTTPException(status_code=403, detail="Admin permission required")
    return user.id


def get_optional_nguoi_dung_id(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(optional_security_scheme),
    db: Session = Depends(get_db),
) -> Optional[int]:
    """
    Optional auth — trả về nguoi_dung_id nếu có token, None nếu không.
    Dùng cho scan endpoints (cho phép anonymous nhưng track user nếu đăng nhập).
    """
    if credentials is None:
        return None
    try:
        return _get_authenticated_user(db, credentials).id
    except HTTPException:
        return None


def _extract_nguoi_dung_id(credentials: HTTPAuthorizationCredentials) -> int:
    """Extract và validate nguoi_dung_id từ JWT credentials."""
    token = credentials.credentials
    payload = decode_token(token)

    if payload is None:
        raise HTTPException(
            status_code=401,
            detail="Token không hợp lệ hoặc đã hết hạn",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if payload.get("type") != "access":
        raise HTTPException(
            status_code=401,
            detail="Token type không hợp lệ, cần access token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    nguoi_dung_id = payload.get("sub")
    if nguoi_dung_id is None:
        raise HTTPException(
            status_code=401,
            detail="Token không chứa nguoi_dung_id",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return int(nguoi_dung_id)
