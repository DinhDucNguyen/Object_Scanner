from sqlalchemy import func
from sqlalchemy.orm import Session
from datetime import timedelta
from app.models.learning_progress import LearningProgress
from app.models.review_log import ReviewLog
from app.core.constants import SM2_MASTERED_MIN_REPETITIONS
from app.utils.timezone import now_vietnam


class LearningProgressRepository:
    def get_by_user_and_translation(self, db: Session, nguoi_dung_id: int, translation_id: int):
        return db.query(LearningProgress).filter(
            LearningProgress.nguoi_dung_id == nguoi_dung_id,
            LearningProgress.ban_dich_id == translation_id
        ).first()

    def get_due_reviews(self, db: Session, nguoi_dung_id: int):
        today = now_vietnam().date()
        return db.query(LearningProgress).filter(
            LearningProgress.nguoi_dung_id == nguoi_dung_id,
            func.date(LearningProgress.ngay_on_tiep) <= today
        ).all()

    def get_by_id(self, db: Session, progress_id: int):
        return db.query(LearningProgress).filter(LearningProgress.id == progress_id).first()

    def count_by_user(self, db: Session, nguoi_dung_id: int):
        return db.query(LearningProgress).filter(LearningProgress.nguoi_dung_id == nguoi_dung_id).count()

    def count_due_today(self, db: Session, nguoi_dung_id: int):
        today = now_vietnam().date()
        return db.query(LearningProgress).filter(
            LearningProgress.nguoi_dung_id == nguoi_dung_id,
            func.date(LearningProgress.ngay_on_tiep) <= today
        ).count()

    def count_mastered(self, db: Session, nguoi_dung_id: int):
        return db.query(LearningProgress).filter(
            LearningProgress.nguoi_dung_id == nguoi_dung_id, 
            LearningProgress.so_lan_lap >= SM2_MASTERED_MIN_REPETITIONS
        ).count()

    def get_weekly_review_counts(self, db: Session, nguoi_dung_id: int) -> list:
        seven_days_ago = now_vietnam().replace(hour=0, minute=0, second=0, microsecond=0) - timedelta(days=6)
        results = (
            db.query(
                func.date(ReviewLog.thoi_diem_on).label("review_date"),
                func.count(ReviewLog.id).label("count")
            )
            .filter(
                ReviewLog.nguoi_dung_id == nguoi_dung_id,
                ReviewLog.thoi_diem_on >= seven_days_ago,
            )
            .group_by(func.date(ReviewLog.thoi_diem_on))
            .order_by(func.date(ReviewLog.thoi_diem_on))
            .all()
        )
        return [(str(r.review_date), int(r.count)) for r in results]

    def create_review_log(self, db: Session, log: ReviewLog):
        db.add(log)
        db.flush()
        return log

    def get_mastery_distribution(self, db: Session, nguoi_dung_id: int) -> dict:
        rows = db.query(LearningProgress.so_lan_lap).filter(
            LearningProgress.nguoi_dung_id == nguoi_dung_id
        ).all()
        new_count      = sum(1 for (r,) in rows if r == 0)
        learning_count = sum(1 for (r,) in rows if 0 < r < SM2_MASTERED_MIN_REPETITIONS)
        mastered_count = sum(1 for (r,) in rows if r >= SM2_MASTERED_MIN_REPETITIONS)
        return {"new": new_count, "learning": learning_count, "mastered": mastered_count}

    def create(self, db: Session, progress: LearningProgress):
        db.add(progress)
        db.flush()
        return progress

    def update(self, db: Session, progress: LearningProgress):
        db.flush()
        return progress
