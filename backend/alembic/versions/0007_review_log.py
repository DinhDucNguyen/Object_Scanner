"""Add review history log table

Revision ID: 0007_review_log
Revises: 0006_profile_reviews_today
Create Date: 2026-05-14
"""
from alembic import op
import sqlalchemy as sa

revision = "0007_review_log"
down_revision = "0006_profile_reviews_today"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "LichSuOnTap",
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column("nguoi_dung_id", sa.Integer(), nullable=False),
        sa.Column("tien_do_hoc_id", sa.BigInteger(), nullable=False),
        sa.Column("ban_dich_id", sa.Integer(), nullable=False),
        sa.Column("chat_luong", sa.Integer(), nullable=False),
        sa.Column("thoi_diem_on", sa.DateTime(), nullable=False),
        sa.Column("khoang_lap_cu", sa.Integer(), nullable=True),
        sa.Column("khoang_lap_moi", sa.Integer(), nullable=True),
        sa.Column("do_de_nho_cu", sa.DECIMAL(precision=5, scale=2), nullable=True),
        sa.Column("do_de_nho_moi", sa.DECIMAL(precision=5, scale=2), nullable=True),
        sa.Column("so_lan_lap_cu", sa.Integer(), nullable=True),
        sa.Column("so_lan_lap_moi", sa.Integer(), nullable=True),
        sa.Column("ngay_on_tiep", sa.DateTime(), nullable=True),
        sa.Column("thoi_gian_tao", sa.DateTime(), nullable=False),
        sa.ForeignKeyConstraint(["nguoi_dung_id"], ["NguoiDung.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["tien_do_hoc_id"], ["TienDoHoc.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["ban_dich_id"], ["BanDich.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_LichSuOnTap_user_time",
        "LichSuOnTap",
        ["nguoi_dung_id", "thoi_diem_on"],
        unique=False,
    )
    op.create_index(
        "ix_LichSuOnTap_progress_time",
        "LichSuOnTap",
        ["tien_do_hoc_id", "thoi_diem_on"],
        unique=False,
    )
    op.create_index(
        "ix_LichSuOnTap_ban_dich_id",
        "LichSuOnTap",
        ["ban_dich_id"],
        unique=False,
    )


def downgrade():
    op.drop_index("ix_LichSuOnTap_ban_dich_id", table_name="LichSuOnTap")
    op.drop_index("ix_LichSuOnTap_progress_time", table_name="LichSuOnTap")
    op.drop_index("ix_LichSuOnTap_user_time", table_name="LichSuOnTap")
    op.drop_table("LichSuOnTap")
