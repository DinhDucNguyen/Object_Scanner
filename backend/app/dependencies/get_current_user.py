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


def get_current_user_id(
    credentials: HTTPAuthorizationCredentials = Depends(security_scheme),
) -> int:
    """
    Parse JWT token từ header Authorization: Bearer <token>.
    Trả về user_id nếu token hợp lệ, raise 401 nếu không.
    """
    return _extract_user_id(credentials)


def get_current_user(
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id),
) -> User:
    user = db.query(User).filter(
        User.id == user_id,
        User.thoi_gian_xoa.is_(None),
    ).first()
    if not user:
        raise HTTPException(status_code=401, detail="User not found")
    return user


def require_admin_user_id(user: User = Depends(get_current_user)) -> int:
    role_name = (user.vai_tro_obj.ten_vai_tro if user.vai_tro_obj else "").strip().lower()
    admin_roles = {role.strip().lower() for role in settings.ADMIN_ROLE_NAMES}
    is_admin = role_name in admin_roles or user.id in settings.ADMIN_USER_IDS
    if not is_admin:
        raise HTTPException(status_code=403, detail="Admin permission required")
    return user.id


def get_optional_user_id(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(optional_security_scheme),
) -> Optional[int]:
    """
    Optional auth — trả về user_id nếu có token, None nếu không.
    Dùng cho scan endpoints (cho phép anonymous nhưng track user nếu đăng nhập).
    """
    if credentials is None:
        return None
    try:
        return _extract_user_id(credentials)
    except HTTPException:
        return None


def _extract_user_id(credentials: HTTPAuthorizationCredentials) -> int:
    """Extract và validate user_id từ JWT credentials."""
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

    user_id = payload.get("sub")
    if user_id is None:
        raise HTTPException(
            status_code=401,
            detail="Token không chứa user_id",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return int(user_id)
