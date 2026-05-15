"""
Backfill DuDoanAI rows for old unconfirmed Gemini translations.

Default mode is dry-run:
    python backend/scripts/backfill_gemini_predictions.py

Apply changes:
    python backend/scripts/backfill_gemini_predictions.py --apply
"""
import argparse
import json
import os
import sys
from pathlib import Path


BACKEND_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_DIR))
os.chdir(BACKEND_DIR)

from app.db.session import SessionLocal  # noqa: E402
from app.models.ai_feedback_report import AIPrediction, NguonAI, TrangThaiDuyet  # noqa: E402
from app.models.object import Object  # noqa: E402
from app.models.scan_history import ScanHistory  # noqa: E402
from app.models.translation import Translation, NguonDuLieu  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true", help="Write DuDoanAI rows to the database.")
    args = parser.parse_args()

    db = SessionLocal()
    created = 0
    skipped = 0
    try:
        translations = (
            db.query(Translation)
            .join(Object, Object.id == Translation.doi_tuong_id)
            .filter(Translation.nguon_du_lieu == NguonDuLieu.gemini)
            .filter(Translation.da_xac_nhan.is_(False))
            .filter(Translation.thoi_gian_xoa.is_(None))
            .filter(Object.thoi_gian_xoa.is_(None))
            .all()
        )

        for translation in translations:
            obj = translation.object
            if not obj or _prediction_exists(db, translation):
                skipped += 1
                continue

            payload = _payload_for_translation(translation)
            if args.apply:
                scan = _latest_scan_for_object(db, obj.id)
                if not scan:
                    scan = ScanHistory(
                        user_id=None,
                        doi_tuong_id=obj.id,
                        do_tin_cay=1.0,
                        url_anh=None,
                    )
                    db.add(scan)
                    db.flush()

                db.add(AIPrediction(
                    scan_id=scan.id,
                    nguon_ai=NguonAI.gemini,
                    nhan_du_doan=obj.ma_doi_tuong,
                    do_tin_cay=1.0,
                    mo_ta=json.dumps(payload, ensure_ascii=False),
                    trang_thai=TrangThaiDuyet.cho_duyet,
                ))
            created += 1

        if args.apply:
            db.commit()
        else:
            db.rollback()

        mode = "applied" if args.apply else "dry-run"
        print(f"{mode}: would_create={created}, skipped={skipped}, scanned={len(translations)}")
    finally:
        db.close()


def _prediction_exists(db, translation: Translation) -> bool:
    obj = translation.object
    if not obj:
        return False

    candidates = db.query(AIPrediction).filter(
        AIPrediction.nhan_du_doan == obj.ma_doi_tuong,
    ).all()
    for prediction in candidates:
        try:
            payload = json.loads(prediction.mo_ta or "{}")
        except Exception:
            continue
        if translation.id in (payload.get("temp_translation_ids") or []):
            return True
    return False


def _latest_scan_for_object(db, object_id: int) -> ScanHistory | None:
    return (
        db.query(ScanHistory)
        .filter(ScanHistory.doi_tuong_id == object_id)
        .order_by(ScanHistory.thoi_gian.desc())
        .first()
    )


def _payload_for_translation(translation: Translation) -> dict:
    obj = translation.object
    lang = translation.language
    examples = [
        example.cau_vi_du
        for example in sorted((translation.examples or []), key=lambda item: item.id or 0)[:3]
        if example.cau_vi_du
    ]
    return {
        "object_code": obj.ma_doi_tuong if obj else None,
        "category": obj.category.ten_danh_muc if obj and obj.category else None,
        "translations": [{
            "lang_code": lang.ma_ngon_ngu if lang else "en",
            "word_name": translation.tu_vung,
            "phonetic": translation.phien_am,
            "part_of_speech": translation.loai_tu,
            "definition": translation.dinh_nghia,
            "example_sentences": examples,
        }],
        "temp_object_id": obj.id if obj else None,
        "temp_translation_ids": [translation.id],
        "source": "backfill_gemini_unconfirmed",
    }


if __name__ == "__main__":
    main()
