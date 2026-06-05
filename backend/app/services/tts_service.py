import hashlib
import io
import logging
import re
from pathlib import Path
from urllib.parse import quote

from gtts import gTTS

logger = logging.getLogger("uvicorn.error")
BACKEND_ROOT = Path(__file__).resolve().parents[2]


class TTSService:
    def __init__(self, cache_dir: str | Path | None = None, max_cache_files: int = 1000):
        self.cache_dir = Path(cache_dir) if cache_dir else BACKEND_ROOT / "uploads" / "tts"
        self.max_cache_files = max_cache_files

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
            logger.info("TTS source=cache text=%r lang=%s file=%s", text, lang, cache_path)
            return cache_path.read_bytes()

        try:
            logger.info("TTS source=gtts_generate text=%r lang=%s file=%s", text, lang, cache_path)
            tts = gTTS(text=text, lang=lang, slow=False)
            audio_buffer = io.BytesIO()
            tts.write_to_fp(audio_buffer)
            audio_buffer.seek(0)
            audio_bytes = audio_buffer.read()

            cache_path.parent.mkdir(parents=True, exist_ok=True)
            cache_path.write_bytes(audio_bytes)
            self._cleanup_cache()
            logger.info("TTS source=gtts_saved text=%r lang=%s bytes=%d file=%s", text, lang, len(audio_bytes), cache_path)
            return audio_bytes
        except Exception as e:
            logger.error("TTS source=error text=%r lang=%s error=%s", text, lang, e)
            return None

    def _normalize_lang(self, lang_code: str) -> str:
        lang = (lang_code or "en").strip().lower().split("-")[0]
        return lang if re.fullmatch(r"[a-z]{2,3}", lang) else "en"

    def _cache_path(self, text: str, lang_code: str) -> Path:
        slug = re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")[:48] or "tts"
        digest = hashlib.sha1(f"{lang_code}:{text}".encode("utf-8")).hexdigest()[:12]
        return self.cache_dir / lang_code / f"{slug}_{digest}.mp3"

    def _cleanup_cache(self) -> None:
        try:
            files = sorted(
                self.cache_dir.glob("*/*.mp3"),
                key=lambda path: path.stat().st_mtime,
            )
            overflow = len(files) - self.max_cache_files
            if overflow <= 0:
                return
            for path in files[:overflow]:
                path.unlink(missing_ok=True)
        except Exception as e:
            logger.warning("TTS cache cleanup skipped: %s", e)
