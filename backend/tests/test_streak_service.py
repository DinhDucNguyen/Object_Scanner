from datetime import date, datetime

from app.services import streak_service
from app.services.streak_service import StreakService


def test_today_uses_vietnam_local_clock(monkeypatch):
    monkeypatch.setattr(
        streak_service,
        "now_vietnam",
        lambda: datetime(2026, 6, 6, 0, 15, 0),
    )

    assert StreakService()._today() == date(2026, 6, 6)


def test_current_streak_counts_today_and_previous_days():
    service = StreakService()
    today = date(2026, 6, 5)
    review_dates = [
        date(2026, 6, 1),
        date(2026, 6, 3),
        date(2026, 6, 4),
        date(2026, 6, 5),
    ]

    assert service._current_streak(review_dates, today) == 3
    assert service._longest_streak(review_dates) == 3


def test_current_streak_accepts_yesterday_when_today_not_reviewed():
    service = StreakService()
    today = date(2026, 6, 5)
    review_dates = [date(2026, 6, 3), date(2026, 6, 4)]

    assert service._current_streak(review_dates, today) == 2


def test_current_streak_breaks_after_missed_yesterday():
    service = StreakService()
    today = date(2026, 6, 5)
    review_dates = [date(2026, 6, 1), date(2026, 6, 3)]

    assert service._current_streak(review_dates, today) == 0
