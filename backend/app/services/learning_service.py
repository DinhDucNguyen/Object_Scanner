from fastapi import HTTPException
from sqlalchemy.orm import Session

from app.models.learning_progress import LearningProgress
from app.models.review_log import ReviewLog
from app.repositories.language_repo import LanguageRepository
from app.repositories.learning_repo import LearningProgressRepository
from app.repositories.translation_repo import TranslationRepository
from app.schemas.common import ReviewCardResponse, ReviewRequest, ReviewResult, ViDuResponse
from app.services.object_media_service import pick_primary_object_image
from app.services.tts_service import TTSService
from app.utils.sm2 import calculate_sm2
from app.utils.timezone import now_vietnam


class LearningService:
    def __init__(self):
        self.repo = LearningProgressRepository()
        self.trans_repo = TranslationRepository()
        self.lang_repo = LanguageRepository()
        self.tts = TTSService()

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
        audio_url_updated = False
        for p in due:
            t = p.translation
            if not t:
                continue
            lang = self.lang_repo.get_by_id(db, t.ngon_ngu_id)
            lang_code = lang.ma_ngon_ngu if lang else "en"
            audio_url = t.am_thanh_url
            if not audio_url:
                audio_url = self.tts.get_audio_url(t.tu_vung, lang_code)
                if audio_url:
                    t.am_thanh_url = audio_url
                    audio_url_updated = True
            examples = [
                ViDuResponse(
                    id=e.id,
                    cau_vi_du=e.cau_vi_du,
                    dich_nghia=e.dich_nghia,
                    nguon_du_lieu=e.nguon_du_lieu,
                )
                for e in sorted((t.examples or []), key=lambda item: item.id or 0)[:3]
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
                    audio_url=audio_url,
                )
            )
        if audio_url_updated:
            db.commit()
        return results

    def get_analytics(self, db: Session, user_id: int) -> dict:
        weekly = self.repo.get_weekly_review_counts(db, user_id)
        mastery = self.repo.get_mastery_distribution(db, user_id)
        return {
            "weekly_reviews": [{"date": d, "count": c} for d, c in weekly],
            "mastery": mastery,
        }

    def submit_review(self, db: Session, progress_id: int, request: ReviewRequest, user_id: int):
        progress = self.repo.get_by_id(db, progress_id)
        if not progress or progress.user_id != user_id:
            raise HTTPException(404, "Progress not found")

        reviewed_at = now_vietnam()
        old_repetitions = progress.so_lan_lap or 0
        old_easiness_factor = float(progress.do_de_nho or 2.5)
        old_interval = progress.khoang_lap or 0

        result = calculate_sm2(
            quality=request.quality,
            repetitions=old_repetitions,
            easiness_factor=old_easiness_factor,
            interval=old_interval,
            reviewed_at=reviewed_at,
        )
        progress.so_lan_lap = result["repetitions"]
        progress.do_de_nho = result["easiness_factor"]
        progress.khoang_lap = result["interval"]
        progress.ngay_on_tiep = result["next_review_date"]
        progress.lan_on_cuoi = reviewed_at

        self.repo.update(db, progress)
        self.repo.create_review_log(
            db,
            ReviewLog(
                user_id=user_id,
                tien_do_hoc_id=progress.id,
                ban_dich_id=progress.ban_dich_id,
                chat_luong=request.quality,
                thoi_diem_on=reviewed_at,
                khoang_lap_cu=old_interval,
                khoang_lap_moi=result["interval"],
                do_de_nho_cu=old_easiness_factor,
                do_de_nho_moi=result["easiness_factor"],
                so_lan_lap_cu=old_repetitions,
                so_lan_lap_moi=result["repetitions"],
                ngay_on_tiep=result["next_review_date"],
                thoi_gian_tao=reviewed_at,
            ),
        )
        db.commit()
        return ReviewResult(
            success=True,
            new_interval=result["interval"],
            new_ef=result["easiness_factor"],
            next_review_date=result["next_review_date"].strftime("%Y-%m-%d"),
        )
