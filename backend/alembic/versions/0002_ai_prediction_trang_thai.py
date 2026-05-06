"""Thay the ket_qua_dung bang trang_thai trong bang DuDoanAI

Revision ID: 0002_ai_prediction_trang_thai
Revises: 0001_init_schema
Create Date: 2026-05-05

"""
from alembic import op
import sqlalchemy as sa

revision = '0002_ai_prediction_trang_thai'
down_revision = '0001_init_schema'
branch_labels = None
depends_on = None


def upgrade() -> None:
    # 1. Thêm cột trang_thai (mặc định cho_duyet)
    op.add_column(
        'DuDoanAI',
        sa.Column(
            'trang_thai',
            sa.Enum('cho_duyet', 'da_duyet', 'tu_choi', name='trangthaiduyet'),
            nullable=False,
            server_default='cho_duyet',
        )
    )
    op.create_index('ix_DuDoanAI_trang_thai', 'DuDoanAI', ['trang_thai'])

    # 2. Xoá cột ket_qua_dung
    op.drop_column('DuDoanAI', 'ket_qua_dung')


def downgrade() -> None:
    # Khôi phục ket_qua_dung
    op.add_column(
        'DuDoanAI',
        sa.Column('ket_qua_dung', sa.Boolean(), nullable=True, server_default=sa.false())
    )

    op.drop_index('ix_DuDoanAI_trang_thai', table_name='DuDoanAI')
    op.drop_column('DuDoanAI', 'trang_thai')
