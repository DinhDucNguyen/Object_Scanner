"""Drop redundant streak counters from HoSo.

Revision ID: 0008_drop_profile_streak
Revises: 0007_review_log
Create Date: 2026-05-14
"""

from alembic import op
import sqlalchemy as sa


revision = "0008_drop_profile_streak"
down_revision = "0007_review_log"
branch_labels = None
depends_on = None


def upgrade():
    op.drop_column("HoSo", "ngay_on_cuoi")
    op.drop_column("HoSo", "luot_on_hom_nay")
    op.drop_column("HoSo", "tong_luot_on")
    op.drop_column("HoSo", "streak_dai_nhat")
    op.drop_column("HoSo", "streak_hien_tai")


def downgrade():
    op.add_column("HoSo", sa.Column("streak_hien_tai", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("HoSo", sa.Column("streak_dai_nhat", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("HoSo", sa.Column("tong_luot_on", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("HoSo", sa.Column("luot_on_hom_nay", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("HoSo", sa.Column("ngay_on_cuoi", sa.Date(), nullable=True))
