"""
Application-wide constants.
Centralized magic numbers and configuration values.
"""

# Image processing
MAX_IMAGE_UPLOAD_BYTES = 10 * 1024 * 1024  # 10MB
IMAGE_COMPRESS_MAX_SIZE = 1024  # pixels (width/height)
IMAGE_COMPRESS_QUALITY = 70     # WEBP quality (65-75 là đủ cho Gemini vision)

# SM-2 Spaced Repetition
SM2_MASTERED_MIN_REPETITIONS = 5
SM2_COLLECTION_MASTERED_MIN_REPETITIONS = 7
SM2_MASTERED_MIN_INTERVAL_DAYS = 7
SM2_BASELINE_EASINESS_FACTOR = 2.5

# Pagination defaults
DEFAULT_HISTORY_LIMIT = 50
