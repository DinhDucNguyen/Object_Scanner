from sqlalchemy.orm import Session
from app.models.scan_history import ScanHistory
from app.models.ai_feedback_report import AIPrediction


class HistoryRepository:
    def get_recent_scans(self, db: Session, user_id: int, limit: int = 50):
        return db.query(ScanHistory).filter(
            ScanHistory.user_id == user_id
        ).order_by(ScanHistory.thoi_gian.desc()).limit(limit).all()

    def count_by_user(self, db: Session, user_id: int):
        return db.query(ScanHistory).filter(ScanHistory.user_id == user_id).count()

    def create_scan(self, db: Session, scan: ScanHistory):
        db.add(scan)
        db.flush()
        return scan

    def create_prediction(self, db: Session, prediction: AIPrediction):
        db.add(prediction)
        db.flush()
        return prediction

    def get_predictions(self, db: Session, scan_id: int):
        return db.query(AIPrediction).filter(
            AIPrediction.scan_id == scan_id
        ).order_by(AIPrediction.thoi_gian.desc()).all()
