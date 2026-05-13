import hashlib
import io
import re
from pathlib import Path
from urllib.parse import quote

from gtts import gTTS


class TTSService:
    def __init__(self, cache_dir: str = "uploads/tts"):
        self.cache_dir = Path(cache_dir)

    def get_audio_url(self, text: str, lang_code: str = "en") -> str | None:
        text = (text or "").strip()
        if not text:
            return None
        lang = self._normalize_lang(lang_code)
        return f"/api/tts/{quote(text, safe='')}?lang={quote(lang, safe='')}"

    def generate_audio(self, text: str, lang_code: str = "en") -> bytes | None:
        """Generate MP3 bytes with gTTS and cache them on disk."""
        text = (text or "").strip()
        if not text:
            return None

        lang = self._normalize_lang(lang_code)
        cache_path = self._cache_path(text, lang)
        if cache_path.exists():
            return cache_path.read_bytes()

        try:
            tts = gTTS(text=text, lang=lang, slow=False)
            audio_buffer = io.BytesIO()
            tts.write_to_fp(audio_buffer)
            audio_buffer.seek(0)
            audio_bytes = audio_buffer.read()

            cache_path.parent.mkdir(parents=True, exist_ok=True)
            cache_path.write_bytes(audio_bytes)
            return audio_bytes
        except Exception as e:
            print(f"TTS error: {e}")
            return None

    def _normalize_lang(self, lang_code: str) -> str:
        lang = (lang_code or "en").strip().lower().split("-")[0]
        return lang if re.fullmatch(r"[a-z]{2,3}", lang) else "en"

    def _cache_path(self, text: str, lang_code: str) -> Path:
        slug = re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")[:48] or "tts"
        digest = hashlib.sha1(f"{lang_code}:{text}".encode("utf-8")).hexdigest()[:12]
        return self.cache_dir / lang_code / f"{slug}_{digest}.mp3"
