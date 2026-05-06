from sqlalchemy import func
from sqlalchemy.orm import Session
from datetime import datetime, timedelta
from app.models.learning_progress import LearningProgress
from app.core.constants import SM2_MASTERED_MIN_REPETITIONS


class LearningProgressRepository:
    def get_by_user_and_translation(self, db: Session, user_id: int, translation_id: int):
        return db.query(LearningProgress).filter(
            LearningProgress.user_id == user_id,
            LearningProgress.ban_dich_id == translation_id
        ).first()

    def get_due_reviews(self, db: Session, user_id: int):
        return db.query(LearningProgress).filter(
            LearningProgress.user_id == user_id,
            LearningProgress.ngay_on_tiep <= datetime.utcnow()
        ).all()

    def get_by_id(self, db: Session, progress_id: int):
        return db.query(LearningProgress).filter(LearningProgress.id == progress_id).first()

    def count_by_user(self, db: Session, user_id: int):
        return db.query(LearningProgress).filter(LearningProgress.user_id == user_id).count()

    def count_due_today(self, db: Session, user_id: int):
        return db.query(LearningProgress).filter(
            LearningProgress.user_id == user_id,
            LearningProgress.ngay_on_tiep <= datetime.utcnow()
        ).count()

    def count_mastered(self, db: Session, user_id: int):
        return db.query(LearningProgress).filter(
            LearningProgress.user_id == user_id, 
            LearningProgress.so_lan_lap >= SM2_MASTERED_MIN_REPETITIONS
        ).count()

    def get_weekly_review_counts(self, db: Session, user_id: int) -> list:
        seven_days_ago = datetime.utcnow() - timedelta(days=6)
        results = (
            db.query(
                func.date(LearningProgress.lan_on_cuoi).label("review_date"),
                func.count(LearningProgress.id).label("count")
            )
            .filter(
                LearningProgress.user_id == user_id,
                LearningProgress.lan_on_cuoi >= seven_days_ago,
                LearningProgress.so_lan_lap > 0
            )
            .group_by(func.date(LearningProgress.lan_on_cuoi))
            .order_by(func.date(LearningProgress.lan_on_cuoi))
            .all()
        )
        return [(str(r.review_date), r.count) for r in results]

    def get_mastery_distribution(self, db: Session, user_id: int) -> dict:
        rows = db.query(LearningProgress.so_lan_lap).filter(
            LearningProgress.user_id == user_id
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
