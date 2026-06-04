"""Backfill auto-approve for high-confidence YOLO training images

Revision ID: 0027
Revises: 0026_remove_thermal_mug
"""
from alembic import op
import sqlalchemy as sa
from datetime import datetime, timezone

revision = "0027"
down_revision = "0026_remove_thermal_mug"
branch_labels = None
depends_on = None

AUTO_APPROVE_THRESHOLD = 0.85
AUTO_APPROVE_NOTE = "auto_approve:yolo_confidence>=0.85"


def upgrade() -> None:
    conn = op.get_bind()

    rows = conn.execute(sa.text("""
        SELECT id
        FROM `AnhHuanLuyen`
        WHERE trang_thai = 'cho_duyet'
          AND thoi_gian_xoa IS NULL
          AND nguon_du_lieu = 'yolo'
          AND doi_tuong_id IS NOT NULL
          AND do_tin_cay >= :threshold
    """), {"threshold": AUTO_APPROVE_THRESHOLD}).fetchall()

    if not rows:
        print("[0027] No training images to auto-approve")
        return

    ids = [r[0] for r in rows]
    now = datetime.now(timezone.utc)

    update_stmt = sa.text("""
        UPDATE `AnhHuanLuyen`
        SET trang_thai = 'da_duyet',
            thoi_gian_duyet = :now,
            ghi_chu = CASE
                WHEN ghi_chu IS NULL OR ghi_chu = '' THEN :note
                ELSE ghi_chu
            END
        WHERE id IN :ids
    """).bindparams(sa.bindparam("ids", expanding=True))

    conn.execute(update_stmt, {"now": now, "note": AUTO_APPROVE_NOTE, "ids": ids})

    print(f"[0027] Auto-approved {len(ids)} training images")


def downgrade() -> None:
    pass
