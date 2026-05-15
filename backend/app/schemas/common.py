from pydantic import BaseModel, Field
from typing import Optional, List
from datetime import datetime


# ====== Object ======

class ObjectResponse(BaseModel):
    id: int
    category_id: Optional[int] = None
    object_code: str
    deleted: bool
    category_name: Optional[str] = None
    translation_count: int = 0

# ====== Example ======

class ViDuResponse(BaseModel):
    id: int
    cau_vi_du: Optional[str] = None
    dich_nghia: Optional[str] = None
    nguon_du_lieu: Optional[str] = None

    class Config:
        from_attributes = True

# ====== Translation ======

class TranslationResponse(BaseModel):
    id: int
    object_id: int
    language_id: int
    language_code: Optional[str] = None
    language_name: Optional[str] = None
    word_name: str
    phonetic: Optional[str] = None
    part_of_speech: Optional[str] = None
    definition: Optional[str] = None
    examples: List[ViDuResponse] = []
    audio_url: Optional[str] = None
    data_source: Optional[str] = None

    class Config:
        from_attributes = True

# ====== Scan ======

class ScanRequest(BaseModel):
    object_code: str
    confidence: float = 0.0
    user_id: int = 1
    image_url: Optional[str] = None


class ScanResponse(BaseModel):
    source: str
    object_id: int
    object_code: str
    category_name: Optional[str] = None
    translations: List[TranslationResponse]
    scan_id: Optional[int] = None
    prediction_id: Optional[int] = None
    pending_review: bool = False

# ====== Review ======

class ReviewRequest(BaseModel):
    quality: int = Field(ge=0, le=5)

class ReviewCardResponse(BaseModel):
    progress_id: int
    translation_id: int
    object_code: str
    word_name: str
    phonetic: Optional[str] = None
    definition: Optional[str] = None
    examples: List[ViDuResponse] = []
    language_code: str
    language_name: str
    easiness_factor: float
    interval: int
    repetitions: int
    image_url: Optional[str] = None
    audio_url: Optional[str] = None

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


# ====== AI Prediction ======

class AIPredictionCreate(BaseModel):
    scan_id: int
    source_ai: str  # 'yolo' or 'gemini'
    predicted_label: Optional[str] = None
    confidence: Optional[float] = None
    description: Optional[str] = None

class AIPredictionResponse(BaseModel):
    id: int
    scan_id: int
    source_ai: str
    predicted_label: Optional[str] = None
    confidence: Optional[float] = None
    description: Optional[str] = None
    ket_qua_dung: bool
    created_at: Optional[datetime] = None

    class Config:
        from_attributes = True

# ====== Lich Su Quet ======

class LichSuQuetResponse(BaseModel):
    id: int
    message: str
    image_url: Optional[str] = None
    learning_added: bool = False
    learning_status: Optional[str] = None
    translation_id: Optional[int] = None

# ====== Stats ======

class StatsResponse(BaseModel):
    total_objects: int
    total_translations: int
    total_languages: int
    total_learned: int
    due_today: int
    mastered: int
    total_scans: int
    current_streak: int = 0
    longest_streak: int = 0
    total_reviews: int = 0

# ====== Language ======

class LanguageResponse(BaseModel):
    id: int
    code: str
    name: str
    flag_icon_url: Optional[str] = None
    is_active: bool

    class Config:
        from_attributes = True

# ====== Category ======

class CategoryResponse(BaseModel):
    id: int
    name: str
    parent_id: Optional[int] = None
    description: Optional[str] = None

    class Config:
        from_attributes = True
