from fastapi import HTTPException
from sqlalchemy.orm import Session

from app.models.learning_progress import LearningProgress
from app.repositories.language_repo import LanguageRepository
from app.repositories.learning_repo import LearningProgressRepository
from app.repositories.translation_repo import TranslationRepository
from app.schemas.common import ReviewCardResponse, ReviewRequest, ReviewResult, ViDuResponse
from app.services.object_media_service import pick_primary_object_image
from app.utils.sm2 import calculate_sm2
from app.utils.timezone import now_vietnam


class LearningService:
    def __init__(self):
        self.repo = LearningProgressRepository()
        self.trans_repo = TranslationRepository()
        self.lang_repo = LanguageRepository()

    def add_to_learning(self, db: Session, translation_id: int, user_id: int):
        progress, created = self.ensure_in_learning(db, translation_id, user_id)
        db.commit()
        if not created:
            return {"message": "Da co trong danh sach hoc"}
        return {"message": "Da them vao danh sach hoc", "id": progress.id}

    def ensure_in_learning(self, db: Session, translation_id: int, user_id: int):
        existing = self.repo.get_by_user_and_translation(db, user_id, translation_id)
        if existing:
            return existing, False

        now = now_vietnam()
        progress = LearningProgress(
            user_id=user_id,
            ban_dich_id=translation_id,
            ngay_on_tiep=now,
            lan_on_cuoi=now,
        )
        self.repo.create(db, progress)
        return progress, True

    def get_due_reviews(self, db: Session, user_id: int):
        due = self.repo.get_due_reviews(db, user_id)
        results = []
        for p in due:
            t = p.translation
            if not t:
                continue
            lang = self.lang_repo.get_by_id(db, t.ngon_ngu_id)
            examples = [
                ViDuResponse(
                    id=e.id,
                    cau_vi_du=e.cau_vi_du,
                    dich_nghia=e.dich_nghia,
                    nguon_du_lieu=e.nguon_du_lieu,
                )
                for e in (t.examples or [])
            ]
            results.append(
                ReviewCardResponse(
                    progress_id=p.id,
                    translation_id=p.ban_dich_id,
                    object_code=t.object.ma_doi_tuong if t.object else "",
                    word_name=t.tu_vung,
                    phonetic=t.phien_am,
                    definition=t.dinh_nghia,
                    examples=examples,
                    language_code=lang.ma_ngon_ngu if lang else "",
                    language_name=lang.ten_ngon_ngu if lang else "",
                    easiness_factor=float(p.do_de_nho),
                    interval=p.khoang_lap,
                    repetitions=p.so_lan_lap,
                    image_url=pick_primary_object_image(t.object),
                )
            )
        return results

    def get_analytics(self, db: Session, user_id: int) -> dict:
        weekly = self.repo.get_weekly_review_counts(db, user_id)
        mastery = self.repo.get_mastery_distribution(db, user_id)
        return {
            "weekly_reviews": [{"date": d, "count": c} for d, c in weekly],
            "mastery": mastery,
        }

    def submit_review(self, db: Session, progress_id: int, request: ReviewRequest):
        progress = self.repo.get_by_id(db, progress_id)
        if not progress:
            raise HTTPException(404, "Progress not found")

        reviewed_at = now_vietnam()
        result = calculate_sm2(
            quality=request.quality,
            repetitions=progress.so_lan_lap,
            easiness_factor=float(progress.do_de_nho),
            interval=progress.khoang_lap,
            reviewed_at=reviewed_at,
        )
        progress.so_lan_lap = result["repetitions"]
        progress.do_de_nho = result["easiness_factor"]
        progress.khoang_lap = result["interval"]
        progress.ngay_on_tiep = result["next_review_date"]
        progress.lan_on_cuoi = reviewed_at

        self.repo.update(db, progress)
        db.commit()
        return ReviewResult(
            success=True,
            new_interval=result["interval"],
            new_ef=result["easiness_factor"],
            next_review_date=result["next_review_date"].strftime("%Y-%m-%d"),
        )
