"""Add traceability columns to BanDich

Revision ID: 0018_bandich_traceability
Revises: 0017_vietnamese_id_column_names
"""
from alembic import op
import sqlalchemy as sa


revision = "0018_bandich_traceability"
down_revision = "0017_vietnamese_id_column_names"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("BanDich", sa.Column("du_doan_ai_id", sa.Integer(), nullable=True))
    op.add_column("BanDich", sa.Column("nguoi_tao_id", sa.Integer(), nullable=True))
    op.add_column("BanDich", sa.Column("nguoi_duyet_id", sa.Integer(), nullable=True))
    op.add_column("BanDich", sa.Column("thoi_gian_duyet", sa.DateTime(), nullable=True))

    op.create_index("ix_BanDich_du_doan_ai_id", "BanDich", ["du_doan_ai_id"])
    op.create_foreign_key(
        "fk_BanDich_du_doan_ai_id",
        "BanDich", "DuDoanAI",
        ["du_doan_ai_id"], ["id"],
        ondelete="SET NULL",
    )
    op.create_foreign_key(
        "fk_BanDich_nguoi_tao_id",
        "BanDich", "NguoiDung",
        ["nguoi_tao_id"], ["id"],
        ondelete="SET NULL",
    )
    op.create_foreign_key(
        "fk_BanDich_nguoi_duyet_id",
        "BanDich", "NguoiDung",
        ["nguoi_duyet_id"], ["id"],
        ondelete="SET NULL",
    )


def downgrade():
    op.drop_constraint("fk_BanDich_nguoi_duyet_id", "BanDich", type_="foreignkey")
    op.drop_constraint("fk_BanDich_nguoi_tao_id", "BanDich", type_="foreignkey")
    op.drop_constraint("fk_BanDich_du_doan_ai_id", "BanDich", type_="foreignkey")
    op.drop_index("ix_BanDich_du_doan_ai_id", table_name="BanDich")
    op.drop_column("BanDich", "thoi_gian_duyet")
    op.drop_column("BanDich", "nguoi_duyet_id")
    op.drop_column("BanDich", "nguoi_tao_id")
    op.drop_column("BanDich", "du_doan_ai_id")
