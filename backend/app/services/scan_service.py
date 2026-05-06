from sqlalchemy.orm import Session
from app.models.object import Object
from app.models.translation import Translation, NguonDuLieu
from app.models.example import ViDu
from app.models.language import Language
from app.models.scan_history import ScanHistory
from app.models.ai_feedback_report import AIPrediction, NguonAI
from app.repositories.object_repo import ObjectRepository
from app.repositories.translation_repo import TranslationRepository
from app.repositories.language_repo import LanguageRepository
from app.repositories.history_repo import HistoryRepository
from app.services.gemini_service import GeminiService
from app.schemas.common import ScanRequest, ScanResponse, TranslationResponse, ViDuResponse


class ScanService:
    def __init__(self):
        self.obj_repo = ObjectRepository()
        self.trans_repo = TranslationRepository()
        self.lang_repo = LanguageRepository()
        self.hist_repo = HistoryRepository()
        self.gemini = GeminiService()

    def process_scan(self, db: Session, request: ScanRequest) -> ScanResponse:
        obj = self.obj_repo.get_by_code(db, request.object_code)
        user_id = request.user_id if request.user_id else 1

        if obj:
            translations = self.trans_repo.get_by_object_id(db, obj.id)
            self.hist_repo.create_scan(db, ScanHistory(
                user_id=user_id, doi_tuong_id=obj.id,
                do_tin_cay=request.confidence,
                url_anh=request.image_url
            ))
            db.commit()
            return ScanResponse(
                source="internal_db", object_id=obj.id,
                object_code=obj.ma_doi_tuong,
                category_name=obj.category.ten_danh_muc if obj.category else None,
                difficulty_level=obj.muc_do_kho,
                translations=[self._to_dto(db, t) for t in translations]
            )

        new_obj = self.obj_repo.create(db, Object(ma_doi_tuong=request.object_code.lower(), muc_do_kho=1))
        self.hist_repo.create_scan(db, ScanHistory(
            user_id=user_id, doi_tuong_id=new_obj.id,
            do_tin_cay=request.confidence
        ))
        db.commit()
        return ScanResponse(
            source="new_object", object_id=new_obj.id,
            object_code=new_obj.ma_doi_tuong, difficulty_level=1, translations=[]
        )

    def process_scan_image(self, db: Session, image_bytes: bytes, user_id: int | None) -> ScanResponse:
        user_id = user_id or 1

        gemini_result = self.gemini.identify_object(image_bytes)
        if not gemini_result:
            return ScanResponse(source="gemini_failed", object_id=0, object_code="unknown", difficulty_level=1, translations=[])

        if gemini_result.get("_error") == "quota_exceeded":
            return ScanResponse(source="gemini_quota_exceeded", object_id=0, object_code="quota_exceeded", difficulty_level=1, translations=[])

        if gemini_result.get("_error"):
            return ScanResponse(source="gemini_failed", object_id=0, object_code="unknown", difficulty_level=1, translations=[])

        object_code = gemini_result.get("object_code", "unknown").lower()
        obj = self.obj_repo.get_by_code(db, object_code)

        if not obj:
            obj = self.obj_repo.create(db, Object(ma_doi_tuong=object_code, muc_do_kho=1))

        existing_translations = self.trans_repo.get_by_object_id(db, obj.id)

        if existing_translations:
            # Object đã có translation trong DB → dùng luôn
            translations = existing_translations
            source = "internal_db"
        else:
            # Object mới hoặc rỗng (do ML Kit tạo trước) → điền translation từ Gemini
            source = "gemini_api"
            translations = []
            for t_data in gemini_result.get("translations", []):
                lang = self._ensure_language(db, t_data.get("lang_code", "en"))
                trans = Translation(
                    doi_tuong_id=obj.id,
                    ngon_ngu_id=lang.id,
                    tu_vung=t_data.get("word_name", object_code),
                    phien_am=t_data.get("phonetic"),
                    loai_tu=t_data.get("part_of_speech"),
                    dinh_nghia=t_data.get("definition"),
                    nguon_du_lieu=NguonDuLieu.gemini,
                )
                db.add(trans)
                db.flush()
                for sentence in t_data.get("example_sentences", [])[:3]:
                    db.add(ViDu(ban_dich_id=trans.id, cau_vi_du=sentence, nguon_du_lieu="gemini"))
                translations.append(trans)

        scan = ScanHistory(user_id=user_id, doi_tuong_id=obj.id, do_tin_cay=1.0)
        self.hist_repo.create_scan(db, scan)
        db.flush()

        if source == "gemini_api":
            db.add(AIPrediction(
                scan_id=scan.id,
                nguon_ai=NguonAI.gemini,
                nhan_du_doan=object_code,
                do_tin_cay=gemini_result.get("confidence", 1.0),
                mo_ta=gemini_result.get("category"),
            ))

        db.commit()
        return ScanResponse(
            source=source, object_id=obj.id,
            object_code=obj.ma_doi_tuong, difficulty_level=obj.muc_do_kho,
            translations=[self._to_dto(db, t) for t in translations]
        )

    def get_translations_by_object_code(self, db: Session, object_code: str):
        obj = self.obj_repo.get_by_code(db, object_code)
        if not obj:
            return None
        translations = self.trans_repo.get_by_object_id(db, obj.id)
        return [self._to_dto(db, t) for t in translations]

    def _ensure_language(self, db: Session, lang_code: str) -> Language:
        lang = db.query(Language).filter(Language.ma_ngon_ngu == lang_code).first()
        if not lang:
            lang_names = {"en": "English", "vi": "Vietnamese"}
            lang = Language(
                ma_ngon_ngu=lang_code,
                ten_ngon_ngu=lang_names.get(lang_code, lang_code.upper()),
                dang_hoat_dong=True
            )
            db.add(lang)
            db.flush()
        return lang

    def _to_dto(self, db: Session, t: Translation) -> TranslationResponse:
        lang = self.lang_repo.get_by_id(db, t.ngon_ngu_id)
        examples = [
            ViDuResponse(id=e.id, cau_vi_du=e.cau_vi_du, dich_nghia=e.dich_nghia, nguon_du_lieu=e.nguon_du_lieu)
            for e in (t.examples or [])
        ]
        return TranslationResponse(
            id=t.id, object_id=t.doi_tuong_id, language_id=t.ngon_ngu_id,
            language_code=lang.ma_ngon_ngu if lang else "",
            language_name=lang.ten_ngon_ngu if lang else "",
            word_name=t.tu_vung, phonetic=t.phien_am,
            part_of_speech=t.loai_tu,
            definition=t.dinh_nghia,
            examples=examples,
            audio_url=t.am_thanh_url,
            data_source=t.nguon_du_lieu.value if isinstance(t.nguon_du_lieu, NguonDuLieu) else (str(t.nguon_du_lieu) if t.nguon_du_lieu else None)
        )
