from fastapi import APIRouter, Depends, Query, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import httpx
import logging
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.dependencies.get_current_user import get_current_user_id, get_optional_user_id
from app.services.dictionary_service import DictionaryService

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/dictionary", tags=["Dictionary"])
dictionary_service = DictionaryService()


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
    source: str = "internal_db"
    is_physical_object: bool = False
    object_id: Optional[int] = None
    object_code: Optional[str] = None
    translation_id: Optional[int] = None
    can_save: bool = False
    pending_review: bool = False


class DictionaryHistoryItem(BaseModel):
    id: int
    tu_tra: str
    ket_qua_dich: Optional[str] = None
    phien_am: Optional[str] = None
    lan_tra_cuoi: Optional[str] = None
    doi_tuong_id: Optional[int] = None
    object_code: Optional[str] = None
    image_url: Optional[str] = None
    den_ngon_ngu: str = "vi"


@router.post("/translate", response_model=TranslateResponse)
def translate(
    req: TranslateRequest,
    db: Session = Depends(get_db),
    user_id: Optional[int] = Depends(get_optional_user_id),
):
    """DB-first dictionary lookup, then MyMemory fallback for plain translation."""
    if not req.text.strip():
        raise HTTPException(400, "Text cannot be empty")

    result = dictionary_service.translate_or_lookup(
        db,
        req.text.strip(),
        req.from_lang,
        req.to_lang,
        user_id,
    )
    if result is None or result.get("_error") in ("quota_exceeded", "service_unavailable"):
        return TranslateResponse(
            original=req.text.strip(),
            translation="Không thể dịch lúc này, vui lòng thử lại sau.",
            from_lang=req.from_lang,
            to_lang=req.to_lang,
            source="external_unavailable",
        )
    return TranslateResponse(**result)


@router.get("/history", response_model=List[DictionaryHistoryItem])
def get_history(
    limit: int = Query(default=30, ge=1, le=100),
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id),
):
    items = dictionary_service.get_history(db, user_id, limit)
    return [DictionaryHistoryItem(**item) for item in items]


@router.delete("/history/{item_id}", status_code=204)
def delete_history_item(
    item_id: int,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id),
):
    if not dictionary_service.delete_history_item(db, user_id, item_id):
        raise HTTPException(status_code=404, detail="Item not found")



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
