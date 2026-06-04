"""
Training Service
================
Quản lý training images, dataset versions, và export dữ liệu huấn luyện.
"""
from sqlalchemy import func, or_
from sqlalchemy.orm import Session, joinedload

from app.models.ai_feedback_report import AIPrediction, TrangThaiDuyet
from app.models.object import Object
from app.models.scan_history import ScanHistory
from app.models.training_image import (
    NguonAnhHuanLuyen,
    TrainingDatasetImage,
    TrainingDatasetVersion,
    TrainingImage,
    TrangThaiAnhHuanLuyen,
)
from app.services.scan_service import COCO_CLASSES, YOLO_CUSTOM_CLASSES
from app.services.training_image_service import TrainingImageService
from app.repositories.object_repo import normalize_object_code
from app.utils.timezone import now_vietnam


CUSTOM_YOLO_CODES = {normalize_object_code(label) for label in YOLO_CUSTOM_CLASSES}
COCO_MODEL_CODES = {normalize_object_code(label) for label in COCO_CLASSES}
MIN_APPROVED_IMAGES_FOR_TRAINING = 5


class TrainingService:
    def __init__(self):
        self.training_images = TrainingImageService()

    def export_training_data(self, db: Session) -> list[dict]:
        """Export approved training images as flat JSONL records."""
        return [
            self._training_image_record(item)
            for item in self._approved_training_images(db)
        ]

    def export_training_data_grouped(self, db: Session) -> list[dict]:
        """Export approved training images grouped by label/object code."""
        return [
            self._training_group_for_export(group)
            for group in self.training_summary(db, only_approved=True)
        ]

    def training_summary(
        self,
        db: Session,
        only_approved: bool = False,
        model_coverage: str | None = None,
        recommendation: str | None = None,
        status: str | None = None,
        source: str | None = None,
        search: str | None = None,
    ) -> list[dict]:
        """Return training images grouped by label for the admin Training Data tab."""
        # Subquery: du_doan_id của các prediction Gemini đang chờ duyệt
        pending_prediction_ids = db.query(AIPrediction.id).filter(
            AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet
        ).subquery()

        query = (
            db.query(TrainingImage)
            .options(
                joinedload(TrainingImage.object).joinedload(Object.category),
                joinedload(TrainingImage.object).joinedload(Object.aliases),
                joinedload(TrainingImage.dataset_links).joinedload(TrainingDatasetImage.dataset),
            )
            .filter(
                TrainingImage.thoi_gian_xoa.is_(None),
                # Loại trừ ảnh Gemini mà prediction chưa được admin xác nhận
                or_(
                    TrainingImage.du_doan_id.is_(None),
                    TrainingImage.du_doan_id.not_in(pending_prediction_ids),
                ),
            )
            .order_by(TrainingImage.thoi_gian_tao.desc(), TrainingImage.id.desc())
        )
        if only_approved:
            query = query.filter(TrainingImage.trang_thai == TrangThaiAnhHuanLuyen.da_duyet)
        if status:
            try:
                query = query.filter(TrainingImage.trang_thai == TrangThaiAnhHuanLuyen(status))
            except ValueError:
                pass
        if source:
            try:
                query = query.filter(TrainingImage.nguon_du_lieu == NguonAnhHuanLuyen(source))
            except ValueError:
                pass

        groups: dict[str, dict] = {}
        search_text = search.strip().lower() if search else None
        for item in query.all():
            code = self._training_label(item)
            if not code:
                continue
            coverage = self._training_model_coverage(code, item)
            advice = self._training_recommendation(coverage)
            if model_coverage == "known" and coverage not in {"custom_yolo", "coco_known"}:
                continue
            if model_coverage and model_coverage != "known" and coverage != model_coverage:
                continue
            if recommendation == "train" and advice not in {"high_priority", "recommended"}:
                continue
            if recommendation and recommendation != "train" and advice != recommendation:
                continue
            if search_text:
                haystack = " ".join(
                    str(part or "").lower()
                    for part in [
                        code,
                        item.nhan,
                        item.object.ma_doi_tuong if item.object else None,
                        item.object.category.ten_danh_muc if item.object and item.object.category else None,
                    ]
                )
                if search_text not in haystack:
                    continue

            if code not in groups:
                groups[code] = {
                    "object_code": code,
                    "label": code,
                    "category": item.object.category.ten_danh_muc if item.object and item.object.category else None,
                    "model_coverage": coverage,
                    "training_recommendation": advice,
                    "training_priority": self._training_priority(advice),
                    "translations": self._training_group_translations(item.object),
                    "images": [],
                    "status_counts": {"cho_duyet": 0, "da_duyet": 0, "tu_choi": 0},
                    "source_counts": {"yolo": 0, "gemini": 0, "admin": 0},
                    "dataset_versions": set(),
                    "latest_image_at": None,
                    "confidence_sum": 0.0,
                    "confidence_count": 0,
                }

            item_status = self._training_status(item)
            groups[code]["status_counts"][item_status] = groups[code]["status_counts"].get(item_status, 0) + 1
            item_source = item.nguon_du_lieu.value if hasattr(item.nguon_du_lieu, "value") else str(item.nguon_du_lieu or "")
            if item_source:
                groups[code]["source_counts"][item_source] = groups[code]["source_counts"].get(item_source, 0) + 1
            if item.thoi_gian_tao:
                latest = groups[code]["latest_image_at"]
                if latest is None or item.thoi_gian_tao > latest:
                    groups[code]["latest_image_at"] = item.thoi_gian_tao
            if item.do_tin_cay is not None:
                groups[code]["confidence_sum"] += float(item.do_tin_cay)
                groups[code]["confidence_count"] += 1
            versions = self._dataset_versions(item)
            groups[code]["dataset_versions"].update(versions)
            groups[code]["images"].append(self._training_image_record(item, dataset_versions=versions))

        records = []
        for group in groups.values():
            counts = group.get("status_counts") or {}
            # Bỏ qua group mà toàn bộ ảnh đều bị từ chối — không có gì để train/duyệt
            if counts.get("da_duyet", 0) == 0 and counts.get("cho_duyet", 0) == 0:
                continue
            group["dataset_versions"] = sorted(group["dataset_versions"])
            group["total_images"] = len(group["images"])
            self._attach_training_decision(group)
            records.append(group)

        records.sort(key=lambda r: (
            # 1. Có ảnh chờ duyệt → xử lý ngay
            1 if r["status_counts"].get("cho_duyet", 0) > 0 else 0,
            # 2. Nên train nhưng chưa đủ ảnh → gần đủ lên đầu (missing ít = ưu tiên cao)
            -r.get("missing_approved_images", 999) if r["training_recommendation"] in {"high_priority", "recommended"} else -999,
            # 3. Đã đủ ảnh sẵn sàng export
            1 if r.get("ready_for_dataset") else 0,
            # 4. Priority tổng thể
            r["training_priority"],
            r["object_code"],
        ), reverse=True)
        return records

    def _attach_training_decision(self, group: dict) -> None:
        counts = group.get("status_counts") or {}
        approved = int(counts.get("da_duyet") or 0)
        pending = int(counts.get("cho_duyet") or 0)
        rejected = int(counts.get("tu_choi") or 0)
        total = int(group.get("total_images") or 0)
        recommendation = group.get("training_recommendation")
        has_dataset = bool(group.get("dataset_versions"))

        group["approved_count"] = approved
        group["pending_count"] = pending
        group["rejected_count"] = rejected
        group["min_images_for_training"] = MIN_APPROVED_IMAGES_FOR_TRAINING
        group["approved_ratio"] = round(approved / total, 3) if total else 0
        group["missing_approved_images"] = max(MIN_APPROVED_IMAGES_FOR_TRAINING - approved, 0)
        group["ready_for_dataset"] = approved >= MIN_APPROVED_IMAGES_FOR_TRAINING
        group["needs_review"] = pending > 0
        group["has_dataset"] = has_dataset
        group["latest_image_at"] = group["latest_image_at"].isoformat() if group.get("latest_image_at") else None
        confidence_count = group.pop("confidence_count", 0)
        confidence_sum = group.pop("confidence_sum", 0.0)
        group["avg_confidence"] = round(confidence_sum / confidence_count, 4) if confidence_count else None

        if pending > 0:
            group["next_action"] = "review_pending"
        elif recommendation in {"high_priority", "recommended"} and approved < MIN_APPROVED_IMAGES_FOR_TRAINING:
            group["next_action"] = "collect_more"
        elif recommendation in {"high_priority", "recommended"} and approved >= MIN_APPROVED_IMAGES_FOR_TRAINING:
            group["next_action"] = "ready_to_train"
        elif has_dataset:
            group["next_action"] = "already_exported"
        elif approved > 0:
            group["next_action"] = "optional_export"
        elif rejected > 0:
            group["next_action"] = "mostly_rejected"
        else:
            group["next_action"] = "monitor"

    def approve_training_image_by_scan(
        self,
        db: Session,
        lich_su_quet_id: int,
        admin_id: int | None = None,
    ) -> TrainingImage | None:
        item = self._training_image_for_scan(db, lich_su_quet_id)
        if not item:
            return None

        if item.scan and item.scan.doi_tuong_id and not item.doi_tuong_id:
            item.doi_tuong_id = item.scan.doi_tuong_id
        self._sync_label_from_object(db, item)

        item.trang_thai = TrangThaiAnhHuanLuyen.da_duyet
        item.nguoi_duyet_id = admin_id
        item.thoi_gian_duyet = now_vietnam()
        db.commit()
        db.refresh(item)
        return item

    def approve_training_image(
        self,
        db: Session,
        training_image_id: int,
        admin_id: int | None = None,
    ) -> TrainingImage | None:
        item = self._training_image_by_id(db, training_image_id)
        if not item:
            return None

        if item.scan and item.scan.doi_tuong_id and not item.doi_tuong_id:
            item.doi_tuong_id = item.scan.doi_tuong_id
        self._sync_label_from_object(db, item)

        item.trang_thai = TrangThaiAnhHuanLuyen.da_duyet
        item.nguoi_duyet_id = admin_id
        item.thoi_gian_duyet = now_vietnam()
        db.commit()
        db.refresh(item)
        return item

    def reject_training_image_by_scan(
        self,
        db: Session,
        lich_su_quet_id: int,
        admin_id: int | None = None,
        note: str | None = None,
    ) -> TrainingImage | None:
        item = self._training_image_for_scan(db, lich_su_quet_id)
        if not item:
            return None

        item.trang_thai = TrangThaiAnhHuanLuyen.tu_choi
        item.nguoi_duyet_id = admin_id
        item.thoi_gian_duyet = now_vietnam()
        if note:
            item.ghi_chu = note
        db.commit()
        db.refresh(item)
        return item

    def reject_training_image(
        self,
        db: Session,
        training_image_id: int,
        admin_id: int | None = None,
        note: str | None = None,
    ) -> TrainingImage | None:
        item = self._training_image_by_id(db, training_image_id)
        if not item:
            return None

        item.trang_thai = TrangThaiAnhHuanLuyen.tu_choi
        item.nguoi_duyet_id = admin_id
        item.thoi_gian_duyet = now_vietnam()
        if note:
            item.ghi_chu = note
        db.commit()
        db.refresh(item)
        return item

    def delete_training_image(
        self,
        db: Session,
        training_image_id: int,
        admin_id: int | None = None,
    ) -> TrainingImage | None:
        item = self._training_image_by_id(db, training_image_id)
        if not item:
            return None

        now = now_vietnam()
        item.trang_thai = TrangThaiAnhHuanLuyen.tu_choi
        item.nguoi_duyet_id = admin_id
        item.thoi_gian_duyet = now
        item.thoi_gian_xoa = now
        db.commit()
        db.refresh(item)
        return item

    def reject_training_images_for_object(
        self,
        db: Session,
        object_code: str,
        admin_id: int | None = None,
    ) -> int:
        code = normalize_object_code(object_code)
        now = now_vietnam()
        items = (
            db.query(TrainingImage)
            .outerjoin(Object, TrainingImage.doi_tuong_id == Object.id)
            .filter(
                TrainingImage.thoi_gian_xoa.is_(None),
                TrainingImage.trang_thai != TrangThaiAnhHuanLuyen.tu_choi,
                or_(
                    Object.ma_doi_tuong == code,
                    TrainingImage.nhan == code,
                    func.replace(func.replace(func.lower(TrainingImage.nhan), "-", " "), " ", "_") == code,
                ),
            )
            .all()
        )
        for item in items:
            item.trang_thai = TrangThaiAnhHuanLuyen.tu_choi
            item.nguoi_duyet_id = admin_id
            item.thoi_gian_duyet = now
        db.commit()
        return len(items)

    def reassign_training_image_by_scan(
        self,
        db: Session,
        lich_su_quet_id: int,
        target_object_code: str,
    ) -> tuple[TrainingImage | None, Object | None]:
        scan = db.query(ScanHistory).filter(ScanHistory.id == lich_su_quet_id).first()
        if not scan:
            return None, None

        code = normalize_object_code(target_object_code)
        obj = db.query(Object).filter(
            Object.ma_doi_tuong == code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            return None, None

        scan.doi_tuong_id = obj.id
        item = self._training_image_for_scan(db, lich_su_quet_id)
        if not item and scan.url_anh:
            item = self.training_images.create_candidate(
                db,
                lich_su_quet_id=scan.id,
                url_anh=scan.url_anh,
                nhan=obj.ma_doi_tuong,
                nguon_du_lieu="admin",
                doi_tuong_id=obj.id,
                do_tin_cay=scan.do_tin_cay,
                ghi_chu="Created when admin reassigned scan image",
            )
        elif item:
            item.doi_tuong_id = obj.id
            item.nhan = obj.ma_doi_tuong
            if item.trang_thai == TrangThaiAnhHuanLuyen.tu_choi:
                item.trang_thai = TrangThaiAnhHuanLuyen.cho_duyet
                item.thoi_gian_duyet = None
                item.nguoi_duyet_id = None

        db.commit()
        if item:
            db.refresh(item)
        return item, obj

    def reassign_training_image(
        self,
        db: Session,
        training_image_id: int,
        target_object_code: str,
    ) -> tuple[TrainingImage | None, Object | None]:
        item = self._training_image_by_id(db, training_image_id)
        if not item:
            return None, None

        code = normalize_object_code(target_object_code)
        obj = db.query(Object).filter(
            Object.ma_doi_tuong == code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            return item, None

        item.doi_tuong_id = obj.id
        item.nhan = obj.ma_doi_tuong
        if item.scan:
            item.scan.doi_tuong_id = obj.id
        if item.trang_thai == TrangThaiAnhHuanLuyen.tu_choi:
            item.trang_thai = TrangThaiAnhHuanLuyen.cho_duyet
            item.thoi_gian_duyet = None
            item.nguoi_duyet_id = None

        db.commit()
        db.refresh(item)
        return item, obj

    def create_dataset_version(
        self,
        db: Session,
        ma_phien_ban: str,
        ghi_chu: str | None = None,
    ) -> TrainingDatasetVersion:
        code = ma_phien_ban.strip()
        if not code:
            raise ValueError("ma_phien_ban is required")
        exists = db.query(TrainingDatasetVersion).filter(
            TrainingDatasetVersion.ma_phien_ban == code,
        ).first()
        if exists:
            raise ValueError(f"Dataset version '{code}' already exists")

        approved = self._approved_training_images(db)
        label_count = len({self._training_label(item) for item in approved if self._training_label(item)})
        dataset = TrainingDatasetVersion(
            ma_phien_ban=code,
            tong_anh=len(approved),
            tong_nhan=label_count,
            ghi_chu=ghi_chu,
        )
        db.add(dataset)
        db.flush()
        for item in approved:
            db.add(TrainingDatasetImage(dataset_id=dataset.id, anh_huan_luyen_id=item.id))
        db.commit()
        db.refresh(dataset)
        return dataset

    def _approved_training_images(self, db: Session) -> list[TrainingImage]:
        return (
            db.query(TrainingImage)
            .options(
                joinedload(TrainingImage.object).joinedload(Object.category),
                joinedload(TrainingImage.object).joinedload(Object.aliases),
                joinedload(TrainingImage.dataset_links).joinedload(TrainingDatasetImage.dataset),
            )
            .filter(
                TrainingImage.thoi_gian_xoa.is_(None),
                TrainingImage.trang_thai == TrangThaiAnhHuanLuyen.da_duyet,
            )
            .order_by(TrainingImage.thoi_gian_tao.desc(), TrainingImage.id.desc())
            .all()
        )

    def _training_image_for_scan(self, db: Session, lich_su_quet_id: int) -> TrainingImage | None:
        return (
            db.query(TrainingImage)
            .options(joinedload(TrainingImage.scan), joinedload(TrainingImage.object))
            .filter(
                TrainingImage.lich_su_quet_id == lich_su_quet_id,
                TrainingImage.thoi_gian_xoa.is_(None),
            )
            .first()
        )

    def _training_image_by_id(self, db: Session, training_image_id: int) -> TrainingImage | None:
        return (
            db.query(TrainingImage)
            .options(joinedload(TrainingImage.scan), joinedload(TrainingImage.object))
            .filter(
                TrainingImage.id == training_image_id,
                TrainingImage.thoi_gian_xoa.is_(None),
            )
            .first()
        )

    def _sync_label_from_object(self, db: Session, item: TrainingImage) -> None:
        if not item.doi_tuong_id:
            return
        obj = item.object or db.get(Object, item.doi_tuong_id)
        if obj and obj.ma_doi_tuong:
            item.nhan = obj.ma_doi_tuong

    def _training_label(self, item: TrainingImage) -> str | None:
        if item.object and item.object.ma_doi_tuong:
            return item.object.ma_doi_tuong
        if item.nhan:
            return normalize_object_code(item.nhan)
        return None

    def _training_model_coverage(self, code: str, item: TrainingImage) -> str:
        normalized = normalize_object_code(code)
        if normalized in CUSTOM_YOLO_CODES:
            return "custom_yolo"
        if normalized in COCO_MODEL_CODES:
            return "coco_known"
        # Check if any alias of this object maps to a known YOLO/COCO label
        if item.object and item.object.aliases:
            for alias in item.object.aliases:
                alias_normalized = normalize_object_code(alias.ma_bi_danh)
                if alias_normalized in CUSTOM_YOLO_CODES:
                    return "custom_yolo"
                if alias_normalized in COCO_MODEL_CODES:
                    return "coco_known"
        if item.object or item.doi_tuong_id:
            return "db_only"
        return "new_gemini"

    def _training_recommendation(self, coverage: str) -> str:
        return {
            "new_gemini": "high_priority",
            "db_only": "recommended",
            "custom_yolo": "optional",
            "coco_known": "not_needed",
        }.get(coverage, "recommended")

    def _training_priority(self, recommendation: str) -> int:
        return {
            "high_priority": 4,
            "recommended": 3,
            "optional": 2,
            "not_needed": 1,
        }.get(recommendation, 0)

    def _training_status(self, item: TrainingImage) -> str:
        if hasattr(item.trang_thai, "value"):
            return item.trang_thai.value
        return str(item.trang_thai) if item.trang_thai else "cho_duyet"

    def _dataset_versions(self, item: TrainingImage) -> list[str]:
        return [
            link.dataset.ma_phien_ban
            for link in item.dataset_links or []
            if link.dataset
        ]

    def _training_group_translations(self, obj: Object | None) -> list[dict]:
        if not obj:
            return []
        return [
            {
                "language_code": tr.language.ma_ngon_ngu if tr.language else None,
                "word": tr.tu_vung,
            }
            for tr in obj.translations or []
            if tr.thoi_gian_xoa is None
        ]

    def _training_image_record(
        self,
        item: TrainingImage,
        *,
        dataset_versions: list[str] | None = None,
    ) -> dict:
        code = self._training_label(item)
        coverage = self._training_model_coverage(code or "", item)
        recommendation = self._training_recommendation(coverage)
        return {
            "id": item.id,
            "lich_su_quet_id": item.lich_su_quet_id,
            "prediction_id": item.du_doan_id,
            "object_code": code,
            "label": item.nhan,
            "image_url": item.url_anh,
            "url": item.url_anh,
            "source": item.nguon_du_lieu.value if hasattr(item.nguon_du_lieu, "value") else (str(item.nguon_du_lieu) if item.nguon_du_lieu else None),
            "status": self._training_status(item),
            "model_coverage": coverage,
            "training_recommendation": recommendation,
            "confidence": item.do_tin_cay,
            "quality_score": item.diem_chat_luong,
            "dataset_versions": dataset_versions if dataset_versions is not None else self._dataset_versions(item),
            "created_at": item.thoi_gian_tao.isoformat() if item.thoi_gian_tao else None,
            "reviewed_at": item.thoi_gian_duyet.isoformat() if item.thoi_gian_duyet else None,
            "note": item.ghi_chu,
        }

    def _training_group_for_export(self, group: dict) -> dict:
        return {
            "object_code": group["object_code"],
            "category": group.get("category"),
            "total_images": group.get("total_images", 0),
            "dataset_versions": group.get("dataset_versions", []),
            "translations": group.get("translations", []),
            "images": [
                {
                    "id": img["id"],
                    "lich_su_quet_id": img["lich_su_quet_id"],
                    "prediction_id": img["prediction_id"],
                    "url": img["url"],
                    "source": img["source"],
                    "confidence": img["confidence"],
                }
                for img in group.get("images", [])
                if img.get("status") == TrangThaiAnhHuanLuyen.da_duyet.value
            ],
        }
