"""
Admin Moderation Service
========================
Xử lý quy trình kiểm duyệt từ vựng do Gemini đề xuất.

Flow:
  DuDoanAI (cho_duyet)
      ↓ admin approve
  DoiTuong (upsert) + BanDich (insert) + ViDu ×3 (insert)
  DuDoanAI.trang_thai = da_duyet

  Hoặc:
      ↓ admin reject
  DuDoanAI.trang_thai = tu_choi
"""
import json
from typing import Optional, List

from sqlalchemy import func, case, or_
from sqlalchemy.orm import Session, joinedload

from app.models.ai_feedback_report import AIPrediction, NguonAI, TrangThaiDuyet, VaiTroDuDoan
from app.models.category import Category
from app.models.collection_item import CollectionItem
from app.models.learning_progress import LearningProgress
from app.models.object import Object
from app.models.object_alias import ObjectAlias
from app.models.object_media import ObjectMedia
from app.models.scan_history import ScanHistory
from app.models.training_image import (
    TrainingDatasetImage,
    TrainingDatasetVersion,
    TrainingImage,
    TrangThaiAnhHuanLuyen,
)
from app.models.translation import Translation, NguonDuLieu
from app.models.example import ViDu
from app.models.language import Language
from app.models.user import User
from app.models.review_log import ReviewLog
from app.services.tts_service import TTSService
from app.services.gemini_service import GeminiService
from app.services.training_image_service import TrainingImageService
from app.repositories.object_repo import normalize_object_code
from app.utils.timezone import now_vietnam
from app.utils.security import hash_password
from app.schemas.admin import (
    ApproveRequest, ApproveResponse,
    AliasPredictionRequest, AliasPredictionResponse,
    RejectResponse, SplitToNewObjectResponse,
    PredictionDetailResponse, PredictionListItem,
    VocabPayloadSchema,
    CategoryAdminResponse, CategoryCreateRequest, CategoryUpdateRequest,
    ObjectAliasItem, ObjectAliasUpsertRequest,
    ObjectListItem, ObjectDetailResponse, ObjectCreateRequest, ObjectUpdateRequest,
    TranslationAdminResponse, TranslationCreateRequest, TranslationUpdateRequest,
    UserAdminResponse, UserRoleUpdate, UserStatusUpdate,
    DashboardStats,
    ScanHistoryAdminItem, UserStatsAdminResponse,
)


class AdminService:
    def __init__(self):
        self.tts = TTSService()
        self.gemini = GeminiService()
        self.training_images = TrainingImageService()

    def _prediction_payload(self, prediction: AIPrediction) -> dict:
        try:
            return json.loads(prediction.mo_ta or "{}")
        except Exception:
            return {}

    def _is_image_only_prediction(self, prediction: AIPrediction) -> bool:
        return prediction.vai_tro == VaiTroDuDoan.anh_bo_sung

    def _visible_prediction_count(self, db: Session, status: TrangThaiDuyet) -> int:
        return db.query(AIPrediction).filter(
            AIPrediction.trang_thai == status,
            AIPrediction.vai_tro == VaiTroDuDoan.chinh,
        ).count()

    # ------------------------------------------------------------------
    # Truy vấn
    # ------------------------------------------------------------------

    def list_predictions(
        self,
        db: Session,
        trang_thai: Optional[str] = None,
        limit: int = 50,
        offset: int = 0,
        search: Optional[str] = None,
    ) -> list[PredictionListItem]:
        query = db.query(AIPrediction).filter(
            AIPrediction.vai_tro == VaiTroDuDoan.chinh,
        ).order_by(AIPrediction.thoi_gian.desc())
        if trang_thai:
            try:
                query = query.filter(AIPrediction.trang_thai == TrangThaiDuyet(trang_thai))
            except ValueError:
                pass
        if search:
            pattern = f"%{search.strip()}%"
            query = query.filter(or_(
                AIPrediction.nhan_du_doan.ilike(pattern),
                AIPrediction.mo_ta.ilike(pattern),
            ))
        predictions = query.offset(offset).limit(limit).all()
        scan_ids = [p.scan_id for p in predictions if p.scan_id]
        scans = (
            {s.id: s for s in db.query(ScanHistory).filter(ScanHistory.id.in_(scan_ids)).all()}
            if scan_ids else {}
        )
        items = []
        for p in predictions:
            scan = scans.get(p.scan_id)
            item = PredictionListItem(
                id=p.id,
                scan_id=p.scan_id,
                nhan_du_doan=p.nhan_du_doan,
                do_tin_cay=p.do_tin_cay,
                trang_thai=p.trang_thai.value,
                thoi_gian=p.thoi_gian,
                scan_image_url=scan.url_anh if scan else None,
                vai_tro=p.vai_tro.value if p.vai_tro else None,
                du_doan_goc_id=p.du_doan_goc_id,
            )
            items.append(item)
        return items

    def get_prediction_detail(self, db: Session, prediction_id: int) -> Optional[PredictionDetailResponse]:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return None

        scan = db.query(ScanHistory).filter(ScanHistory.id == p.scan_id).first()

        vocab_payload = None
        if p.mo_ta:
            try:
                vocab_payload = VocabPayloadSchema.model_validate(json.loads(p.mo_ta))
            except Exception:
                pass

        return PredictionDetailResponse(
            id=p.id,
            scan_id=p.scan_id,
            nhan_du_doan=p.nhan_du_doan,
            do_tin_cay=p.do_tin_cay,
            trang_thai=p.trang_thai.value,
            thoi_gian=p.thoi_gian,
            scan_image_url=scan.url_anh if scan else None,
            vai_tro=p.vai_tro.value if p.vai_tro else None,
            du_doan_goc_id=p.du_doan_goc_id,
            related_images=self._related_prediction_images(db, p),
            vocab_payload=vocab_payload,
        )

    def _related_prediction_images(self, db: Session, prediction: AIPrediction) -> list[dict]:
        root_id = prediction.du_doan_goc_id or prediction.id
        related = (
            db.query(AIPrediction, ScanHistory)
            .join(ScanHistory, AIPrediction.scan_id == ScanHistory.id)
            .filter(
                (AIPrediction.id == root_id) |
                (AIPrediction.du_doan_goc_id == root_id)
            )
            .order_by(AIPrediction.vai_tro.asc(), AIPrediction.thoi_gian.asc())
            .all()
        )
        return [
            {
                "prediction_id": pred.id,
                "scan_id": scan.id,
                "user_id": scan.user_id,
                "image_url": scan.url_anh,
                "vai_tro": pred.vai_tro.value if pred.vai_tro else None,
                "thoi_gian": scan.thoi_gian,
            }
            for pred, scan in related
            if scan.url_anh
        ]

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

    def training_summary(self, db: Session, only_approved: bool = False) -> list[dict]:
        """Return training images grouped by label for the admin Training Data tab."""
        query = (
            db.query(TrainingImage)
            .options(
                joinedload(TrainingImage.object).joinedload(Object.category),
                joinedload(TrainingImage.dataset_links).joinedload(TrainingDatasetImage.dataset),
            )
            .filter(TrainingImage.thoi_gian_xoa.is_(None))
            .order_by(TrainingImage.thoi_gian_tao.desc(), TrainingImage.id.desc())
        )
        if only_approved:
            query = query.filter(TrainingImage.trang_thai == TrangThaiAnhHuanLuyen.da_duyet)

        groups: dict[str, dict] = {}
        for item in query.all():
            code = self._training_label(item)
            if not code:
                continue

            if code not in groups:
                groups[code] = {
                    "object_code": code,
                    "label": code,
                    "category": item.object.category.ten_danh_muc if item.object and item.object.category else None,
                    "translations": self._training_group_translations(item.object),
                    "images": [],
                    "status_counts": {"cho_duyet": 0, "da_duyet": 0, "tu_choi": 0},
                    "dataset_versions": set(),
                }

            status = self._training_status(item)
            groups[code]["status_counts"][status] = groups[code]["status_counts"].get(status, 0) + 1
            versions = self._dataset_versions(item)
            groups[code]["dataset_versions"].update(versions)
            groups[code]["images"].append(self._training_image_record(item, dataset_versions=versions))

        records = []
        for group in groups.values():
            group["dataset_versions"] = sorted(group["dataset_versions"])
            group["total_images"] = len(group["images"])
            records.append(group)

        records.sort(
            key=lambda r: (
                r["status_counts"].get("da_duyet", 0),
                r["total_images"],
                r["object_code"],
            ),
            reverse=True,
        )
        return records

    def approve_training_image_by_scan(
        self,
        db: Session,
        scan_id: int,
        admin_id: int | None = None,
    ) -> TrainingImage | None:
        item = self._training_image_for_scan(db, scan_id)
        if not item:
            return None

        if item.scan and item.scan.doi_tuong_id and not item.doi_tuong_id:
            item.doi_tuong_id = item.scan.doi_tuong_id
        if item.object:
            item.nhan = item.object.ma_doi_tuong

        item.trang_thai = TrangThaiAnhHuanLuyen.da_duyet
        item.nguoi_duyet_id = admin_id
        item.thoi_gian_duyet = now_vietnam()
        db.commit()
        db.refresh(item)
        return item

    def reject_training_image_by_scan(
        self,
        db: Session,
        scan_id: int,
        admin_id: int | None = None,
        note: str | None = None,
    ) -> TrainingImage | None:
        item = self._training_image_for_scan(db, scan_id)
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
        scan_id: int,
        target_object_code: str,
    ) -> tuple[TrainingImage | None, Object | None]:
        scan = db.query(ScanHistory).filter(ScanHistory.id == scan_id).first()
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
        item = self._training_image_for_scan(db, scan_id)
        if not item and scan.url_anh:
            item = self.training_images.create_candidate(
                db,
                scan_id=scan.id,
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
                joinedload(TrainingImage.dataset_links).joinedload(TrainingDatasetImage.dataset),
            )
            .filter(
                TrainingImage.thoi_gian_xoa.is_(None),
                TrainingImage.trang_thai == TrangThaiAnhHuanLuyen.da_duyet,
            )
            .order_by(TrainingImage.thoi_gian_tao.desc(), TrainingImage.id.desc())
            .all()
        )

    def _training_image_for_scan(self, db: Session, scan_id: int) -> TrainingImage | None:
        return (
            db.query(TrainingImage)
            .options(joinedload(TrainingImage.scan), joinedload(TrainingImage.object))
            .filter(
                TrainingImage.scan_id == scan_id,
                TrainingImage.thoi_gian_xoa.is_(None),
            )
            .first()
        )

    def _training_label(self, item: TrainingImage) -> str | None:
        if item.object and item.object.ma_doi_tuong:
            return item.object.ma_doi_tuong
        if item.nhan:
            return normalize_object_code(item.nhan)
        return None

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
        return {
            "id": item.id,
            "scan_id": item.scan_id,
            "prediction_id": item.du_doan_id,
            "object_code": self._training_label(item),
            "label": item.nhan,
            "image_url": item.url_anh,
            "url": item.url_anh,
            "source": item.nguon_du_lieu.value if hasattr(item.nguon_du_lieu, "value") else (str(item.nguon_du_lieu) if item.nguon_du_lieu else None),
            "status": self._training_status(item),
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
                    "scan_id": img["scan_id"],
                    "prediction_id": img["prediction_id"],
                    "url": img["url"],
                    "source": img["source"],
                    "confidence": img["confidence"],
                }
                for img in group.get("images", [])
                if img.get("status") == TrangThaiAnhHuanLuyen.da_duyet.value
            ],
        }

    # ------------------------------------------------------------------
    # Approve — Insert vào bảng chính
    # ------------------------------------------------------------------

    def approve_prediction(
        self,
        db: Session,
        prediction_id: int,
        request: ApproveRequest,
    ) -> ApproveResponse:
        """
        Duyệt prediction:
          1. Parse vocab payload từ mo_ta
          2. Upsert DoiTuong
          3. Loop qua từng ngôn ngữ trong translations: upsert Language + BanDich + ViDu
          4. Đánh dấu da_duyet
        """
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return ApproveResponse(success=False, message="Không tìm thấy prediction", prediction_id=prediction_id)

        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return ApproveResponse(
                success=False,
                message=f"Prediction đã ở trạng thái '{p.trang_thai.value}'",
                prediction_id=prediction_id,
            )

        if not p.mo_ta:
            return ApproveResponse(success=False, message="Không có dữ liệu từ vựng (mo_ta trống)", prediction_id=prediction_id)

        try:
            raw = json.loads(p.mo_ta)
        except Exception:
            return ApproveResponse(success=False, message="mo_ta không phải JSON hợp lệ", prediction_id=prediction_id)

        object_code = normalize_object_code(raw.get("object_code", p.nhan_du_doan or "unknown"))
        image_url_suggestion = raw.get("image_url_suggestion")
        translations_raw = raw.get("translations", [])
        if not translations_raw:
            return ApproveResponse(success=False, message="Không có translations trong payload", prediction_id=prediction_id)

        # Upsert DoiTuong (một lần, trước khi loop ngôn ngữ)
        obj = db.query(Object).filter(
            Object.ma_doi_tuong == object_code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            obj = Object(
                ma_doi_tuong=object_code,
                danh_muc_id=request.category_id,
            )
            db.add(obj)
            db.flush()
        else:
            if request.category_id is not None:
                obj.danh_muc_id = request.category_id

        # Tự gán danh mục từ Gemini nếu admin chưa chọn và Gemini trả về đúng tên trong DB
        if obj.danh_muc_id is None:
            gemini_category = raw.get("category")
            if gemini_category:
                matched_cat = db.query(Category).filter(
                    Category.ten_danh_muc == gemini_category,
                    Category.thoi_gian_xoa.is_(None),
                ).first()
                if matched_cat:
                    obj.danh_muc_id = matched_cat.id

        training_scan_ids: set[int] = set()
        if p.scan:
            p.scan.doi_tuong_id = obj.id
            training_scan_ids.add(p.scan.id)

        total_examples_created = 0
        first_translation_id = None
        first_word_name = None

        for idx, t_data in enumerate(translations_raw):
            lang_code = t_data.get("lang_code", "en")

            # Admin override chỉ áp cho translation đầu tiên (single-value override)
            if idx == 0:
                word_name      = request.override_word_name      or t_data.get("word_name", object_code)
                phonetic       = request.override_phonetic       or t_data.get("phonetic")
                part_of_speech = request.override_part_of_speech or t_data.get("part_of_speech")
                definition     = request.override_definition     or t_data.get("definition")
                examples = (
                    request.override_example_sentences
                    if request.override_example_sentences is not None
                    else t_data.get("example_sentences", [])
                )
                first_word_name = word_name
            else:
                word_name      = t_data.get("word_name", object_code)
                phonetic       = t_data.get("phonetic")
                part_of_speech = t_data.get("part_of_speech")
                definition     = t_data.get("definition")
                examples       = t_data.get("example_sentences", [])

            audio_url = self.tts.get_audio_url(word_name, lang_code)

            # Upsert Language
            lang = db.query(Language).filter(Language.ma_ngon_ngu == lang_code).first()
            if not lang:
                lang_names = {"en": "English", "vi": "Vietnamese", "ja": "Japanese", "ko": "Korean"}
                lang = Language(
                    ma_ngon_ngu=lang_code,
                    ten_ngon_ngu=lang_names.get(lang_code, lang_code.upper()),
                    dang_hoat_dong=True,
                )
                db.add(lang)
                db.flush()

            # Upsert BanDich
            existing = db.query(Translation).filter(
                Translation.doi_tuong_id == obj.id,
                Translation.ngon_ngu_id == lang.id,
            ).first()

            examples_created = 0
            if existing:
                existing.tu_vung      = word_name      or existing.tu_vung
                existing.phien_am     = phonetic       or existing.phien_am
                existing.loai_tu      = part_of_speech or existing.loai_tu
                existing.dinh_nghia   = definition     or existing.dinh_nghia
                existing.am_thanh_url = existing.am_thanh_url or audio_url
                existing.da_xac_nhan  = True
                translation = existing
                seen = {
                    (e.cau_vi_du or "").strip().lower()
                    for e in (translation.examples or [])
                    if e.cau_vi_du
                }
                for item in examples[:3]:
                    cau, dich = self._parse_example_item(item)
                    if cau and cau.lower() not in seen:
                        db.add(ViDu(ban_dich_id=translation.id, cau_vi_du=cau, dich_nghia=dich, nguon_du_lieu="gemini"))
                        seen.add(cau.lower())
                        examples_created += 1
            else:
                translation = Translation(
                    doi_tuong_id=obj.id,
                    ngon_ngu_id=lang.id,
                    tu_vung=word_name,
                    phien_am=phonetic,
                    loai_tu=part_of_speech,
                    dinh_nghia=definition,
                    am_thanh_url=audio_url,
                    nguon_du_lieu=NguonDuLieu.gemini,
                    da_xac_nhan=True,
                )
                db.add(translation)
                db.flush()
                for item in examples[:3]:
                    cau, dich = self._parse_example_item(item)
                    if cau:
                        db.add(ViDu(ban_dich_id=translation.id, cau_vi_du=cau, dich_nghia=dich, nguon_du_lieu="gemini"))
                        examples_created += 1

            if first_translation_id is None:
                first_translation_id = translation.id
            total_examples_created += examples_created

        # Auto-enroll tất cả user đã quét object này (pending) vào learning
        user_ids_to_enroll: set[int] = set()

        if p.scan and p.scan.user_id:
            user_ids_to_enroll.add(p.scan.user_id)

        # Tìm các prediction khác cùng object_code còn cho_duyet → auto-resolve luôn
        related_predictions = db.query(AIPrediction).filter(
            AIPrediction.id != prediction_id,
            AIPrediction.nguon_ai == NguonAI.gemini,
            AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
            or_(
                AIPrediction.du_doan_goc_id == prediction_id,
                AIPrediction.nhan_du_doan == object_code,
            ),
        ).all()
        for rel in related_predictions:
            if rel.scan:
                rel.scan.doi_tuong_id = obj.id
                training_scan_ids.add(rel.scan.id)
                if rel.scan.user_id:
                    user_ids_to_enroll.add(rel.scan.user_id)
            rel.trang_thai = TrangThaiDuyet.da_duyet

        now = now_vietnam()
        if training_scan_ids:
            training_images = db.query(TrainingImage).filter(
                TrainingImage.scan_id.in_(training_scan_ids),
                TrainingImage.thoi_gian_xoa.is_(None),
            ).all()
            for image in training_images:
                image.doi_tuong_id = obj.id
                image.nhan = obj.ma_doi_tuong
                image.trang_thai = TrangThaiAnhHuanLuyen.da_duyet
                image.thoi_gian_duyet = now

        for uid in user_ids_to_enroll:
            already = db.query(LearningProgress).filter(
                LearningProgress.user_id == uid,
                LearningProgress.ban_dich_id == first_translation_id,
            ).first()
            if not already:
                db.add(LearningProgress(
                    user_id=uid,
                    ban_dich_id=first_translation_id,
                    ngay_on_tiep=now,
                    lan_on_cuoi=now,
                ))

        # Ghi ObjectMedia nếu có ảnh gợi ý
        if image_url_suggestion:
            media_exists = db.query(ObjectMedia).filter(
                ObjectMedia.doi_tuong_id == obj.id,
                ObjectMedia.url == image_url_suggestion,
                ObjectMedia.thoi_gian_xoa.is_(None),
            ).first()
            if not media_exists:
                db.add(ObjectMedia(
                    doi_tuong_id=obj.id,
                    url=image_url_suggestion,
                    doi_tuong_chinh=True,
                ))

        p.trang_thai = TrangThaiDuyet.da_duyet
        db.commit()

        langs_approved = [t.get("lang_code", "en") for t in translations_raw]
        return ApproveResponse(
            success=True,
            message=f"Đã duyệt '{first_word_name}' — {len(langs_approved)} ngôn ngữ: {', '.join(langs_approved)}",
            prediction_id=prediction_id,
            object_id=obj.id,
            translation_id=first_translation_id,
            examples_created=total_examples_created,
            users_enrolled=len(user_ids_to_enroll),
        )

    # ------------------------------------------------------------------
    # Alias
    # ------------------------------------------------------------------

    def assign_prediction_alias(
        self,
        db: Session,
        prediction_id: int,
        request: AliasPredictionRequest,
    ) -> AliasPredictionResponse:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return AliasPredictionResponse(success=False, message="Không tìm thấy prediction", prediction_id=prediction_id)

        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return AliasPredictionResponse(
                success=False,
                message=f"Prediction đã ở trạng thái '{p.trang_thai.value}'",
                prediction_id=prediction_id,
            )

        obj = db.query(Object).filter(
            Object.id == request.doi_tuong_id,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            return AliasPredictionResponse(success=False, message="Không tìm thấy đối tượng đích", prediction_id=prediction_id)

        raw = self._prediction_payload(p)
        alias_code = normalize_object_code(
            request.ma_bi_danh
            or raw.get("object_code")
            or p.nhan_du_doan
            or ""
        )
        if not alias_code:
            return AliasPredictionResponse(success=False, message="Mã bí danh không hợp lệ", prediction_id=prediction_id)

        canonical_conflict = db.query(Object).filter(
            Object.id != obj.id,
            Object.ma_doi_tuong == alias_code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if canonical_conflict:
            return AliasPredictionResponse(
                success=False,
                message=f"'{alias_code}' đang là mã đối tượng chính của object khác",
                prediction_id=prediction_id,
            )

        alias = db.query(ObjectAlias).filter(ObjectAlias.ma_bi_danh == alias_code).first()
        if alias and alias.doi_tuong_id != obj.id:
            return AliasPredictionResponse(
                success=False,
                message=f"'{alias_code}' đã là bí danh của object khác",
                prediction_id=prediction_id,
            )

        if alias_code != obj.ma_doi_tuong and not alias:
            alias = ObjectAlias(
                doi_tuong_id=obj.id,
                ma_bi_danh=alias_code,
                ten_hien_thi=(request.ten_hien_thi or "").strip() or None,
                ngon_ngu=(request.ngon_ngu or "").strip() or None,
            )
            db.add(alias)
            db.flush()
        elif alias:
            if request.ten_hien_thi is not None:
                alias.ten_hien_thi = request.ten_hien_thi.strip() or alias.ten_hien_thi
            if request.ngon_ngu is not None:
                alias.ngon_ngu = request.ngon_ngu.strip() or alias.ngon_ngu

        root_id = p.du_doan_goc_id or p.id
        related_predictions = db.query(AIPrediction).filter(
            AIPrediction.nguon_ai == NguonAI.gemini,
            AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
            or_(
                AIPrediction.id == root_id,
                AIPrediction.du_doan_goc_id == root_id,
                AIPrediction.nhan_du_doan == alias_code,
            ),
        ).all()
        root_prediction = self._approved_root_prediction_for_object(db, obj)

        review_translation = self._pick_review_translation(db, obj.id)
        user_ids_to_enroll: set[int] = set()
        training_scan_ids: set[int] = set()
        for rel in related_predictions:
            if rel.scan:
                rel.scan.doi_tuong_id = obj.id
                training_scan_ids.add(rel.scan.id)
                if rel.scan.user_id:
                    user_ids_to_enroll.add(rel.scan.user_id)

            payload = self._prediction_payload(rel)
            payload["ma_bi_danh"] = alias_code
            payload["ma_doi_tuong_chinh"] = obj.ma_doi_tuong
            payload["doi_tuong_chinh_id"] = obj.id
            rel.mo_ta = json.dumps(payload, ensure_ascii=False)
            rel.nhan_du_doan = obj.ma_doi_tuong
            if root_prediction and rel.id != root_prediction.id:
                rel.vai_tro = VaiTroDuDoan.anh_bo_sung
                rel.du_doan_goc_id = root_prediction.id
            rel.trang_thai = TrangThaiDuyet.da_duyet

        users_enrolled = 0
        now = now_vietnam()
        if training_scan_ids:
            training_images = db.query(TrainingImage).filter(
                TrainingImage.scan_id.in_(training_scan_ids),
                TrainingImage.thoi_gian_xoa.is_(None),
            ).all()
            for image in training_images:
                image.doi_tuong_id = obj.id
                image.nhan = obj.ma_doi_tuong
                image.trang_thai = TrangThaiAnhHuanLuyen.da_duyet
                image.thoi_gian_duyet = now

        if review_translation:
            for uid in user_ids_to_enroll:
                already = db.query(LearningProgress).filter(
                    LearningProgress.user_id == uid,
                    LearningProgress.ban_dich_id == review_translation.id,
                ).first()
                if not already:
                    db.add(LearningProgress(
                        user_id=uid,
                        ban_dich_id=review_translation.id,
                        ngay_on_tiep=now,
                        lan_on_cuoi=now,
                    ))
                    users_enrolled += 1

        db.commit()

        return AliasPredictionResponse(
            success=True,
            message=f"Đã gán '{alias_code}' làm bí danh của '{obj.ma_doi_tuong}'",
            prediction_id=prediction_id,
            doi_tuong_id=obj.id,
            bi_danh_id=alias.id if alias else None,
            ma_bi_danh=alias_code,
            ma_doi_tuong=obj.ma_doi_tuong,
            users_enrolled=users_enrolled,
        )

    def detach_review_image(self, db: Session, prediction_id: int) -> dict:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return {"success": False, "message": "Không tìm thấy prediction", "prediction_id": prediction_id}
        if p.vai_tro != VaiTroDuDoan.anh_bo_sung:
            return {"success": False, "message": "Chỉ có thể bỏ ảnh bổ sung, không bỏ ảnh chính", "prediction_id": prediction_id}
        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return {"success": False, "message": "Chỉ có thể thao tác ảnh đang chờ duyệt", "prediction_id": prediction_id}

        root_id = p.du_doan_goc_id
        if p.scan:
            p.scan.doi_tuong_id = None

        payload = self._prediction_payload(p)
        payload["review_kind"] = "detached_image"
        payload["detached_from_prediction_id"] = root_id
        p.mo_ta = json.dumps(payload, ensure_ascii=False)
        p.du_doan_goc_id = None
        p.trang_thai = TrangThaiDuyet.tu_choi
        db.commit()
        return {
            "success": True,
            "message": "Đã bỏ ảnh khỏi nhóm duyệt",
            "prediction_id": prediction_id,
            "root_prediction_id": root_id,
        }

    def reassign_review_image(self, db: Session, prediction_id: int, target_object_code: str) -> dict:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return {"success": False, "message": "Không tìm thấy prediction", "prediction_id": prediction_id}
        if p.vai_tro != VaiTroDuDoan.anh_bo_sung:
            return {"success": False, "message": "Chỉ có thể chuyển ảnh bổ sung, không chuyển ảnh chính", "prediction_id": prediction_id}
        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return {"success": False, "message": "Chỉ có thể thao tác ảnh đang chờ duyệt", "prediction_id": prediction_id}

        target_code = normalize_object_code(target_object_code)
        obj = db.query(Object).filter(
            Object.ma_doi_tuong == target_code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            return {"success": False, "message": f"Không tìm thấy đối tượng '{target_object_code}'", "prediction_id": prediction_id}

        root_id = p.du_doan_goc_id
        target_root = self._approved_root_prediction_for_object(db, obj)
        if p.scan:
            p.scan.doi_tuong_id = obj.id

        payload = self._prediction_payload(p)
        payload["review_kind"] = "reassigned_image"
        payload["reassigned_from_prediction_id"] = root_id
        payload["ma_doi_tuong_chinh"] = obj.ma_doi_tuong
        payload["doi_tuong_chinh_id"] = obj.id
        p.mo_ta = json.dumps(payload, ensure_ascii=False)
        p.nhan_du_doan = obj.ma_doi_tuong
        p.du_doan_goc_id = target_root.id if target_root and target_root.id != p.id else None
        p.trang_thai = TrangThaiDuyet.da_duyet

        review_translation = self._pick_review_translation(db, obj.id)
        users_enrolled = 0
        if review_translation and p.scan and p.scan.user_id:
            already = db.query(LearningProgress).filter(
                LearningProgress.user_id == p.scan.user_id,
                LearningProgress.ban_dich_id == review_translation.id,
            ).first()
            if not already:
                now = now_vietnam()
                db.add(LearningProgress(
                    user_id=p.scan.user_id,
                    ban_dich_id=review_translation.id,
                    ngay_on_tiep=now,
                    lan_on_cuoi=now,
                ))
                users_enrolled = 1

        db.commit()
        return {
            "success": True,
            "message": f"Đã chuyển ảnh sang '{obj.ma_doi_tuong}'",
            "prediction_id": prediction_id,
            "root_prediction_id": root_id,
            "target_object_code": obj.ma_doi_tuong,
            "users_enrolled": users_enrolled,
        }

    def _pick_review_translation(self, db: Session, object_id: int) -> Translation | None:
        translations = (
            db.query(Translation)
            .outerjoin(Language, Language.id == Translation.ngon_ngu_id)
            .filter(
                Translation.doi_tuong_id == object_id,
                Translation.thoi_gian_xoa.is_(None),
                Translation.da_xac_nhan.is_(True),
            )
            .order_by((Language.ma_ngon_ngu == "en").desc(), Translation.id.asc())
            .all()
        )
        return translations[0] if translations else None

    def _approved_root_prediction_for_object(self, db: Session, obj: Object) -> AIPrediction | None:
        predictions = (
            db.query(AIPrediction)
            .filter(
                AIPrediction.nguon_ai == NguonAI.gemini,
                AIPrediction.trang_thai == TrangThaiDuyet.da_duyet,
                AIPrediction.vai_tro == VaiTroDuDoan.chinh,
                AIPrediction.nhan_du_doan == obj.ma_doi_tuong,
            )
            .order_by(AIPrediction.thoi_gian.asc())
            .all()
        )
        for prediction in predictions:
            payload = self._prediction_payload(prediction)
            payload_alias = normalize_object_code(payload.get("ma_bi_danh") or "")
            payload_code = normalize_object_code(payload.get("object_code") or "")
            if not payload_alias and (not payload_code or payload_code == obj.ma_doi_tuong):
                return prediction
        return predictions[0] if predictions else None

    # ------------------------------------------------------------------
    # Reject
    # ------------------------------------------------------------------

    def reject_prediction(self, db: Session, prediction_id: int) -> RejectResponse:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return RejectResponse(success=False, message="Không tìm thấy prediction", prediction_id=prediction_id)

        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return RejectResponse(
                success=False,
                message=f"Prediction đã ở trạng thái '{p.trang_thai.value}'",
                prediction_id=prediction_id,
            )

        raw = {}
        deleted_translations = 0
        deleted_object = False
        if p.mo_ta:
            try:
                raw = json.loads(p.mo_ta)
            except Exception:
                raw = {}

            for translation_id in raw.get("temp_translation_ids") or []:
                translation = db.query(Translation).filter(Translation.id == translation_id).first()
                if translation and not translation.da_xac_nhan:
                    db.query(CollectionItem).filter(CollectionItem.ban_dich_id == translation.id).delete(synchronize_session=False)
                    db.query(LearningProgress).filter(LearningProgress.ban_dich_id == translation.id).delete(synchronize_session=False)
                    db.delete(translation)
                    deleted_translations += 1

            temp_object_id = raw.get("temp_object_id")
            if temp_object_id:
                obj = db.query(Object).filter(Object.id == temp_object_id).first()
                if obj:
                    approved_count = db.query(Translation).filter(
                        Translation.doi_tuong_id == obj.id,
                        Translation.da_xac_nhan.is_(True),
                    ).count()
                    other_predictions = db.query(AIPrediction).filter(
                        AIPrediction.id != p.id,
                        AIPrediction.nhan_du_doan == obj.ma_doi_tuong,
                    ).count()
                    if approved_count == 0 and other_predictions == 0:
                        db.delete(obj)
                        deleted_object = True

        object_code = (raw.get("object_code") or p.nhan_du_doan or "").lower()
        related_rejected = 0
        rejected_scan_ids: set[int] = set()
        if p.scan_id:
            rejected_scan_ids.add(p.scan_id)
        if object_code:
            related_predictions = db.query(AIPrediction).filter(
                AIPrediction.id != prediction_id,
                AIPrediction.nguon_ai == NguonAI.gemini,
                AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
                or_(
                    AIPrediction.du_doan_goc_id == prediction_id,
                    AIPrediction.nhan_du_doan == object_code,
                ),
            ).all()
            for rel in related_predictions:
                rel.trang_thai = TrangThaiDuyet.tu_choi
                if rel.scan_id:
                    rejected_scan_ids.add(rel.scan_id)
                related_rejected += 1

        p.trang_thai = TrangThaiDuyet.tu_choi
        if rejected_scan_ids:
            now = now_vietnam()
            training_images = db.query(TrainingImage).filter(
                TrainingImage.scan_id.in_(rejected_scan_ids),
                TrainingImage.thoi_gian_xoa.is_(None),
            ).all()
            for image in training_images:
                image.trang_thai = TrangThaiAnhHuanLuyen.tu_choi
                image.thoi_gian_duyet = now
        db.commit()

        details = []
        if deleted_translations:
            details.append(f"xoa {deleted_translations} ban dich tam")
        if deleted_object:
            details.append("xoa doi tuong tam")
        if related_rejected:
            details.append(f"tu choi {related_rejected} prediction lien quan")
        suffix = f" ({', '.join(details)})" if details else ""
        return RejectResponse(success=True, message=f"Da tu choi prediction #{prediction_id}{suffix}", prediction_id=prediction_id)

    # ------------------------------------------------------------------
    # Split to new object
    # ------------------------------------------------------------------

    def split_to_new_object(
        self,
        db: Session,
        prediction_id: int,
        new_object_code: str,
    ) -> SplitToNewObjectResponse:
        """
        Tách một ảnh ra khỏi nhóm kiểm duyệt hiện tại và tạo một prediction mới
        với object_code do admin chỉ định.

        Hữu ích khi Gemini nhận diện sai nhãn (vd: quét đậu phụ nhưng trả kết quả là cục tẩy)
        — admin có thể cứu ảnh đó thành training data cho đối tượng đúng.
        """
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return SplitToNewObjectResponse(
                success=False, message="Không tìm thấy prediction", old_prediction_id=prediction_id
            )
        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return SplitToNewObjectResponse(
                success=False,
                message=f"Prediction đã ở trạng thái '{p.trang_thai.value}', không thể tách",
                old_prediction_id=prediction_id,
            )

        new_code = normalize_object_code(new_object_code)
        if not new_code or new_code == "unknown":
            return SplitToNewObjectResponse(
                success=False, message="Mã đối tượng mới không hợp lệ", old_prediction_id=prediction_id
            )

        # Sinh vocab mới cho object_code đúng bằng Gemini (text prompt)
        vocab_result = self.gemini.generate_vocab_for_object_code(new_code)
        vocab_generated = "_error" not in vocab_result

        if vocab_generated:
            vocab_result["object_code"] = new_code
            new_mo_ta = json.dumps(vocab_result, ensure_ascii=False)
        else:
            # Tạo payload tối thiểu để admin có thể approve sau
            new_mo_ta = json.dumps({
                "object_code": new_code,
                "category": None,
                "translations": [],
                "_admin_split": True,
                "_vocab_error": vocab_result.get("_message", "unknown"),
            }, ensure_ascii=False)

        # Tạo prediction mới cho object đúng
        new_pred = AIPrediction(
            scan_id=p.scan_id,
            nguon_ai=NguonAI.gemini,
            nhan_du_doan=new_code,
            do_tin_cay=p.do_tin_cay,
            mo_ta=new_mo_ta,
            trang_thai=TrangThaiDuyet.cho_duyet,
            vai_tro=VaiTroDuDoan.chinh,
            du_doan_goc_id=None,
        )
        db.add(new_pred)
        db.flush()  # lấy new_pred.id

        # Nếu tách prediction chính, chuyển ảnh bổ sung sang prediction mới
        if p.vai_tro == VaiTroDuDoan.chinh:
            supplementary_preds = db.query(AIPrediction).filter(
                AIPrediction.du_doan_goc_id == p.id,
                AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
            ).all()
            for supp in supplementary_preds:
                supp.du_doan_goc_id = new_pred.id
                supp.nhan_du_doan = new_code
                if supp.scan:
                    supp.scan.doi_tuong_id = None

        # Ghi audit trail vào prediction cũ rồi đánh dấu tu_choi
        old_payload = self._prediction_payload(p)
        old_payload["review_kind"] = "split_to_new_object"
        old_payload["original_object_code"] = p.nhan_du_doan
        old_payload["split_to_new_prediction_id"] = new_pred.id
        old_payload["corrected_by_admin"] = True
        p.mo_ta = json.dumps(old_payload, ensure_ascii=False)
        p.trang_thai = TrangThaiDuyet.tu_choi
        p.du_doan_goc_id = None

        # Tháo liên kết scan khỏi đối tượng cũ
        if p.scan:
            p.scan.doi_tuong_id = None

        db.commit()
        return SplitToNewObjectResponse(
            success=True,
            message=f"Đã tách ảnh thành prediction mới cho '{new_code}'",
            old_prediction_id=prediction_id,
            new_prediction_id=new_pred.id,
            new_object_code=new_code,
            vocab_generated=vocab_generated,
        )

    # ------------------------------------------------------------------
    # Dashboard
    # ------------------------------------------------------------------

    def get_dashboard_stats(self, db: Session) -> DashboardStats:
        from sqlalchemy import exists
        has_media_subq = exists().where(
            (ObjectMedia.doi_tuong_id == Object.id) &
            ObjectMedia.thoi_gian_xoa.is_(None)
        )
        objects_without_images = db.query(Object).filter(
            Object.thoi_gian_xoa.is_(None),
            ~has_media_subq,
        ).count()
        return DashboardStats(
            total_users=db.query(User).filter(User.thoi_gian_xoa.is_(None)).count(),
            total_objects=db.query(Object).filter(Object.thoi_gian_xoa.is_(None)).count(),
            total_translations=db.query(Translation).filter(Translation.thoi_gian_xoa.is_(None)).count(),
            total_scans=db.query(ScanHistory).count(),
            pending_predictions=self._visible_prediction_count(db, TrangThaiDuyet.cho_duyet),
            approved_predictions=self._visible_prediction_count(db, TrangThaiDuyet.da_duyet),
            rejected_predictions=self._visible_prediction_count(db, TrangThaiDuyet.tu_choi),
            objects_without_images=objects_without_images,
        )

    # ------------------------------------------------------------------
    # Category CRUD
    # ------------------------------------------------------------------

    def list_categories(self, db: Session) -> List[CategoryAdminResponse]:
        from sqlalchemy import func
        cats = db.query(Category).filter(Category.thoi_gian_xoa.is_(None)).order_by(Category.id).all()
        counts = dict(
            db.query(Object.danh_muc_id, func.count(Object.id))
            .filter(Object.thoi_gian_xoa.is_(None), Object.danh_muc_id.isnot(None))
            .group_by(Object.danh_muc_id)
            .all()
        )
        result = []
        for c in cats:
            item = CategoryAdminResponse.model_validate(c)
            item.object_count = counts.get(c.id, 0)
            result.append(item)
        return result

    def create_category(self, db: Session, req: CategoryCreateRequest) -> CategoryAdminResponse:
        cat = Category(ten_danh_muc=req.ten_danh_muc, danh_muc_cha=req.danh_muc_cha, mo_ta=req.mo_ta)
        db.add(cat)
        db.commit()
        db.refresh(cat)
        return CategoryAdminResponse.model_validate(cat)

    def update_category(self, db: Session, category_id: int, req: CategoryUpdateRequest) -> Optional[CategoryAdminResponse]:
        cat = db.query(Category).filter(Category.id == category_id, Category.thoi_gian_xoa.is_(None)).first()
        if not cat:
            return None
        if req.ten_danh_muc is not None:
            cat.ten_danh_muc = req.ten_danh_muc
        if req.danh_muc_cha is not None:
            cat.danh_muc_cha = req.danh_muc_cha
        if req.mo_ta is not None:
            cat.mo_ta = req.mo_ta
        db.commit()
        db.refresh(cat)
        return CategoryAdminResponse.model_validate(cat)

    def delete_category(self, db: Session, category_id: int) -> bool:
        cat = db.query(Category).filter(Category.id == category_id, Category.thoi_gian_xoa.is_(None)).first()
        if not cat:
            return False
        cat.thoi_gian_xoa = now_vietnam()
        db.commit()
        return True

    # ------------------------------------------------------------------
    # Object CRUD
    # ------------------------------------------------------------------

    def list_objects(self, db: Session, limit: int = 50, offset: int = 0, search: Optional[str] = None, category_id: Optional[int] = None, no_image: Optional[bool] = None) -> List[ObjectListItem]:
        from sqlalchemy import exists
        query = (
            db.query(Object)
            .options(joinedload(Object.category), joinedload(Object.aliases))
            .filter(Object.thoi_gian_xoa.is_(None))
        )
        if search:
            pattern = f"%{search}%"
            query = (
                query.outerjoin(ObjectAlias, ObjectAlias.doi_tuong_id == Object.id)
                .filter(or_(
                    Object.ma_doi_tuong.ilike(pattern),
                    ObjectAlias.ma_bi_danh.ilike(pattern),
                ))
                .distinct()
            )
        if category_id is not None:
            query = query.filter(Object.danh_muc_id == category_id)
        has_media_subq = exists().where(
            (ObjectMedia.doi_tuong_id == Object.id) &
            ObjectMedia.thoi_gian_xoa.is_(None)
        )
        if no_image is True:
            query = query.filter(~has_media_subq)
        elif no_image is False:
            query = query.filter(has_media_subq)
        objs = query.order_by(Object.id.desc()).offset(offset).limit(limit).all()
        if not objs:
            return []

        obj_ids = [obj.id for obj in objs]

        trans_rows = (
            db.query(
                Translation.doi_tuong_id,
                func.sum(case((Translation.da_xac_nhan == True, 1), else_=0)).label("approved"),  # noqa: E712
                func.sum(case((Translation.da_xac_nhan == False, 1), else_=0)).label("pending"),  # noqa: E712
            )
            .filter(Translation.doi_tuong_id.in_(obj_ids), Translation.thoi_gian_xoa.is_(None))
            .group_by(Translation.doi_tuong_id)
            .all()
        )
        trans_counts = {row.doi_tuong_id: (int(row.approved or 0), int(row.pending or 0)) for row in trans_rows}

        has_img_ids = {
            row[0]
            for row in db.query(ObjectMedia.doi_tuong_id)
            .filter(ObjectMedia.doi_tuong_id.in_(obj_ids), ObjectMedia.thoi_gian_xoa.is_(None))
            .distinct()
            .all()
        }

        result = []
        for obj in objs:
            approved_count, pending_count = trans_counts.get(obj.id, (0, 0))
            result.append(ObjectListItem(
                id=obj.id,
                ma_doi_tuong=obj.ma_doi_tuong,
                danh_muc_id=obj.danh_muc_id,
                category_name=obj.category.ten_danh_muc if obj.category else None,
                translation_count=approved_count,
                pending_translation_count=pending_count,
                has_image=obj.id in has_img_ids,
                aliases=[ObjectAliasItem.model_validate(alias) for alias in (obj.aliases or [])],
            ))
        return result

    def get_object(self, db: Session, object_id: int) -> Optional[ObjectDetailResponse]:
        from sqlalchemy import exists
        obj = (
            db.query(Object)
            .options(joinedload(Object.aliases))
            .filter(Object.id == object_id, Object.thoi_gian_xoa.is_(None))
            .first()
        )
        if not obj:
            return None
        cat_name = obj.category.ten_danh_muc if obj.category else None
        approved_count, pending_count = self._object_translation_counts(db, obj.id)
        has_img = db.query(exists().where(
            (ObjectMedia.doi_tuong_id == obj.id) &
            ObjectMedia.thoi_gian_xoa.is_(None)
        )).scalar()
        return ObjectDetailResponse(
            id=obj.id,
            ma_doi_tuong=obj.ma_doi_tuong,
            danh_muc_id=obj.danh_muc_id,
            category_name=cat_name,
            translation_count=approved_count,
            pending_translation_count=pending_count,
            has_image=bool(has_img),
            aliases=[ObjectAliasItem.model_validate(alias) for alias in (obj.aliases or [])],
        )

    def _object_translation_counts(self, db: Session, object_id: int) -> tuple[int, int]:
        base = db.query(Translation).filter(
            Translation.doi_tuong_id == object_id,
            Translation.thoi_gian_xoa.is_(None),
        )
        approved_count = base.filter(Translation.da_xac_nhan.is_(True)).count()
        pending_count = base.filter(Translation.da_xac_nhan.is_(False)).count()
        return approved_count, pending_count

    def create_object(self, db: Session, req: ObjectCreateRequest) -> ObjectDetailResponse:
        obj = Object(ma_doi_tuong=normalize_object_code(req.ma_doi_tuong), danh_muc_id=req.danh_muc_id)
        db.add(obj)
        db.commit()
        db.refresh(obj)
        return self.get_object(db, obj.id)

    def update_object(self, db: Session, object_id: int, req: ObjectUpdateRequest) -> Optional[ObjectDetailResponse]:
        obj = db.query(Object).filter(Object.id == object_id, Object.thoi_gian_xoa.is_(None)).first()
        if not obj:
            return None
        if req.danh_muc_id is not None:
            obj.danh_muc_id = req.danh_muc_id
        db.commit()
        return self.get_object(db, obj.id)

    def delete_object(self, db: Session, object_id: int) -> bool:
        obj = db.query(Object).filter(Object.id == object_id, Object.thoi_gian_xoa.is_(None)).first()
        if not obj:
            return False
        obj.thoi_gian_xoa = now_vietnam()
        db.commit()
        return True

    def upsert_object_alias(self, db: Session, req: ObjectAliasUpsertRequest) -> ObjectAliasItem | None:
        obj = db.query(Object).filter(
            Object.id == req.doi_tuong_id,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            return None

        alias_code = normalize_object_code(req.ma_bi_danh)
        if not alias_code:
            return None
        if alias_code == obj.ma_doi_tuong:
            raise ValueError("Bí danh không được trùng mã đối tượng chính")

        canonical_conflict = db.query(Object).filter(
            Object.id != obj.id,
            Object.ma_doi_tuong == alias_code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if canonical_conflict:
            raise ValueError(f"'{alias_code}' đang là mã đối tượng chính của object khác")

        alias = db.query(ObjectAlias).filter(ObjectAlias.ma_bi_danh == alias_code).first()
        if alias:
            alias.doi_tuong_id = obj.id
            if req.ten_hien_thi is not None:
                alias.ten_hien_thi = req.ten_hien_thi.strip() or None
            if req.ngon_ngu is not None:
                alias.ngon_ngu = req.ngon_ngu.strip() or None
        else:
            alias = ObjectAlias(
                doi_tuong_id=obj.id,
                ma_bi_danh=alias_code,
                ten_hien_thi=(req.ten_hien_thi or "").strip() or None,
                ngon_ngu=(req.ngon_ngu or "").strip() or None,
            )
            db.add(alias)

        self._rewire_alias_predictions(db, alias_code, obj)
        db.commit()
        db.refresh(alias)
        return ObjectAliasItem.model_validate(alias)

    def _rewire_alias_predictions(self, db: Session, alias_code: str, obj: Object) -> int:
        candidates = (
            db.query(AIPrediction)
            .filter(
                AIPrediction.nguon_ai == NguonAI.gemini,
                AIPrediction.mo_ta.contains(alias_code),
            )
            .all()
        )
        root_prediction = self._approved_root_prediction_for_object(db, obj)
        changed = 0
        for pred in candidates:
            payload = self._prediction_payload(pred)
            payload_alias = normalize_object_code(payload.get("ma_bi_danh") or "")
            payload_code = normalize_object_code(payload.get("object_code") or "")
            if payload_alias != alias_code and payload_code != alias_code:
                continue

            payload["ma_bi_danh"] = alias_code
            payload["ma_doi_tuong_chinh"] = obj.ma_doi_tuong
            payload["doi_tuong_chinh_id"] = obj.id
            pred.mo_ta = json.dumps(payload, ensure_ascii=False)
            pred.nhan_du_doan = obj.ma_doi_tuong
            if root_prediction and pred.id != root_prediction.id:
                pred.vai_tro = VaiTroDuDoan.anh_bo_sung
                pred.du_doan_goc_id = root_prediction.id

            scan = pred.scan or (
                db.query(ScanHistory).filter(ScanHistory.id == pred.scan_id).first()
                if pred.scan_id else None
            )
            if scan:
                scan.doi_tuong_id = obj.id
            changed += 1
        return changed

    def update_object_alias(self, db: Session, alias_id: int, ma_bi_danh: str, ten_hien_thi: str | None, ngon_ngu: str | None) -> ObjectAliasItem | None:
        alias = db.query(ObjectAlias).filter(ObjectAlias.id == alias_id).first()
        if not alias:
            return None

        new_code = normalize_object_code(ma_bi_danh)
        if not new_code:
            raise ValueError("Mã bí danh không hợp lệ")

        obj = db.query(Object).filter(Object.id == alias.doi_tuong_id, Object.thoi_gian_xoa.is_(None)).first()
        if obj and new_code == obj.ma_doi_tuong:
            raise ValueError("Bí danh không được trùng mã đối tượng chính")

        conflict = db.query(ObjectAlias).filter(
            ObjectAlias.ma_bi_danh == new_code,
            ObjectAlias.id != alias_id,
        ).first()
        if conflict:
            raise ValueError(f"'{new_code}' đã tồn tại dưới dạng bí danh khác")

        alias.ma_bi_danh = new_code
        alias.ten_hien_thi = (ten_hien_thi or "").strip() or None
        if ngon_ngu is not None:
            alias.ngon_ngu = ngon_ngu.strip() or None
        db.commit()
        db.refresh(alias)
        return ObjectAliasItem.model_validate(alias)

    def delete_object_alias(self, db: Session, alias_id: int) -> bool:
        alias = db.query(ObjectAlias).filter(ObjectAlias.id == alias_id).first()
        if not alias:
            return False
        db.delete(alias)
        db.commit()
        return True

    # ------------------------------------------------------------------
    # Translation CRUD
    # ------------------------------------------------------------------

    def list_translations(
        self,
        db: Session,
        object_id: Optional[int] = None,
        search: Optional[str] = None,
        lang_code: Optional[str] = None,
        approved: Optional[bool] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> List[TranslationAdminResponse]:
        query = db.query(Translation).filter(Translation.thoi_gian_xoa.is_(None))
        if object_id is not None:
            query = query.filter(Translation.doi_tuong_id == object_id)
        if search:
            query = query.join(Translation.object).filter(Object.ma_doi_tuong.ilike(f"%{search}%"))
        if lang_code:
            from app.models.language import Language as LangModel
            query = query.join(Translation.language).filter(LangModel.ma_ngon_ngu == lang_code)
        if approved is not None:
            query = query.filter(Translation.da_xac_nhan == approved)
        trans = query.order_by(Translation.id.desc()).offset(offset).limit(limit).all()
        result = []
        for t in trans:
            obj_code = t.object.ma_doi_tuong if t.object else None
            lang_code = t.language.ma_ngon_ngu if t.language else None
            ex_count = len(t.examples) if t.examples else 0
            from app.schemas.admin import ExampleItem
            result.append(TranslationAdminResponse(
                id=t.id,
                doi_tuong_id=t.doi_tuong_id,
                object_code=obj_code,
                lang_code=lang_code,
                tu_vung=t.tu_vung,
                phien_am=t.phien_am,
                loai_tu=t.loai_tu,
                dinh_nghia=t.dinh_nghia,
                am_thanh_url=t.am_thanh_url,
                da_xac_nhan=bool(t.da_xac_nhan),
                example_count=ex_count,
                examples=[
                    ExampleItem(id=ex.id, cau_vi_du=ex.cau_vi_du, dich_nghia=ex.dich_nghia)
                    for ex in (t.examples or [])
                ],
            ))
        return result

    def create_translation(self, db: Session, req: TranslationCreateRequest) -> TranslationAdminResponse:
        lang = db.query(Language).filter(Language.ma_ngon_ngu == req.lang_code).first()
        if not lang:
            lang_names = {"en": "English", "vi": "Vietnamese", "ja": "Japanese", "ko": "Korean"}
            lang = Language(ma_ngon_ngu=req.lang_code, ten_ngon_ngu=lang_names.get(req.lang_code, req.lang_code.upper()), dang_hoat_dong=True)
            db.add(lang)
            db.flush()
        audio_url = self.tts.get_audio_url(req.tu_vung, req.lang_code)
        t = Translation(
            doi_tuong_id=req.doi_tuong_id,
            ngon_ngu_id=lang.id,
            tu_vung=req.tu_vung,
            phien_am=req.phien_am,
            loai_tu=req.loai_tu,
            dinh_nghia=req.dinh_nghia,
            am_thanh_url=audio_url,
            nguon_du_lieu=NguonDuLieu.thu_cong,
            da_xac_nhan=True,
        )
        db.add(t)
        db.flush()
        for sentence in req.example_sentences[:5]:
            self._add_example(db, t.id, sentence)
        db.commit()
        db.refresh(t)
        return self.list_translations(db, object_id=t.doi_tuong_id, limit=1, offset=0)[0]

    @staticmethod
    def _parse_example_item(item) -> tuple[str, str | None]:
        """Parse example từ Gemini (dict {'en','vi'}) hoặc string thuần."""
        if isinstance(item, dict):
            return (item.get("en") or "").strip(), (item.get("vi") or "").strip() or None
        s = (item or "").strip()
        return s, None

    @staticmethod
    def _add_example(db: Session, translation_id: int, line: str) -> None:
        """Parse 'sentence | dịch nghĩa' hoặc chỉ 'sentence', lưu vào ViDu."""
        line = line.strip()
        if not line:
            return
        if '|' in line:
            parts = line.split('|', 1)
            cau_vi_du = parts[0].strip()
            dich_nghia = parts[1].strip() or None
        else:
            cau_vi_du = line
            dich_nghia = None
        if cau_vi_du:
            db.add(ViDu(ban_dich_id=translation_id, cau_vi_du=cau_vi_du, dich_nghia=dich_nghia, nguon_du_lieu="thu_cong"))

    def update_translation(self, db: Session, translation_id: int, req: TranslationUpdateRequest) -> Optional[TranslationAdminResponse]:
        t = db.query(Translation).filter(Translation.id == translation_id, Translation.thoi_gian_xoa.is_(None)).first()
        if not t:
            return None
        if req.tu_vung is not None:
            t.tu_vung = req.tu_vung
        if req.phien_am is not None:
            t.phien_am = req.phien_am
        if req.loai_tu is not None:
            t.loai_tu = req.loai_tu
        if req.dinh_nghia is not None:
            t.dinh_nghia = req.dinh_nghia
        if req.da_xac_nhan is not None:
            t.da_xac_nhan = req.da_xac_nhan
        if req.example_sentences is not None:
            db.query(ViDu).filter(ViDu.ban_dich_id == t.id).delete(synchronize_session=False)
            for sentence in req.example_sentences[:5]:
                self._add_example(db, t.id, sentence)
        db.commit()
        db.refresh(t)
        trans_list = self.list_translations(db, object_id=t.doi_tuong_id, limit=200, offset=0)
        return next((x for x in trans_list if x.id == t.id), None)

    def delete_translation(self, db: Session, translation_id: int) -> bool:
        t = db.query(Translation).filter(Translation.id == translation_id, Translation.thoi_gian_xoa.is_(None)).first()
        if not t:
            return False
        t.thoi_gian_xoa = now_vietnam()
        db.commit()
        return True

    # ------------------------------------------------------------------
    # User management
    # ------------------------------------------------------------------

    def list_users(self, db: Session, limit: int = 50, offset: int = 0, search: Optional[str] = None) -> List[UserAdminResponse]:
        query = db.query(User).filter(User.thoi_gian_xoa.is_(None))
        if search:
            query = query.filter(
                (User.ten_dang_nhap.ilike(f"%{search}%")) | (User.email.ilike(f"%{search}%"))
            )
        users = query.order_by(User.id.desc()).offset(offset).limit(limit).all()
        result = []
        for u in users:
            ho_ten = u.profile.ho_ten if u.profile else None
            vai_tro = u.vai_tro_obj.ten_vai_tro if u.vai_tro_obj else None
            trang_thai = u.trang_thai_obj.ten_trang_thai if u.trang_thai_obj else None
            result.append(UserAdminResponse(
                id=u.id,
                ten_dang_nhap=u.ten_dang_nhap,
                email=u.email,
                ho_ten=ho_ten,
                vai_tro=vai_tro,
                trang_thai=trang_thai,
                ngay_tao=u.ngay_tao,
                lan_dang_nhap_cuoi=u.lan_dang_nhap_cuoi,
            ))
        return result

    def update_user_role(self, db: Session, user_id: int, req: UserRoleUpdate) -> bool:
        u = db.query(User).filter(User.id == user_id, User.thoi_gian_xoa.is_(None)).first()
        if not u:
            return False
        u.vai_tro_id = req.vai_tro_id
        db.commit()
        return True

    def update_user_status(self, db: Session, user_id: int, req: UserStatusUpdate) -> bool:
        u = db.query(User).filter(User.id == user_id, User.thoi_gian_xoa.is_(None)).first()
        if not u:
            return False
        u.trang_thai_id = req.trang_thai_id
        db.commit()
        return True

    def delete_user(self, db: Session, user_id: int) -> bool:
        u = db.query(User).filter(User.id == user_id, User.thoi_gian_xoa.is_(None)).first()
        if not u:
            return False
        u.thoi_gian_xoa = now_vietnam()
        db.commit()
        return True

    def reset_user_password(self, db: Session, user_id: int, new_password: str) -> bool:
        u = db.query(User).filter(User.id == user_id, User.thoi_gian_xoa.is_(None)).first()
        if not u:
            return False
        u.mat_khau_ma_hoa = hash_password(new_password)
        db.commit()
        return True

    # ------------------------------------------------------------------
    # Scan History
    # ------------------------------------------------------------------

    def list_scan_history(
        self,
        db: Session,
        user_id: Optional[int] = None,
        username: Optional[str] = None,
        object_code: Optional[str] = None,
        date_from: Optional[str] = None,
        date_to: Optional[str] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> List[ScanHistoryAdminItem]:
        from datetime import datetime as dt
        query = (
            db.query(ScanHistory, User, Object)
            .outerjoin(User, ScanHistory.user_id == User.id)
            .outerjoin(Object, ScanHistory.doi_tuong_id == Object.id)
            .order_by(ScanHistory.thoi_gian.desc())
        )
        if user_id:
            query = query.filter(ScanHistory.user_id == user_id)
        if username:
            query = query.filter(User.ten_dang_nhap.ilike(f"%{username}%"))
        if object_code:
            from sqlalchemy import exists
            pattern = f"%{object_code}%"
            alias_match = exists().where(
                (ObjectAlias.doi_tuong_id == Object.id) &
                ObjectAlias.ma_bi_danh.ilike(pattern)
            )
            query = query.filter(or_(
                Object.ma_doi_tuong.ilike(pattern),
                alias_match,
            ))
        if date_from:
            query = query.filter(ScanHistory.thoi_gian >= dt.fromisoformat(date_from))
        if date_to:
            query = query.filter(ScanHistory.thoi_gian <= dt.fromisoformat(date_to + "T23:59:59"))
        rows = query.offset(offset).limit(limit).all()
        scan_ids = [scan.id for scan, _, _ in rows]
        pending_scan_ids = set(
            r[0] for r in db.query(AIPrediction.scan_id)
            .filter(
                AIPrediction.scan_id.in_(scan_ids),
                AIPrediction.trang_thai == TrangThaiDuyet.cho_duyet,
            ).all()
        ) if scan_ids else set()
        result = []
        for scan, user, obj in rows:
            result.append(ScanHistoryAdminItem(
                id=scan.id,
                user_id=scan.user_id,
                username=user.ten_dang_nhap if user else None,
                doi_tuong_id=scan.doi_tuong_id,
                object_code=obj.ma_doi_tuong if obj else None,
                url_anh=scan.url_anh,
                do_tin_cay=scan.do_tin_cay,
                thoi_gian=scan.thoi_gian,
                has_pending_prediction=scan.id in pending_scan_ids,
            ))
        return result

    # ------------------------------------------------------------------
    # User Stats
    # ------------------------------------------------------------------

    def get_user_stats(self, db: Session, user_id: int) -> Optional[UserStatsAdminResponse]:
        from sqlalchemy import func
        from datetime import date, timedelta

        user = db.query(User).filter(User.id == user_id, User.thoi_gian_xoa.is_(None)).first()
        if not user:
            return None

        total_scans = db.query(ScanHistory).filter(ScanHistory.user_id == user_id).count()
        total_reviews = db.query(ReviewLog).filter(ReviewLog.user_id == user_id).count()
        total_learned = db.query(LearningProgress).filter(LearningProgress.user_id == user_id).count()

        review_date_rows = (
            db.query(func.date(ReviewLog.thoi_diem_on))
            .filter(ReviewLog.user_id == user_id)
            .distinct()
            .order_by(func.date(ReviewLog.thoi_diem_on).desc())
            .all()
        )
        dates = [r[0] for r in review_date_rows]

        streak_hien_tai = 0
        streak_dai_nhat = 0
        if dates:
            today = date.today()
            cur = 0
            if dates[0] == today or dates[0] == today - timedelta(days=1):
                check = dates[0]
                for d in dates:
                    if d == check:
                        cur += 1
                        check -= timedelta(days=1)
                    else:
                        break
            streak_hien_tai = cur

            longest = 1
            run = 1
            for i in range(1, len(dates)):
                if (dates[i - 1] - dates[i]).days == 1:
                    run += 1
                    if run > longest:
                        longest = run
                else:
                    run = 1
            streak_dai_nhat = longest

        last_scan = (
            db.query(ScanHistory.thoi_gian)
            .filter(ScanHistory.user_id == user_id)
            .order_by(ScanHistory.thoi_gian.desc())
            .first()
        )
        last_review = (
            db.query(ReviewLog.thoi_diem_on)
            .filter(ReviewLog.user_id == user_id)
            .order_by(ReviewLog.thoi_diem_on.desc())
            .first()
        )

        return UserStatsAdminResponse(
            user_id=user_id,
            total_scans=total_scans,
            total_reviews=total_reviews,
            total_learned=total_learned,
            streak_hien_tai=streak_hien_tai,
            streak_dai_nhat=streak_dai_nhat,
            last_scan_at=last_scan[0] if last_scan else None,
            last_review_at=last_review[0] if last_review else None,
        )
