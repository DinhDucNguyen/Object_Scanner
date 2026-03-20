from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime


# ====== Object ======

class ObjectResponse(BaseModel):
    id: int
    category_id: Optional[int]
    object_code: str
    difficulty_level: int
    is_deleted: bool
    category_name: Optional[str] = None
    translation_count: int = 0

# ====== Translation ======

class TranslationResponse(BaseModel):
    id: int
    object_id: int
    language_id: int
    language_code: Optional[str] = None
    language_name: Optional[str] = None
    word_name: str
    phonetic: Optional[str]
    definition: Optional[str]
    example_sentence: Optional[str]

    class Config:
        from_attributes = True

# ====== Scan ======

class ScanRequest(BaseModel):
    object_code: str
    confidence: float = 0.0
    user_id: int = 1
    image_captured_url: Optional[str] = None
    gps_latitude: Optional[float] = None
    gps_longitude: Optional[float] = None
    device_model: str = "Google Pixel 6 Pro"


class ScanResponse(BaseModel):
    source: str
    object_id: int
    object_code: str
    category_name: Optional[str] = None
    difficulty_level: int = 1
    translations: List[TranslationResponse]

# ====== Review ======

class ReviewRequest(BaseModel):
    quality: int  # 0-5

class ReviewCardResponse(BaseModel):
    progress_id: int
    translation_id: int
    object_code: str
    word_name: str
    phonetic: Optional[str]
    definition: Optional[str]
    example_sentence: Optional[str]
    language_code: str
    language_name: str
    easiness_factor: float
    interval: int
    repetitions: int

class ReviewResult(BaseModel):
    success: bool
    new_interval: int
    new_ef: float
    next_review_date: str

# ====== Collection ======

class CollectionCreate(BaseModel):
    name: str
    is_public: bool = False

class CollectionResponse(BaseModel):
    id: int
    name: str
    is_public: bool
    item_count: int = 0
    created_at: Optional[datetime] = None

    class Config:
        from_attributes = True

class CollectionItemAdd(BaseModel):
    translation_id: int


class CollectionItemResponse(BaseModel):
    id: int
    translation_id: int
    object_name: str
    translation: str
    category: str
    image_url: Optional[str] = None

    class Config:
        from_attributes = True


class CollectionDetailResponse(BaseModel):
    id: int
    name: str
    is_public: bool
    items: List[CollectionItemResponse]
    created_at: Optional[datetime] = None

    class Config:
        from_attributes = True


class CollectionInsightsResponse(BaseModel):
    collection_id: int
    collection_name: str
    total_items: int
    reviewed_items: int
    mastered_items: int
    average_quality: float
    total_reviews: int
    success_rate: float
    last_review_date: Optional[datetime] = None

    class Config:
        from_attributes = True


# ====== Feedback ======

class FeedbackCreate(BaseModel):
    scan_id: int
    error_type: str
    correct_label: Optional[str] = None
    user_note: Optional[str] = None

class FeedbackResponse(BaseModel):
    id: int
    scan_id: int
    error_type: Optional[str]
    correct_label: Optional[str]
    user_note: Optional[str]
    is_resolved: bool
    created_at: Optional[datetime]

    class Config:
        from_attributes = True

# ====== Stats ======

class StatsResponse(BaseModel):
    total_objects: int
    total_translations: int
    total_languages: int
    total_learned: int
    due_today: int
    mastered: int
    total_scans: int

# ====== Language ======

class LanguageResponse(BaseModel):
    id: int
    code: str
    name: str
    flag_icon_url: Optional[str]
    is_active: bool

    class Config:
        from_attributes = True

# ====== Category ======

class CategoryResponse(BaseModel):
    id: int
    name: str
    parent_id: Optional[int]
    description: Optional[str]
    icon_url: Optional[str]

    class Config:
        from_attributes = True
