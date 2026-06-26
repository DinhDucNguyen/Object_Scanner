from datetime import timedelta

from sqlalchemy.orm import Session

from app.models.training_image import NguonAnhHuanLuyen, TrangThaiAnhHuanLuyen, TrainingImage
from app.utils.timezone import now_vietnam

# Confidence >= ngưỡng này + object có trong DB + nguồn yolo/coco → auto-approve
AUTO_APPROVE_CONFIDENCE_THRESHOLD = 0.85

# Trong vòng bao nhiêu phút thì coi là duplicate từ cùng user + cùng object
DUPLICATE_WINDOW_MINUTES = 10


class TrainingImageService:
    def create_candidate(
        self,
        db: Session,
        *,
        url_anh: str | None,
        nhan: str | None,
        nguon_du_lieu: NguonAnhHuanLuyen | str,
        lich_su_quet_id: int | None = None,
        du_doan_id: int | None = None,
        doi_tuong_id: int | None = None,
        do_tin_cay: float | None = None,
        ghi_chu: str | None = None,
        diem_chat_luong: int | None = None,
        auto_approve: bool = False,
        nguoi_dung_id: int | None = None,
    ) -> TrainingImage | None:
        if not url_anh:
            return None

        source = self._source(nguon_du_lieu)

        # Kiểm tra ảnh đã tồn tại cho scan này chưa
        item = None
        if lich_su_quet_id is not None:
            item = (
                db.query(TrainingImage)
                .filter(
                    TrainingImage.lich_su_quet_id == lich_su_quet_id,
                    TrainingImage.thoi_gian_xoa.is_(None),
                )
                .first()
            )

        if item is not None:
            item.du_doan_id = item.du_doan_id or du_doan_id
            item.doi_tuong_id = item.doi_tuong_id or doi_tuong_id
            item.url_anh = item.url_anh or url_anh
            item.nhan = item.nhan or nhan
            item.nguon_du_lieu = item.nguon_du_lieu or source
            item.do_tin_cay = item.do_tin_cay if item.do_tin_cay is not None else do_tin_cay
            if ghi_chu and not item.ghi_chu:
                item.ghi_chu = ghi_chu
            if diem_chat_luong is not None and item.diem_chat_luong is None:
                item.diem_chat_luong = diem_chat_luong
            db.flush()
            return item

        # Duplicate detection: cùng user + cùng object + trong DUPLICATE_WINDOW_MINUTES
        if doi_tuong_id and nguoi_dung_id:
            window_start = now_vietnam() - timedelta(minutes=DUPLICATE_WINDOW_MINUTES)
            duplicate = (
                db.query(TrainingImage)
                .join(TrainingImage.scan)
                .filter(
                    TrainingImage.doi_tuong_id == doi_tuong_id,
                    TrainingImage.thoi_gian_xoa.is_(None),
                    TrainingImage.trang_thai != TrangThaiAnhHuanLuyen.tu_choi,
                    TrainingImage.thoi_gian_tao >= window_start,
                    TrainingImage.scan.has(nguoi_dung_id=nguoi_dung_id),
                )
                .first()
            )
            if duplicate:
                # Giữ ảnh confidence cao hơn
                if (do_tin_cay or 0) > (duplicate.do_tin_cay or 0):
                    duplicate.url_anh = url_anh
                    duplicate.do_tin_cay = do_tin_cay
                    if lich_su_quet_id:
                        duplicate.lich_su_quet_id = lich_su_quet_id
                    db.flush()
                return duplicate

        # Auto-approve: nguồn yolo/coco + object có DB + confidence cao + ảnh đạt chất lượng
        now = now_vietnam()
        is_known_source = source in (NguonAnhHuanLuyen.yolo, NguonAnhHuanLuyen.gemini)
        high_confidence = (do_tin_cay or 0) >= AUTO_APPROVE_CONFIDENCE_THRESHOLD
        # Dùng prefix "quality_fail:" để phân biệt ghi_chu chất lượng vs ghi_chu từ nguồn khác
        quality_failed = ghi_chu and ghi_chu.startswith("quality_fail:")
        should_auto_approve = auto_approve or (
            is_known_source and doi_tuong_id is not None and high_confidence and not quality_failed
        )

        initial_status = TrangThaiAnhHuanLuyen.da_duyet if should_auto_approve else TrangThaiAnhHuanLuyen.cho_duyet
        item = TrainingImage(
            lich_su_quet_id=lich_su_quet_id,
            du_doan_id=du_doan_id,
            doi_tuong_id=doi_tuong_id,
            url_anh=url_anh,
            nhan=nhan,
            nguon_du_lieu=source,
            do_tin_cay=do_tin_cay,
            diem_chat_luong=diem_chat_luong,
            trang_thai=initial_status,
            ghi_chu=ghi_chu,
            thoi_gian_tao=now,
            thoi_gian_duyet=now if should_auto_approve else None,
        )
        db.add(item)
        db.flush()
        return item

    def attach_object_for_scan(
        self,
        db: Session,
        *,
        lich_su_quet_id: int | None,
        doi_tuong_id: int,
        nhan: str | None = None,
    ) -> None:
        if lich_su_quet_id is None:
            return
        item = (
            db.query(TrainingImage)
            .filter(
                TrainingImage.lich_su_quet_id == lich_su_quet_id,
                TrainingImage.thoi_gian_xoa.is_(None),
            )
            .first()
        )
        if not item:
            return
        item.doi_tuong_id = doi_tuong_id
        if nhan:
            item.nhan = nhan
        db.flush()

    def _source(self, value: NguonAnhHuanLuyen | str) -> NguonAnhHuanLuyen:
        if isinstance(value, NguonAnhHuanLuyen):
            return value
        try:
            return NguonAnhHuanLuyen(value)
        except ValueError:
            return NguonAnhHuanLuyen.admin
