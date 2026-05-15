"""
Backfill dich_nghia for ViDu records where dich_nghia IS NULL.

Run from the backend directory:
    python scripts/backfill_example_translations.py
    python scripts/backfill_example_translations.py --dry-run
    python scripts/backfill_example_translations.py --batch 10
"""

from __future__ import annotations

import argparse
import io
import json
import re
import sys
import time
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import warnings
warnings.filterwarnings("ignore")

import google.generativeai as genai
from app.core.config import settings
from app.db.session import SessionLocal
from app.models.example import ViDu


MODELS = ["gemini-2.5-flash", "gemini-2.0-flash", "gemini-flash-latest"]
DELAY_BETWEEN_BATCHES = 15   # seconds — free tier: 5 RPM
MAX_RETRIES = 3


def get_model():
    if not settings.GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY not configured in .env")
    genai.configure(api_key=settings.GEMINI_API_KEY)
    for name in MODELS:
        try:
            m = genai.GenerativeModel(name)
            print(f"[OK] Using model: {name}")
            return m
        except Exception:
            continue
    raise RuntimeError("No Gemini model available")


def translate_batch(model, sentences: list[str]) -> list[str]:
    """
    Translate sentences, return list of translations in same order.
    Uses numbered array to avoid key-matching issues.
    """
    numbered = "\n".join(f"{i+1}. {s}" for i, s in enumerate(sentences))
    prompt = f"""Translate these English sentences to Vietnamese.
Return ONLY a JSON array of Vietnamese translations, in the same order.
Example: ["dich 1", "dich 2", "dich 3"]
No explanation, no markdown, just valid JSON array.

Sentences:
{numbered}"""

    for attempt in range(MAX_RETRIES):
        try:
            response = model.generate_content(prompt)
            raw = response.text.strip()
            # Strip markdown fences
            raw = re.sub(r"^```(?:json)?\s*", "", raw)
            raw = re.sub(r"\s*```$", "", raw)
            result = json.loads(raw.strip())
            if isinstance(result, list):
                return result
            raise ValueError(f"Expected list, got {type(result)}")
        except Exception as e:
            msg = str(e)
            # Extract retry delay from 429 error
            retry_match = re.search(r"retry_delay\s*\{\s*seconds:\s*(\d+)", msg)
            wait = int(retry_match.group(1)) + 3 if retry_match else 30
            if "429" in msg or "quota" in msg.lower():
                print(f"  [429] Rate limit — waiting {wait}s (attempt {attempt+1}/{MAX_RETRIES})")
                time.sleep(wait)
            else:
                raise

    raise RuntimeError(f"Failed after {MAX_RETRIES} retries")


def run(batch_size: int, dry_run: bool) -> None:
    model = get_model()
    db = SessionLocal()

    try:
        records = (
            db.query(ViDu)
            .filter(ViDu.dich_nghia.is_(None), ViDu.cau_vi_du.isnot(None))
            .all()
        )
        total = len(records)
        print(f"[INFO] Found {total} sentences to translate")
        if total == 0:
            print("[DONE] Nothing to backfill.")
            return

        updated = 0
        errors = 0

        for start in range(0, total, batch_size):
            batch = records[start : start + batch_size]
            sentences = [(r.cau_vi_du or "").strip() for r in batch]
            end = min(start + batch_size, total)
            print(f"[{start+1}-{end}/{total}] Translating {len(sentences)} sentences...")

            try:
                translations = translate_batch(model, sentences)
            except Exception as e:
                print(f"  [ERROR] {e} — skipping batch")
                errors += len(sentences)
                continue

            for i, record in enumerate(batch):
                vi = translations[i].strip() if i < len(translations) else ""
                if vi:
                    if not dry_run:
                        record.dich_nghia = vi
                    updated += 1
                    src = (record.cau_vi_du or "")[:45]
                    print(f"  + [{record.id}] {src} -> {vi[:45]}")
                else:
                    print(f"  - [{record.id}] No translation returned")
                    errors += 1

            if not dry_run:
                db.commit()

            if end < total:
                print(f"  [wait {DELAY_BETWEEN_BATCHES}s]")
                time.sleep(DELAY_BETWEEN_BATCHES)

        print(f"\n[DONE] Updated: {updated}/{total} | Errors: {errors}")
        if dry_run:
            print("[DRY RUN] No changes saved.")

    finally:
        db.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--batch", type=int, default=10)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    run(batch_size=args.batch, dry_run=args.dry_run)
