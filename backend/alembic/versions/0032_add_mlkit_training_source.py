"""Add ML Kit as a training image source.

Revision ID: 0032
Revises: 0031
Create Date: 2026-06-08
"""

from alembic import op


revision = "0032"
down_revision = "0031"
branch_labels = None
depends_on = None


def _is_mysql() -> bool:
    return op.get_bind().dialect.name in {"mysql", "mariadb"}


def upgrade() -> None:
    if _is_mysql():
        op.execute(
            "ALTER TABLE AnhHuanLuyen "
            "MODIFY COLUMN nguon_du_lieu ENUM('yolo', 'mlkit', 'gemini', 'admin') NOT NULL"
        )


def downgrade() -> None:
    if _is_mysql():
        op.execute("UPDATE AnhHuanLuyen SET nguon_du_lieu = 'yolo' WHERE nguon_du_lieu = 'mlkit'")
        op.execute(
            "ALTER TABLE AnhHuanLuyen "
            "MODIFY COLUMN nguon_du_lieu ENUM('yolo', 'gemini', 'admin') NOT NULL"
        )
