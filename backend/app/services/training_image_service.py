from sqlalchemy.orm import Session

from app.models.training_image import NguonAnhHuanLuyen, TrangThaiAnhHuanLuyen, TrainingImage
from app.utils.timezone import now_vietnam


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
    ) -> TrainingImage | None:
        if not url_anh:
            return None

        source = self._source(nguon_du_lieu)
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

        if item is None:
            item = TrainingImage(
                lich_su_quet_id=lich_su_quet_id,
                du_doan_id=du_doan_id,
                doi_tuong_id=doi_tuong_id,
                url_anh=url_anh,
                nhan=nhan,
                nguon_du_lieu=source,
                do_tin_cay=do_tin_cay,
                trang_thai=TrangThaiAnhHuanLuyen.cho_duyet,
                ghi_chu=ghi_chu,
                thoi_gian_tao=now_vietnam(),
            )
            db.add(item)
            db.flush()
            return item

        item.du_doan_id = item.du_doan_id or du_doan_id
        item.doi_tuong_id = item.doi_tuong_id or doi_tuong_id
        item.url_anh = item.url_anh or url_anh
        item.nhan = item.nhan or nhan
        item.nguon_du_lieu = item.nguon_du_lieu or source
        item.do_tin_cay = item.do_tin_cay if item.do_tin_cay is not None else do_tin_cay
        if ghi_chu and not item.ghi_chu:
            item.ghi_chu = ghi_chu
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
