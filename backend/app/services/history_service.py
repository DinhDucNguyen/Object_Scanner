import uuid
import os
from sqlalchemy.orm import Session
from app.repositories.history_repo import HistoryRepository
from app.repositories.object_repo import ObjectRepository
from app.models.scan_history import ScanHistory
from app.models.ai_feedback_report import AIPrediction
from app.schemas.common import AIPredictionCreate, LichSuQuetResponse
from app.utils.cloudinary_helper import upload_image

UPLOAD_DIR = "uploads/scans"


class HistoryFeedbackService:
    def __init__(self):
        self.hist_repo = HistoryRepository()

    def get_history(self, db: Session, user_id: int, limit: int = 50):
        scans = self.hist_repo.get_recent_scans(db, user_id, limit)
        return [
            {
                "id": s.id,
                "object_code": s.object.ma_doi_tuong if s.object else None,
                "confidence_score": s.do_tin_cay,
                "scanned_at": s.thoi_gian.isoformat() if s.thoi_gian else None,
                "image_url": s.url_anh
            }
            for s in scans
        ]

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

    def get_predictions(self, db: Session, scan_id: int):
        predictions = self.hist_repo.get_predictions(db, scan_id)
        return [
            {
                "id": p.id,
                "scan_id": p.scan_id,
                "source_ai": p.nguon_ai.value if p.nguon_ai else None,
                "predicted_label": p.nhan_du_doan,
                "confidence": p.do_tin_cay,
                "description": p.mo_ta,
                "ket_qua_dung": p.ket_qua_dung,
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
        )
        saved = self.hist_repo.create_scan(db, scan)
        db.commit()

        return LichSuQuetResponse(
            id=saved.id,
            message="Đã lưu lịch sử quét",
            image_url=image_url,
        )
