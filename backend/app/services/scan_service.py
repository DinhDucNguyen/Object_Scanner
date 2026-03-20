from sqlalchemy.orm import Session
from app.models.object import Object
from app.models.translation import Translation
from app.models.language import Language
from app.models.scan_history import ScanHistory
from app.repositories.object_repo import ObjectRepository
from app.repositories.translation_repo import TranslationRepository
from app.repositories.language_repo import LanguageRepository
from app.repositories.history_repo import HistoryRepository
from app.services.gemini_service import GeminiService
from app.schemas.common import ScanRequest, ScanResponse, TranslationResponse


class ScanService:
    def __init__(self):
        self.obj_repo = ObjectRepository()
        self.trans_repo = TranslationRepository()
        self.lang_repo = LanguageRepository()
        self.hist_repo = HistoryRepository()
        self.gemini = GeminiService()

    def process_scan(self, db: Session, request: ScanRequest) -> ScanResponse:
        """Scan bằng object_code (từ ML Kit/YOLOv8 on-device)."""
        obj = self.obj_repo.get_by_code(db, request.object_code)
        user_id = request.user_id if request.user_id else 1  # Default to anonymous user

        if obj:
            translations = self.trans_repo.get_by_object_id(db, obj.id)
            self.hist_repo.create_scan(db, ScanHistory(
                user_id=user_id, object_id=obj.id,
                confidence_score=request.confidence,
                image_captured_url=request.image_captured_url,
                device_model=request.device_model
            ))
            return ScanResponse(
                source="internal_db", object_id=obj.id,
                object_code=obj.object_code, category_name=obj.category.name if obj.category else None,
                difficulty_level=obj.difficulty_level,
                translations=[self._to_dto(db, t) for t in translations]
            )

        # Object chưa có trong DB → tạo mới (trống, chờ Gemini bổ sung)
        new_obj = self.obj_repo.create(db, Object(object_code=request.object_code.lower(), difficulty_level=1))
        self.hist_repo.create_scan(db, ScanHistory(
            user_id=user_id, object_id=new_obj.id,
            confidence_score=request.confidence, device_model=request.device_model
        ))
        
        return ScanResponse(
            source="new_object", object_id=new_obj.id,
            object_code=new_obj.object_code, difficulty_level=1, translations=[]
        )

    def process_scan_image(self, db: Session, image_bytes: bytes, user_id: int | None) -> ScanResponse:
        """
        Scan bằng ảnh — gọi Gemini Vision API.
        1. Gemini nhận diện vật thể
        2. Check DB xem đã có chưa
        3. Nếu chưa → auto-save Object + Translations
        4. Trả ScanResponse
        """
        user_id = user_id if user_id else 1  # Default to anonymous user

        gemini_result = self.gemini.identify_object(image_bytes)
        if not gemini_result:
            return ScanResponse(
                source="gemini_failed", object_id=0,
                object_code="unknown", difficulty_level=1, translations=[]
            )

        if gemini_result.get("_error") == "quota_exceeded":
            return ScanResponse(
                source="gemini_quota_exceeded", object_id=0,
                object_code="quota_exceeded", difficulty_level=1, translations=[]
            )

        if gemini_result.get("_error"):
            return ScanResponse(
                source="gemini_failed", object_id=0,
                object_code="unknown", difficulty_level=1, translations=[]
            )

        object_code = gemini_result.get("object_code", "unknown").lower()
        
        # Check DB
        obj = self.obj_repo.get_by_code(db, object_code)
        
        if obj:
            # Đã có trong DB
            translations = self.trans_repo.get_by_object_id(db, obj.id)
            source = "internal_db"
        else:
            # Tạo mới từ Gemini result
            obj = self.obj_repo.create(db, Object(
                object_code=object_code, difficulty_level=1
            ))
            source = "gemini_api"
            
            # Auto-save translations từ Gemini
            translations = []
            for t_data in gemini_result.get("translations", []):
                lang = self._ensure_language(db, t_data.get("lang_code", "en"))
                
                example_sentences = t_data.get("example_sentences", [])
                example_text = " | ".join(example_sentences[:3]) if example_sentences else None
                
                trans = Translation(
                    object_id=obj.id,
                    language_id=lang.id,
                    word_name=t_data.get("word_name", object_code),
                    phonetic=t_data.get("phonetic"),
                    definition=t_data.get("definition"),
                    example_sentence=example_text,
                )
                db.add(trans)
                db.flush()
                translations.append(trans)
            
            db.commit()

        # Lưu scan history
        scan = ScanHistory(user_id=user_id, object_id=obj.id, confidence_score=1.0)
        self.hist_repo.create_scan(db, scan)

        return ScanResponse(
            source=source, object_id=obj.id,
            object_code=obj.object_code, difficulty_level=obj.difficulty_level,
            translations=[self._to_dto(db, t) for t in translations]
        )

    def get_translations_by_object_code(self, db: Session, object_code: str):
        obj = self.obj_repo.get_by_code(db, object_code)
        if not obj:
            return None
        translations = self.trans_repo.get_by_object_id(db, obj.id)
        return [self._to_dto(db, t) for t in translations]

    def _ensure_language(self, db: Session, lang_code: str) -> Language:
        """Đảm bảo language tồn tại trong DB, tạo mới nếu chưa có."""
        lang = db.query(Language).filter(Language.code == lang_code).first()
        if not lang:
            lang_names = {"en": "English", "vi": "Vietnamese", "ja": "Japanese", "ko": "Korean"}
            lang = Language(code=lang_code, name=lang_names.get(lang_code, lang_code.upper()), is_active=True)
            db.add(lang)
            db.flush()
        return lang

    def _to_dto(self, db: Session, t) -> TranslationResponse:
        lang = self.lang_repo.get_by_id(db, t.language_id)
        return TranslationResponse(
            id=t.id, object_id=t.object_id, language_id=t.language_id,
            language_code=lang.code if lang else "", language_name=lang.name if lang else "",
            word_name=t.word_name, phonetic=t.phonetic,
            definition=t.definition, example_sentence=t.example_sentence
        )
