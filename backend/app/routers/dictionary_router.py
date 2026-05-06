from fastapi import APIRouter, Query, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import httpx
import logging

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/dictionary", tags=["Dictionary"])


class DictionaryMeaning(BaseModel):
    part_of_speech: str
    definition: str
    example: Optional[str] = None


class DictionaryResponse(BaseModel):
    word: str
    phonetic: Optional[str] = None
    audio_url: Optional[str] = None
    meanings: List[DictionaryMeaning] = []
    source: str = "free_dictionary_api"


class TranslateRequest(BaseModel):
    text: str
    from_lang: str = "en"
    to_lang: str = "vi"


class TranslateResponse(BaseModel):
    original: str
    translation: str
    phonetic: Optional[str] = None
    definitions: List[str] = []
    from_lang: str
    to_lang: str


@router.post("/translate", response_model=TranslateResponse)
async def translate(req: TranslateRequest):
    """Translate text between languages using Gemini."""
    from app.services.gemini_service import GeminiService
    if not req.text.strip():
        raise HTTPException(400, "Text cannot be empty")
    gemini = GeminiService()
    result = gemini.translate_text(req.text.strip(), req.from_lang, req.to_lang)
    if result is None:
        raise HTTPException(503, "Translation service unavailable")
    if result.get("_error") == "quota_exceeded":
        raise HTTPException(429, "Gemini quota exceeded. Please try again later.")
    return TranslateResponse(
        original=req.text,
        translation=result.get("translation", ""),
        phonetic=result.get("phonetic"),
        definitions=result.get("definitions", []),
        from_lang=req.from_lang,
        to_lang=req.to_lang,
    )


@router.get("/lookup", response_model=DictionaryResponse)
async def lookup_word(
    word: str = Query(..., min_length=1, description="Word to look up"),
    from_lang: str = Query(default="en", description="Source language code"),
    to_lang: str = Query(default="en", description="Target language code"),
):
    """
    Look up a word in the dictionary.
    Currently supports English word lookups via Free Dictionary API.
    """
    if not word.strip():
        raise HTTPException(400, "Word cannot be empty")
    
    word = word.strip().lower()
    
    try:
        # Use Free Dictionary API for English lookups
        if from_lang == "en":
            return await _lookup_english(word)
        else:
            # For non-English, try Free Dictionary API with the word as-is
            # (it may have entries for some languages)
            try:
                return await _lookup_english(word)
            except HTTPException:
                raise HTTPException(
                    404,
                    f"No definition found for '{word}'. Only English lookups are currently supported."
                )
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Dictionary lookup error: {e}")
        raise HTTPException(500, f"Dictionary lookup failed: {str(e)}")


async def _lookup_english(word: str) -> DictionaryResponse:
    """Look up an English word using Free Dictionary API (dictionaryapi.dev)."""
    url = f"https://api.dictionaryapi.dev/api/v2/entries/en/{word}"
    
    async with httpx.AsyncClient(timeout=10.0) as client:
        response = await client.get(url)
    
    if response.status_code == 404:
        raise HTTPException(404, f"No definition found for '{word}'")
    
    if response.status_code != 200:
        raise HTTPException(502, "Dictionary service unavailable")
    
    data = response.json()
    if not data or not isinstance(data, list):
        raise HTTPException(404, f"No definition found for '{word}'")
    
    entry = data[0]
    
    # Extract phonetic
    phonetic = entry.get("phonetic", "")
    
    # Extract audio URL
    audio_url = None
    phonetics = entry.get("phonetics", [])
    for p in phonetics:
        if p.get("audio"):
            audio_url = p["audio"]
            break
        if not phonetic and p.get("text"):
            phonetic = p["text"]
    
    # Extract meanings
    meanings = []
    for meaning in entry.get("meanings", []):
        pos = meaning.get("partOfSpeech", "")
        for defn in meaning.get("definitions", [])[:3]:  # Max 3 definitions per POS
            meanings.append(DictionaryMeaning(
                part_of_speech=pos,
                definition=defn.get("definition", ""),
                example=defn.get("example"),
            ))
    
    return DictionaryResponse(
        word=entry.get("word", word),
        phonetic=phonetic or None,
        audio_url=audio_url,
        meanings=meanings,
        source="free_dictionary_api",
    )
