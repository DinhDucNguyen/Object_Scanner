"""Drop muc_do_kho column from DoiTuong

Revision ID: 0009_drop_muc_do_kho
Revises: 0008_drop_profile_streak
"""
from alembic import op
import sqlalchemy as sa

revision = "0009_drop_muc_do_kho"
down_revision = "0008_drop_profile_streak"
branch_labels = None
depends_on = None


def upgrade():
    op.drop_column("DoiTuong", "muc_do_kho")


def downgrade():
    op.add_column("DoiTuong", sa.Column("muc_do_kho", sa.Integer(), nullable=True, server_default="1"))
