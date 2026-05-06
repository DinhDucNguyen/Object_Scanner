"""Init HeThongHocNgonNgu schema v2

Revision ID: 0001_init_schema
Revises:
Create Date: 2026-05-04
"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa

revision: str = '0001_init_schema'
down_revision: Union[str, Sequence[str], None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table('VaiTro',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('ten_vai_tro', sa.String(length=50), nullable=True),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('ten_vai_tro')
    )
    op.create_table('TrangThaiNguoiDung',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('ten_trang_thai', sa.String(length=50), nullable=True),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('ten_trang_thai')
    )
    op.create_table('NgonNgu',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('ma_ngon_ngu', sa.String(length=10), nullable=False),
        sa.Column('ten_ngon_ngu', sa.String(length=50), nullable=False),
        sa.Column('icon_co', sa.String(length=255), nullable=True),
        sa.Column('dang_hoat_dong', sa.Boolean(), nullable=True),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('ma_ngon_ngu')
    )
    op.create_table('NguoiDung',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('ten_dang_nhap', sa.String(length=50), nullable=False),
        sa.Column('email', sa.String(length=100), nullable=False),
        sa.Column('mat_khau_ma_hoa', sa.String(length=255), nullable=False),
        sa.Column('vai_tro_id', sa.Integer(), nullable=False),
        sa.Column('trang_thai_id', sa.Integer(), nullable=False),
        sa.Column('lan_dang_nhap_cuoi', sa.DateTime(), nullable=True),
        sa.Column('ngay_tao', sa.TIMESTAMP(), nullable=True),
        sa.Column('ngay_cap_nhat', sa.TIMESTAMP(), nullable=True),
        sa.Column('thoi_gian_xoa', sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(['vai_tro_id'], ['VaiTro.id'], name='fk_nguoiDung_vaitro'),
        sa.ForeignKeyConstraint(['trang_thai_id'], ['TrangThaiNguoiDung.id'], name='fk_nguoiDung_trangthai'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('email'),
        sa.UniqueConstraint('ten_dang_nhap')
    )
    op.create_table('HoSo',
        sa.Column('user_id', sa.Integer(), nullable=False),
        sa.Column('ho_ten', sa.String(length=100), nullable=True),
        sa.Column('anh_dai_dien', sa.String(length=255), nullable=True),
        sa.Column('gioi_thieu', sa.String(length=500), nullable=True),
        sa.ForeignKeyConstraint(['user_id'], ['NguoiDung.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('user_id')
    )
    op.create_table('CaiDatNguoiDung',
        sa.Column('user_id', sa.Integer(), nullable=False),
        sa.Column('ngon_ngu_me', sa.Integer(), nullable=True),
        sa.Column('ngon_ngu_hoc', sa.Integer(), nullable=True),
        sa.ForeignKeyConstraint(['user_id'], ['NguoiDung.id'], ondelete='CASCADE'),
        sa.ForeignKeyConstraint(['ngon_ngu_me'], ['NgonNgu.id'], name='fk_caidat_lang_native'),
        sa.ForeignKeyConstraint(['ngon_ngu_hoc'], ['NgonNgu.id'], name='fk_caidat_lang_target'),
        sa.PrimaryKeyConstraint('user_id')
    )
    op.create_table('DanhMuc',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('ten_danh_muc', sa.String(length=100), nullable=True),
        sa.Column('danh_muc_cha', sa.Integer(), nullable=True),
        sa.Column('mo_ta', sa.Text(), nullable=True),
        sa.Column('thoi_gian_xoa', sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(['danh_muc_cha'], ['DanhMuc.id'], ondelete='SET NULL'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_table('DoiTuong',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('danh_muc_id', sa.Integer(), nullable=True),
        sa.Column('ma_doi_tuong', sa.String(length=100), nullable=True),
        sa.Column('muc_do_kho', sa.Integer(), nullable=True),
        sa.Column('tao_boi', sa.Integer(), nullable=True),
        sa.Column('thoi_gian_xoa', sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(['danh_muc_id'], ['DanhMuc.id']),
        sa.ForeignKeyConstraint(['tao_boi'], ['NguoiDung.id']),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('ma_doi_tuong')
    )
    op.create_index('ix_DoiTuong_danh_muc_id', 'DoiTuong', ['danh_muc_id'], unique=False)
    op.create_table('BanDich',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('doi_tuong_id', sa.Integer(), nullable=True),
        sa.Column('ngon_ngu_id', sa.Integer(), nullable=True),
        sa.Column('tu_vung', sa.String(length=255), nullable=True),
        sa.Column('phien_am', sa.String(length=255), nullable=True),
        sa.Column('loai_tu', sa.String(length=50), nullable=True),
        sa.Column('dinh_nghia', sa.Text(), nullable=True),
        sa.Column('am_thanh_url', sa.String(length=255), nullable=True),
        sa.Column('nguon_du_lieu', sa.Enum('thu_cong', 'gemini', name='nguondulieu'), nullable=True),
        sa.Column('da_xac_nhan', sa.Boolean(), nullable=True),
        sa.Column('ngay_tao', sa.TIMESTAMP(), nullable=True),
        sa.Column('thoi_gian_xoa', sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(['doi_tuong_id'], ['DoiTuong.id'], ondelete='CASCADE'),
        sa.ForeignKeyConstraint(['ngon_ngu_id'], ['NgonNgu.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('doi_tuong_id', 'ngon_ngu_id', name='unique_trans')
    )
    op.create_index('ix_BanDich_doi_tuong_id', 'BanDich', ['doi_tuong_id'], unique=False)
    op.create_index('ix_BanDich_ngon_ngu_id', 'BanDich', ['ngon_ngu_id'], unique=False)
    op.create_table('ViDu',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('ban_dich_id', sa.Integer(), nullable=True),
        sa.Column('cau_vi_du', sa.Text(), nullable=True),
        sa.Column('dich_nghia', sa.Text(), nullable=True),
        sa.Column('nguon_du_lieu', sa.String(length=50), nullable=True),
        sa.ForeignKeyConstraint(['ban_dich_id'], ['BanDich.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_table('AnhDoiTuong',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('doi_tuong_id', sa.Integer(), nullable=False),
        sa.Column('url', sa.String(length=255), nullable=False),
        sa.Column('doi_tuong_chinh', sa.Boolean(), nullable=True),
        sa.Column('thoi_gian_xoa', sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(['doi_tuong_id'], ['DoiTuong.id'], ondelete='CASCADE', name='fk_media_obj'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_table('BoSuuTap',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('user_id', sa.Integer(), nullable=False),
        sa.Column('ten_bo_suu_tap', sa.String(length=100), nullable=False),
        sa.Column('cong_khai', sa.Boolean(), nullable=True),
        sa.Column('ngay_tao', sa.TIMESTAMP(), nullable=True),
        sa.Column('thoi_gian_xoa', sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(['user_id'], ['NguoiDung.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_table('ChiTietBoSuuTap',
        sa.Column('bo_suu_tap_id', sa.Integer(), nullable=False),
        sa.Column('ban_dich_id', sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(['ban_dich_id'], ['BanDich.id'], ondelete='CASCADE'),
        sa.ForeignKeyConstraint(['bo_suu_tap_id'], ['BoSuuTap.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('bo_suu_tap_id', 'ban_dich_id')
    )
    op.create_table('LichSuQuet',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('user_id', sa.Integer(), nullable=True),
        sa.Column('doi_tuong_id', sa.Integer(), nullable=True),
        sa.Column('url_anh', sa.String(length=255), nullable=True),
        sa.Column('do_tin_cay', sa.Float(), nullable=True),
        sa.Column('thoi_gian', sa.TIMESTAMP(), nullable=True),
        sa.ForeignKeyConstraint(['doi_tuong_id'], ['DoiTuong.id'], ondelete='SET NULL'),
        sa.ForeignKeyConstraint(['user_id'], ['NguoiDung.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_table('DuDoanAI',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('scan_id', sa.Integer(), nullable=False),
        sa.Column('nguon_ai', sa.Enum('yolo', 'gemini', name='nguonai'), nullable=False),
        sa.Column('nhan_du_doan', sa.String(length=255), nullable=True),
        sa.Column('do_tin_cay', sa.Float(), nullable=True),
        sa.Column('mo_ta', sa.Text(), nullable=True),
        sa.Column('ket_qua_dung', sa.Boolean(), nullable=True),
        sa.Column('thoi_gian', sa.TIMESTAMP(), nullable=True),
        sa.ForeignKeyConstraint(['scan_id'], ['LichSuQuet.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_table('TienDoHoc',
        sa.Column('id', sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column('user_id', sa.Integer(), nullable=False),
        sa.Column('ban_dich_id', sa.Integer(), nullable=False),
        sa.Column('do_de_nho', sa.DECIMAL(precision=5, scale=2), nullable=True),
        sa.Column('khoang_lap', sa.Integer(), nullable=True),
        sa.Column('so_lan_lap', sa.Integer(), nullable=True),
        sa.Column('ngay_on_tiep', sa.DateTime(), nullable=True),
        sa.Column('lan_on_cuoi', sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(['ban_dich_id'], ['BanDich.id'], ondelete='CASCADE'),
        sa.ForeignKeyConstraint(['user_id'], ['NguoiDung.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('user_id', 'ban_dich_id', name='unique_user_translation')
    )
    op.create_table('TraTuDien',
        sa.Column('id', sa.Integer(), autoincrement=True, nullable=False),
        sa.Column('user_id', sa.Integer(), nullable=True),
        sa.Column('tu_tra', sa.String(length=255), nullable=False),
        sa.Column('ngon_ngu_tra_id', sa.Integer(), nullable=True),
        sa.Column('doi_tuong_id', sa.Integer(), nullable=True),
        sa.Column('nguon_du_lieu', sa.Enum('he_thong', 'gemini', name='nguontracuu'), nullable=True),
        sa.Column('lan_tra_cuoi', sa.TIMESTAMP(), nullable=True),
        sa.ForeignKeyConstraint(['doi_tuong_id'], ['DoiTuong.id'], ondelete='SET NULL'),
        sa.ForeignKeyConstraint(['ngon_ngu_tra_id'], ['NgonNgu.id'], ondelete='CASCADE'),
        sa.ForeignKeyConstraint(['user_id'], ['NguoiDung.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )


def downgrade() -> None:
    op.drop_table('TraTuDien')
    op.drop_table('TienDoHoc')
    op.drop_table('DuDoanAI')
    op.drop_table('LichSuQuet')
    op.drop_table('ChiTietBoSuuTap')
    op.drop_table('BoSuuTap')
    op.drop_table('AnhDoiTuong')
    op.drop_table('ViDu')
    op.drop_index('ix_BanDich_ngon_ngu_id', table_name='BanDich')
    op.drop_index('ix_BanDich_doi_tuong_id', table_name='BanDich')
    op.drop_table('BanDich')
    op.drop_index('ix_DoiTuong_danh_muc_id', table_name='DoiTuong')
    op.drop_table('DoiTuong')
    op.drop_table('DanhMuc')
    op.drop_table('CaiDatNguoiDung')
    op.drop_table('HoSo')
    op.drop_table('NguoiDung')
    op.drop_table('NgonNgu')
    op.drop_table('TrangThaiNguoiDung')
    op.drop_table('VaiTro')
