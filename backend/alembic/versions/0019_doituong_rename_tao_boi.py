"""Rename DoiTuong.tao_boi to nguoi_tao_id

Revision ID: 0019_doituong_rename_tao_boi
Revises: 0018_bandich_traceability
"""
from alembic import op
import sqlalchemy as sa


revision = "0019_doituong_rename_tao_boi"
down_revision = "0018_bandich_traceability"
branch_labels = None
depends_on = None


def upgrade():
    inspector = sa.inspect(op.get_bind())
    columns = {col["name"] for col in inspector.get_columns("DoiTuong")}
    if "tao_boi" in columns and "nguoi_tao_id" not in columns:
        op.alter_column("DoiTuong", "tao_boi", new_column_name="nguoi_tao_id", existing_type=sa.Integer())


def downgrade():
    inspector = sa.inspect(op.get_bind())
    columns = {col["name"] for col in inspector.get_columns("DoiTuong")}
    if "nguoi_tao_id" in columns and "tao_boi" not in columns:
        op.alter_column("DoiTuong", "nguoi_tao_id", new_column_name="tao_boi", existing_type=sa.Integer())
