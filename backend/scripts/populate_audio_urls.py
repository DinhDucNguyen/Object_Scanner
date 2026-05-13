"""
Fill BanDich.am_thanh_url with the local TTS endpoint.

Run from the backend directory:
    python scripts/populate_audio_urls.py

Optional, to pre-warm MP3 cache with gTTS:
    python scripts/populate_audio_urls.py --generate
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from app.db.session import SessionLocal
from app.models.language import Language
from app.models.translation import Translation
from app.services.tts_service import TTSService


def populate(generate: bool, dry_run: bool) -> dict[str, int]:
    stats = {
        "translations_seen": 0,
        "audio_urls_added": 0,
        "audio_urls_kept": 0,
        "audio_generated": 0,
        "audio_failed": 0,
    }
    tts = TTSService()
    db = SessionLocal()
    try:
        rows = (
            db.query(Translation, Language)
            .outerjoin(Language, Translation.ngon_ngu_id == Language.id)
            .filter(Translation.thoi_gian_xoa.is_(None))
            .order_by(Translation.id)
            .all()
        )
        for translation, lang in rows:
            stats["translations_seen"] += 1
            lang_code = lang.ma_ngon_ngu if lang else "en"
            word = translation.tu_vung
            if not word:
                continue

            audio_url = tts.get_audio_url(word, lang_code)
            if not translation.am_thanh_url:
                translation.am_thanh_url = audio_url
                stats["audio_urls_added"] += 1
            else:
                stats["audio_urls_kept"] += 1

            if generate:
                audio = tts.generate_audio(word, lang_code)
                if audio:
                    stats["audio_generated"] += 1
                else:
                    stats["audio_failed"] += 1

        if dry_run:
            db.rollback()
        else:
            db.commit()
        return stats
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Populate translation audio URLs.")
    parser.add_argument("--generate", action="store_true", help="Generate/cache MP3 files with gTTS now.")
    parser.add_argument("--dry-run", action="store_true", help="Run without committing changes.")
    args = parser.parse_args()

    stats = populate(generate=args.generate, dry_run=args.dry_run)
    print("Populate audio URLs:", "DRY RUN" if args.dry_run else "COMMITTED")
    for key, value in stats.items():
        print(f"{key}: {value}")


if __name__ == "__main__":
    main()
