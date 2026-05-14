# pyrefly: ignore [missing-import]
from sqlalchemy.orm import Session
# pyrefly: ignore [missing-import]
from app.models.profile import Profile
# pyrefly: ignore [missing-import]
from app.utils.timezone import now_vietnam
from datetime import timedelta


class StreakService:
    """
    Daily streak rules:
      - Same day review: keep current streak, increment total reviews.
      - Next consecutive day: current streak += 1.
      - Missed more than one day: current streak resets to 1 on the next review.
    """

    def get_streak(self, db: Session, user_id: int) -> dict:
        profile = self._get_or_create_profile(db, user_id)
        if self._normalize_profile(profile):
            db.commit()
            db.refresh(profile)
        return self._to_dict(profile)

    def record_review(self, db: Session, user_id: int) -> dict:
        profile = self._get_or_create_profile(db, user_id)
        today = self._today()
        last = profile.ngay_on_cuoi

        profile.tong_luot_on = (profile.tong_luot_on or 0) + 1
        profile.luot_on_hom_nay = (profile.luot_on_hom_nay or 0) + 1 if last == today else 1

        if last is None:
            profile.streak_hien_tai = 1
        elif last == today:
            profile.streak_hien_tai = max(profile.streak_hien_tai or 0, 1)
        elif last == today - timedelta(days=1):
            profile.streak_hien_tai = (profile.streak_hien_tai or 0) + 1
        else:
            profile.streak_hien_tai = 1

        if profile.streak_hien_tai > (profile.streak_dai_nhat or 0):
            profile.streak_dai_nhat = profile.streak_hien_tai

        profile.ngay_on_cuoi = today

        db.commit()
        db.refresh(profile)
        return self._to_dict(profile)

    def sync_from_client(
        self,
        db: Session,
        user_id: int,
        streak_hien_tai: int,
        streak_dai_nhat: int,
        tong_luot_on: int,
        luot_on_hom_nay: int,
        ngay_on_cuoi,
    ) -> dict:
        profile = self._get_or_create_profile(db, user_id)
        today = self._today()

        self._normalize_profile(profile, today)
        client_current = self._normalized_streak_value(streak_hien_tai, ngay_on_cuoi, today)

        if client_current > (profile.streak_hien_tai or 0):
            profile.streak_hien_tai = client_current
        if streak_dai_nhat > (profile.streak_dai_nhat or 0):
            profile.streak_dai_nhat = streak_dai_nhat
        if tong_luot_on > (profile.tong_luot_on or 0):
            profile.tong_luot_on = tong_luot_on
        if (
            ngay_on_cuoi
            and ngay_on_cuoi <= today
            and (profile.ngay_on_cuoi is None or ngay_on_cuoi > profile.ngay_on_cuoi)
        ):
            profile.ngay_on_cuoi = ngay_on_cuoi

        if ngay_on_cuoi == today and luot_on_hom_nay > (profile.luot_on_hom_nay or 0):
            profile.luot_on_hom_nay = luot_on_hom_nay

        if profile.streak_hien_tai > (profile.streak_dai_nhat or 0):
            profile.streak_dai_nhat = profile.streak_hien_tai

        db.commit()
        db.refresh(profile)
        return self._to_dict(profile)

    def _get_or_create_profile(self, db: Session, user_id: int) -> Profile:
        profile = db.query(Profile).filter(Profile.user_id == user_id).first()
        if not profile:
            profile = Profile(user_id=user_id)
            db.add(profile)
            db.flush()
        return profile

    def _normalize_profile(self, profile: Profile, today=None) -> bool:
        today = today or self._today()
        changed = False
        normalized = self._normalized_streak_value(
            profile.streak_hien_tai or 0,
            profile.ngay_on_cuoi,
            today,
        )
        if normalized != (profile.streak_hien_tai or 0):
            profile.streak_hien_tai = normalized
            changed = True
        if (profile.streak_dai_nhat or 0) < normalized:
            profile.streak_dai_nhat = normalized
            changed = True
        expected_today_reviews = profile.luot_on_hom_nay or 0
        if profile.ngay_on_cuoi == today and normalized > 0:
            expected_today_reviews = max(expected_today_reviews, 1)
        else:
            expected_today_reviews = 0
        if expected_today_reviews != (profile.luot_on_hom_nay or 0):
            profile.luot_on_hom_nay = expected_today_reviews
            changed = True
        return changed

    def _normalized_streak_value(self, streak: int, last_review_date, today) -> int:
        if not last_review_date:
            return 0
        if last_review_date < today - timedelta(days=1):
            return 0
        return max(streak or 0, 0)

    def _today(self):
        return now_vietnam().date()

    def _to_dict(self, profile: Profile) -> dict:
        return {
            "streak_hien_tai": profile.streak_hien_tai or 0,
            "streak_dai_nhat": profile.streak_dai_nhat or 0,
            "tong_luot_on": profile.tong_luot_on or 0,
            "luot_on_hom_nay": profile.luot_on_hom_nay or 0,
            "ngay_on_cuoi": str(profile.ngay_on_cuoi) if profile.ngay_on_cuoi else None,
        }
