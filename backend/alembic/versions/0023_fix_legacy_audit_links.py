"""Fix legacy approved audit links

Revision ID: 0023_fix_legacy_audit_links
Revises: 0022_require_object_source
"""
from __future__ import annotations

import json

from alembic import op
import sqlalchemy as sa


revision = "0023_fix_legacy_audit_links"
down_revision = "0022_require_object_source"
branch_labels = None
depends_on = None


def _legacy_admin_id(conn) -> int | None:
    return conn.execute(
        sa.text(
            """
            SELECT nd.id
            FROM NguoiDung nd
            JOIN VaiTro vt ON vt.id = nd.vai_tro_id
            WHERE nd.thoi_gian_xoa IS NULL
              AND LOWER(vt.ten_vai_tro) IN ('quan_tri', 'admin')
            ORDER BY nd.id
            LIMIT 1
            """
        )
    ).scalar()


def _language_id(conn, lang_code: str) -> int:
    lang_id = conn.execute(
        sa.text("SELECT id FROM NgonNgu WHERE ma_ngon_ngu = :code LIMIT 1"),
        {"code": lang_code},
    ).scalar()
    if lang_id:
        return lang_id

    conn.execute(
        sa.text(
            """
            INSERT INTO NgonNgu (ma_ngon_ngu, ten_ngon_ngu, dang_hoat_dong)
            VALUES (:code, :name, 1)
            """
        ),
        {"code": lang_code, "name": lang_code.upper()},
    )
    return conn.execute(sa.text("SELECT LAST_INSERT_ID()")).scalar()


def _insert_translation_from_prediction(conn, prediction_id: int, admin_id: int) -> None:
    row = conn.execute(
        sa.text(
            """
            SELECT ai.id, ai.lich_su_quet_id, ai.mo_ta, ai.thoi_gian,
                   COALESCE(MIN(ahl.thoi_gian_duyet), ai.thoi_gian) AS reviewed_at
            FROM DuDoanAI ai
            LEFT JOIN AnhHuanLuyen ahl ON ahl.du_doan_id = ai.id
            WHERE ai.id = :prediction_id
            GROUP BY ai.id, ai.lich_su_quet_id, ai.mo_ta, ai.thoi_gian
            """
        ),
        {"prediction_id": prediction_id},
    ).mappings().first()
    if not row or not row["mo_ta"]:
        return

    try:
        payload = json.loads(row["mo_ta"])
    except Exception:
        return

    object_id = payload.get("doi_tuong_chinh_id")
    if not object_id:
        object_code = payload.get("ma_doi_tuong_chinh") or payload.get("object_code")
        object_id = conn.execute(
            sa.text(
                """
                SELECT id FROM DoiTuong
                WHERE ma_doi_tuong = :object_code
                  AND thoi_gian_xoa IS NULL
                LIMIT 1
                """
            ),
            {"object_code": object_code},
        ).scalar()
    if not object_id:
        return

    reviewed_at = row["reviewed_at"] or row["thoi_gian"]

    conn.execute(
        sa.text(
            """
            UPDATE DoiTuong
            SET nguon_tao = 'gemini',
                nguoi_tao_id = NULL,
                du_doan_ai_id = :prediction_id,
                nguoi_duyet_id = COALESCE(nguoi_duyet_id, :admin_id),
                thoi_gian_duyet = COALESCE(thoi_gian_duyet, :reviewed_at)
            WHERE id = :object_id
              AND thoi_gian_xoa IS NULL
            """
        ),
        {
            "prediction_id": prediction_id,
            "admin_id": admin_id,
            "reviewed_at": reviewed_at,
            "object_id": object_id,
        },
    )

    if row["lich_su_quet_id"]:
        conn.execute(
            sa.text(
                """
                UPDATE LichSuQuet
                SET doi_tuong_id = :object_id
                WHERE id = :scan_id
                  AND doi_tuong_id IS NULL
                """
            ),
            {"object_id": object_id, "scan_id": row["lich_su_quet_id"]},
        )

    object_code = conn.execute(
        sa.text("SELECT ma_doi_tuong FROM DoiTuong WHERE id = :object_id"),
        {"object_id": object_id},
    ).scalar()
    conn.execute(
        sa.text(
            """
            UPDATE AnhHuanLuyen
            SET doi_tuong_id = COALESCE(doi_tuong_id, :object_id),
                nhan = COALESCE(:object_code, nhan),
                nguoi_duyet_id = COALESCE(nguoi_duyet_id, :admin_id)
            WHERE du_doan_id = :prediction_id
              AND thoi_gian_xoa IS NULL
            """
        ),
        {
            "object_id": object_id,
            "object_code": object_code,
            "admin_id": admin_id,
            "prediction_id": prediction_id,
        },
    )

    for item in payload.get("translations") or []:
        lang_code = item.get("lang_code") or "en"
        lang_id = _language_id(conn, lang_code)
        existing_id = conn.execute(
            sa.text(
                """
                SELECT id FROM BanDich
                WHERE doi_tuong_id = :object_id
                  AND ngon_ngu_id = :lang_id
                  AND thoi_gian_xoa IS NULL
                LIMIT 1
                """
            ),
            {"object_id": object_id, "lang_id": lang_id},
        ).scalar()
        if existing_id:
            continue

        conn.execute(
            sa.text(
                """
                INSERT INTO BanDich (
                    doi_tuong_id, ngon_ngu_id, tu_vung, phien_am, loai_tu,
                    dinh_nghia, nguon_du_lieu, da_xac_nhan, ngay_tao,
                    du_doan_ai_id, nguoi_duyet_id, thoi_gian_duyet
                )
                VALUES (
                    :object_id, :lang_id, :word_name, :phonetic, :part_of_speech,
                    :definition, 'gemini', 1, :reviewed_at,
                    :prediction_id, :admin_id, :reviewed_at
                )
                """
            ),
            {
                "object_id": object_id,
                "lang_id": lang_id,
                "word_name": item.get("word_name"),
                "phonetic": item.get("phonetic"),
                "part_of_speech": item.get("part_of_speech"),
                "definition": item.get("definition"),
                "reviewed_at": reviewed_at,
                "prediction_id": prediction_id,
                "admin_id": admin_id,
            },
        )
        translation_id = conn.execute(sa.text("SELECT LAST_INSERT_ID()")).scalar()

        for example in (item.get("example_sentences") or [])[:3]:
            if isinstance(example, dict):
                sentence = (example.get("en") or "").strip()
                meaning = (example.get("vi") or "").strip() or None
            else:
                sentence = str(example or "").strip()
                meaning = None
            if not sentence:
                continue
            conn.execute(
                sa.text(
                    """
                    INSERT INTO ViDu (ban_dich_id, cau_vi_du, dich_nghia, nguon_du_lieu)
                    VALUES (:translation_id, :sentence, :meaning, 'gemini')
                    """
                ),
                {
                    "translation_id": translation_id,
                    "sentence": sentence,
                    "meaning": meaning,
                },
            )


def upgrade():
    conn = op.get_bind()
    legacy_admin_id = _legacy_admin_id(conn)
    if legacy_admin_id is None:
        return

    conn.execute(
        sa.text(
            """
            UPDATE AnhHuanLuyen
            SET nguoi_duyet_id = :admin_id
            WHERE thoi_gian_xoa IS NULL
              AND trang_thai IN ('da_duyet', 'tu_choi')
              AND thoi_gian_duyet IS NOT NULL
              AND nguoi_duyet_id IS NULL
            """
        ),
        {"admin_id": legacy_admin_id},
    )

    prediction_ids = conn.execute(
        sa.text(
            """
            SELECT id
            FROM DuDoanAI
            WHERE nguon_ai = 'gemini'
              AND trang_thai = 'da_duyet'
              AND mo_ta IS NOT NULL
              AND mo_ta <> ''
            ORDER BY id
            """
        )
    ).scalars().all()

    for prediction_id in prediction_ids:
        _insert_translation_from_prediction(conn, prediction_id=prediction_id, admin_id=legacy_admin_id)


def downgrade():
    # Keep repaired audit links and recovered vocabulary data intact.
    pass
