"""
Clean obvious stale/noisy data from the development database.

Run from the backend directory:
    python scripts/cleanup_database.py --dry-run
    python scripts/cleanup_database.py

The committed run writes a JSON backup of rows it deletes or changes to
backend/backups/db_cleanup_YYYYmmdd_HHMMSS.json.
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
from app.models.ai_feedback_report import AIPrediction, TrangThaiDuyet
from app.models.category import Category
from app.models.collection_item import CollectionItem
from app.models.dictionary_lookup import DictionaryLookup
from app.models.example import ViDu
from app.models.language import Language
from app.models.learning_progress import LearningProgress
from app.models.object import Object
from app.models.object_media import ObjectMedia
from app.models.scan_history import ScanHistory
from app.models.translation import NguonDuLieu, Translation
from scripts.seed_yolo_coco import SEED_ENTRIES, normalize_object_code


BACKUP_DIR = BACKEND_ROOT / "backups"

CATEGORY_DESCRIPTIONS = {
    "Con người": "Người và các vai trò cơ bản",
    "Địa điểm": "Địa danh và nơi chốn thường gặp",
    "Đồ dùng học tập": "Sách vở, giấy tờ và dụng cụ học tập",
    "Điện tử": "Thiết bị điện tử và công nghệ",
    "Nhà bếp": "Đồ dùng nhà bếp và thiết bị bếp",
    "Phụ kiện": "Vật dụng cá nhân và phụ kiện",
    "Thực phẩm": "Trái cây, rau củ và món ăn",
}

GEMINI_OBJECT_CATEGORY = {
    "baby": "Con người",
    "boy": "Con người",
    "child": "Con người",
    "man": "Con người",
    "young_man": "Con người",
    "bouquet": "Phụ kiện",
    "flower_bouquet": "Phụ kiện",
    "graduation_bouquet": "Phụ kiện",
    "certificate_of_merit": "Đồ dùng học tập",
    "framed_certificate": "Đồ dùng học tập",
    "voter_card": "Đồ dùng học tập",
    "earbud": "Điện tử",
    "lime_drink": "Thực phẩm",
    "tumbler": "Nhà bếp",
    "village": "Địa điểm",
    "watch": "Phụ kiện",
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
    table = getattr(instance, "__tablename__", instance.__class__.__name__)
    backup.setdefault(action, []).append({
        "table": table,
        "row": row_dict(instance),
    })


def ensure_category(db: Session, name: str, stats: dict[str, int], backup: dict[str, list[dict[str, Any]]]) -> Category:
    category = db.query(Category).filter(
        Category.ten_danh_muc == name,
        Category.thoi_gian_xoa.is_(None),
    ).first()
    if category:
        if not category.mo_ta and CATEGORY_DESCRIPTIONS.get(name):
            backup_add(backup, "updated_before", category)
            category.mo_ta = CATEGORY_DESCRIPTIONS[name]
            backup_add(backup, "updated_after", category)
            stats["categories_updated"] += 1
        return category

    category = Category(
        ten_danh_muc=name,
        mo_ta=CATEGORY_DESCRIPTIONS.get(name),
    )
    db.add(category)
    db.flush()
    backup_add(backup, "created", category)
    stats["categories_created"] += 1
    return category


def has_translation_refs(db: Session, translation_id: int) -> bool:
    return bool(
        db.query(LearningProgress).filter(LearningProgress.ban_dich_id == translation_id).first()
        or db.query(CollectionItem).filter(CollectionItem.ban_dich_id == translation_id).first()
    )


def cleanup_placeholder_objects(db: Session, stats: dict[str, int], backup: dict[str, list[dict[str, Any]]]) -> None:
    objects = (
        db.query(Object)
        .outerjoin(Translation, Translation.doi_tuong_id == Object.id)
        .filter(Object.thoi_gian_xoa.is_(None), Translation.id.is_(None))
        .order_by(Object.ma_doi_tuong)
        .all()
    )
    for obj in objects:
        has_refs = bool(
            db.query(ScanHistory).filter(ScanHistory.doi_tuong_id == obj.id).first()
            or db.query(ObjectMedia).filter(ObjectMedia.doi_tuong_id == obj.id).first()
            or db.query(AIPrediction).filter(AIPrediction.nhan_du_doan == obj.ma_doi_tuong).first()
        )
        if has_refs:
            stats["placeholder_objects_skipped"] += 1
            continue

        backup_add(backup, "deleted", obj)
        db.delete(obj)
        stats["placeholder_objects_deleted"] += 1


def cleanup_orphan_dictionary_logs(db: Session, stats: dict[str, int], backup: dict[str, list[dict[str, Any]]]) -> None:
    logs = (
        db.query(DictionaryLookup)
        .filter(DictionaryLookup.doi_tuong_id.is_(None))
        .order_by(DictionaryLookup.id)
        .all()
    )
    for log in logs:
        backup_add(backup, "deleted", log)
        db.delete(log)
        stats["dictionary_logs_deleted"] += 1


def cleanup_obsolete_cat_pending(db: Session, stats: dict[str, int], backup: dict[str, list[dict[str, Any]]]) -> None:
    cat = db.query(Object).filter(Object.ma_doi_tuong == "cat", Object.thoi_gian_xoa.is_(None)).first()
    if not cat:
        return

    vi = db.query(Language).filter(Language.ma_ngon_ngu == "vi").first()
    if vi:
        stale_vi = (
            db.query(Translation)
            .filter(
                Translation.doi_tuong_id == cat.id,
                Translation.ngon_ngu_id == vi.id,
                Translation.da_xac_nhan.is_(False),
            )
            .all()
        )
        for trans in stale_vi:
            if has_translation_refs(db, trans.id):
                stats["stale_cat_vi_skipped"] += 1
                continue
            for example in db.query(ViDu).filter(ViDu.ban_dich_id == trans.id).all():
                backup_add(backup, "deleted", example)
                db.delete(example)
                stats["examples_deleted"] += 1
            backup_add(backup, "deleted", trans)
            db.delete(trans)
            stats["translations_deleted"] += 1

    en = db.query(Language).filter(Language.ma_ngon_ngu == "en").first()
    if en:
        en_trans = (
            db.query(Translation)
            .filter(Translation.doi_tuong_id == cat.id, Translation.ngon_ngu_id == en.id)
            .first()
        )
        if en_trans:
            stale_examples = (
                db.query(ViDu)
                .filter(ViDu.ban_dich_id == en_trans.id, ViDu.nguon_du_lieu == "gemini")
                .filter(ViDu.cau_vi_du.like("%mèo%"))
                .all()
            )
            for example in stale_examples:
                backup_add(backup, "deleted", example)
                db.delete(example)
                stats["examples_deleted"] += 1

    predictions = (
        db.query(AIPrediction)
        .join(ScanHistory, ScanHistory.id == AIPrediction.scan_id)
        .filter(
            AIPrediction.nhan_du_doan == "cat",
            AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
            ScanHistory.url_anh.is_(None),
        )
        .order_by(AIPrediction.id)
        .all()
    )
    scans_to_check: set[int] = set()
    for prediction in predictions:
        if prediction.mo_ta and '"source": "dictionary_lookup"' not in prediction.mo_ta:
            stats["predictions_skipped"] += 1
            continue
        scans_to_check.add(prediction.scan_id)
        backup_add(backup, "deleted", prediction)
        db.delete(prediction)
        stats["predictions_deleted"] += 1

    db.flush()
    for scan_id in scans_to_check:
        scan = db.query(ScanHistory).filter(ScanHistory.id == scan_id).first()
        if not scan:
            continue
        remaining_predictions = db.query(AIPrediction).filter(AIPrediction.scan_id == scan.id).count()
        if remaining_predictions == 0 and scan.url_anh is None:
            backup_add(backup, "deleted", scan)
            db.delete(scan)
            stats["scan_logs_deleted"] += 1


def normalize_yolo_translations(db: Session, stats: dict[str, int], backup: dict[str, list[dict[str, Any]]]) -> None:
    yolo_codes = {normalize_object_code(entry.label) for entry in SEED_ENTRIES}
    en = db.query(Language).filter(Language.ma_ngon_ngu == "en").first()
    if not en:
        return

    for obj in db.query(Object).filter(Object.ma_doi_tuong.in_(yolo_codes), Object.thoi_gian_xoa.is_(None)).all():
        trans = (
            db.query(Translation)
            .filter(
                Translation.doi_tuong_id == obj.id,
                Translation.ngon_ngu_id == en.id,
                Translation.thoi_gian_xoa.is_(None),
            )
            .first()
        )
        if not trans:
            continue

        changed = False
        before_saved = False
        if trans.nguon_du_lieu != NguonDuLieu.thu_cong:
            backup_add(backup, "updated_before", trans)
            before_saved = True
            trans.nguon_du_lieu = NguonDuLieu.thu_cong
            changed = True
        if not trans.da_xac_nhan:
            if not before_saved:
                backup_add(backup, "updated_before", trans)
                before_saved = True
            trans.da_xac_nhan = True
            changed = True
        if changed:
            backup_add(backup, "updated_after", trans)
            stats["yolo_translations_normalized"] += 1


def categorize_and_confirm_gemini_objects(db: Session, stats: dict[str, int], backup: dict[str, list[dict[str, Any]]]) -> None:
    for object_code, category_name in GEMINI_OBJECT_CATEGORY.items():
        obj = db.query(Object).filter(Object.ma_doi_tuong == object_code, Object.thoi_gian_xoa.is_(None)).first()
        if not obj:
            continue

        category = ensure_category(db, category_name, stats, backup)
        if obj.danh_muc_id != category.id:
            backup_add(backup, "updated_before", obj)
            obj.danh_muc_id = category.id
            backup_add(backup, "updated_after", obj)
            stats["objects_categorized"] += 1

        translations = (
            db.query(Translation)
            .filter(Translation.doi_tuong_id == obj.id, Translation.thoi_gian_xoa.is_(None))
            .all()
        )
        for trans in translations:
            if not trans.da_xac_nhan and trans.tu_vung and trans.dinh_nghia:
                backup_add(backup, "updated_before", trans)
                trans.da_xac_nhan = True
                backup_add(backup, "updated_after", trans)
                stats["gemini_translations_confirmed"] += 1


def write_backup(backup: dict[str, list[dict[str, Any]]]) -> Path:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    path = BACKUP_DIR / f"db_cleanup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    path.write_text(json.dumps(backup, ensure_ascii=False, indent=2, default=json_default), encoding="utf-8")
    return path


def cleanup(dry_run: bool) -> tuple[dict[str, int], Path | None]:
    stats = {
        "categories_created": 0,
        "categories_updated": 0,
        "placeholder_objects_deleted": 0,
        "placeholder_objects_skipped": 0,
        "dictionary_logs_deleted": 0,
        "translations_deleted": 0,
        "stale_cat_vi_skipped": 0,
        "examples_deleted": 0,
        "predictions_deleted": 0,
        "predictions_skipped": 0,
        "scan_logs_deleted": 0,
        "yolo_translations_normalized": 0,
        "objects_categorized": 0,
        "gemini_translations_confirmed": 0,
    }
    backup: dict[str, list[dict[str, Any]]] = {
        "metadata": [{
            "dry_run": dry_run,
            "created_at": datetime.now().isoformat(),
        }]
    }

    db = SessionLocal()
    try:
        cleanup_placeholder_objects(db, stats, backup)
        cleanup_orphan_dictionary_logs(db, stats, backup)
        cleanup_obsolete_cat_pending(db, stats, backup)
        normalize_yolo_translations(db, stats, backup)
        categorize_and_confirm_gemini_objects(db, stats, backup)

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
    parser = argparse.ArgumentParser(description="Clean stale database rows safely.")
    parser.add_argument("--dry-run", action="store_true", help="Show cleanup counts without committing.")
    args = parser.parse_args()

    stats, backup_path = cleanup(dry_run=args.dry_run)
    print("Database cleanup:", "DRY RUN" if args.dry_run else "COMMITTED")
    for key, value in stats.items():
        print(f"{key}: {value}")
    if backup_path:
        print(f"backup: {backup_path}")


if __name__ == "__main__":
    main()
