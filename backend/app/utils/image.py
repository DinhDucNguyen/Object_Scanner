"""Image processing utilities."""

from PIL import Image
import io
import logging

from app.core.constants import IMAGE_COMPRESS_MAX_SIZE, IMAGE_COMPRESS_QUALITY

logger = logging.getLogger(__name__)


def compress_image(image_bytes: bytes, max_size: int = IMAGE_COMPRESS_MAX_SIZE) -> bytes:
    """
    Resize ảnh xuống max_size x max_size (giữ tỷ lệ), giảm dung lượng.
    Giúp Gemini API xử lý nhanh hơn 2-3x.
    """
    try:
        logger.info("Compressing image: %d bytes", len(image_bytes))
        img = Image.open(io.BytesIO(image_bytes))
        logger.debug("Original size: %s, mode: %s", img.size, img.mode)

        # Convert RGBA -> RGB nếu cần
        if img.mode in ('RGBA', 'LA', 'P'):
            background = Image.new('RGB', img.size, (255, 255, 255))
            if img.mode == 'P':
                img = img.convert('RGBA')
            background.paste(img, mask=img.split()[-1] if img.mode in ('RGBA', 'LA') else None)
            img = background

        # Resize giữ tỷ lệ
        img.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)

        # Compress với JPEG
        output = io.BytesIO()
        img.save(output, format='JPEG', quality=IMAGE_COMPRESS_QUALITY, optimize=True)
        compressed = output.getvalue()
        logger.info("Compressed to: %d bytes, size: %s", len(compressed), img.size)
        return compressed
    except Exception as e:
        logger.warning("Image compression failed: %s", e)
        return image_bytes
