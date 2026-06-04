# pyrefly: ignore [missing-import]
from fastapi import APIRouter, Depends, File, UploadFile, HTTPException, Query, Request
# pyrefly: ignore [missing-import]
from fastapi.responses import StreamingResponse, Response
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool
from typing import List, Optional
import io
import logging

from app.db.session import get_db
from app.services.scan_service import ScanService
from app.services.gemini_service import GeminiService
from app.services.tts_service import TTSService
from app.schemas.common import ScanRequest, ScanResponse, TranslationResponse
from app.dependencies.get_current_user import get_optional_nguoi_dung_id
from app.utils.image import compress_image
from app.core.constants import MAX_IMAGE_UPLOAD_BYTES
from app.core.limiter import limiter
from app.utils.upload import read_upload_bytes

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api", tags=["Scan"])
scan_service = ScanService()
gemini_service = GeminiService()
tts_service = TTSService()

@router.post("/scan", response_model=ScanResponse)
def scan_object(
    request: ScanRequest, 
    db: Session = Depends(get_db),
    nguoi_dung_id: Optional[int] = Depends(get_optional_nguoi_dung_id)
):
    """Scan bằng object_code (từ ML Kit/YOLOv10 on-device)."""
    logger.info("Scan request: object_code=%s, confidence=%s", request.object_code, request.confidence)
    request.nguoi_dung_id = nguoi_dung_id
    result = scan_service.process_scan(db, request)
    logger.info("Scan response: source=%s, translations=%d", result.source, len(result.translations))
    return result


@router.post("/scan/image", response_model=ScanResponse)
@limiter.limit("10/minute")
async def scan_image(
    request: Request,
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    nguoi_dung_id: Optional[int] = Depends(get_optional_nguoi_dung_id),
):
    """Scan bằng ảnh — gọi Gemini Vision API để nhận diện."""
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(400, "File phải là ảnh (JPEG, PNG, ...)")
    
    image_bytes = await read_upload_bytes(file)
    if len(image_bytes) > MAX_IMAGE_UPLOAD_BYTES:
        raise HTTPException(400, "Ảnh quá lớn, tối đa 10MB")
    
    compressed_bytes = compress_image(image_bytes)

    return await run_in_threadpool(
        scan_service.process_scan_image,
        db,
        compressed_bytes,
        nguoi_dung_id,
        str(request.base_url).rstrip("/"),
    )


@router.get("/objects/{object_code}/translations", response_model=List[TranslationResponse])
def get_translations(object_code: str, db: Session = Depends(get_db)):
    result = scan_service.get_translations_by_object_code(db, object_code)
    if result is None:
        raise HTTPException(404, "Object not found")
    return result


@router.get("/objects/{object_code}/examples")
@limiter.limit("20/minute")
def get_example_sentences(
    request: Request,
    object_code: str,
    lang: str = Query(default="en", description="Language code"),
    count: int = Query(default=3, ge=1, le=3),
):
    """Sinh câu ví dụ cho từ vựng bằng Gemini."""
    sentences = gemini_service.get_example_sentences(object_code, lang, count)
    return {"word": object_code, "lang": lang, "sentences": sentences[:3]}


@router.get("/tts/{word}")
@limiter.limit("30/minute")
def text_to_speech(
    request: Request,
    word: str,
    lang: str = Query(default="en", description="Language code"),
):
    """Chuyển từ vựng thành audio MP3."""
    word = word.strip()
    if not word:
        raise HTTPException(400, "Từ phát âm không được rỗng")
    if len(word) > 100:
        raise HTTPException(400, "Từ phát âm quá dài, tối đa 100 ký tự")

    audio_bytes = tts_service.generate_audio(word, lang)
    if not audio_bytes:
        raise HTTPException(500, "Không thể tạo audio")
    
    return Response(
        content=audio_bytes,
        media_type="audio/mpeg",
        headers={"Content-Disposition": f"inline; filename={word}.mp3"},
    )
