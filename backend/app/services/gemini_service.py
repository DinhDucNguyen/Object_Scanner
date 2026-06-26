import json
import logging
import re
from google import genai
from google.genai import types
from google.api_core.exceptions import ResourceExhausted
from app.core.config import settings

logger = logging.getLogger(__name__)


class GeminiService:
    def __init__(self):
        # Quick scan — chỉ nhận diện object_code/confidence, ưu tiên model nhẹ để tránh tốn thời gian.
        self.models_to_try = [
            "gemini-3.1-flash-lite",
        ]
        # Full identify — dùng khi confidence thấp hoặc DB miss cần sinh vocab đầy đủ.
        self.vision_full_models = [
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash",
            "gemini-2.5-flash",
            "gemini-3-flash-preview",
        ]
        # Text-only (translate, dictionary, example) — nhẹ trước, giữ 2.5 flash cuối vì hay quá tải.
        self.translate_models = [
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-2.5-flash",
            "gemini-3-flash-preview",
        ]
        self.client: genai.Client | None = None

        if settings.GEMINI_API_KEY:
            self.client = genai.Client(api_key=settings.GEMINI_API_KEY)
            logger.debug("Gemini client initialized")
        else:
            logger.warning("No GEMINI_API_KEY configured — Gemini disabled")

    def _generate(self, contents: list, models: list[str] | None = None) -> str | None:
        """Gọi Gemini với fallback qua từng model, trả về response.text."""
        if not self.client:
            return None
        last_error: Exception | None = None
        for model_name in (models or self.models_to_try):
            try:
                response = self.client.models.generate_content(
                    model=model_name,
                    contents=contents,
                )
                logger.debug("Gemini model used: %s", model_name)
                return response.text
            except ResourceExhausted as e:
                logger.warning("Model %s quota exhausted", model_name)
                last_error = e
            except Exception as e:
                logger.warning("Model %s error: %s", model_name, e)
                last_error = e
        if last_error:
            raise last_error
        return None

    def _parse_json(self, text: str) -> dict | list | None:
        """Strip markdown fences và parse JSON."""
        text = text.strip()
        if text.startswith("```"):
            text = text.split("\n", 1)[1] if "\n" in text else text
            text = text.rsplit("```", 1)[0].strip()
        first, last = text.find("{"), text.rfind("}")
        if first != -1 and last != -1 and last > first:
            try:
                return json.loads(text[first:last + 1])
            except Exception:
                pass
        first, last = text.find("["), text.rfind("]")
        if first != -1 and last != -1 and last > first:
            try:
                return json.loads(text[first:last + 1])
            except Exception:
                pass
        return None

    def identify_object_quick(self, image_bytes: bytes) -> dict:
        """Bước 1: nhận diện nhanh — chỉ trả object_name, confidence, is_clear.
        Dùng model nhẹ/quota cao trước để tiết kiệm RPD."""
        if not self.client:
            return {"_error": "model_not_initialized"}

        prompt = """Look at this image and identify the main object.
Return ONLY valid JSON, no markdown:
{
  "object_code": "english_name_lowercase_underscores",
  "confidence": 90,
  "is_clear": true
}
Rules:
- object_code: lowercase English, underscores for spaces (e.g. "water_bottle")
- confidence: integer 0-100, how certain you are about the identification
- is_clear: true if the object is clearly visible and unambiguous, false if blurry/partial/uncertain
Return ONLY JSON."""

        try:
            image_part = types.Part.from_bytes(data=image_bytes, mime_type="image/webp")
            text = self._generate([prompt, image_part])
            if not text:
                return {"_error": "empty_response"}
            result = self._parse_json(text)
            if not isinstance(result, dict):
                return {"_error": "parse_error"}
            return {
                "object_code": self._normalize_object_code(result.get("object_code") or ""),
                "confidence": int(result.get("confidence") or 0),
                "is_clear": bool(result.get("is_clear", True)),
            }
        except ResourceExhausted as e:
            return {"_error": "quota_exceeded", "_message": str(e)}
        except Exception as e:
            logger.error("identify_object_quick error: %s", e)
            return {"_error": "api_error", "_message": str(e)}

    def identify_object(self, image_bytes: bytes, categories_str: str = "") -> dict:
        """Bước 2: nhận diện đầy đủ — trả object + toàn bộ vocab.
        Gọi khi quick check thất bại hoặc object chưa có trong DB."""
        if not self.client:
            return {"_error": "model_not_initialized", "_message": "Gemini not configured"}

        logger.debug("Calling Gemini API with image size: %d bytes", len(image_bytes))

        cat_list = categories_str if categories_str else "Con người, Phương tiện, Động vật, Phụ kiện, Nhà bếp, Thực phẩm, Nội thất, Đồ công nghệ, Đồ dùng học tập, Biển báo & đô thị"

        prompt = f"""Analyze this image and identify the main object. Return a JSON object with this exact format:
{{
    "object_code": "english_name_lowercase_no_spaces",
    "category": "one category from the list below",
    "translations": [
        {{
            "lang_code": "en",
            "word_name": "English name",
            "phonetic": "/IPA transcription/",
            "part_of_speech": "n",
            "definition": "từ tiếng Việt: giải thích ngắn gọn bằng tiếng Việt",
            "example_sentences": [
                {{"en": "Example sentence 1 using the word.", "vi": "Câu ví dụ 1 bằng tiếng Việt."}},
                {{"en": "Example sentence 2 using the word.", "vi": "Câu ví dụ 2 bằng tiếng Việt."}},
                {{"en": "Example sentence 3 using the word.", "vi": "Câu ví dụ 3 bằng tiếng Việt."}}
            ]
        }}
    ]
}}

Rules:
- object_code: lowercase English, underscores for spaces (e.g., "water_bottle")
- category: choose EXACTLY one from this list (use the exact Vietnamese name): {cat_list}
- phonetic: IPA format with slashes (e.g., /ˈæp.əl/)
- part_of_speech: use abbreviations ONLY — n (noun), v (verb), adj (adjective), adv (adverb), prep (preposition), conj (conjunction), pron (pronoun), interj (interjection)
- definition: format MUST be "Vietnamese word: brief Vietnamese explanation" (e.g., "máy tính xách tay: thiết bị điện tử cầm tay dùng để làm việc")
- example_sentences: EXACTLY 3 objects, each with "en" (English sentence) and "vi" (Vietnamese translation), simple and suitable for language learners
- Return ONLY valid JSON, no markdown, no extra text"""

        try:
            image_part = types.Part.from_bytes(data=image_bytes, mime_type="image/webp")
            text = self._generate([prompt, image_part], models=self.vision_full_models)

            if not text:
                return {"_error": "empty_response", "_message": "Gemini returned empty response"}

            logger.debug("Gemini response: %s...", text[:200])
            result = self._parse_json(text)

            if isinstance(result, dict):
                result = self._sanitize_result(result)
                logger.debug("Parsed object_code: %s", result.get("object_code", "N/A"))
                return result

            # Fallback regex parse
            object_code_match = re.search(r'"object_code"\s*:\s*"([^"]+)"', text)
            word_name_match = re.search(r'"word_name"\s*:\s*"([^"]+)"', text)
            object_code = self._normalize_object_code(
                object_code_match.group(1) if object_code_match else (
                    word_name_match.group(1) if word_name_match else "unknown"
                )
            )
            fallback = {
                "object_code": object_code,
                "category": "unknown",
                "translations": [{
                    "lang_code": "en",
                    "word_name": word_name_match.group(1) if word_name_match else object_code.replace("_", " ").title(),
                    "phonetic": None,
                    "definition": None,
                    "example_sentences": [],
                }],
            }
            logger.warning("Fallback parsed object_code: %s", object_code)
            return self._sanitize_result(fallback)

        except ResourceExhausted as e:
            logger.error("Gemini quota exceeded: %s", e)
            return {"_error": "quota_exceeded", "_message": str(e)}
        except Exception as e:
            logger.error("Gemini API error: %s", e, exc_info=True)
            return {"_error": "api_error", "_message": str(e)}

    def generate_vocab_for_object_code(self, object_code: str, categories_str: str = "") -> dict:
        """Sinh dữ liệu từ vựng cho object_code bằng text prompt (không cần ảnh)."""
        if not self.client:
            return {"_error": "no_model", "_message": "Gemini not configured"}

        display_name = object_code.replace("_", " ")
        cat_list = categories_str if categories_str else "Con người, Phương tiện, Động vật, Phụ kiện, Nhà bếp, Thực phẩm, Nội thất, Đồ công nghệ, Đồ dùng học tập, Biển báo & đô thị"

        prompt = f"""Generate vocabulary data for the object: "{display_name}"

Return a JSON object with this exact format:
{{
    "object_code": "{object_code}",
    "category": "one category from the list below",
    "translations": [
        {{
            "lang_code": "en",
            "word_name": "English name",
            "phonetic": "/IPA/",
            "part_of_speech": "n",
            "definition": "Vietnamese word: brief Vietnamese explanation",
            "example_sentences": [
                {{"en": "sentence 1", "vi": "dich 1"}},
                {{"en": "sentence 2", "vi": "dich 2"}},
                {{"en": "sentence 3", "vi": "dich 3"}}
            ]
        }},
        {{
            "lang_code": "vi",
            "word_name": "Ten tieng Viet",
            "phonetic": null,
            "part_of_speech": "n",
            "definition": "ten Viet: giai thich ngan tieng Viet",
            "example_sentences": [
                {{"en": "sentence 1", "vi": "dich 1"}},
                {{"en": "sentence 2", "vi": "dich 2"}},
                {{"en": "sentence 3", "vi": "dich 3"}}
            ]
        }}
    ]
}}

Rules:
- object_code: keep exactly as "{object_code}"
- category: choose EXACTLY one from this list (use the exact Vietnamese name): {cat_list}
- phonetic: IPA format with slashes (e.g., /ap.el/), null if unknown
- part_of_speech: n/v/adj/adv only
- example_sentences: EXACTLY 3 per language
Return ONLY valid JSON, no markdown."""

        try:
            text = self._generate([prompt])
            if not text:
                return {"_error": "empty_response", "_message": "Empty response"}
            result = self._parse_json(text)
            if not isinstance(result, dict):
                return {"_error": "parse_error", "_message": "No JSON in response"}
            result = self._sanitize_result(result)
            result["object_code"] = object_code
            return result
        except ResourceExhausted as e:
            return {"_error": "quota_exceeded", "_message": str(e)}
        except Exception as e:
            logger.error("Error generating vocab for %s: %s", object_code, e)
            return {"_error": "api_error", "_message": str(e)}

    def translate_text(self, text: str, from_lang: str = "en", to_lang: str = "vi") -> dict | None:
        if not self.client:
            return None
        lang_names = {
            "vi": "Vietnamese", "en": "English",
            "ja": "Japanese", "ko": "Korean",
            "zh": "Chinese", "fr": "French",
        }
        from_name = lang_names.get(from_lang, from_lang.upper())
        to_name = lang_names.get(to_lang, to_lang.upper())
        is_single = len(text.strip().split()) == 1

        prompt = f"""Translate from {from_name} to {to_name}.
Return ONLY valid JSON:
{{
  "translation": "translated text",
  "phonetic": {"'IPA phonetic of the translation'" if is_single else "null"},
  "definitions": {"['brief definition 1', 'brief definition 2']" if is_single else "[]"}
}}
Text: {text}"""

        try:
            raw = self._generate([prompt], models=self.translate_models)
            if not raw:
                return None
            result = self._parse_json(raw)
            if not isinstance(result, dict):
                return None
            return {
                "translation": result.get("translation", ""),
                "phonetic": result.get("phonetic"),
                "definitions": result.get("definitions", []),
            }
        except ResourceExhausted:
            return {"_error": "quota_exceeded"}
        except Exception as e:
            logger.error("Gemini translate error: %s", e)
            return None

    def analyze_dictionary_text(self, text: str, from_lang: str = "en", to_lang: str = "vi") -> dict | None:
        if not self.client:
            return None

        lang_names = {
            "vi": "Vietnamese", "en": "English",
            "ja": "Japanese", "ko": "Korean",
            "zh": "Chinese", "fr": "French",
        }
        from_name = lang_names.get(from_lang, from_lang.upper())
        to_name = lang_names.get(to_lang, to_lang.upper())

        prompt = f"""You are powering a language-learning dictionary.
Analyze this input and return ONLY valid JSON:
{{
  "original": "normalized original text",
  "translation": "translation in {to_name}",
  "phonetic": "IPA for the original single word, or null",
  "definitions": ["brief learner-friendly definition 1", "brief definition 2"],
  "is_physical_object": true,
  "object_code": "lowercase_english_object_name_with_underscores_or_null",
  "category": "category name or null",
  "image_url_suggestion": null,
  "translations": [
    {{
      "lang_code": "{from_lang}",
      "word_name": "display word",
      "phonetic": "IPA or null",
      "part_of_speech": "n | v | adj | adv | prep | conj | pron | interj",
      "definition": "short {to_name} meaning/definition",
      "example_sentences": ["simple sentence 1", "simple sentence 2", "simple sentence 3"]
    }}
  ]
}}

Rules:
- is_physical_object is true only for concrete visible physical objects that can be photographed as a single main object.
- For verbs, adjectives, abstract nouns, people names, places, and full sentences, is_physical_object must be false and object_code must be null.
- If is_physical_object is true, object_code must be English lowercase with underscores.
- Return exactly JSON, no markdown.

Source language: {from_name}
Target language: {to_name}
Input: {text}"""

        try:
            raw = self._generate([prompt], models=self.translate_models)
            if not raw:
                return None
            result = self._parse_json(raw)
            if not isinstance(result, dict):
                return None
            result["object_code"] = self._normalize_object_code(result.get("object_code") or "")
            if result["object_code"] == "unknown":
                result["object_code"] = None
            result["translations"] = result.get("translations") if isinstance(result.get("translations"), list) else []
            result["definitions"] = result.get("definitions") if isinstance(result.get("definitions"), list) else []
            result["is_physical_object"] = bool(result.get("is_physical_object"))
            return result
        except ResourceExhausted:
            return {"_error": "quota_exceeded"}
        except Exception as e:
            logger.error("Gemini dictionary analysis error: %s", e)
            return None

    def get_example_sentences(self, word: str, lang_code: str = "en", count: int = 3) -> list:
        """Sinh câu ví dụ cho một từ vựng."""
        if not self.client:
            return []

        prompt = f"""Generate exactly {count} example sentences using the word "{word}" in English.
Each sentence should be simple and suitable for language learners.
Return a JSON array of strings, like: ["sentence 1", "sentence 2", "sentence 3"]
Return ONLY valid JSON, no markdown or extra text."""

        try:
            text = self._generate([prompt], models=self.translate_models)
            if not text:
                return []
            result = self._parse_json(text)
            if not isinstance(result, list):
                return []
            return [str(s).strip() for s in result if str(s).strip()][:count]
        except Exception as e:
            logger.error("Gemini example sentences error: %s", e)
            return []

    def _normalize_object_code(self, raw: str) -> str:
        if not raw:
            return "unknown"
        code = raw.lower().strip()
        code = re.sub(r"[^a-z0-9\s_-]", "", code)
        code = re.sub(r"[\s-]+", "_", code)
        code = re.sub(r"_+", "_", code).strip("_")
        return code or "unknown"

    def _sanitize_result(self, result: dict) -> dict:
        if not isinstance(result, dict):
            return {"object_code": "unknown", "category": "unknown", "translations": []}
        translations = result.get("translations")
        if not isinstance(translations, list):
            translations = []
        object_code = self._normalize_object_code(result.get("object_code", ""))
        if object_code == "unknown" and translations:
            first_word = translations[0].get("word_name") if isinstance(translations[0], dict) else None
            object_code = self._normalize_object_code(first_word or "")
        result["object_code"] = object_code
        result["translations"] = translations
        return result

