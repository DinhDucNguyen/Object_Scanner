"""Add index for due review lookups.

Revision ID: 0030
Revises: 0029
Create Date: 2026-06-05
"""

from alembic import op


revision = "0030"
down_revision = "0029"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_index(
        "ix_TienDoHoc_nguoi_dung_ngay_on_tiep",
        "TienDoHoc",
        ["nguoi_dung_id", "ngay_on_tiep"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("ix_TienDoHoc_nguoi_dung_ngay_on_tiep", table_name="TienDoHoc")
