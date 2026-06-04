"""Dictionary lookup history columns

Revision ID: 0012_dictionary_lookup_history
Revises: 0011_object_alias
"""
from alembic import op
import sqlalchemy as sa

revision = "0012_dictionary_lookup_history"
down_revision = "0011_object_alias"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column("TraTuDien", sa.Column("ket_qua_dich", sa.String(500), nullable=True))
    op.add_column("TraTuDien", sa.Column("phien_am", sa.String(100), nullable=True))
    op.add_column("TraTuDien", sa.Column("den_ngon_ngu", sa.String(10), nullable=True))
    op.create_index("ix_TraTuDien_user_lan_tra", "TraTuDien", ["nguoi_dung_id", "lan_tra_cuoi"])


def downgrade():
    op.drop_index("ix_TraTuDien_user_lan_tra", table_name="TraTuDien")
    op.drop_column("TraTuDien", "den_ngon_ngu")
    op.drop_column("TraTuDien", "phien_am")
    op.drop_column("TraTuDien", "ket_qua_dich")
