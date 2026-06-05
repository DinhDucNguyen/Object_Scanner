from datetime import datetime

from app.utils.sm2 import calculate_sm2


def test_sm2_first_success_sets_one_day_interval_at_midnight():
    result = calculate_sm2(
        quality=5,
        repetitions=0,
        easiness_factor=2.5,
        interval=0,
        reviewed_at=datetime(2026, 6, 5, 22, 30, 0),
    )

    assert result["repetitions"] == 1
    assert result["easiness_factor"] == 2.6
    assert result["interval"] == 1
    assert result["next_review_date"] == datetime(2026, 6, 6, 0, 0, 0)


def test_sm2_second_success_uses_six_day_interval():
    result = calculate_sm2(
        quality=4,
        repetitions=1,
        easiness_factor=2.5,
        interval=1,
        reviewed_at=datetime(2026, 6, 5, 9, 0, 0),
    )

    assert result["repetitions"] == 2
    assert result["interval"] == 6
    assert result["next_review_date"] == datetime(2026, 6, 11, 0, 0, 0)


def test_sm2_failed_review_resets_repetitions_and_clamps_easiness():
    result = calculate_sm2(
        quality=-1,
        repetitions=5,
        easiness_factor=1.31,
        interval=30,
        reviewed_at=datetime(2026, 6, 5, 9, 0, 0),
    )

    assert result["repetitions"] == 0
    assert result["easiness_factor"] == 1.3
    assert result["interval"] == 1
