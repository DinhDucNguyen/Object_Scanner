"""Add streak columns to HoSo (Profile)

Revision ID: 0003_profile_streak
Revises: 0002_ai_prediction_trang_thai
Create Date: 2026-05-06
"""
from alembic import op
import sqlalchemy as sa

revision = "0003_profile_streak"
down_revision = "0002_ai_prediction_trang_thai"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("HoSo", sa.Column("streak_hien_tai", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("HoSo", sa.Column("streak_dai_nhat", sa.Integer(), nullable=False, server_default="0"))
    op.add_column("HoSo", sa.Column("tong_luot_on",    sa.Integer(), nullable=False, server_default="0"))
    op.add_column("HoSo", sa.Column("ngay_on_cuoi",    sa.Date(),    nullable=True))


def downgrade():
    op.drop_column("HoSo", "ngay_on_cuoi")
    op.drop_column("HoSo", "tong_luot_on")
    op.drop_column("HoSo", "streak_dai_nhat")
    op.drop_column("HoSo", "streak_hien_tai")
