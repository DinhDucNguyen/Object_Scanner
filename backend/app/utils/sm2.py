"""
SM-2 Algorithm (Spaced Repetition)
Tính toán interval, EF, repetitions cho ôn tập từ vựng
"""

from datetime import datetime, timedelta, timezone


def calculate_sm2(quality: int, repetitions: int, easiness_factor: float, interval: int) -> dict:
    """
    SM-2 Algorithm
    quality: 0-5 (0=blackout, 5=perfect)
    Returns: { repetitions, easiness_factor, interval, next_review_date }
    """
    if quality < 0 or quality > 5:
        quality = max(0, min(5, quality))

    # Tính EF mới
    new_ef = easiness_factor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
    new_ef = max(1.3, new_ef)

    if quality < 3:
        # Reset nếu trả lời sai
        new_reps = 0
        new_interval = 1
    else:
        new_reps = repetitions + 1
        if new_reps == 1:
            new_interval = 1
        elif new_reps == 2:
            new_interval = 6
        else:
            new_interval = int(interval * new_ef)

    next_review = datetime.now(timezone.utc) + timedelta(days=new_interval)

    return {
        "repetitions": new_reps,
        "easiness_factor": round(new_ef, 2),
        "interval": new_interval,
        "next_review_date": next_review
    }
