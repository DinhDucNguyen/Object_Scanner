"""Backfill missing training reviewers

Revision ID: 0024_backfill_training_reviewers
Revises: 0023_fix_legacy_audit_links
"""
from alembic import op
import sqlalchemy as sa


revision = "0024_backfill_training_reviewers"
down_revision = "0023_fix_legacy_audit_links"
branch_labels = None
depends_on = None


def _legacy_admin_id(conn) -> int | None:
    return conn.execute(
        sa.text(
            """
            SELECT nd.id
            FROM NguoiDung nd
            JOIN VaiTro vt ON vt.id = nd.vai_tro_id
            WHERE nd.thoi_gian_xoa IS NULL
              AND LOWER(vt.ten_vai_tro) IN ('quan_tri', 'admin')
            ORDER BY nd.id
            LIMIT 1
            """
        )
    ).scalar()


def upgrade():
    conn = op.get_bind()
    legacy_admin_id = _legacy_admin_id(conn)
    if legacy_admin_id is None:
        return

    conn.execute(
        sa.text(
            """
            UPDATE AnhHuanLuyen
            SET nguoi_duyet_id = :admin_id
            WHERE thoi_gian_xoa IS NULL
              AND trang_thai IN ('da_duyet', 'tu_choi')
              AND thoi_gian_duyet IS NOT NULL
              AND nguoi_duyet_id IS NULL
            """
        ),
        {"admin_id": legacy_admin_id},
    )


def downgrade():
    # Keep repaired audit values intact.
    pass
