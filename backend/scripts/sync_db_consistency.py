"""
Synchronize obvious database inconsistencies without touching TraTuDien.

Run from the repo root:
    python backend/scripts/sync_db_consistency.py --dry-run
    python backend/scripts/sync_db_consistency.py
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
from typing import Any

from sqlalchemy import inspect as sa_inspect
from sqlalchemy.orm import Session

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from app.db.session import SessionLocal
from app.models.ai_feedback_report import AIPrediction
from app.models.collection_item import CollectionItem
from app.models.learning_progress import LearningProgress
from app.models.object import Object
from app.models.object_media import ObjectMedia
from app.models.review_log import ReviewLog
from app.models.scan_history import ScanHistory
from app.models.translation import Translation
from app.models.user_collection import UserCollection
from app.utils.timezone import now_vietnam


BACKUP_DIR = BACKEND_ROOT / "backups"

CATEGORY_BY_OBJECT_CODE = {
    "computer_mouse": 1,        # Điện tử
    "smartphone": 1,            # Điện tử
    "power_adapter": 1,         # Điện tử
    "wireless_earbuds": 1,      # Điện tử
    "microphone_arm_stand": 1,  # Điện tử
    "pen": 2,                   # Đồ dùng học tập
    "ballpoint_pen": 2,         # Đồ dùng học tập
    "travel_mug": 3,            # Nhà bếp
    "thermal_mug": 3,           # Nhà bếp
    "eyeglasses": 8,            # Phụ kiện
    "first_aid_cabinet": 16,    # Đồ gia dụng
    "bear_figurine": 16,        # Đồ gia dụng
    "air_cooler": 16,           # Đồ gia dụng
    "electric_fly_swatter": 16, # Đồ gia dụng
    "bonsai_tree": 4,           # Nội thất
}


def json_default(value: Any) -> Any:
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if isinstance(value, Decimal):
        return str(value)
    if hasattr(value, "value"):
        return value.value
    return str(value)


def row_dict(instance: Any) -> dict[str, Any]:
    return {
        attr.key: getattr(instance, attr.key)
        for attr in sa_inspect(instance).mapper.column_attrs
    }


def backup_add(backup: dict[str, list[dict[str, Any]]], action: str, instance: Any) -> None:
    backup.setdefault(action, []).append({
        "table": getattr(instance, "__tablename__", instance.__class__.__name__),
        "row": row_dict(instance),
    })


def estimate_quality(progress: LearningProgress) -> int:
    ef = float(progress.do_de_nho or 2.5)
    if ef >= 2.55:
        return 5
    if ef >= 2.45:
        return 4
    return 3


def sm2_ef_delta(quality: int) -> float:
    quality = max(0, min(5, quality))
    return 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)


def infer_previous_easiness_factor(current_ef: Any, quality: int) -> float:
    ef = float(current_ef or 2.5)
    previous = ef - sm2_ef_delta(quality)
    return round(max(1.3, previous), 2)


def backfill_review_logs(db: Session, stats: dict[str, int], backup: dict[str, list[dict[str, Any]]]) -> None:
    progress_rows = (
        db.query(LearningProgress)
        .outerjoin(ReviewLog, ReviewLog.tien_do_hoc_id == LearningProgress.id)
        .filter(
            LearningProgress.so_lan_lap > 0,
            ReviewLog.id.is_(None),
        )
        .order_by(LearningProgress.nguoi_dung_id, LearningProgress.lan_on_cuoi)
        .all()
    )

    for progress in progress_rows:
        repetitions = int(progress.so_lan_lap or 0)
        interval = int(progress.khoang_lap or 0)
        quality = estimate_quality(progress)
        log = ReviewLog(
            nguoi_dung_id=progress.nguoi_dung_id,
            tien_do_hoc_id=progress.id,
            ban_dich_id=progress.ban_dich_id,
            chat_luong=quality,
            thoi_diem_on=progress.lan_on_cuoi,
            khoang_lap_cu=0 if repetitions <= 1 else 1,
            khoang_lap_moi=interval,
            do_de_nho_cu=infer_previous_easiness_factor(progress.do_de_nho, quality),
            do_de_nho_moi=progress.do_de_nho,
            so_lan_lap_cu=max(0, repetitions - 1),
            so_lan_lap_moi=repetitions,
            ngay_on_tiep=progress.ngay_on_tiep,
            thoi_gian_tao=progress.lan_on_cuoi,
        )
        db.add(log)
        db.flush()
        backup_add(backup, "created", log)
        stats["review_logs_backfilled"] += 1


def fill_missing_old_easiness(db: Session, stats: dict[str, int], backup: dict[str, list[dict[str, Any]]]) -> None:
    logs = (
        db.query(ReviewLog)
        .filter(ReviewLog.do_de_nho_cu.is_(None), ReviewLog.do_de_nho_moi.isnot(None))
        .order_by(ReviewLog.id)
        .all()
    )
    for log in logs:
        backup_add(backup, "updated_before", log)
        log.do_de_nho_cu = infer_previous_easiness_factor(log.do_de_nho_moi, log.chat_luong)
        backup_add(backup, "updated_after", log)
        stats["review_logs_old_ef_filled"] += 1


def categorize_active_objects(db: Session, stats: dict[str, int], backup: dict[str, list[dict[str, Any]]]) -> None:
    for object_code, category_id in CATEGORY_BY_OBJECT_CODE.items():
        obj = (
            db.query(Object)
            .filter(Object.ma_doi_tuong == object_code, Object.thoi_gian_xoa.is_(None))
            .first()
        )
        if not obj or obj.danh_muc_id == category_id:
            continue

        has_translation = (
            db.query(Translation)
            .filter(Translation.doi_tuong_id == obj.id, Translation.thoi_gian_xoa.is_(None))
            .first()
            is not None
        )
        if not has_translation:
            continue

        backup_add(backup, "updated_before", obj)
        obj.danh_muc_id = category_id
        backup_add(backup, "updated_after", obj)
        stats["objects_categorized"] += 1


def delete_unused_empty_objects(db: Session, stats: dict[str, int], backup: dict[str, list[dict[str, Any]]]) -> None:
    objects = (
        db.query(Object)
        .outerjoin(Translation, Translation.doi_tuong_id == Object.id)
        .filter(Object.thoi_gian_xoa.is_(None), Translation.id.is_(None))
        .order_by(Object.id)
        .all()
    )

    for obj in objects:
        has_refs = bool(
            db.query(ScanHistory).filter(ScanHistory.doi_tuong_id == obj.id).first()
            or db.query(ObjectMedia).filter(ObjectMedia.doi_tuong_id == obj.id).first()
            or db.query(AIPrediction).filter(AIPrediction.nhan_du_doan == obj.ma_doi_tuong).first()
        )
        if has_refs:
            stats["empty_objects_skipped"] += 1
            continue

        backup_add(backup, "deleted", obj)
        db.delete(obj)
        stats["empty_objects_deleted"] += 1


def delete_empty_collections(db: Session, stats: dict[str, int], backup: dict[str, list[dict[str, Any]]]) -> None:
    collections = db.query(UserCollection).order_by(UserCollection.id).all()
    for collection in collections:
        item_count = (
            db.query(CollectionItem)
            .filter(CollectionItem.bo_suu_tap_id == collection.id)
            .count()
        )
        if item_count:
            continue
        backup_add(backup, "deleted", collection)
        db.delete(collection)
        stats["empty_collections_deleted"] += 1


def write_backup(backup: dict[str, list[dict[str, Any]]]) -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    path = BACKUP_DIR / f"db_sync_consistency_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    path.write_text(
        json.dumps(backup, ensure_ascii=False, indent=2, default=json_default),
        encoding="utf-8",
    )
    return path


def sync_database(dry_run: bool) -> tuple[dict[str, int], Path | None]:
    stats = {
        "review_logs_backfilled": 0,
        "review_logs_old_ef_filled": 0,
        "objects_categorized": 0,
        "empty_objects_deleted": 0,
        "empty_objects_skipped": 0,
        "empty_collections_deleted": 0,
    }
    backup: dict[str, list[dict[str, Any]]] = {
        "metadata": [{
            "dry_run": dry_run,
            "created_at": now_vietnam().isoformat(),
            "note": "TraTuDien intentionally untouched.",
        }]
    }

    db = SessionLocal()
    try:
        backfill_review_logs(db, stats, backup)
        fill_missing_old_easiness(db, stats, backup)
        categorize_active_objects(db, stats, backup)
        delete_unused_empty_objects(db, stats, backup)
        delete_empty_collections(db, stats, backup)

        backup_path = None
        if dry_run:
            db.rollback()
        else:
            backup_path = write_backup(backup)
            db.commit()
        return stats, backup_path
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Synchronize DB consistency without touching TraTuDien.")
    parser.add_argument("--dry-run", action="store_true", help="Preview counts without committing.")
    args = parser.parse_args()

    stats, backup_path = sync_database(args.dry_run)
    print("DB consistency sync:", "DRY RUN" if args.dry_run else "COMMITTED")
    for key, value in stats.items():
        print(f"{key}: {value}")
    if backup_path:
        print(f"backup: {backup_path}")


if __name__ == "__main__":
    main()
