from fastapi import HTTPException
from sqlalchemy.orm import Session
from datetime import datetime
from app.repositories.learning_repo import LearningProgressRepository
from app.repositories.translation_repo import TranslationRepository
from app.repositories.language_repo import LanguageRepository
from app.repositories.object_repo import ObjectRepository
from app.models.learning_progress import LearningProgress
from app.utils.sm2 import calculate_sm2
from app.schemas.common import ReviewRequest, ReviewCardResponse, ReviewResult


class LearningService:
    def __init__(self):
        self.repo = LearningProgressRepository()
        self.trans_repo = TranslationRepository()
        self.lang_repo = LanguageRepository()
        self.obj_repo = ObjectRepository()

    def add_to_learning(self, db: Session, translation_id: int, user_id: int):
        existing = self.repo.get_by_user_and_translation(db, user_id, translation_id)
        if existing:
            return {"message": "Đã có trong danh sách học"}

        progress = LearningProgress(
            user_id=user_id, translation_id=translation_id, next_review_date=datetime.utcnow()
        )
        self.repo.create(db, progress)
        db.commit()
        return {"message": "Đã thêm vào danh sách học", "id": progress.id}

    def get_due_reviews(self, db: Session, user_id: int):
        due = self.repo.get_due_reviews(db, user_id)
        results = []
        for p in due:
            t = p.translation
            if not t:
                continue
            lang = self.lang_repo.get_by_id(db, t.language_id)
            results.append(ReviewCardResponse(
                progress_id=p.id, translation_id=p.translation_id,
                object_code=t.object.object_code if t.object else "",
                word_name=t.word_name, phonetic=t.phonetic,
                definition=t.definition, example_sentence=t.example_sentence,
                language_code=lang.code if lang else "", language_name=lang.name if lang else "",
                easiness_factor=float(p.easiness_factor), interval=p.interval, repetitions=p.repetitions
            ))
        return results

    def submit_review(self, db: Session, progress_id: int, request: ReviewRequest):
        progress = self.repo.get_by_id(db, progress_id)
        if not progress:
            raise HTTPException(404, "Progress not found")

        result = calculate_sm2(
            quality=request.quality, repetitions=progress.repetitions,
            easiness_factor=float(progress.easiness_factor), interval=progress.interval
        )
        progress.repetitions = result["repetitions"]
        progress.easiness_factor = result["easiness_factor"]
        progress.interval = result["interval"]
        progress.next_review_date = result["next_review_date"]
        progress.last_reviewed_at = datetime.utcnow()

        self.repo.update(db, progress)
        db.commit()
        return ReviewResult(
            success=True, new_interval=result["interval"],
            new_ef=result["easiness_factor"],
            next_review_date=result["next_review_date"].strftime("%Y-%m-%d")
        )
