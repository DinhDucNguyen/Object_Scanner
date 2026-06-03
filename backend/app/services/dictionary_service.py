import httpx
from copy import deepcopy
from datetime import datetime, timedelta
from typing import Optional

from sqlalchemy import func, or_
from sqlalchemy.orm import Session

from app.models.dictionary_lookup import DictionaryLookup, NguonTraCuu
from app.models.language import Language
from app.models.object import Object
from app.models.object_alias import ObjectAlias
from app.models.translation import Translation
from app.repositories.object_repo import normalize_object_code
from app.services.gemini_service import GeminiService
from app.utils.timezone import now_vietnam


class DictionaryService:
    def __init__(self):
        self._cache: dict[tuple[str, str, str], tuple[datetime, dict]] = {}
        self._cache_ttl = timedelta(minutes=10)
        self._gemini = GeminiService()

    # ------------------------------------------------------------------
    # Public
    # ------------------------------------------------------------------

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
            self._upsert_lookup(db, user_id, normalized, from_lang, to_lang, cached)
            db.commit()
            return cached

        # 1. Tìm thẳng trong DB
        found = self._find_existing_translation(db, normalized, from_lang, to_lang)
        if found:
            obj, matched_trans, target_trans = found
            response = self._response_from_translation(
                normalized, from_lang, to_lang, obj, matched_trans, target_trans
            )
            self._upsert_lookup(db, user_id, normalized, from_lang, to_lang, response, obj.id)
            db.commit()
            self._set_cached(cache_key, response)
            return response

        # 2. Nếu tiếng Việt → tìm trong DB bằng dinh_nghia (phần trước ":") trước
        if from_lang == "vi":
            found = self._find_by_vi_definition(db, normalized, to_lang)
            if found:
                obj, matched_trans, target_trans = found
                response = self._response_from_translation(
                    normalized, from_lang, to_lang, obj, matched_trans, target_trans
                )
                self._upsert_lookup(db, user_id, normalized, from_lang, to_lang, response, obj.id)
                db.commit()
                self._set_cached(cache_key, response)
                return response

            # Nếu không thấy trong DB → dịch sang EN qua Gemini rồi tìm lại
            en_text = self._translate_gemini(normalized, "vi", "en")
            if en_text:
                for candidate in self._singular_candidates(en_text):
                    found = self._find_existing_translation(db, candidate, "en", to_lang)
                    if found:
                        obj, matched_trans, target_trans = found
                        response = self._response_from_translation(
                            normalized, from_lang, to_lang, obj, matched_trans, target_trans
                        )
                        self._upsert_lookup(db, user_id, normalized, from_lang, to_lang, response, obj.id)
                        db.commit()
                        self._set_cached(cache_key, response)
                        return response

        # 3. Không có trong DB → dịch bằng Gemini
        translation = self._translate_gemini(normalized, from_lang, to_lang)
        if not translation:
            return {"_error": "service_unavailable"}

        # Nếu dịch sang EN, thử tìm dạng số ít trong DB để lấy canonical form
        if to_lang == "en":
            for candidate in self._singular_candidates(translation):
                found = self._find_existing_translation(db, candidate, "en", "en")
                if found:
                    obj, matched_trans, _ = found
                    translation = matched_trans.tu_vung or candidate
                    break

        if to_lang == "en":
            phonetic = self._fetch_phonetic(translation, "en")
        else:
            phonetic = self._fetch_phonetic(normalized, from_lang)
        response = {
            "original": normalized,
            "translation": translation,
            "phonetic": phonetic,
            "definitions": [],
            "from_lang": from_lang,
            "to_lang": to_lang,
            "source": "gemini",
            "is_physical_object": False,
            "object_id": None,
            "object_code": None,
            "translation_id": None,
            "can_save": False,
            "pending_review": False,
        }
        # Chỉ cache từ/cụm từ ngắn — đoạn văn dài thì dịch xong trả về, không lưu DB
        if len(normalized) <= 200:
            self._upsert_lookup(
                db, user_id, normalized, from_lang, to_lang, response,
                translation_text=translation, phonetic=phonetic,
            )
            db.commit()
            self._set_cached(cache_key, response)
        return response

    # ------------------------------------------------------------------
    # History
    # ------------------------------------------------------------------

    def delete_history_item(self, db: Session, user_id: int, item_id: int) -> bool:
        item = (
            db.query(DictionaryLookup)
            .filter(DictionaryLookup.id == item_id, DictionaryLookup.user_id == user_id)
            .first()
        )
        if not item:
            return False
        db.delete(item)
        db.commit()
        return True

    def get_history(self, db: Session, user_id: int, limit: int = 30) -> list[dict]:
        from app.models.object_media import ObjectMedia

        rows = (
            db.query(DictionaryLookup)
            .filter(DictionaryLookup.user_id == user_id)
            .order_by(DictionaryLookup.lan_tra_cuoi.desc())
            .limit(limit)
            .all()
        )

        result = []
        for lookup in rows:
            image_url = None
            translation_text = lookup.ket_qua_dich
            phonetic = lookup.phien_am
            object_code = None

            if lookup.doi_tuong_id:
                obj = db.query(Object).filter(Object.id == lookup.doi_tuong_id).first()
                if obj:
                    object_code = obj.ma_doi_tuong
                    media = (
                        db.query(ObjectMedia)
                        .filter(
                            ObjectMedia.doi_tuong_id == obj.id,
                            ObjectMedia.thoi_gian_xoa.is_(None),
                        )
                        .order_by(ObjectMedia.doi_tuong_chinh.desc())
                        .first()
                    )
                    image_url = media.url if media else None

                    to_lang = (lookup.den_ngon_ngu_obj.ma_ngon_ngu if lookup.den_ngon_ngu_obj else None) or "vi"
                    target = self._find_target_translation(db, obj.id, to_lang)
                    if not target:
                        target = (
                            db.query(Translation)
                            .filter(
                                Translation.doi_tuong_id == obj.id,
                                Translation.thoi_gian_xoa.is_(None),
                            )
                            .first()
                        )
                    if target:
                        translation_text = (
                            target.dinh_nghia if to_lang == "vi" else target.tu_vung
                        ) or target.dinh_nghia or target.tu_vung or translation_text
                        phonetic = target.phien_am or phonetic

            result.append({
                "id": lookup.id,
                "tu_tra": lookup.tu_tra,
                "ket_qua_dich": translation_text,
                "phien_am": phonetic,
                "lan_tra_cuoi": lookup.lan_tra_cuoi.isoformat() if lookup.lan_tra_cuoi else None,
                "doi_tuong_id": lookup.doi_tuong_id,
                "object_code": object_code,
                "image_url": image_url,
                "den_ngon_ngu": (lookup.den_ngon_ngu_obj.ma_ngon_ngu if lookup.den_ngon_ngu_obj else None) or "vi",
            })
        return result

    # ------------------------------------------------------------------
    # DB lookup helpers
    # ------------------------------------------------------------------

    def _find_existing_translation(
        self, db: Session, text: str, from_lang: str, to_lang: str
    ) -> tuple[Object, Translation, Translation] | None:
        code = normalize_object_code(text)
        text_lower = text.lower()
        like_text = f"%{text_lower}%"

        query = (
            db.query(Object, Translation)
            .join(Translation, Translation.doi_tuong_id == Object.id)
            .outerjoin(ObjectAlias, ObjectAlias.doi_tuong_id == Object.id)
            .outerjoin(Language, Language.id == Translation.ngon_ngu_id)
            .filter(Object.thoi_gian_xoa.is_(None))
            .filter(Translation.thoi_gian_xoa.is_(None))
            .filter(self._source_match_filter(from_lang, code, text_lower, like_text))
        )
        if from_lang:
            query = query.filter(
                (Language.ma_ngon_ngu == from_lang) | (Language.ma_ngon_ngu.is_(None))
            )

        found = query.first()

        if not found:
            return None

        obj, matched_trans = found
        target_trans = self._find_target_translation(db, obj.id, to_lang) or matched_trans
        return obj, matched_trans, target_trans

    def _source_match_filter(self, from_lang: str, code: str, text_lower: str, like_text: str):
        if from_lang == "vi":
            return func.lower(Translation.tu_vung) == text_lower
        return or_(
            func.lower(Object.ma_doi_tuong) == code,
            func.lower(ObjectAlias.ma_bi_danh) == code,
            func.lower(Translation.tu_vung) == text_lower,
        )

    def _find_by_vi_definition(
        self, db: Session, vi_text: str, to_lang: str
    ) -> tuple[Object, Translation, Translation] | None:
        """Tìm object bằng phần tên tiếng Việt trong dinh_nghia (trước dấu ':')."""
        text_lower = vi_text.strip().lower()
        rows = (
            db.query(Object, Translation)
            .join(Translation, Translation.doi_tuong_id == Object.id)
            .filter(Object.thoi_gian_xoa.is_(None))
            .filter(Translation.thoi_gian_xoa.is_(None))
            .filter(Translation.dinh_nghia.isnot(None))
            .all()
        )
        for obj, trans in rows:
            dinh_nghia = (trans.dinh_nghia or "").strip()
            vi_name = dinh_nghia.split(":")[0].strip().lower() if ":" in dinh_nghia else dinh_nghia.lower()
            if vi_name == text_lower:
                target_trans = self._find_target_translation(db, obj.id, to_lang) or trans
                return obj, trans, target_trans
        return None

    def _find_target_translation(
        self, db: Session, object_id: int, to_lang: str
    ) -> Translation | None:
        return (
            db.query(Translation)
            .join(Language, Language.id == Translation.ngon_ngu_id)
            .filter(Translation.doi_tuong_id == object_id)
            .filter(Translation.thoi_gian_xoa.is_(None))
            .filter(Language.ma_ngon_ngu == to_lang)
            .first()
        )

    def _response_from_translation(
        self,
        original: str,
        from_lang: str,
        to_lang: str,
        obj: Object,
        matched_trans: Translation,
        target_trans: Translation,
    ) -> dict:
        dinh_nghia = (target_trans.dinh_nghia or "").strip()

        if to_lang == "en":
            translation_text = target_trans.tu_vung or obj.ma_doi_tuong or matched_trans.tu_vung or ""
        elif to_lang == "vi":
            # dinh_nghia format: "tên tiếng Việt: mô tả..." hoặc chỉ là mô tả
            if ":" in dinh_nghia:
                translation_text = dinh_nghia.split(":")[0].strip() or dinh_nghia
            else:
                translation_text = dinh_nghia or obj.ma_doi_tuong or ""
        else:
            translation_text = target_trans.tu_vung or dinh_nghia or ""

        definitions = []
        if to_lang == "vi":
            if ":" in dinh_nghia:
                desc = dinh_nghia.split(":", 1)[1].strip()
                if desc:
                    definitions.append(desc)
            elif dinh_nghia:
                definitions.append(dinh_nghia)
        else:
            if dinh_nghia:
                definitions.append(dinh_nghia)
        definitions.extend(
            [ex.cau_vi_du for ex in (target_trans.examples or []) if ex.cau_vi_du][:2]
        )

        return {
            "original": original,
            "translation": translation_text,
            "phonetic": target_trans.phien_am,
            "definitions": definitions,
            "from_lang": from_lang,
            "to_lang": to_lang,
            "source": "internal_db",
            "is_physical_object": True,
            "object_id": obj.id,
            "object_code": obj.ma_doi_tuong,
            "translation_id": target_trans.id,
            "can_save": True,
            "pending_review": not bool(target_trans.da_xac_nhan),
        }

    # ------------------------------------------------------------------
    # Gemini translate + phonetic
    # ------------------------------------------------------------------

    def _singular_candidates(self, word: str) -> list[str]:
        """Trả về [word, dạng số ít có thể] để thử lookup DB."""
        w = word.strip().lower()
        candidates = [w]
        if w.endswith("ies") and len(w) > 4:
            candidates.append(w[:-3] + "y")
        elif w.endswith("ves") and len(w) > 4:
            candidates.append(w[:-3] + "f")
        elif w.endswith("es") and len(w) > 4:
            candidates.append(w[:-2])
        elif w.endswith("s") and len(w) > 3:
            candidates.append(w[:-1])
        return candidates

    def _translate_gemini(self, text: str, from_lang: str, to_lang: str) -> str | None:
        result = self._gemini.translate_text(text, from_lang, to_lang)
        if not result or "_error" in result:
            return None
        translation = (result.get("translation") or "").strip()
        return translation if translation and translation.lower() != text.lower() else None

    def _fetch_phonetic(self, word: str, lang: str) -> str | None:
        """IPA từ dictionaryapi.dev — chỉ cho EN từ đơn."""
        if lang != "en" or " " in word.strip() or len(word) > 40:
            return None
        try:
            with httpx.Client(timeout=4.0) as client:
                resp = client.get(
                    f"https://api.dictionaryapi.dev/api/v2/entries/en/{word.lower().strip()}"
                )
            if resp.status_code != 200:
                return None
            data = resp.json()
            if not data or not isinstance(data, list):
                return None
            entry = data[0]
            phonetic = entry.get("phonetic", "")
            if not phonetic:
                for p in entry.get("phonetics", []):
                    if p.get("text"):
                        phonetic = p["text"]
                        break
            return phonetic or None
        except Exception:
            return None

    # ------------------------------------------------------------------
    # Upsert history
    # ------------------------------------------------------------------

    def _upsert_lookup(
        self,
        db: Session,
        user_id: Optional[int],
        term: str,
        from_lang: str,
        to_lang: str,
        response: dict,
        object_id: Optional[int] = None,
        translation_text: Optional[str] = None,
        phonetic: Optional[str] = None,
    ) -> None:
        if not user_id:
            return

        lang = db.query(Language).filter(Language.ma_ngon_ngu == from_lang).first()
        to_lang_obj = db.query(Language).filter(Language.ma_ngon_ngu == to_lang).first()
        existing = (
            db.query(DictionaryLookup)
            .filter(
                DictionaryLookup.user_id == user_id,
                func.lower(DictionaryLookup.tu_tra) == term.lower(),
            )
            .first()
        )

        trans_text = translation_text or response.get("translation") or ""
        ipa = phonetic or response.get("phonetic")
        obj_id = object_id or response.get("object_id")
        # TraTuDien currently stores external dictionary fallback under the legacy
        # `gemini` enum value; API responses still expose the concrete provider.
        source = (
            NguonTraCuu.he_thong
            if response.get("source") == "internal_db"
            else NguonTraCuu.gemini
        )

        if existing:
            existing.lan_tra_cuoi = now_vietnam()
            existing.doi_tuong_id = obj_id
            existing.den_ngon_ngu_id = to_lang_obj.id if to_lang_obj else None
            if not obj_id:
                existing.ket_qua_dich = trans_text[:500] if trans_text else None
                existing.phien_am = ipa[:100] if ipa else None
        else:
            db.add(DictionaryLookup(
                user_id=user_id,
                tu_tra=term,
                ngon_ngu_tra_id=lang.id if lang else None,
                doi_tuong_id=obj_id,
                nguon_du_lieu=source,
                lan_tra_cuoi=now_vietnam(),
                den_ngon_ngu_id=to_lang_obj.id if to_lang_obj else None,
                ket_qua_dich=(trans_text[:500] if trans_text and not obj_id else None),
                phien_am=(ipa[:100] if ipa and not obj_id else None),
            ))

    # ------------------------------------------------------------------
    # Cache
    # ------------------------------------------------------------------

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
