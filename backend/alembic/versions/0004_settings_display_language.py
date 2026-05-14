"""Migrate CaiDatNguoiDung: replace ngon_ngu_me/hoc with ngon_ngu_giao_dien

Revision ID: 0004_settings_display_language
Revises: 0003_profile_streak
Create Date: 2026-05-14
"""
from alembic import op
import sqlalchemy as sa

revision = "0004_settings_display_language"
down_revision = "0003_profile_streak"
branch_labels = None
depends_on = None


def upgrade():
    # Drop old foreign key constraints first
    op.drop_constraint("fk_caidat_lang_native", "CaiDatNguoiDung", type_="foreignkey")
    op.drop_constraint("fk_caidat_lang_target", "CaiDatNguoiDung", type_="foreignkey")

    # Drop old columns
    op.drop_column("CaiDatNguoiDung", "ngon_ngu_me")
    op.drop_column("CaiDatNguoiDung", "ngon_ngu_hoc")

    # Add new display language column with default 'vi'
    op.add_column(
        "CaiDatNguoiDung",
        sa.Column("ngon_ngu_giao_dien", sa.String(length=10), nullable=False, server_default="vi"),
    )


def downgrade():
    op.drop_column("CaiDatNguoiDung", "ngon_ngu_giao_dien")

    op.add_column("CaiDatNguoiDung", sa.Column("ngon_ngu_hoc", sa.Integer(), nullable=True))
    op.add_column("CaiDatNguoiDung", sa.Column("ngon_ngu_me", sa.Integer(), nullable=True))

    op.create_foreign_key(
        "fk_caidat_lang_target", "CaiDatNguoiDung", "NgonNgu", ["ngon_ngu_hoc"], ["id"]
    )
    op.create_foreign_key(
        "fk_caidat_lang_native", "CaiDatNguoiDung", "NgonNgu", ["ngon_ngu_me"], ["id"]
    )
