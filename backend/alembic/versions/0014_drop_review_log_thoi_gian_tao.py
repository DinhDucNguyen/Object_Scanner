"""Drop redundant thoi_gian_tao column from LichSuOnTap

Revision ID: 0014_drop_review_log_thoi_gian_tao
Revises: 0013_training_image_pipeline
"""
from alembic import op
import sqlalchemy as sa


revision = "0014_drop_lichsuontap_col"
down_revision = "0013_training_image_pipeline"
branch_labels = None
depends_on = None


def upgrade():
    op.drop_column("LichSuOnTap", "thoi_gian_tao")


def downgrade():
    op.add_column(
        "LichSuOnTap",
        sa.Column("thoi_gian_tao", sa.DateTime(), nullable=False, server_default=sa.func.now()),
    )
