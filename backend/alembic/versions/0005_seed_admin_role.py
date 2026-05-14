"""Seed default user roles and statuses

Revision ID: 0005_seed_admin_role
Revises: 0004_settings_display_language
Create Date: 2026-05-14
"""
from alembic import op

revision = "0005_seed_admin_role"
down_revision = "0004_settings_display_language"
branch_labels = None
depends_on = None


def upgrade():
    op.execute(
        """
        INSERT IGNORE INTO VaiTro (ten_vai_tro)
        VALUES ('nguoi_dung'), ('admin')
        """
    )
    op.execute(
        """
        INSERT IGNORE INTO TrangThaiNguoiDung (ten_trang_thai)
        VALUES ('hoat_dong'), ('khoa')
        """
    )


def downgrade():
    op.execute(
        """
        DELETE FROM VaiTro
        WHERE ten_vai_tro = 'admin'
          AND id NOT IN (
              SELECT vai_tro_id FROM NguoiDung WHERE vai_tro_id IS NOT NULL
          )
        """
    )
