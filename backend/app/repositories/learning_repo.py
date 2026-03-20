from sqlalchemy.orm import Session
from datetime import datetime
from app.models.learning_progress import LearningProgress


class LearningProgressRepository:
    def get_by_user_and_translation(self, db: Session, user_id: int, translation_id: int):
        return db.query(LearningProgress).filter(
            LearningProgress.user_id == user_id,
            LearningProgress.translation_id == translation_id
        ).first()

    def get_due_reviews(self, db: Session, user_id: int):
        return db.query(LearningProgress).filter(
            LearningProgress.user_id == user_id,
            LearningProgress.next_review_date <= datetime.utcnow()
        ).all()

    def get_by_id(self, db: Session, progress_id: int):
        return db.query(LearningProgress).filter(LearningProgress.id == progress_id).first()

    def count_by_user(self, db: Session, user_id: int):
        return db.query(LearningProgress).filter(LearningProgress.user_id == user_id).count()

    def count_due_today(self, db: Session, user_id: int):
        return db.query(LearningProgress).filter(
            LearningProgress.user_id == user_id,
            LearningProgress.next_review_date <= datetime.utcnow()
        ).count()

    def count_mastered(self, db: Session, user_id: int):
        return db.query(LearningProgress).filter(
            LearningProgress.user_id == user_id, 
            LearningProgress.repetitions >= 3
        ).count()

    def create(self, db: Session, progress: LearningProgress):
        db.add(progress)
        db.commit()
        db.refresh(progress)
        return progress

    def update(self, db: Session, progress: LearningProgress):
        db.commit()
        return progress
