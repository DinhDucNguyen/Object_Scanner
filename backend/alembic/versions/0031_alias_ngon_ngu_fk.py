"""Normalize BiDanhDoiTuong.ngon_ngu to FK on NgonNgu.

Revision ID: 0031
Revises: 0030
Create Date: 2026-06-05
"""

from alembic import op
import sqlalchemy as sa


revision = "0031"
down_revision = "0030"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("BiDanhDoiTuong", sa.Column("ngon_ngu_id", sa.Integer(), nullable=True))

    op.execute("""
        UPDATE BiDanhDoiTuong b
        JOIN NgonNgu n ON n.ma_ngon_ngu = b.ngon_ngu
        SET b.ngon_ngu_id = n.id
        WHERE b.ngon_ngu IS NOT NULL
    """)

    op.create_foreign_key(
        "fk_BiDanhDoiTuong_ngon_ngu_id",
        "BiDanhDoiTuong", "NgonNgu",
        ["ngon_ngu_id"], ["id"],
        ondelete="SET NULL",
    )
    op.create_index("ix_BiDanhDoiTuong_ngon_ngu_id", "BiDanhDoiTuong", ["ngon_ngu_id"])

    op.drop_column("BiDanhDoiTuong", "ngon_ngu")


def downgrade() -> None:
    op.add_column("BiDanhDoiTuong", sa.Column("ngon_ngu", sa.String(10), nullable=True))

    op.execute("""
        UPDATE BiDanhDoiTuong b
        JOIN NgonNgu n ON n.id = b.ngon_ngu_id
        SET b.ngon_ngu = n.ma_ngon_ngu
        WHERE b.ngon_ngu_id IS NOT NULL
    """)

    op.drop_index("ix_BiDanhDoiTuong_ngon_ngu_id", table_name="BiDanhDoiTuong")
    op.drop_constraint("fk_BiDanhDoiTuong_ngon_ngu_id", "BiDanhDoiTuong", type_="foreignkey")
    op.drop_column("BiDanhDoiTuong", "ngon_ngu_id")
