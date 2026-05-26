from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from pydantic import BaseModel
from typing import Optional
from datetime import date

from app.db.session import get_db
from app.services.streak_service import StreakService
from app.dependencies.get_current_user import get_current_user_id

router = APIRouter(prefix="/api/streak", tags=["Streak"])
streak_service = StreakService()


# ── Schemas ─────────────────────────────────────────────────────────────────

class StreakResponse(BaseModel):
    streak_hien_tai: int
    streak_dai_nhat: int
    tong_luot_on:    int
    luot_on_hom_nay: int
    ngay_on_cuoi:    Optional[str] = None


class StreakSyncRequest(BaseModel):
    """Payload cũ từ Android; server không dùng làm nguồn thống kê."""
    streak_hien_tai: int = 0
    streak_dai_nhat: int = 0
    tong_luot_on:    int = 0
    luot_on_hom_nay: int = 0
    ngay_on_cuoi:    Optional[date] = None


class CalendarDay(BaseModel):
    date: str        # "yyyy-MM-dd"
    count: int       # số lượt ôn trong ngày


class StreakCalendarResponse(BaseModel):
    days: list[CalendarDay]


# ── Endpoints ────────────────────────────────────────────────────────────────

@router.get("", response_model=StreakResponse, summary="Lấy streak hiện tại")
def get_streak(
    db:      Session = Depends(get_db),
    user_id: int     = Depends(get_current_user_id),
):
    """Trả về streak, kỷ lục và tổng lượt ôn của user."""
    return streak_service.get_streak(db, user_id)


@router.post("/record", response_model=StreakResponse, summary="Lấy lại streak sau lượt ôn")
def record_review(
    db:      Session = Depends(get_db),
    user_id: int     = Depends(get_current_user_id),
):
    """
    Lượt ôn thật được ghi ở endpoint submit review vào LichSuOnTap.
    Endpoint này chỉ trả về streak mới nhất để tương thích client cũ.
    """
    return streak_service.record_review(db, user_id)


@router.get("/calendar", response_model=StreakCalendarResponse, summary="Lịch học 30 ngày gần nhất")
def get_streak_calendar(
    days:    int     = Query(default=30, ge=7, le=90),
    db:      Session = Depends(get_db),
    user_id: int     = Depends(get_current_user_id),
):
    """Trả về số lượt ôn mỗi ngày trong `days` ngày gần nhất."""
    calendar_days = streak_service.get_calendar(db, user_id, days)
    return StreakCalendarResponse(days=calendar_days)


@router.post("/sync", response_model=StreakResponse, summary="Lấy streak server cho client cũ")
def sync_streak(
    body:    StreakSyncRequest,
    db:      Session = Depends(get_db),
    user_id: int     = Depends(get_current_user_id),
):
    """
    Server bỏ qua counter local vì LichSuOnTap là nguồn dữ liệu chính.
    Giữ endpoint để app phiên bản cũ không lỗi khi gọi sync.
    """
    return streak_service.sync_from_client(
        db, user_id,
        body.streak_hien_tai,
        body.streak_dai_nhat,
        body.tong_luot_on,
        body.luot_on_hom_nay,
        body.ngay_on_cuoi,
    )
