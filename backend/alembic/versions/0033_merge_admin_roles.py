"""Merge legacy admin roles into admin

Revision ID: 0033_merge_admin_roles
Revises: 0032_add_mlkit_training_source
Create Date: 2026-06-12
"""
from alembic import op
import sqlalchemy as sa


revision = "0033_merge_admin_roles"
down_revision = "0032"
branch_labels = None
depends_on = None


ADMIN_ROLE_NAME = "admin"
LEGACY_ADMIN_ROLE_NAMES = ("quan_tri", "quan_tri_vien")


def _role_id(conn, role_name: str):
    return conn.execute(
        sa.text(
            """
            SELECT id
            FROM VaiTro
            WHERE LOWER(ten_vai_tro) = LOWER(:role_name)
            LIMIT 1
            """
        ),
        {"role_name": role_name},
    ).scalar()


def upgrade():
    conn = op.get_bind()

    admin_id = _role_id(conn, ADMIN_ROLE_NAME)
    legacy_ids = [
        role_id
        for role_name in LEGACY_ADMIN_ROLE_NAMES
        if (role_id := _role_id(conn, role_name)) is not None
    ]

    if admin_id is None:
        if legacy_ids:
            admin_id = legacy_ids[0]
            conn.execute(
                sa.text(
                    """
                    UPDATE VaiTro
                    SET ten_vai_tro = :admin_role_name
                    WHERE id = :role_id
                    """
                ),
                {"admin_role_name": ADMIN_ROLE_NAME, "role_id": admin_id},
            )
        else:
            conn.execute(
                sa.text("INSERT INTO VaiTro (ten_vai_tro) VALUES (:admin_role_name)"),
                {"admin_role_name": ADMIN_ROLE_NAME},
            )
            admin_id = _role_id(conn, ADMIN_ROLE_NAME)

    if admin_id is None:
        return

    for legacy_id in legacy_ids:
        if legacy_id == admin_id:
            continue
        conn.execute(
            sa.text(
                """
                UPDATE NguoiDung
                SET vai_tro_id = :admin_id
                WHERE vai_tro_id = :legacy_id
                """
            ),
            {"admin_id": admin_id, "legacy_id": legacy_id},
        )
        conn.execute(
            sa.text("DELETE FROM VaiTro WHERE id = :legacy_id"),
            {"legacy_id": legacy_id},
        )


def downgrade():
    # Không thể tách ngược người dùng đã được gộp về admin một cách an toàn.
    pass