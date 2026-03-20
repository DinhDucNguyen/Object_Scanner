from sqlalchemy.orm import Session
from app.repositories.history_repo import HistoryRepository
from app.repositories.object_repo import ObjectRepository
from app.models.ai_feedback_report import AIFeedbackReport
from app.schemas.common import FeedbackCreate


class HistoryFeedbackService:
    def __init__(self):
        self.hist_repo = HistoryRepository()
        self.obj_repo = ObjectRepository()

    def get_history(self, db: Session, user_id: int, limit: int = 50):
        scans = self.hist_repo.get_recent_scans(db, user_id, limit)
        results = []
        for s in scans:
            obj = self.obj_repo.get_by_code(db, s.object.object_code) if s.object else None
            results.append({
                "id": s.id,
                "object_code": obj.object_code if obj else None,
                "confidence_score": s.confidence_score,
                "device_model": s.device_model,
                "scanned_at": s.scanned_at.isoformat() if s.scanned_at else None,
                "image_captured_url": s.image_captured_url
            })
        return results

    def submit_feedback(self, db: Session, user_id: int, data: FeedbackCreate):
        feedback = AIFeedbackReport(
            scan_id=data.scan_id, reported_by=user_id,
            error_type=data.error_type, correct_label=data.correct_label,
            user_note=data.user_note
        )
        return self.hist_repo.create_feedback(db, feedback)

    def get_feedback(self, db: Session, is_resolved: bool = None):
        return self.hist_repo.get_feedback(db, is_resolved)
