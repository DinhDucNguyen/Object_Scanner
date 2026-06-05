"""Rename Google identifier column to Vietnamese

Revision ID: 0029
Revises: 0028
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy import inspect


revision = "0029"
down_revision = "0028"
branch_labels = None
depends_on = None

OLD_COLUMN = "google_sub"
NEW_COLUMN = "ma_dinh_danh_google"
OLD_CONSTRAINT = "uq_NguoiDung_google_sub"
NEW_CONSTRAINT = "uq_NguoiDung_ma_dinh_danh_google"


def _columns() -> set[str]:
    return {col["name"] for col in inspect(op.get_bind()).get_columns("NguoiDung")}


def _unique_names() -> set[str]:
    inspector = inspect(op.get_bind())
    names = {item["name"] for item in inspector.get_unique_constraints("NguoiDung") if item.get("name")}
    names.update(item["name"] for item in inspector.get_indexes("NguoiDung") if item.get("unique") and item.get("name"))
    return names


def _drop_unique_if_exists(name: str) -> None:
    if name in _unique_names():
        op.drop_constraint(name, "NguoiDung", type_="unique")


def _create_unique_if_missing(name: str, column: str) -> None:
    if name not in _unique_names():
        op.create_unique_constraint(name, "NguoiDung", [column])


def upgrade() -> None:
    columns = _columns()
    if OLD_COLUMN in columns:
        _drop_unique_if_exists(OLD_CONSTRAINT)
        op.alter_column(
            "NguoiDung",
            OLD_COLUMN,
            new_column_name=NEW_COLUMN,
            existing_type=sa.String(length=128),
            existing_nullable=True,
        )
    elif NEW_COLUMN not in columns:
        op.add_column("NguoiDung", sa.Column(NEW_COLUMN, sa.String(length=128), nullable=True))

    _create_unique_if_missing(NEW_CONSTRAINT, NEW_COLUMN)


def downgrade() -> None:
    columns = _columns()
    if NEW_COLUMN in columns:
        _drop_unique_if_exists(NEW_CONSTRAINT)
        op.alter_column(
            "NguoiDung",
            NEW_COLUMN,
            new_column_name=OLD_COLUMN,
            existing_type=sa.String(length=128),
            existing_nullable=True,
        )
    elif OLD_COLUMN not in columns:
        op.add_column("NguoiDung", sa.Column(OLD_COLUMN, sa.String(length=128), nullable=True))

    _create_unique_if_missing(OLD_CONSTRAINT, OLD_COLUMN)
