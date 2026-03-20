from fastapi import APIRouter, Depends, File, UploadFile, HTTPException, Query
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session
from typing import List, Optional
from PIL import Image
import io

from app.db.session import get_db
from app.services.scan_service import ScanService
from app.services.gemini_service import GeminiService
from app.services.tts_service import TTSService
from app.schemas.common import ScanRequest, ScanResponse, TranslationResponse

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
    user_id: Optional[int] = None
):
    """Scan bằng object_code (từ ML Kit/YOLOv8 on-device)."""
    print(f"🔍 [SCAN] Received request: object_code={request.object_code}, confidence={request.confidence}")
    request.user_id = user_id
    result = scan_service.process_scan(db, request)
    print(f"✅ [SCAN] Response: source={result.source}, translations={len(result.translations)}")
    return result


@router.post("/scan/image", response_model=ScanResponse)
async def scan_image(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    user_id: Optional[int] = None
):
    """Scan bằng ảnh — gọi Gemini Vision API để nhận diện."""
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(400, "File phải là ảnh (JPEG, PNG, ...)")
    
    image_bytes = await file.read()
    if len(image_bytes) > 10 * 1024 * 1024:  # 10MB limit
        raise HTTPException(400, "Ảnh quá lớn, tối đa 10MB")
    
    # ✅ THÊM: Compress ảnh xuống 800x800 để Gemini xử lý nhanh hơn (5-10s → 3-5s)
    compressed_bytes = compress_image(image_bytes, max_size=800)
    
    return scan_service.process_scan_image(db, compressed_bytes, user_id or 1)


def compress_image(image_bytes: bytes, max_size: int = 800) -> bytes:
    """
    Resize ảnh xuống max_size x max_size (giữ tỷ lệ), giảm dung lượng.
    Giúp Gemini API xử lý nhanh hơn 2-3x.
    """
    try:
        print(f"📸 Compressing image: {len(image_bytes)} bytes")
        img = Image.open(io.BytesIO(image_bytes))
        print(f"📸 Original size: {img.size}, mode: {img.mode}")
        
        # Convert RGBA -> RGB nếu cần
        if img.mode in ('RGBA', 'LA', 'P'):
            background = Image.new('RGB', img.size, (255, 255, 255))
            if img.mode == 'P':
                img = img.convert('RGBA')
            background.paste(img, mask=img.split()[-1] if img.mode in ('RGBA', 'LA') else None)
            img = background
            print(f"📸 Converted to RGB")
        
        # Resize giữ tỷ lệ
        img.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)
        
        # Compress với JPEG quality 85
        output = io.BytesIO()
        img.save(output, format='JPEG', quality=85, optimize=True)
        compressed = output.getvalue()
        print(f"✅ Compressed to: {len(compressed)} bytes, size: {img.size}")
        return compressed
    except Exception as e:
        # Nếu lỗi thì trả ảnh gốc
        print(f"⚠️ Image compression failed: {e}")
        return image_bytes


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
