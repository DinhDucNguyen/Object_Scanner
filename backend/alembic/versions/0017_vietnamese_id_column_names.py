"""Rename English id columns to Vietnamese names

Revision ID: 0017_vietnamese_id_column_names
Revises: 0016_user_email_verified
"""
from alembic import op
import sqlalchemy as sa


revision = "0017_vietnamese_id_column_names"
down_revision = "0016_user_email_verified"
branch_labels = None
depends_on = None

OLD_NGUOI_DUNG_ID = "user" + "_id"
OLD_LICH_SU_QUET_ID = "scan" + "_id"


def _columns(table_name: str) -> dict[str, dict]:
    inspector = sa.inspect(op.get_bind())
    return {col["name"]: col for col in inspector.get_columns(table_name)}


def _rename_column(table_name: str, old_name: str, new_name: str, column_type) -> None:
    columns = _columns(table_name)
    if old_name in columns and new_name not in columns:
        nullable = columns[old_name].get("nullable", True)
        op.alter_column(
            table_name,
            old_name,
            new_column_name=new_name,
            existing_type=column_type,
            existing_nullable=nullable,
        )


def upgrade():
    integer = sa.Integer()

    for table_name in (
        "HoSo",
        "CaiDatNguoiDung",
        "BoSuuTap",
        "LichSuQuet",
        "TienDoHoc",
        "TraTuDien",
        "LichSuOnTap",
    ):
        _rename_column(table_name, OLD_NGUOI_DUNG_ID, "nguoi_dung_id", integer)

    for table_name in ("DuDoanAI", "AnhHuanLuyen"):
        _rename_column(table_name, OLD_LICH_SU_QUET_ID, "lich_su_quet_id", integer)


def downgrade():
    integer = sa.Integer()

    for table_name in (
        "HoSo",
        "CaiDatNguoiDung",
        "BoSuuTap",
        "LichSuQuet",
        "TienDoHoc",
        "TraTuDien",
        "LichSuOnTap",
    ):
        _rename_column(table_name, "nguoi_dung_id", OLD_NGUOI_DUNG_ID, integer)

    for table_name in ("DuDoanAI", "AnhHuanLuyen"):
        _rename_column(table_name, "lich_su_quet_id", OLD_LICH_SU_QUET_ID, integer)
