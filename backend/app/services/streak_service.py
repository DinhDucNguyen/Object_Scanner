from datetime import date, datetime, timedelta

# pyrefly: ignore [missing-import]
from sqlalchemy import func
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import Session

from app.models.review_log import ReviewLog
from app.utils.timezone import now_vietnam


class StreakService:
    """Build streak metrics from LichSuOnTap, the single source of review truth."""

    def get_streak(self, db: Session, user_id: int) -> dict:
        return self._build_streak(db, user_id)

    def record_review(self, db: Session, user_id: int) -> dict:
        # Review submissions create LichSuOnTap rows; this is a compatibility read.
        return self._build_streak(db, user_id)

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
        # Client summary counters are ignored because LichSuOnTap is authoritative.
        return self._build_streak(db, user_id)

    def _build_streak(self, db: Session, user_id: int) -> dict:
        today = self._today()
        review_dates = self._review_dates(db, user_id, today)
        total_reviews = self._total_reviews(db, user_id)
        reviews_today = self._reviews_on_date(db, user_id, today)
        last_review = review_dates[-1] if review_dates else None

        return {
            "streak_hien_tai": self._current_streak(review_dates, today),
            "streak_dai_nhat": self._longest_streak(review_dates),
            "tong_luot_on": total_reviews,
            "luot_on_hom_nay": reviews_today,
            "ngay_on_cuoi": str(last_review) if last_review else None,
        }

    def get_calendar(self, db: Session, user_id: int, days: int) -> list[dict]:
        today = self._today()
        start = today - timedelta(days=days - 1)
        review_day = func.date(ReviewLog.thoi_diem_on)
        rows = (
            db.query(review_day, func.count(ReviewLog.id))
            .filter(
                ReviewLog.user_id == user_id,
                func.date(ReviewLog.thoi_diem_on) >= str(start),
            )
            .group_by(review_day)
            .all()
        )
        count_map: dict[str, int] = {}
        for (value, cnt) in rows:
            d = self._coerce_date(value)
            if d:
                count_map[str(d)] = cnt
        result = []
        for i in range(days):
            day = start + timedelta(days=i)
            result.append({"date": str(day), "count": count_map.get(str(day), 0)})
        return result

    def _review_dates(self, db: Session, user_id: int, today: date) -> list[date]:
        review_day = func.date(ReviewLog.thoi_diem_on)
        rows = (
            db.query(review_day)
            .filter(ReviewLog.user_id == user_id)
            .distinct()
            .order_by(review_day)
            .all()
        )
        dates = []
        for (value,) in rows:
            review_date = self._coerce_date(value)
            if review_date and review_date <= today:
                dates.append(review_date)
        return sorted(set(dates))

    def _total_reviews(self, db: Session, user_id: int) -> int:
        return db.query(func.count(ReviewLog.id)).filter(ReviewLog.user_id == user_id).scalar() or 0

    def _reviews_on_date(self, db: Session, user_id: int, review_date: date) -> int:
        return (
            db.query(func.count(ReviewLog.id))
            .filter(
                ReviewLog.user_id == user_id,
                func.date(ReviewLog.thoi_diem_on) == review_date,
            )
            .scalar()
            or 0
        )

    def _current_streak(self, review_dates: list[date], today: date) -> int:
        if not review_dates:
            return 0

        review_days = set(review_dates)
        cursor = today if today in review_days else today - timedelta(days=1)
        if cursor not in review_days:
            return 0

        streak = 0
        while cursor in review_days:
            streak += 1
            cursor -= timedelta(days=1)
        return streak

    def _longest_streak(self, review_dates: list[date]) -> int:
        longest = 0
        current = 0
        previous = None

        for review_date in review_dates:
            if previous and review_date == previous + timedelta(days=1):
                current += 1
            else:
                current = 1
            longest = max(longest, current)
            previous = review_date

        return longest

    def _coerce_date(self, value) -> date | None:
        if isinstance(value, datetime):
            return value.date()
        if isinstance(value, date):
            return value
        if isinstance(value, str):
            try:
                return date.fromisoformat(value[:10])
            except ValueError:
                return None
        return None

    def _today(self) -> date:
        return now_vietnam().date()
