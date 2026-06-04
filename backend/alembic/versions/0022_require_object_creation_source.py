"""Require object creation source

Revision ID: 0022_require_object_source
Revises: 0021_object_creation_source
"""
from alembic import op
import sqlalchemy as sa


revision = "0022_require_object_source"
down_revision = "0021_object_creation_source"
branch_labels = None
depends_on = None


def upgrade():
    op.execute("UPDATE DoiTuong SET nguon_tao = 'admin' WHERE nguon_tao IS NULL")
    op.alter_column(
        "DoiTuong",
        "nguon_tao",
        existing_type=sa.Enum("admin", "gemini", name="nguontaodoituong"),
        nullable=False,
        server_default="admin",
    )


def downgrade():
    op.alter_column(
        "DoiTuong",
        "nguon_tao",
        existing_type=sa.Enum("admin", "gemini", name="nguontaodoituong"),
        nullable=True,
        server_default=None,
    )
