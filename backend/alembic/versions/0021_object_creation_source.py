"""Track object creation source

Revision ID: 0021_object_creation_source
Revises: 0020_backfill_legacy_audit
"""
from alembic import op
import sqlalchemy as sa


revision = "0021_object_creation_source"
down_revision = "0020_backfill_legacy_audit"
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
    op.add_column(
        "DoiTuong",
        sa.Column(
            "nguon_tao",
            sa.Enum("admin", "gemini", name="nguontaodoituong"),
            nullable=True,
        ),
    )
    op.add_column("DoiTuong", sa.Column("du_doan_ai_id", sa.Integer(), nullable=True))
    op.add_column("DoiTuong", sa.Column("nguoi_duyet_id", sa.Integer(), nullable=True))
    op.add_column("DoiTuong", sa.Column("thoi_gian_duyet", sa.DateTime(), nullable=True))

    op.create_index("ix_DoiTuong_du_doan_ai_id", "DoiTuong", ["du_doan_ai_id"])
    op.create_foreign_key(
        "fk_DoiTuong_du_doan_ai_id",
        "DoiTuong", "DuDoanAI",
        ["du_doan_ai_id"], ["id"],
        ondelete="SET NULL",
    )
    op.create_foreign_key(
        "fk_DoiTuong_nguoi_duyet_id",
        "DoiTuong", "NguoiDung",
        ["nguoi_duyet_id"], ["id"],
        ondelete="SET NULL",
    )

    conn = op.get_bind()
    legacy_admin_id = _legacy_admin_id(conn)

    if legacy_admin_id is not None:
        # Default legacy objects to administrator-managed manual data.
        conn.execute(
            sa.text(
                """
                UPDATE DoiTuong
                SET nguon_tao = 'admin',
                    nguoi_tao_id = :legacy_admin_id,
                    du_doan_ai_id = NULL,
                    nguoi_duyet_id = NULL,
                    thoi_gian_duyet = NULL
                WHERE thoi_gian_xoa IS NULL
                """
            ),
            {"legacy_admin_id": legacy_admin_id},
        )

        # Objects whose only active translations came from Gemini should not
        # look like they were manually created by an admin. The admin approved
        # them, while the creation source remains Gemini.
        conn.execute(
            sa.text(
                """
                UPDATE DoiTuong obj
                JOIN (
                    SELECT
                        bd.doi_tuong_id,
                        SUM(bd.nguon_du_lieu = 'thu_cong') AS manual_count,
                        SUM(bd.nguon_du_lieu = 'gemini') AS gemini_count,
                        MIN(bd.du_doan_ai_id) AS du_doan_ai_id,
                        MIN(bd.nguoi_duyet_id) AS nguoi_duyet_id,
                        MIN(bd.thoi_gian_duyet) AS thoi_gian_duyet
                    FROM BanDich bd
                    WHERE bd.thoi_gian_xoa IS NULL
                    GROUP BY bd.doi_tuong_id
                ) src ON src.doi_tuong_id = obj.id
                SET obj.nguon_tao = 'gemini',
                    obj.nguoi_tao_id = NULL,
                    obj.du_doan_ai_id = src.du_doan_ai_id,
                    obj.nguoi_duyet_id = COALESCE(src.nguoi_duyet_id, :legacy_admin_id),
                    obj.thoi_gian_duyet = COALESCE(src.thoi_gian_duyet, CURRENT_TIMESTAMP)
                WHERE obj.thoi_gian_xoa IS NULL
                  AND src.gemini_count > 0
                  AND src.manual_count = 0
                """
            ),
            {"legacy_admin_id": legacy_admin_id},
        )


def downgrade():
    op.drop_constraint("fk_DoiTuong_nguoi_duyet_id", "DoiTuong", type_="foreignkey")
    op.drop_constraint("fk_DoiTuong_du_doan_ai_id", "DoiTuong", type_="foreignkey")
    op.drop_index("ix_DoiTuong_du_doan_ai_id", table_name="DoiTuong")
    op.drop_column("DoiTuong", "thoi_gian_duyet")
    op.drop_column("DoiTuong", "nguoi_duyet_id")
    op.drop_column("DoiTuong", "du_doan_ai_id")
    op.drop_column("DoiTuong", "nguon_tao")
