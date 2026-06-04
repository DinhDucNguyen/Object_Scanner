"""Seed YOLO/COCO model aliases for objects whose DB code differs from the detector label

For example DoiTuong.ma_doi_tuong = 'computer_mouse' but COCO labels it 'mouse'.
We insert a BiDanhDoiTuong row so _training_model_coverage can resolve coverage correctly.

Revision ID: 0025_seed_yolo_coco_aliases
Revises: 0024_backfill_training_reviewers
"""
from alembic import op
import sqlalchemy as sa


revision = "0025_seed_yolo_coco_aliases"
down_revision = "0024_backfill_training_reviewers"
branch_labels = None
depends_on = None

# (object_code_in_db, detector_label)
ALIAS_MAP = [
    ("computer_mouse", "mouse"),
    ("smartphone", "cell_phone"),
]


def upgrade():
    conn = op.get_bind()
    for obj_code, alias_label in ALIAS_MAP:
        row = conn.execute(
            sa.text("SELECT id FROM DoiTuong WHERE ma_doi_tuong = :code AND thoi_gian_xoa IS NULL LIMIT 1"),
            {"code": obj_code},
        ).fetchone()
        if not row:
            continue
        obj_id = row[0]

        # Do not create aliases that collide with an existing canonical object.
        canonical = conn.execute(
            sa.text(
                """
                SELECT id FROM DoiTuong
                WHERE ma_doi_tuong = :alias
                  AND thoi_gian_xoa IS NULL
                LIMIT 1
                """
            ),
            {"alias": alias_label},
        ).fetchone()
        if canonical:
            continue

        exists = conn.execute(
            sa.text("SELECT id FROM BiDanhDoiTuong WHERE ma_bi_danh = :alias LIMIT 1"),
            {"alias": alias_label},
        ).fetchone()
        if exists:
            continue

        conn.execute(
            sa.text(
                "INSERT INTO BiDanhDoiTuong (doi_tuong_id, ma_bi_danh, ngon_ngu) VALUES (:obj_id, :alias, 'en')"
            ),
            {"obj_id": obj_id, "alias": alias_label},
        )


def downgrade():
    conn = op.get_bind()
    for _, alias_label in ALIAS_MAP:
        conn.execute(
            sa.text("DELETE FROM BiDanhDoiTuong WHERE ma_bi_danh = :alias"),
            {"alias": alias_label},
        )
