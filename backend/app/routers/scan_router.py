from fastapi import APIRouter, Depends, File, UploadFile, HTTPException, Query
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session
from typing import List, Optional
import io
import logging

from app.db.session import get_db
from app.services.scan_service import ScanService
from app.services.gemini_service import GeminiService
from app.services.tts_service import TTSService
from app.schemas.common import ScanRequest, ScanResponse, TranslationResponse
from app.dependencies.get_current_user import get_optional_user_id
from app.utils.image import compress_image
from app.core.constants import MAX_IMAGE_UPLOAD_BYTES

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api", tags=["Scan"])
scan_service = ScanService()
gemini_service = GeminiService()
tts_service = TTSService()

@router.get("/scan/test")
def test_endpoint():
    """Test endpoint - no auth required"""
    return {"status": "ok", "message": "Scan API is working"}


@router.post("/scan", response_model=ScanResponse)
def scan_object(
    request: ScanRequest, 
    db: Session = Depends(get_db),
    user_id: Optional[int] = Depends(get_optional_user_id)
):
    """Scan bằng object_code (từ ML Kit/YOLOv8 on-device)."""
    logger.info("Scan request: object_code=%s, confidence=%s", request.object_code, request.confidence)
    request.user_id = user_id
    result = scan_service.process_scan(db, request)
    logger.info("Scan response: source=%s, translations=%d", result.source, len(result.translations))
    return result


@router.post("/scan/image", response_model=ScanResponse)
async def scan_image(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
):
    """Scan bằng ảnh — gọi Gemini Vision API để nhận diện."""
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(400, "File phải là ảnh (JPEG, PNG, ...)")
    
    image_bytes = await file.read()
    if len(image_bytes) > MAX_IMAGE_UPLOAD_BYTES:
        raise HTTPException(400, "Ảnh quá lớn, tối đa 10MB")
    
    compressed_bytes = compress_image(image_bytes)
    
    return scan_service.process_scan_image(db, compressed_bytes)


@router.get("/objects/{object_code}/translations", response_model=List[TranslationResponse])
def get_translations(object_code: str, db: Session = Depends(get_db)):
    result = scan_service.get_translations_by_object_code(db, object_code)
    if result is None:
        raise HTTPException(404, "Object not found")
    return result


@router.get("/objects/{object_code}/examples")
def get_example_sentences(
    object_code: str,
    lang: str = Query(default="en", description="Language code"),
    count: int = Query(default=3, ge=1, le=5),
):
    """Sinh câu ví dụ cho từ vựng bằng Gemini."""
    sentences = gemini_service.get_example_sentences(object_code, lang, count)
    return {"word": object_code, "lang": lang, "sentences": sentences}


@router.get("/tts/{word}")
def text_to_speech(
    word: str,
    lang: str = Query(default="en", description="Language code"),
):
    """Chuyển từ vựng thành audio MP3."""
    audio_bytes = tts_service.generate_audio(word, lang)
    if not audio_bytes:
        raise HTTPException(500, "Không thể tạo audio")
    
    return StreamingResponse(
        io.BytesIO(audio_bytes),
        media_type="audio/mpeg",
        headers={"Content-Disposition": f"inline; filename={word}.mp3"}
    )
