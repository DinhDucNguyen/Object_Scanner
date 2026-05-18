"""Add vai_tro and du_doan_goc_id to DuDoanAI

Revision ID: 0010_ai_prediction_vai_tro
Revises: 0009_drop_muc_do_kho
"""
from alembic import op
import sqlalchemy as sa

revision = "0010_ai_prediction_vai_tro"
down_revision = "0009_drop_muc_do_kho"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column(
        "DuDoanAI",
        sa.Column(
            "vai_tro",
            sa.Enum("chinh", "anh_bo_sung", name="vaitrodudoan"),
            nullable=False,
            server_default="chinh",
        ),
    )
    op.add_column(
        "DuDoanAI",
        sa.Column("du_doan_goc_id", sa.Integer(), nullable=True),
    )
    op.create_index("ix_DuDoanAI_vai_tro", "DuDoanAI", ["vai_tro"])
    op.create_index("ix_DuDoanAI_du_doan_goc_id", "DuDoanAI", ["du_doan_goc_id"])
    op.create_foreign_key(
        "fk_DuDoanAI_du_doan_goc_id",
        "DuDoanAI",
        "DuDoanAI",
        ["du_doan_goc_id"],
        ["id"],
        ondelete="SET NULL",
    )

    # Backfill rows created before these explicit columns existed.
    op.execute(
        """
        UPDATE DuDoanAI
        SET
            vai_tro = 'anh_bo_sung',
            du_doan_goc_id = CAST(JSON_UNQUOTE(JSON_EXTRACT(mo_ta, '$.duplicate_of_prediction_id')) AS UNSIGNED)
        WHERE JSON_VALID(mo_ta)
          AND JSON_UNQUOTE(JSON_EXTRACT(mo_ta, '$.review_kind')) = 'image_only'
          AND JSON_UNQUOTE(JSON_EXTRACT(mo_ta, '$.duplicate_of_prediction_id')) IS NOT NULL
        """
    )


def downgrade():
    op.drop_constraint("fk_DuDoanAI_du_doan_goc_id", "DuDoanAI", type_="foreignkey")
    op.drop_index("ix_DuDoanAI_du_doan_goc_id", table_name="DuDoanAI")
    op.drop_index("ix_DuDoanAI_vai_tro", table_name="DuDoanAI")
    op.drop_column("DuDoanAI", "du_doan_goc_id")
    op.drop_column("DuDoanAI", "vai_tro")
