"""Training image review pipeline

Revision ID: 0013_training_image_pipeline
Revises: 0012_dictionary_lookup_history
"""
from alembic import op
import sqlalchemy as sa


revision = "0013_training_image_pipeline"
down_revision = "0012_dictionary_lookup_history"
branch_labels = None
depends_on = None


nguon_anh_huan_luyen = sa.Enum("yolo", "gemini", "admin", name="nguonanhhuanluyen")
trang_thai_anh_huan_luyen = sa.Enum("cho_duyet", "da_duyet", "tu_choi", name="trangthaianhhuanluyen")


def upgrade():
    op.create_table(
        "PhienBanDataset",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("ma_phien_ban", sa.String(length=100), nullable=False),
        sa.Column("mo_ta", sa.Text(), nullable=True),
        sa.Column("tong_anh", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("tong_nhan", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("thoi_gian_tao", sa.TIMESTAMP(), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("ghi_chu", sa.Text(), nullable=True),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("ma_phien_ban"),
    )

    op.create_table(
        "AnhHuanLuyen",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("lich_su_quet_id", sa.Integer(), nullable=True),
        sa.Column("du_doan_id", sa.Integer(), nullable=True),
        sa.Column("doi_tuong_id", sa.Integer(), nullable=True),
        sa.Column("url_anh", sa.String(length=255), nullable=False),
        sa.Column("nhan", sa.String(length=255), nullable=True),
        sa.Column("nguon_du_lieu", nguon_anh_huan_luyen, nullable=False),
        sa.Column("do_tin_cay", sa.Float(), nullable=True),
        sa.Column("diem_chat_luong", sa.Integer(), nullable=True),
        sa.Column("trang_thai", trang_thai_anh_huan_luyen, nullable=False, server_default="cho_duyet"),
        sa.Column("nguoi_duyet_id", sa.Integer(), nullable=True),
        sa.Column("thoi_gian_tao", sa.TIMESTAMP(), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("thoi_gian_duyet", sa.DateTime(), nullable=True),
        sa.Column("ghi_chu", sa.Text(), nullable=True),
        sa.Column("thoi_gian_xoa", sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(["doi_tuong_id"], ["DoiTuong.id"], ondelete="SET NULL"),
        sa.ForeignKeyConstraint(["du_doan_id"], ["DuDoanAI.id"], ondelete="SET NULL"),
        sa.ForeignKeyConstraint(["nguoi_duyet_id"], ["NguoiDung.id"], ondelete="SET NULL"),
        sa.ForeignKeyConstraint(["lich_su_quet_id"], ["LichSuQuet.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_AnhHuanLuyen_doi_tuong_id", "AnhHuanLuyen", ["doi_tuong_id"])
    op.create_index("ix_AnhHuanLuyen_du_doan_id", "AnhHuanLuyen", ["du_doan_id"])
    op.create_index("ix_AnhHuanLuyen_nhan", "AnhHuanLuyen", ["nhan"])
    op.create_index("ix_AnhHuanLuyen_lich_su_quet_id", "AnhHuanLuyen", ["lich_su_quet_id"])
    op.create_index("ix_AnhHuanLuyen_trang_thai", "AnhHuanLuyen", ["trang_thai"])

    op.create_table(
        "AnhHuanLuyenDataset",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("dataset_id", sa.Integer(), nullable=False),
        sa.Column("anh_huan_luyen_id", sa.Integer(), nullable=False),
        sa.Column("thoi_gian_tao", sa.TIMESTAMP(), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.ForeignKeyConstraint(["anh_huan_luyen_id"], ["AnhHuanLuyen.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["dataset_id"], ["PhienBanDataset.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("dataset_id", "anh_huan_luyen_id", name="uq_dataset_training_image"),
    )
    op.create_index("ix_AnhHuanLuyenDataset_anh_huan_luyen_id", "AnhHuanLuyenDataset", ["anh_huan_luyen_id"])
    op.create_index("ix_AnhHuanLuyenDataset_dataset_id", "AnhHuanLuyenDataset", ["dataset_id"])

    op.execute(
        """
        INSERT INTO AnhHuanLuyen (
            lich_su_quet_id,
            doi_tuong_id,
            url_anh,
            nhan,
            nguon_du_lieu,
            do_tin_cay,
            trang_thai,
            thoi_gian_tao,
            ghi_chu
        )
        SELECT
            l.id,
            l.doi_tuong_id,
            l.url_anh,
            d.ma_doi_tuong,
            'admin',
            l.do_tin_cay,
            'cho_duyet',
            COALESCE(l.thoi_gian, CURRENT_TIMESTAMP),
            'Migrated from existing training pool'
        FROM LichSuQuet l
        JOIN DoiTuong d ON l.doi_tuong_id = d.id
        WHERE l.doi_tuong_id IS NOT NULL
          AND l.url_anh IS NOT NULL
        """
    )


def downgrade():
    op.drop_index("ix_AnhHuanLuyenDataset_dataset_id", table_name="AnhHuanLuyenDataset")
    op.drop_index("ix_AnhHuanLuyenDataset_anh_huan_luyen_id", table_name="AnhHuanLuyenDataset")
    op.drop_table("AnhHuanLuyenDataset")
    op.drop_index("ix_AnhHuanLuyen_trang_thai", table_name="AnhHuanLuyen")
    op.drop_index("ix_AnhHuanLuyen_lich_su_quet_id", table_name="AnhHuanLuyen")
    op.drop_index("ix_AnhHuanLuyen_nhan", table_name="AnhHuanLuyen")
    op.drop_index("ix_AnhHuanLuyen_du_doan_id", table_name="AnhHuanLuyen")
    op.drop_index("ix_AnhHuanLuyen_doi_tuong_id", table_name="AnhHuanLuyen")
    op.drop_table("AnhHuanLuyen")
    op.drop_table("PhienBanDataset")
