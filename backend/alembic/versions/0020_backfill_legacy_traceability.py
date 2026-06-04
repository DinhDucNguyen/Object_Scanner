"""Backfill legacy traceability values

Revision ID: 0020_backfill_legacy_audit
Revises: 0019_doituong_rename_tao_boi
"""
from alembic import op
import sqlalchemy as sa


revision = "0020_backfill_legacy_audit"
down_revision = "0019_doituong_rename_tao_boi"
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

    # Legacy objects without a recorded creator are treated as existing
    # administrator-managed data from before traceability was introduced.
    conn.execute(
        sa.text(
            """
            UPDATE DoiTuong
            SET nguoi_tao_id = :legacy_admin_id
            WHERE nguoi_tao_id IS NULL
              AND thoi_gian_xoa IS NULL
            """
        ),
        {"legacy_admin_id": legacy_admin_id},
    )

    # Manual translations are already official administrator-managed data.
    # Older rows did not record the exact editor, so use the legacy admin.
    conn.execute(
        sa.text(
            """
            UPDATE BanDich
            SET nguoi_tao_id = :legacy_admin_id,
                nguoi_duyet_id = :legacy_admin_id,
                thoi_gian_duyet = COALESCE(thoi_gian_duyet, ngay_tao, CURRENT_TIMESTAMP)
            WHERE thoi_gian_xoa IS NULL
              AND nguon_du_lieu = 'thu_cong'
              AND da_xac_nhan = 1
            """
        ),
        {"legacy_admin_id": legacy_admin_id},
    )

    # Gemini translations can only be linked to DuDoanAI when there is a clear
    # approved prediction with the same object code.
    conn.execute(
        sa.text(
            """
            UPDATE BanDich bd
            JOIN DoiTuong obj ON obj.id = bd.doi_tuong_id
            JOIN (
                SELECT MIN(id) AS du_doan_ai_id, nhan_du_doan
                FROM DuDoanAI
                WHERE nguon_ai = 'gemini'
                  AND trang_thai = 'da_duyet'
                GROUP BY nhan_du_doan
            ) pred ON pred.nhan_du_doan = obj.ma_doi_tuong
            SET bd.du_doan_ai_id = pred.du_doan_ai_id
            WHERE bd.thoi_gian_xoa IS NULL
              AND bd.nguon_du_lieu = 'gemini'
              AND bd.du_doan_ai_id IS NULL
            """
        )
    )

    # Approved Gemini translations are legacy-approved data; the exact reviewer
    # was not recorded before this migration, so use the administrator account
    # as the legacy owner of that approval.
    conn.execute(
        sa.text(
            """
            UPDATE BanDich
            SET nguoi_duyet_id = COALESCE(nguoi_duyet_id, :legacy_admin_id),
                thoi_gian_duyet = COALESCE(thoi_gian_duyet, ngay_tao, CURRENT_TIMESTAMP)
            WHERE thoi_gian_xoa IS NULL
              AND nguon_du_lieu = 'gemini'
              AND da_xac_nhan = 1
            """
        ),
        {"legacy_admin_id": legacy_admin_id},
    )


def downgrade():
    # Keep legacy audit values intact; the following migrations remove/rename
    # the related columns if the schema is downgraded further.
    pass
