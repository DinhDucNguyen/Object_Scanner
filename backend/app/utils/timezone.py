from datetime import datetime, timedelta, timezone


VIETNAM_TZ = timezone(timedelta(hours=7))


def now_vietnam() -> datetime:
    """Return current Vietnam local time as a naive datetime for MySQL DateTime columns."""
    return datetime.now(VIETNAM_TZ).replace(tzinfo=None)
