import cloudinary
import cloudinary.uploader
from app.core.config import settings

_configured = False


def _ensure_configured():
    global _configured
    if not _configured:
        cloudinary.config(
            cloud_name=settings.CLOUDINARY_CLOUD_NAME,
            api_key=settings.CLOUDINARY_API_KEY,
            api_secret=settings.CLOUDINARY_API_SECRET,
        )
        _configured = True


def upload_image(image_bytes: bytes, folder: str = "object_scanner/scans") -> str | None:
    """Upload ảnh lên Cloudinary, trả về secure URL hoặc None nếu lỗi/chưa cấu hình."""
    if not settings.CLOUDINARY_CLOUD_NAME:
        return None
    try:
        _ensure_configured()
        result = cloudinary.uploader.upload(
            image_bytes,
            folder=folder,
            resource_type="image",
            format="jpg",
        )
        return result.get("secure_url")
    except Exception:
        return None

