"""
Admin Moderation Service
========================
Xử lý quy trình kiểm duyệt từ vựng do Gemini đề xuất.

Flow:
  DuDoanAI (cho_duyet)
      ↓ admin approve
  DoiTuong (upsert) + BanDich (insert) + ViDu ×3 (insert)
  DuDoanAI.trang_thai = da_duyet

  Hoặc:
      ↓ admin reject
  DuDoanAI.trang_thai = tu_choi
"""
import json
from typing import Optional

from sqlalchemy.orm import Session

from app.models.ai_feedback_report import AIPrediction, TrangThaiDuyet
from app.models.object import Object
from app.models.translation import Translation, NguonDuLieu
from app.models.example import ViDu
from app.models.language import Language
from app.schemas.admin import (
    ApproveRequest, ApproveResponse,
    RejectResponse,
    PredictionDetailResponse, PredictionListItem,
    VocabPayloadSchema,
)


class AdminService:

    # ------------------------------------------------------------------
    # Truy vấn
    # ------------------------------------------------------------------

    def list_predictions(
        self,
        db: Session,
        trang_thai: Optional[str] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> list[PredictionListItem]:
        query = db.query(AIPrediction).order_by(AIPrediction.thoi_gian.desc())
        if trang_thai:
            try:
                query = query.filter(AIPrediction.trang_thai == TrangThaiDuyet(trang_thai))
            except ValueError:
                pass
        return [PredictionListItem.model_validate(p) for p in query.offset(offset).limit(limit).all()]

    def get_prediction_detail(self, db: Session, prediction_id: int) -> Optional[PredictionDetailResponse]:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return None

        vocab_payload = None
        if p.mo_ta:
            try:
                vocab_payload = VocabPayloadSchema.model_validate(json.loads(p.mo_ta))
            except Exception:
                pass

        return PredictionDetailResponse(
            id=p.id,
            scan_id=p.scan_id,
            nhan_du_doan=p.nhan_du_doan,
            do_tin_cay=p.do_tin_cay,
            trang_thai=p.trang_thai.value,
            thoi_gian=p.thoi_gian,
            vocab_payload=vocab_payload,
        )

    # ------------------------------------------------------------------
    # Approve — Insert vào bảng chính
    # ------------------------------------------------------------------

    def approve_prediction(
        self,
        db: Session,
        prediction_id: int,
        request: ApproveRequest,
    ) -> ApproveResponse:
        """
        Duyệt prediction:
          1. Parse vocab payload từ mo_ta
          2. Upsert DoiTuong
          3. Upsert Language
          4. Insert BanDich (bỏ qua nếu đã tồn tại)
          5. Insert ViDu (3 câu ví dụ)
          6. Đánh dấu da_duyet
        """
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return ApproveResponse(success=False, message="Không tìm thấy prediction", prediction_id=prediction_id)

        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return ApproveResponse(
                success=False,
                message=f"Prediction đã ở trạng thái '{p.trang_thai.value}'",
                prediction_id=prediction_id,
            )

        if not p.mo_ta:
            return ApproveResponse(success=False, message="Không có dữ liệu từ vựng (mo_ta trống)", prediction_id=prediction_id)

        try:
            raw = json.loads(p.mo_ta)
        except Exception:
            return ApproveResponse(success=False, message="mo_ta không phải JSON hợp lệ", prediction_id=prediction_id)

        object_code = raw.get("object_code", p.nhan_du_doan or "unknown").lower()
        translations_raw = raw.get("translations", [])
        if not translations_raw:
            return ApproveResponse(success=False, message="Không có translations trong payload", prediction_id=prediction_id)

        t_data = translations_raw[0]

        # Admin có thể override bất kỳ trường nào
        word_name      = request.override_word_name      or t_data.get("word_name", object_code)
        phonetic       = request.override_phonetic       or t_data.get("phonetic")
        part_of_speech = request.override_part_of_speech or t_data.get("part_of_speech")
        definition     = request.override_definition     or t_data.get("definition")
        examples = (
            request.override_example_sentences
            if request.override_example_sentences is not None
            else t_data.get("example_sentences", [])
        )
        lang_code = t_data.get("lang_code", "en")

        # Upsert DoiTuong
        obj = db.query(Object).filter(
            Object.ma_doi_tuong == object_code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            obj = Object(
                ma_doi_tuong=object_code,
                muc_do_kho=request.difficulty_level,
                danh_muc_id=request.category_id,
            )
            db.add(obj)
            db.flush()

        # Upsert Language
        lang = db.query(Language).filter(Language.ma_ngon_ngu == lang_code).first()
        if not lang:
            lang_names = {"en": "English", "vi": "Vietnamese", "ja": "Japanese", "ko": "Korean"}
            lang = Language(
                ma_ngon_ngu=lang_code,
                ten_ngon_ngu=lang_names.get(lang_code, lang_code.upper()),
                dang_hoat_dong=True,
            )
            db.add(lang)
            db.flush()

        # Insert BanDich (skip nếu đã tồn tại)
        existing = db.query(Translation).filter(
            Translation.doi_tuong_id == obj.id,
            Translation.ngon_ngu_id == lang.id,
        ).first()

        if existing:
            translation = existing
            examples_created = 0
        else:
            translation = Translation(
                doi_tuong_id=obj.id,
                ngon_ngu_id=lang.id,
                tu_vung=word_name,
                phien_am=phonetic,
                loai_tu=part_of_speech,
                dinh_nghia=definition,
                nguon_du_lieu=NguonDuLieu.gemini,
                da_xac_nhan=True,
            )
            db.add(translation)
            db.flush()

            # Insert ViDu
            examples_created = 0
            for sentence in examples[:3]:
                if sentence and sentence.strip():
                    db.add(ViDu(ban_dich_id=translation.id, cau_vi_du=sentence.strip(), nguon_du_lieu="gemini"))
                    examples_created += 1

        # Đánh dấu đã duyệt
        p.trang_thai = TrangThaiDuyet.da_duyet
        db.commit()

        return ApproveResponse(
            success=True,
            message=f"Đã duyệt '{word_name}' ({phonetic}) — {definition}",
            prediction_id=prediction_id,
            object_id=obj.id,
            translation_id=translation.id,
            examples_created=examples_created,
        )

    # ------------------------------------------------------------------
    # Reject
    # ------------------------------------------------------------------

    def reject_prediction(self, db: Session, prediction_id: int) -> RejectResponse:
        p = db.query(AIPrediction).filter(AIPrediction.id == prediction_id).first()
        if not p:
            return RejectResponse(success=False, message="Không tìm thấy prediction", prediction_id=prediction_id)

        if p.trang_thai != TrangThaiDuyet.cho_duyet:
            return RejectResponse(
                success=False,
                message=f"Prediction đã ở trạng thái '{p.trang_thai.value}'",
                prediction_id=prediction_id,
            )

        p.trang_thai = TrangThaiDuyet.tu_choi
        db.commit()

        return RejectResponse(success=True, message=f"Đã từ chối prediction #{prediction_id}", prediction_id=prediction_id)
