# pyrefly: ignore [missing-import]
from sqlalchemy.orm import Session
# pyrefly: ignore [missing-import]
from app.models.profile import Profile
# pyrefly: ignore [missing-import]
from datetime import date, timedelta


class StreakService:
    """
    Logic tính streak theo ngày.
    Quy tắc:
      - Ôn cùng ngày → không tăng streak, chỉ tăng tong_luot_on
      - Ôn ngày hôm sau (liên tiếp) → streak += 1
      - Bỏ quá 1 ngày → reset streak về 1
    """

    def get_streak(self, db: Session, user_id: int) -> dict:
        profile = self._get_or_create_profile(db, user_id)
        return self._to_dict(profile)

    def record_review(self, db: Session, user_id: int) -> dict:
        profile = self._get_or_create_profile(db, user_id)
        today   = date.today()
        last    = profile.ngay_on_cuoi

        # Tăng tổng lượt ôn dù ngày nào
        profile.tong_luot_on = (profile.tong_luot_on or 0) + 1

        if last is None:
            # Lần đầu tiên
            profile.streak_hien_tai = 1
        elif last == today:
            # Đã ôn hôm nay rồi → không làm gì thêm
            pass
        elif last == today - timedelta(days=1):
            # Ngày tiếp theo liên tiếp → tăng streak
            profile.streak_hien_tai = (profile.streak_hien_tai or 0) + 1
        else:
            # Bỏ cách → reset
            profile.streak_hien_tai = 1

        # Cập nhật kỷ lục
        if profile.streak_hien_tai > (profile.streak_dai_nhat or 0):
            profile.streak_dai_nhat = profile.streak_hien_tai

        # Cập nhật ngày ôn cuối (chỉ khi khác hôm nay để tránh overwrite)
        profile.ngay_on_cuoi = today

        db.commit()
        db.refresh(profile)
        return self._to_dict(profile)

    def sync_from_client(self, db: Session, user_id: int,
                         streak_hien_tai: int, streak_dai_nhat: int,
                         tong_luot_on: int, ngay_on_cuoi) -> dict:
        """
        Android gửi toàn bộ trạng thái streak lên.
        Server chỉ nhận giá trị lớn hơn hiện tại (tránh ghi đè lùi).
        """
        profile = self._get_or_create_profile(db, user_id)

        if streak_hien_tai > (profile.streak_hien_tai or 0):
            profile.streak_hien_tai = streak_hien_tai
        if streak_dai_nhat > (profile.streak_dai_nhat or 0):
            profile.streak_dai_nhat = streak_dai_nhat
        if tong_luot_on > (profile.tong_luot_on or 0):
            profile.tong_luot_on = tong_luot_on
        if ngay_on_cuoi and (profile.ngay_on_cuoi is None or ngay_on_cuoi > profile.ngay_on_cuoi):
            profile.ngay_on_cuoi = ngay_on_cuoi

        db.commit()
        db.refresh(profile)
        return self._to_dict(profile)

    # ── Helpers ─────────────────────────────────────────────────────────────

    def _get_or_create_profile(self, db: Session, user_id: int) -> Profile:
        profile = db.query(Profile).filter(Profile.user_id == user_id).first()
        if not profile:
            profile = Profile(user_id=user_id)
            db.add(profile)
            db.flush()
        return profile

    def _to_dict(self, profile: Profile) -> dict:
        return {
            "streak_hien_tai": profile.streak_hien_tai or 0,
            "streak_dai_nhat": profile.streak_dai_nhat or 0,
            "tong_luot_on":    profile.tong_luot_on    or 0,
            "ngay_on_cuoi":    str(profile.ngay_on_cuoi) if profile.ngay_on_cuoi else None,
        }
