"""Add BiDanhDoiTuong aliases

Revision ID: 0011_object_alias
Revises: 0010_ai_prediction_vai_tro
"""
from alembic import op
import sqlalchemy as sa

revision = "0011_object_alias"
down_revision = "0010_ai_prediction_vai_tro"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "BiDanhDoiTuong",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("doi_tuong_id", sa.Integer(), nullable=False),
        sa.Column("ma_bi_danh", sa.String(length=100), nullable=False),
        sa.Column("ten_hien_thi", sa.String(length=255), nullable=True),
        sa.Column("ngon_ngu", sa.String(length=10), nullable=True),
        sa.Column("thoi_gian_tao", sa.TIMESTAMP(), nullable=True),
        sa.ForeignKeyConstraint(["doi_tuong_id"], ["DoiTuong.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("ma_bi_danh", name="uq_BiDanhDoiTuong_ma_bi_danh"),
    )
    op.create_index("ix_BiDanhDoiTuong_doi_tuong_id", "BiDanhDoiTuong", ["doi_tuong_id"])
    op.create_index("ix_BiDanhDoiTuong_ma_bi_danh", "BiDanhDoiTuong", ["ma_bi_danh"])


def downgrade():
    op.drop_index("ix_BiDanhDoiTuong_ma_bi_danh", table_name="BiDanhDoiTuong")
    op.drop_index("ix_BiDanhDoiTuong_doi_tuong_id", table_name="BiDanhDoiTuong")
    op.drop_table("BiDanhDoiTuong")
