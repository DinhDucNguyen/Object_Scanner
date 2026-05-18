from datetime import date as Date
from datetime import datetime, timedelta

from sqlalchemy import func, or_
from sqlalchemy.orm import Session
from app.models.scan_history import ScanHistory
from app.models.ai_feedback_report import AIPrediction
from app.models.object import Object
from app.models.translation import Translation
from app.utils.timezone import now_vietnam


class HistoryRepository:
    def get_recent_scans(
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
        query = db.query(ScanHistory).filter(
            ScanHistory.user_id == user_id
        )

        start_date, end_date = self._date_range(from_date, to_date)
        if start_date is None and end_date is None:
            start_date, end_date = self._period_range(period)
        if start_date:
            query = query.filter(ScanHistory.thoi_gian >= start_date)
        if end_date:
            query = query.filter(ScanHistory.thoi_gian < end_date)

        if keyword and keyword.strip():
            pattern = f"%{keyword.strip().lower()}%"
            query = (
                query
                .outerjoin(Object, ScanHistory.doi_tuong_id == Object.id)
                .outerjoin(Translation, Translation.doi_tuong_id == Object.id)
                .outerjoin(AIPrediction, AIPrediction.scan_id == ScanHistory.id)
                .filter(or_(
                    func.lower(Object.ma_doi_tuong).like(pattern),
                    func.lower(Translation.tu_vung).like(pattern),
                    func.lower(Translation.dinh_nghia).like(pattern),
                    func.lower(AIPrediction.nhan_du_doan).like(pattern),
                    func.lower(AIPrediction.mo_ta).like(pattern),
                ))
                .distinct()
            )

        return (
            query
            .order_by(ScanHistory.thoi_gian.desc())
            .offset(offset)
            .limit(limit)
            .all()
        )

    def get_by_id_for_user(self, db: Session, scan_id: int, user_id: int):
        return db.query(ScanHistory).filter(
            ScanHistory.id == scan_id,
            ScanHistory.user_id == user_id,
        ).first()

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

    def delete_scan(self, db: Session, scan: ScanHistory):
        db.delete(scan)

    def _period_range(self, period: str | None):
        if not period or period == "all":
            return None, None

        now = now_vietnam()
        today = datetime(now.year, now.month, now.day)
        if period == "today":
            return today, today + timedelta(days=1)
        if period == "week":
            start = today - timedelta(days=today.weekday())
            return start, start + timedelta(days=7)
        if period == "last_week":
            end = today - timedelta(days=today.weekday())
            return end - timedelta(days=7), end
        if period == "month":
            start = datetime(now.year, now.month, 1)
            if now.month == 12:
                end = datetime(now.year + 1, 1, 1)
            else:
                end = datetime(now.year, now.month + 1, 1)
            return start, end
        return None, None

    def _date_range(self, from_date: Date | None, to_date: Date | None):
        if from_date and to_date and from_date > to_date:
            from_date, to_date = to_date, from_date

        start = (
            datetime(from_date.year, from_date.month, from_date.day)
            if from_date else None
        )
        end = (
            datetime(to_date.year, to_date.month, to_date.day) + timedelta(days=1)
            if to_date else None
        )
        return start, end
