from sqlalchemy.orm import Session
from app.models.scan_history import ScanHistory
from app.models.ai_feedback_report import AIFeedbackReport


class HistoryRepository:
    def get_recent_scans(self, db: Session, user_id: int, limit: int = 50):
        return db.query(ScanHistory).filter(
            ScanHistory.user_id == user_id
        ).order_by(ScanHistory.scanned_at.desc()).limit(limit).all()

    def count_by_user(self, db: Session, user_id: int):
        return db.query(ScanHistory).filter(ScanHistory.user_id == user_id).count()

    def create_scan(self, db: Session, scan: ScanHistory):
        db.add(scan)
        db.flush()
        return scan

    def create_feedback(self, db: Session, feedback: AIFeedbackReport):
        db.add(feedback)
        db.flush()
        return feedback

    def get_feedback(self, db: Session, is_resolved: bool = None):
        query = db.query(AIFeedbackReport)
        if is_resolved is not None:
            query = query.filter(AIFeedbackReport.is_resolved == is_resolved)
        return query.order_by(AIFeedbackReport.created_at.desc()).all()
