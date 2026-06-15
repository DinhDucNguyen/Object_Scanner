from __future__ import annotations


ADMIN_ROLE_NAME = "admin"
LEGACY_ADMIN_ROLE_NAMES = {"quan_tri", "quan_tri_vien"}


def canonical_role_name(role_name: str | None) -> str:
    normalized = (role_name or "").strip().lower()
    if normalized in LEGACY_ADMIN_ROLE_NAMES:
        return ADMIN_ROLE_NAME
    return normalized


def is_admin_role(role_name: str | None) -> bool:
    return canonical_role_name(role_name) == ADMIN_ROLE_NAME