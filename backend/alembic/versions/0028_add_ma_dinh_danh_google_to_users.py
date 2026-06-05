"""Add Vietnamese Google identifier to users

Revision ID: 0028
Revises: 0027
"""
from alembic import op
import sqlalchemy as sa


revision = "0028"
down_revision = "0027"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("NguoiDung", sa.Column("ma_dinh_danh_google", sa.String(length=128), nullable=True))
    op.create_unique_constraint("uq_NguoiDung_ma_dinh_danh_google", "NguoiDung", ["ma_dinh_danh_google"])


def downgrade() -> None:
    op.drop_constraint("uq_NguoiDung_ma_dinh_danh_google", "NguoiDung", type_="unique")
    op.drop_column("NguoiDung", "ma_dinh_danh_google")
