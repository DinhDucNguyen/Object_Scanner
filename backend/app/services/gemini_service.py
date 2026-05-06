import json
import re
# TODO: Migrate to google.genai package (google-generativeai is deprecated)
# See: https://github.com/google-gemini/deprecated-generative-ai-python
import google.generativeai as genai
from google.api_core.exceptions import ResourceExhausted
from app.core.config import settings


class GeminiService:
    def __init__(self):
        self.models_to_try = [
            "gemini-2.5-flash",      # Stable, mid-size multimodal (RECOMMENDED)
            "gemini-2.0-flash",      # Fast, versatile alternative
            "gemini-flash-latest",   # Always latest stable release
        ]
        if settings.GEMINI_API_KEY:
            genai.configure(api_key=settings.GEMINI_API_KEY)

            self.model = None
            for model_name in self.models_to_try:
                try:
                    self.model = genai.GenerativeModel(model_name)
                    print(f"[OK] Using {model_name}")
                    break
                except Exception as e:
                    print(f"[WARN] {model_name} not available: {e}")
                    continue

            if not self.model:
                print("[ERROR] No Gemini model available!")
        else:
            self.model = None

    def identify_object(self, image_bytes: bytes) -> dict:
        """
        Gọi Gemini Vision API để nhận diện vật thể từ ảnh.
        """
        if not self.model:
            print("[WARN] Gemini model not initialized!")
            return {"_error": "model_not_initialized", "_message": "Gemini model not initialized"}

        print(f"[INFO] Calling Gemini API with image size: {len(image_bytes)} bytes")

        prompt = """Analyze this image and identify the main object. Return a JSON object with this exact format:
{
    "object_code": "english_name_lowercase_no_spaces",
    "category": "category_name",
    "translations": [
        {
            "lang_code": "en",
            "word_name": "English name",
            "phonetic": "/IPA transcription/",
            "part_of_speech": "n",
            "definition": "từ tiếng Việt: giải thích ngắn gọn bằng tiếng Việt",
            "example_sentences": [
                "Example sentence 1 using the word.",
                "Example sentence 2 using the word.",
                "Example sentence 3 using the word."
            ]
        }
    ]
}

Rules:
- object_code: lowercase English, underscores for spaces (e.g., "water_bottle")
- phonetic: IPA format with slashes (e.g., /ˈæp.əl/)
- part_of_speech: use abbreviations ONLY — n (noun), v (verb), adj (adjective), adv (adverb), prep (preposition), conj (conjunction), pron (pronoun), interj (interjection)
- definition: format MUST be "Vietnamese word: brief Vietnamese explanation" (e.g., "máy tính xách tay: thiết bị điện tử cầm tay dùng để làm việc")
- example_sentences: EXACTLY 3 sentences in English, simple and suitable for language learners
- Return ONLY valid JSON, no markdown, no extra text"""

        try:
            response = None
            last_quota_error = None
            for model_name in self.models_to_try:
                try:
                    model = genai.GenerativeModel(model_name)
                    response = model.generate_content([
                        prompt,
                        {"mime_type": "image/jpeg", "data": image_bytes}
                    ])
                    if model_name != "gemini-2.5-flash":
                        print(f"[OK] Fallback model succeeded: {model_name}")
                    break
                except ResourceExhausted as e:
                    print(f"[WARN] Model quota exhausted: {model_name}")
                    last_quota_error = e
                    continue

            if response is None:
                if last_quota_error is not None:
                    raise last_quota_error
                raise RuntimeError("No Gemini model produced a response")

            text = (response.text or "").strip()
            if text.startswith("```"):
                # Handle markdown-wrapped JSON responses.
                text = text.split("\n", 1)[1] if "\n" in text else text
                text = text.rsplit("```", 1)[0].strip()

            if not text:
                print("[ERROR] Gemini returned empty text response")
                return {"_error": "empty_response", "_message": "Gemini returned empty response"}

            print(f"[OK] Gemini response: {text[:200]}...")

            # Trích JSON block chắc chắn hơn (lấy từ { đầu tiên tới } cuối cùng)
            json_str = text
            first = text.find("{")
            last = text.rfind("}")
            if first != -1 and last != -1 and last > first:
                json_str = text[first:last + 1]

            try:
                result = json.loads(json_str)
                result = self._sanitize_result(result)
                print(f"[OK] Parsed object_code: {result.get('object_code', 'N/A')}")
                return result
            except Exception:
                # Fallback parse khi model trả text gần-JSON nhưng sai format.
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
                        "example_sentences": []
                    }]
                }
                fallback = self._sanitize_result(fallback)
                print(f"[WARN] Fallback parsed object_code: {fallback.get('object_code', 'N/A')}")
                return fallback
        except ResourceExhausted as e:
            print(f"[ERROR] Gemini quota exceeded: {e}")
            return {"_error": "quota_exceeded", "_message": str(e)}
        except Exception as e:
            print(f"[ERROR] Gemini API error: {e}")
            import traceback
            traceback.print_exc()
            return {"_error": "api_error", "_message": str(e)}

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

    def translate_text(self, text: str, from_lang: str = "en", to_lang: str = "vi") -> dict | None:
        if not self.model:
            return None
        lang_names = {
            "vi": "Vietnamese", "en": "English",
            "ja": "Japanese", "ko": "Korean",
            "zh": "Chinese", "fr": "French"
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
            response = self.model.generate_content(prompt)
            raw = (response.text or "").strip()
            if raw.startswith("```"):
                raw = raw.split("\n", 1)[1] if "\n" in raw else raw
                raw = raw.rsplit("```", 1)[0].strip()
            first, last = raw.find("{"), raw.rfind("}")
            if first != -1 and last != -1:
                raw = raw[first:last + 1]
            result = json.loads(raw)
            return {
                "translation": result.get("translation", ""),
                "phonetic": result.get("phonetic"),
                "definitions": result.get("definitions", [])
            }
        except ResourceExhausted:
            return {"_error": "quota_exceeded"}
        except Exception as e:
            print(f"[ERROR] Gemini translate error: {e}")
            return None

    def get_example_sentences(self, word: str, lang_code: str = "en", count: int = 3) -> list:
        """
        Sinh câu ví dụ cho một từ vựng.
        """
        if not self.model:
            return []

        prompt = f"""Generate exactly {count} example sentences using the word "{word}" in English.
Each sentence should be simple and suitable for language learners.
Return a JSON array of strings, like: ["sentence 1", "sentence 2", "sentence 3"]
Return ONLY valid JSON, no markdown or extra text."""

        try:
            response = self.model.generate_content(prompt)
            text = response.text.strip()
            if text.startswith("```"):
                text = text.split("\n", 1)[1]
                text = text.rsplit("```", 1)[0]
            return json.loads(text)
        except Exception as e:
            print(f"Gemini example sentences error: {e}")
            return []
