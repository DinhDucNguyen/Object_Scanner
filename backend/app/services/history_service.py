import uuid
import os
from datetime import date as Date
from sqlalchemy.orm import Session
from app.repositories.history_repo import HistoryRepository
from app.repositories.object_repo import ObjectRepository
from app.repositories.translation_repo import TranslationRepository
from app.models.scan_history import ScanHistory
from app.models.ai_feedback_report import AIPrediction
from app.models.translation import Translation
from app.schemas.common import AIPredictionCreate, LichSuQuetResponse
from app.services.learning_service import LearningService
from app.utils.cloudinary_helper import upload_image
from app.utils.timezone import now_vietnam

UPLOAD_DIR = "uploads/scans"


class HistoryFeedbackService:
    def __init__(self):
        self.hist_repo = HistoryRepository()
        self.trans_repo = TranslationRepository()
        self.learning_service = LearningService()

    def get_history(
        self,
        db: Session,
        user_id: int,
        limit: int = 50,
        offset: int = 0,
        keyword: str | None = None,
        period: str | None = None,
        from_date: Date | None = None,
        to_date: Date | None = None,
    ):
        scans = self.hist_repo.get_recent_scans(
            db, user_id, limit, offset, keyword, period, from_date, to_date
        )
        return [self._scan_to_history_item(db, scan) for scan in scans]

    def get_history_detail(self, db: Session, user_id: int, scan_id: int):
        scan = self.hist_repo.get_by_id_for_user(db, scan_id, user_id)
        if not scan:
            return None

        item = self._scan_to_history_item(db, scan)
        translations = []
        if scan.object:
            translations = [
                self._translation_to_dict(t)
                for t in self.trans_repo.get_by_object_id(db, scan.object.id)
                if getattr(t, "thoi_gian_xoa", None) is None
            ]
        item["translations"] = translations
        return item

    def delete_history(self, db: Session, user_id: int, scan_id: int) -> bool:
        scan = self.hist_repo.get_by_id_for_user(db, scan_id, user_id)
        if not scan:
            return False
        self.hist_repo.delete_scan(db, scan)
        db.commit()
        return True

    def create_prediction(self, db: Session, data: AIPredictionCreate):
        prediction = AIPrediction(
            scan_id=data.scan_id,
            nguon_ai=data.source_ai,
            nhan_du_doan=data.predicted_label,
            do_tin_cay=data.confidence,
            mo_ta=data.description
        )
        result = self.hist_repo.create_prediction(db, prediction)
        db.commit()
        return result

    def get_predictions(self, db: Session, scan_id: int, user_id: int | None = None):
        if user_id is not None and not self.hist_repo.get_by_id_for_user(db, scan_id, user_id):
            return None

        predictions = self.hist_repo.get_predictions(db, scan_id)
        return [
            {
                "id": p.id,
                "scan_id": p.scan_id,
                "source_ai": p.nguon_ai.value if p.nguon_ai else None,
                "predicted_label": p.nhan_du_doan,
                "confidence": p.do_tin_cay,
                "description": p.mo_ta,
                "trang_thai": p.trang_thai.value if p.trang_thai else None,
                "created_at": p.thoi_gian.isoformat() if p.thoi_gian else None
            }
            for p in predictions
        ]

    def save_with_image(
        self,
        db: Session,
        user_id: int,
        object_code: str,
        confidence: float,
        image_bytes: bytes | None,
        base_url: str,
    ) -> LichSuQuetResponse:
        obj_repo = ObjectRepository()
        obj = obj_repo.get_by_code(db, object_code)
        review_translation = self._pick_review_translation(db, obj.id) if obj else None

        learning_added = False
        learning_status = "object_not_found"
        translation_id: int | None = None
        if obj:
            if review_translation:
                translation_id = review_translation.id
                _, learning_added = self.learning_service.ensure_in_learning(
                    db, translation_id, user_id
                )
                learning_status = "added" if learning_added else "already_in_learning"
            else:
                learning_status = "no_translation"

        image_url: str | None = None
        if image_bytes:
            image_url = upload_image(image_bytes)
            if image_url is None:
                # Fallback: lưu local nếu Cloudinary chưa cấu hình
                os.makedirs(UPLOAD_DIR, exist_ok=True)
                filename = f"{uuid.uuid4().hex}.jpg"
                filepath = os.path.join(UPLOAD_DIR, filename)
                with open(filepath, "wb") as f:
                    f.write(image_bytes)
                image_url = f"{base_url}/uploads/scans/{filename}"

        scan = ScanHistory(
            user_id=user_id,
            doi_tuong_id=obj.id if obj else None,
            do_tin_cay=confidence,
            url_anh=image_url,
            thoi_gian=now_vietnam(),
        )
        saved = self.hist_repo.create_scan(db, scan)
        db.commit()

        return LichSuQuetResponse(
            id=saved.id,
            message="Đã lưu lịch sử quét",
            image_url=image_url,
            learning_added=learning_added,
            learning_status=learning_status,
            translation_id=translation_id,
        )

    def _pick_review_translation(self, db: Session, object_id: int) -> Translation | None:
        translations = [
            t for t in self.trans_repo.get_by_object_id(db, object_id)
            if getattr(t, "thoi_gian_xoa", None) is None and bool(t.da_xac_nhan)
        ]
        if not translations:
            return None

        english = next(
            (
                t for t in translations
                if getattr(getattr(t, "language", None), "ma_ngon_ngu", None) == "en"
            ),
            None,
        )
        return english or translations[0]

    def _scan_to_history_item(self, db: Session, scan: ScanHistory) -> dict:
        obj = scan.object
        translation = self._pick_review_translation(db, obj.id) if obj else None
        object_code = obj.ma_doi_tuong if obj else None
        word_name = translation.tu_vung if translation else object_code

        return {
            "id": scan.id,
            "object_code": object_code,
            "object_name": word_name or object_code,
            "word_name": word_name,
            "phonetic": translation.phien_am if translation else None,
            "definition": translation.dinh_nghia if translation else None,
            "translation_id": translation.id if translation else None,
            "category_name": obj.category.ten_danh_muc if obj and obj.category else None,
            "confidence_score": scan.do_tin_cay,
            "scanned_at": scan.thoi_gian.isoformat() if scan.thoi_gian else None,
            "image_url": scan.url_anh,
        }

    def _translation_to_dict(self, translation: Translation) -> dict:
        lang = translation.language
        return {
            "id": translation.id,
            "object_id": translation.doi_tuong_id,
            "language_id": translation.ngon_ngu_id,
            "language_code": lang.ma_ngon_ngu if lang else None,
            "language_name": lang.ten_ngon_ngu if lang else None,
            "word_name": translation.tu_vung,
            "phonetic": translation.phien_am,
            "part_of_speech": translation.loai_tu,
            "definition": translation.dinh_nghia,
            "examples": [
                {
                    "id": ex.id,
                    "cau_vi_du": ex.cau_vi_du,
                    "dich_nghia": ex.dich_nghia,
                    "nguon_du_lieu": ex.nguon_du_lieu,
                }
                for ex in (translation.examples or [])
            ],
            "audio_url": translation.am_thanh_url,
            "data_source": (
                translation.nguon_du_lieu.value
                if hasattr(translation.nguon_du_lieu, "value")
                else str(translation.nguon_du_lieu) if translation.nguon_du_lieu else None
            ),
        }
