"""Add email verification flag to users

Revision ID: 0016_user_email_verified
Revises: 0015_den_ngon_ngu_fk
"""
from alembic import op
import sqlalchemy as sa


revision = "0016_user_email_verified"
down_revision = "0015_den_ngon_ngu_fk"
branch_labels = None
depends_on = None


def upgrade():
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    columns = {col["name"] for col in inspector.get_columns("NguoiDung")}

    if "email_da_xac_thuc" not in columns:
        op.add_column(
            "NguoiDung",
            sa.Column("email_da_xac_thuc", sa.Boolean(), nullable=False, server_default=sa.text("0")),
        )
        op.execute("UPDATE NguoiDung SET email_da_xac_thuc = 1")


def downgrade():
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    columns = {col["name"] for col in inspector.get_columns("NguoiDung")}

    if "email_da_xac_thuc" in columns:
        op.drop_column("NguoiDung", "email_da_xac_thuc")
