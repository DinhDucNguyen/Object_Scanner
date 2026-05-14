"""Add daily review count to profile streak data

Revision ID: 0006_profile_reviews_today
Revises: 0005_seed_admin_role
Create Date: 2026-05-14
"""
from alembic import op
import sqlalchemy as sa

revision = "0006_profile_reviews_today"
down_revision = "0005_seed_admin_role"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column(
        "HoSo",
        sa.Column("luot_on_hom_nay", sa.Integer(), nullable=False, server_default="0"),
    )


def downgrade():
    op.drop_column("HoSo", "luot_on_hom_nay")
