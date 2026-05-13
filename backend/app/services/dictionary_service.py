import json
from copy import deepcopy
from datetime import datetime, timedelta
from typing import Optional

from sqlalchemy import func, or_
from sqlalchemy.orm import Session

from app.models.ai_feedback_report import AIPrediction, NguonAI, TrangThaiDuyet
from app.models.dictionary_lookup import DictionaryLookup, NguonTraCuu
from app.models.example import ViDu
from app.models.language import Language
from app.models.object import Object
from app.models.scan_history import ScanHistory
from app.models.translation import Translation, NguonDuLieu
from app.services.gemini_service import GeminiService
from app.services.tts_service import TTSService
from app.utils.timezone import now_vietnam


class DictionaryService:
    def __init__(self):
        self.gemini = GeminiService()
        self.tts = TTSService()
        self._cache: dict[tuple[str, str, str], tuple[datetime, dict]] = {}
        self._cache_ttl = timedelta(minutes=10)
        self._gemini_blocked_until: datetime | None = None
        self._gemini_cooldown = timedelta(minutes=15)

    def translate_or_lookup(
        self,
        db: Session,
        text: str,
        from_lang: str = "en",
        to_lang: str = "vi",
        user_id: Optional[int] = None,
    ) -> dict | None:
        normalized = text.strip()
        if not normalized:
            return None

        cache_key = (normalized.lower(), from_lang.lower(), to_lang.lower())
        cached = self._get_cached(cache_key)
        if cached:
            return cached

        found = self._find_existing_translation(db, normalized, from_lang, to_lang)
        if found:
            obj, matched_trans, target_trans = found
            self._log_lookup(db, user_id, normalized, from_lang, obj.id, NguonTraCuu.he_thong)
            db.commit()
            response = self._response_from_translation(
                normalized, from_lang, to_lang, obj, matched_trans, target_trans, "internal_db"
            )
            self._set_cached(cache_key, response)
            return response

        if self._is_gemini_blocked():
            return self._gemini_unavailable_response(normalized, from_lang, to_lang)

        ai_result = self.gemini.analyze_dictionary_text(normalized, from_lang, to_lang)
        if not ai_result:
            return self._gemini_unavailable_response(normalized, from_lang, to_lang)
        if ai_result.get("_error"):
            if ai_result.get("_error") == "quota_exceeded":
                self._block_gemini()
            return self._gemini_unavailable_response(normalized, from_lang, to_lang)

        if ai_result.get("is_physical_object") and ai_result.get("object_code"):
            response = self._persist_pending_object(db, ai_result, normalized, from_lang, to_lang, user_id)
            db.commit()
            self._set_cached(cache_key, response)
            return response

        self._log_lookup(db, user_id, normalized, from_lang, None, NguonTraCuu.gemini)
        db.commit()
        response = {
            "original": normalized,
            "translation": ai_result.get("translation", ""),
            "phonetic": ai_result.get("phonetic"),
            "definitions": ai_result.get("definitions", []),
            "from_lang": from_lang,
            "to_lang": to_lang,
            "source": "gemini_transient",
            "is_physical_object": False,
            "object_id": None,
            "object_code": None,
            "translation_id": None,
            "can_save": False,
            "pending_review": False,
        }
        self._set_cached(cache_key, response)
        return response

    def _find_existing_translation(
        self,
        db: Session,
        text: str,
        from_lang: str,
        to_lang: str,
    ) -> tuple[Object, Translation, Translation] | None:
        code = self._normalize_object_code(text)
        text_lower = text.lower()
        like_text = f"%{text_lower}%"

        query = (
            db.query(Object, Translation)
            .join(Translation, Translation.doi_tuong_id == Object.id)
            .outerjoin(Language, Language.id == Translation.ngon_ngu_id)
            .filter(Object.thoi_gian_xoa.is_(None))
            .filter(Translation.thoi_gian_xoa.is_(None))
            .filter(self._source_match_filter(from_lang, code, text_lower, like_text))
        )
        if from_lang:
            query = query.filter((Language.ma_ngon_ngu == from_lang) | (Language.ma_ngon_ngu.is_(None)))

        found = query.first()
        if not found and from_lang == "vi":
            # Most app vocabulary stores English words with Vietnamese meanings in `dinh_nghia`.
            # Allow Vietnamese input to match those meanings and return the English word.
            found = (
                db.query(Object, Translation)
                .join(Translation, Translation.doi_tuong_id == Object.id)
                .outerjoin(Language, Language.id == Translation.ngon_ngu_id)
                .filter(Object.thoi_gian_xoa.is_(None))
                .filter(Translation.thoi_gian_xoa.is_(None))
                .filter(or_(
                    func.lower(Translation.dinh_nghia).like(like_text),
                    func.lower(Translation.tu_vung).like(like_text),
                ))
                .order_by(
                    (func.lower(Translation.dinh_nghia) == text_lower).desc(),
                    (func.lower(Translation.tu_vung) == text_lower).desc(),
                )
                .first()
            )

        if not found:
            return None

        obj, matched_trans = found
        target_trans = self._find_target_translation(db, obj.id, to_lang) or matched_trans
        return obj, matched_trans, target_trans

    def _source_match_filter(self, from_lang: str, code: str, text_lower: str, like_text: str):
        if from_lang == "vi":
            return or_(
                func.lower(Translation.tu_vung) == text_lower,
                func.lower(Translation.dinh_nghia).like(like_text),
            )
        return or_(
            func.lower(Object.ma_doi_tuong) == code,
            func.lower(Translation.tu_vung) == text_lower,
        )

    def _find_target_translation(
        self,
        db: Session,
        object_id: int,
        to_lang: str,
    ) -> Translation | None:
        return (
            db.query(Translation)
            .join(Language, Language.id == Translation.ngon_ngu_id)
            .filter(Translation.doi_tuong_id == object_id)
            .filter(Translation.thoi_gian_xoa.is_(None))
            .filter(Language.ma_ngon_ngu == to_lang)
            .first()
        )

    def _persist_pending_object(
        self,
        db: Session,
        ai_result: dict,
        original: str,
        from_lang: str,
        to_lang: str,
        user_id: Optional[int],
    ) -> dict:
        object_code = ai_result["object_code"]
        obj = db.query(Object).filter(
            Object.ma_doi_tuong == object_code,
            Object.thoi_gian_xoa.is_(None),
        ).first()
        if not obj:
            obj = Object(ma_doi_tuong=object_code, muc_do_kho=1, tao_boi=user_id)
            db.add(obj)
            db.flush()

        translations = ai_result.get("translations") or []
        first_translation = translations[0] if translations else {}
        lang_code = first_translation.get("lang_code") or from_lang
        lang = self._ensure_language(db, lang_code)

        trans = db.query(Translation).filter(
            Translation.doi_tuong_id == obj.id,
            Translation.ngon_ngu_id == lang.id,
        ).first()
        if not trans:
            word_name = first_translation.get("word_name") or original
            trans = Translation(
                doi_tuong_id=obj.id,
                ngon_ngu_id=lang.id,
                tu_vung=word_name,
                phien_am=first_translation.get("phonetic") or ai_result.get("phonetic"),
                loai_tu=first_translation.get("part_of_speech") or "n",
                dinh_nghia=first_translation.get("definition") or ai_result.get("translation"),
                am_thanh_url=self.tts.get_audio_url(word_name, lang_code),
                nguon_du_lieu=NguonDuLieu.gemini,
                da_xac_nhan=False,
            )
            db.add(trans)
            db.flush()

            for sentence in (first_translation.get("example_sentences") or [])[:3]:
                if sentence and sentence.strip():
                    db.add(ViDu(ban_dich_id=trans.id, cau_vi_du=sentence.strip(), nguon_du_lieu="gemini"))
        else:
            if not trans.am_thanh_url:
                trans.am_thanh_url = self.tts.get_audio_url(trans.tu_vung or original, lang_code)
            trans.da_xac_nhan = trans.da_xac_nhan or False

        scan = ScanHistory(
            user_id=user_id,
            doi_tuong_id=obj.id,
            do_tin_cay=1.0,
            url_anh=None,
        )
        db.add(scan)
        db.flush()

        payload = dict(ai_result)
        payload["temp_object_id"] = obj.id
        payload["temp_translation_ids"] = [trans.id]
        payload["source"] = "dictionary_lookup"

        prediction = AIPrediction(
            scan_id=scan.id,
            nguon_ai=NguonAI.gemini,
            nhan_du_doan=object_code,
            do_tin_cay=1.0,
            mo_ta=json.dumps(payload, ensure_ascii=False),
            trang_thai=TrangThaiDuyet.cho_duyet,
        )
        db.add(prediction)

        self._log_lookup(db, user_id, original, from_lang, obj.id, NguonTraCuu.gemini)

        return {
            "original": original,
            "translation": ai_result.get("translation") or trans.dinh_nghia or "",
            "phonetic": trans.phien_am,
            "definitions": ai_result.get("definitions", []),
            "from_lang": from_lang,
            "to_lang": to_lang,
            "source": "gemini_pending_review",
            "is_physical_object": True,
            "object_id": obj.id,
            "object_code": obj.ma_doi_tuong,
            "translation_id": trans.id,
            "can_save": True,
            "pending_review": True,
        }

    def _response_from_translation(
        self,
        original: str,
        from_lang: str,
        to_lang: str,
        obj: Object,
        matched_trans: Translation,
        target_trans: Translation,
        source: str,
    ) -> dict:
        translation_text = self._translation_text_for_target(to_lang, obj, matched_trans, target_trans)
        definitions = []
        if target_trans.dinh_nghia:
            definitions.append(target_trans.dinh_nghia)
        definitions.extend([ex.cau_vi_du for ex in (target_trans.examples or []) if ex.cau_vi_du][:2])
        return {
            "original": original,
            "translation": translation_text,
            "phonetic": target_trans.phien_am,
            "definitions": definitions,
            "from_lang": from_lang,
            "to_lang": to_lang,
            "source": source,
            "is_physical_object": True,
            "object_id": obj.id,
            "object_code": obj.ma_doi_tuong,
            "translation_id": target_trans.id,
            "can_save": True,
            "pending_review": not bool(target_trans.da_xac_nhan),
        }

    def _translation_text_for_target(
        self,
        to_lang: str,
        obj: Object,
        matched_trans: Translation,
        target_trans: Translation,
    ) -> str:
        if to_lang == "en":
            return target_trans.tu_vung or obj.ma_doi_tuong or matched_trans.tu_vung or ""
        if to_lang == "vi":
            return target_trans.dinh_nghia or matched_trans.dinh_nghia or target_trans.tu_vung or ""
        return target_trans.tu_vung or target_trans.dinh_nghia or matched_trans.tu_vung or obj.ma_doi_tuong or ""

    def _ensure_language(self, db: Session, lang_code: str) -> Language:
        lang = db.query(Language).filter(Language.ma_ngon_ngu == lang_code).first()
        if lang:
            return lang
        names = {"en": "English", "vi": "Vietnamese", "ja": "Japanese", "ko": "Korean"}
        lang = Language(ma_ngon_ngu=lang_code, ten_ngon_ngu=names.get(lang_code, lang_code.upper()), dang_hoat_dong=True)
        db.add(lang)
        db.flush()
        return lang

    def _log_lookup(
        self,
        db: Session,
        user_id: Optional[int],
        term: str,
        lang_code: str,
        object_id: Optional[int],
        source: NguonTraCuu,
    ) -> None:
        lang = db.query(Language).filter(Language.ma_ngon_ngu == lang_code).first()
        db.add(DictionaryLookup(
            user_id=user_id,
            tu_tra=term,
            ngon_ngu_tra_id=lang.id if lang else None,
            doi_tuong_id=object_id,
            nguon_du_lieu=source,
            lan_tra_cuoi=now_vietnam(),
        ))

    def _normalize_object_code(self, text: str) -> str:
        return "_".join(text.lower().strip().replace("-", " ").split())

    def _get_cached(self, key: tuple[str, str, str]) -> dict | None:
        cached = self._cache.get(key)
        if not cached:
            return None
        created_at, value = cached
        if datetime.utcnow() - created_at > self._cache_ttl:
            self._cache.pop(key, None)
            return None
        return deepcopy(value)

    def _set_cached(self, key: tuple[str, str, str], value: dict) -> None:
        if len(self._cache) > 200:
            self._cache.clear()
        self._cache[key] = (datetime.utcnow(), deepcopy(value))

    def _is_gemini_blocked(self) -> bool:
        return bool(self._gemini_blocked_until and datetime.utcnow() < self._gemini_blocked_until)

    def _block_gemini(self) -> None:
        self._gemini_blocked_until = datetime.utcnow() + self._gemini_cooldown

    def _gemini_unavailable_response(self, original: str, from_lang: str, to_lang: str) -> dict:
        message = (
            "Chưa có dữ liệu cho từ này. AI tạm hết quota, vui lòng thử lại sau."
            if to_lang == "vi"
            else "No local entry yet. AI quota is temporarily exhausted, please try again later."
        )
        return {
            "original": original,
            "translation": message,
            "phonetic": None,
            "definitions": [],
            "from_lang": from_lang,
            "to_lang": to_lang,
            "source": "gemini_unavailable",
            "is_physical_object": False,
            "object_id": None,
            "object_code": None,
            "translation_id": None,
            "can_save": False,
            "pending_review": False,
        }
