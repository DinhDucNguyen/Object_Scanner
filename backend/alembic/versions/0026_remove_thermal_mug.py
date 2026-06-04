"""Remove thermal mug data

Revision ID: 0026_remove_thermal_mug
Revises: 0025_seed_yolo_coco_aliases
"""
from alembic import op
import sqlalchemy as sa


revision = "0026_remove_thermal_mug"
down_revision = "0025_seed_yolo_coco_aliases"
branch_labels = None
depends_on = None


THERMAL_LABELS = (
    "thermal_mug",
    "thermal mug",
    "Thermal Mug",
    "travel_mug",
    "travel mug",
    "Travel Mug",
    "insulated_mug",
    "insulated mug",
    "Insulated Mug",
)


def upgrade():
    conn = op.get_bind()
    object_id = conn.execute(
        sa.text(
            """
            SELECT id FROM DoiTuong
            WHERE ma_doi_tuong = 'thermal_mug'
            LIMIT 1
            """
        )
    ).scalar()
    if object_id is None:
        return

    params = {"object_id": object_id, "labels": THERMAL_LABELS}

    def execute_with_labels(sql):
        conn.execute(
            sa.text(sql).bindparams(sa.bindparam("labels", expanding=True)),
            params,
        )

    conn.execute(
        sa.text(
            """
            DELETE ls FROM LichSuOnTap ls
            JOIN BanDich bd ON bd.id = ls.ban_dich_id
            WHERE bd.doi_tuong_id = :object_id
            """
        ),
        params,
    )
    conn.execute(
        sa.text(
            """
            DELETE td FROM TienDoHoc td
            JOIN BanDich bd ON bd.id = td.ban_dich_id
            WHERE bd.doi_tuong_id = :object_id
            """
        ),
        params,
    )
    conn.execute(
        sa.text(
            """
            DELETE ct FROM ChiTietBoSuuTap ct
            JOIN BanDich bd ON bd.id = ct.ban_dich_id
            WHERE bd.doi_tuong_id = :object_id
            """
        ),
        params,
    )
    conn.execute(
        sa.text(
            """
            DELETE vd FROM ViDu vd
            JOIN BanDich bd ON bd.id = vd.ban_dich_id
            WHERE bd.doi_tuong_id = :object_id
            """
        ),
        params,
    )
    conn.execute(
        sa.text("DELETE FROM BanDich WHERE doi_tuong_id = :object_id"),
        params,
    )

    execute_with_labels(
        """
            DELETE adi FROM AnhHuanLuyenDataset adi
            JOIN AnhHuanLuyen ahl ON ahl.id = adi.anh_huan_luyen_id
            WHERE ahl.doi_tuong_id = :object_id
               OR ahl.nhan IN :labels
            """
    )
    execute_with_labels(
        """
            DELETE FROM AnhHuanLuyen
            WHERE doi_tuong_id = :object_id
               OR nhan IN :labels
            """
    )

    execute_with_labels(
        """
            DELETE FROM DuDoanAI
            WHERE nhan_du_doan IN :labels
               OR mo_ta LIKE '%thermal_mug%'
               OR mo_ta LIKE '%travel_mug%'
               OR mo_ta LIKE '%insulated_mug%'
               OR lich_su_quet_id IN (
                    SELECT id FROM LichSuQuet WHERE doi_tuong_id = :object_id
               )
            """
    )
    conn.execute(
        sa.text("DELETE FROM LichSuQuet WHERE doi_tuong_id = :object_id"),
        params,
    )
    execute_with_labels(
        """
            DELETE FROM TraTuDien
            WHERE doi_tuong_id = :object_id
               OR tu_tra IN :labels
            """
    )
    conn.execute(
        sa.text("DELETE FROM AnhDoiTuong WHERE doi_tuong_id = :object_id"),
        params,
    )
    execute_with_labels(
        """
            DELETE FROM BiDanhDoiTuong
            WHERE doi_tuong_id = :object_id
               OR ma_bi_danh IN :labels
            """
    )
    conn.execute(
        sa.text("DELETE FROM DoiTuong WHERE id = :object_id"),
        params,
    )


def downgrade():
    # This migration intentionally removes a confusing object and its related
    # learning/moderation records. Recreating those rows would require restoring
    # historical user activity, so downgrade is intentionally a no-op.
    pass
