"""Image processing utilities."""

from PIL import Image, ImageFilter
import io
import logging

from app.core.constants import IMAGE_COMPRESS_MAX_SIZE, IMAGE_COMPRESS_QUALITY

logger = logging.getLogger(__name__)

# Ngưỡng chất lượng ảnh
_BLUR_THRESHOLD = 100.0      # variance of Laplacian — dưới ngưỡng này là mờ
_BRIGHTNESS_MIN = 30.0       # 0-255, dưới này là quá tối
_BRIGHTNESS_MAX = 230.0      # 0-255, trên này là quá sáng/overexposed
_MIN_DIMENSION = 64          # px, ảnh quá nhỏ không đủ thông tin


def check_image_quality(image_bytes: bytes) -> tuple[bool, str | None, int]:
    """Kiểm tra chất lượng ảnh: blur, brightness, kích thước.

    Returns:
        (passed, fail_reason, score_0_100)
        - passed=True, reason=None, score>=60 nếu đạt
        - passed=False, reason="quality_fail:...", score<60 nếu không đạt
        - fail_reason luôn có prefix "quality_fail:" để phân biệt với ghi_chu từ nguồn khác
    """
    try:
        img = Image.open(io.BytesIO(image_bytes))

        # Kích thước tối thiểu
        w, h = img.size
        if w < _MIN_DIMENSION or h < _MIN_DIMENSION:
            return False, f"quality_fail:anh_qua_nho ({w}x{h}px)", 0

        gray = img.convert("L")
        pixels = list(gray.getdata())
        brightness = sum(pixels) / len(pixels)

        if brightness < _BRIGHTNESS_MIN:
            return False, f"quality_fail:anh_qua_toi (brightness={brightness:.0f})", 10
        if brightness > _BRIGHTNESS_MAX:
            return False, f"quality_fail:anh_qua_sang (brightness={brightness:.0f})", 10

        # Blur — variance of Laplacian (edge sharpness)
        laplacian = gray.filter(ImageFilter.Kernel(
            size=3,
            kernel=[-1, -1, -1, -1, 8, -1, -1, -1, -1],
            scale=1,
            offset=128,
        ))
        lap_pixels = list(laplacian.getdata())
        mean = sum(lap_pixels) / len(lap_pixels)
        variance = sum((p - mean) ** 2 for p in lap_pixels) / len(lap_pixels)

        if variance < _BLUR_THRESHOLD:
            score = max(0, int(variance / _BLUR_THRESHOLD * 50))
            return False, f"quality_fail:anh_bi_mo (blur_variance={variance:.1f})", score

        # Đạt — score 60-100 dựa trên độ sắc nét
        score = min(100, 60 + int((variance - _BLUR_THRESHOLD) / _BLUR_THRESHOLD * 40))
        return True, None, score
    except Exception as e:
        logger.warning("Image quality check failed: %s", e)
        return True, None, 100


def compress_image(image_bytes: bytes, max_size: int = IMAGE_COMPRESS_MAX_SIZE) -> bytes:
    """
    Resize ảnh xuống max_size x max_size (giữ tỷ lệ), giảm dung lượng.
    Giúp Gemini API xử lý nhanh hơn 2-3x.
    """
    try:
        logger.debug("Compressing image: %d bytes", len(image_bytes))
        img = Image.open(io.BytesIO(image_bytes))
        logger.debug("Original size: %s, mode: %s", img.size, img.mode)

        # Convert RGBA -> RGB nếu cần
        if img.mode in ('RGBA', 'LA', 'P'):
            background = Image.new('RGB', img.size, (255, 255, 255))
            if img.mode == 'P':
                img = img.convert('RGBA')
            background.paste(img, mask=img.split()[-1] if img.mode in ('RGBA', 'LA') else None)
            img = background

        # Resize giữ tỷ lệ — chỉ resize khi ảnh lớn hơn max_size
        if img.width > max_size or img.height > max_size:
            img.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)

        # Compress sang WEBP — nhỏ hơn JPEG ~30%, Gemini hỗ trợ tốt
        output = io.BytesIO()
        img.save(output, format='WEBP', quality=IMAGE_COMPRESS_QUALITY, method=6)
        compressed = output.getvalue()
        logger.debug("Compressed to: %d bytes, size: %s (WEBP)", len(compressed), img.size)
        return compressed
    except Exception as e:
        logger.warning("Image compression failed: %s", e)
        return image_bytes
