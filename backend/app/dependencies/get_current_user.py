from fastapi import Request, HTTPException, Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from typing import Optional

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
