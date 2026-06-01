"""Replace den_ngon_ngu VARCHAR with den_ngon_ngu_id FK to NgonNgu

Revision ID: 0015_dictionary_lookup_den_ngon_ngu_fk
Revises: 0014_drop_lichsuontap_col
"""
from alembic import op
import sqlalchemy as sa


revision = "0015_den_ngon_ngu_fk"
down_revision = "0014_drop_lichsuontap_col"
branch_labels = None
depends_on = None


def upgrade():
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    columns = {col["name"] for col in inspector.get_columns("TraTuDien")}
    foreign_keys = {fk["name"] for fk in inspector.get_foreign_keys("TraTuDien")}

    if "den_ngon_ngu_id" not in columns:
        op.add_column(
            "TraTuDien",
            sa.Column("den_ngon_ngu_id", sa.Integer(), nullable=True),
        )

    if "den_ngon_ngu" in columns:
        op.execute("""
            UPDATE TraTuDien t
            JOIN NgonNgu n ON n.ma_ngon_ngu = t.den_ngon_ngu
            SET t.den_ngon_ngu_id = n.id
            WHERE t.den_ngon_ngu IS NOT NULL
        """)
        op.drop_column("TraTuDien", "den_ngon_ngu")

    if "fk_tratudien_den_ngon_ngu_id" not in foreign_keys:
        op.create_foreign_key(
            "fk_tratudien_den_ngon_ngu_id",
            "TraTuDien",
            "NgonNgu",
            ["den_ngon_ngu_id"],
            ["id"],
            ondelete="SET NULL",
        )


def downgrade():
    inspector = sa.inspect(op.get_bind())
    columns = {col["name"] for col in inspector.get_columns("TraTuDien")}
    foreign_keys = {fk["name"] for fk in inspector.get_foreign_keys("TraTuDien")}

    if "den_ngon_ngu" not in columns:
        op.add_column(
            "TraTuDien",
            sa.Column("den_ngon_ngu", sa.String(10), nullable=True),
        )

    if "den_ngon_ngu_id" in columns:
        op.execute("""
            UPDATE TraTuDien t
            JOIN NgonNgu n ON n.id = t.den_ngon_ngu_id
            SET t.den_ngon_ngu = n.ma_ngon_ngu
            WHERE t.den_ngon_ngu_id IS NOT NULL
        """)
        if "fk_tratudien_den_ngon_ngu_id" in foreign_keys:
            op.drop_constraint("fk_tratudien_den_ngon_ngu_id", "TraTuDien", type_="foreignkey")
        op.drop_column("TraTuDien", "den_ngon_ngu_id")
