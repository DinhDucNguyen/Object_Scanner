# pyrefly: ignore [missing-import]
from pydantic import BaseModel, field_validator
# pyrefly: ignore [missing-import]
from typing import Optional, List, Union, Any
from datetime import datetime
from app.schemas.user import validate_password_strength



class VocabTranslationSchema(BaseModel):
    """
    Dữ liệu từ vựng cho một ngôn ngữ.
    Ví dụ: apple /ˈæp.əl/ (n) — quả táo
    """
    lang_code: str
    word_name: str
    phonetic: Optional[str] = None
    part_of_speech: Optional[str] = None   
    definition: Optional[str] = None       
    example_sentences: List[Union[str, dict]] = []


class VocabPayloadSchema(BaseModel):
    """Toàn bộ payload parse từ DuDoanAI.mo_ta."""
    object_code: str
    category: Optional[str] = None
    translations: List[VocabTranslationSchema] = []


class RelatedPredictionImage(BaseModel):
    prediction_id: int
    lich_su_quet_id: Optional[int] = None
    nguoi_dung_id: Optional[int] = None
    image_url: Optional[str] = None
    vai_tro: Optional[str] = None
    thoi_gian: Optional[datetime] = None




class PredictionListItem(BaseModel):
    id: int
    lich_su_quet_id: int
    nhan_du_doan: Optional[str] = None
    do_tin_cay: Optional[float] = None
    trang_thai: str
    thoi_gian: Optional[datetime] = None
    scan_image_url: Optional[str] = None
    vai_tro: Optional[str] = None
    du_doan_goc_id: Optional[int] = None

    class Config:
        from_attributes = True


class PredictionDetailResponse(BaseModel):
    id: int
    lich_su_quet_id: int
    nhan_du_doan: Optional[str] = None
    do_tin_cay: Optional[float] = None
    trang_thai: str
    thoi_gian: Optional[datetime] = None
    scan_image_url: Optional[str] = None
    vai_tro: Optional[str] = None
    du_doan_goc_id: Optional[int] = None
    related_images: List[RelatedPredictionImage] = []
    vocab_payload: Optional[VocabPayloadSchema] = None 



class ApproveRequest(BaseModel):
    """
    Body khi admin approve. Tất cả trường đều optional —
    nếu để trống sẽ dùng nguyên dữ liệu Gemini.
    """
    override_word_name: Optional[str] = None
    override_phonetic: Optional[str] = None
    override_part_of_speech: Optional[str] = None
    override_definition: Optional[str] = None
    override_example_sentences: Optional[List[str]] = None
    category_id: Optional[int] = None


class ApproveResponse(BaseModel):
    success: bool
    message: str
    prediction_id: int
    object_id: Optional[int] = None
    translation_id: Optional[int] = None
    examples_created: int = 0
    users_enrolled: int = 0


class AliasPredictionRequest(BaseModel):
    doi_tuong_id: int
    ma_bi_danh: Optional[str] = None
    ten_hien_thi: Optional[str] = None
    ngon_ngu: Optional[str] = "en"


class AliasPredictionResponse(BaseModel):
    success: bool
    message: str
    prediction_id: int
    doi_tuong_id: Optional[int] = None
    bi_danh_id: Optional[int] = None
    ma_bi_danh: Optional[str] = None
    ma_doi_tuong: Optional[str] = None
    users_enrolled: int = 0


class RejectResponse(BaseModel):
    success: bool
    message: str
    prediction_id: int


class SplitToNewObjectResponse(BaseModel):
    success: bool
    message: str
    old_prediction_id: int
    new_prediction_id: Optional[int] = None
    new_object_code: Optional[str] = None
    vocab_generated: bool = False


# ---------------------------------------------------------------------------
# Category
# ---------------------------------------------------------------------------

class CategoryAdminResponse(BaseModel):
    id: int
    ten_danh_muc: Optional[str] = None
    danh_muc_cha: Optional[int] = None
    mo_ta: Optional[str] = None
    object_count: int = 0

    class Config:
        from_attributes = True


class CategoryCreateRequest(BaseModel):
    ten_danh_muc: str
    danh_muc_cha: Optional[int] = None
    mo_ta: Optional[str] = None


class CategoryUpdateRequest(BaseModel):
    ten_danh_muc: Optional[str] = None
    danh_muc_cha: Optional[int] = None
    mo_ta: Optional[str] = None


# ---------------------------------------------------------------------------
# Object
# ---------------------------------------------------------------------------

class ObjectAliasItem(BaseModel):
    id: int
    doi_tuong_id: int
    ma_bi_danh: str
    ten_hien_thi: Optional[str] = None
    ngon_ngu: Optional[str] = None

    @classmethod
    def from_alias(cls, alias):
        return cls(**{
            "id": alias.id,
            "doi_tuong_id": alias.doi_tuong_id,
            "ma_bi_danh": alias.ma_bi_danh,
            "ten_hien_thi": alias.ten_hien_thi,
            "ngon_ngu": alias.language.ma_ngon_ngu if alias.language else None,
        })

    class Config:
        from_attributes = True


class ObjectAliasUpsertRequest(BaseModel):
    doi_tuong_id: int
    ma_bi_danh: str
    ten_hien_thi: Optional[str] = None
    ngon_ngu: Optional[str] = "en"


class ObjectAliasUpdateRequest(BaseModel):
    ma_bi_danh: str
    ten_hien_thi: Optional[str] = None
    ngon_ngu: Optional[str] = "en"


class ObjectListItem(BaseModel):
    id: int
    ma_doi_tuong: Optional[str] = None
    danh_muc_id: Optional[int] = None
    category_name: Optional[str] = None
    translation_count: int = 0
    pending_translation_count: int = 0
    has_image: bool = False
    aliases: List[ObjectAliasItem] = []
    nguon_tao: Optional[str] = None
    nguoi_tao_id: Optional[int] = None
    du_doan_ai_id: Optional[int] = None
    nguoi_duyet_id: Optional[int] = None
    thoi_gian_duyet: Optional[datetime] = None

    class Config:
        from_attributes = True


class ObjectDetailResponse(BaseModel):
    id: int
    ma_doi_tuong: Optional[str] = None
    danh_muc_id: Optional[int] = None
    category_name: Optional[str] = None
    translation_count: int = 0
    pending_translation_count: int = 0
    has_image: bool = False
    aliases: List[ObjectAliasItem] = []
    nguon_tao: Optional[str] = None
    nguoi_tao_id: Optional[int] = None
    du_doan_ai_id: Optional[int] = None
    nguoi_duyet_id: Optional[int] = None
    thoi_gian_duyet: Optional[datetime] = None

    class Config:
        from_attributes = True


class ObjectCreateRequest(BaseModel):
    ma_doi_tuong: str
    danh_muc_id: Optional[int] = None


class ObjectUpdateRequest(BaseModel):
    danh_muc_id: Optional[int] = None


# ---------------------------------------------------------------------------
# Translation
# ---------------------------------------------------------------------------

class ExampleItem(BaseModel):
    id: int
    cau_vi_du: Optional[str] = None
    dich_nghia: Optional[str] = None


class TranslationAdminResponse(BaseModel):
    id: int
    doi_tuong_id: Optional[int] = None
    object_code: Optional[str] = None
    lang_code: Optional[str] = None
    tu_vung: Optional[str] = None
    phien_am: Optional[str] = None
    loai_tu: Optional[str] = None
    dinh_nghia: Optional[str] = None
    am_thanh_url: Optional[str] = None
    da_xac_nhan: bool = False
    example_count: int = 0
    examples: List[ExampleItem] = []
    du_doan_ai_id: Optional[int] = None
    nguoi_tao_id: Optional[int] = None
    nguoi_duyet_id: Optional[int] = None
    thoi_gian_duyet: Optional[datetime] = None

    class Config:
        from_attributes = True


class TranslationCreateRequest(BaseModel):
    doi_tuong_id: int
    lang_code: str
    tu_vung: str
    phien_am: Optional[str] = None
    loai_tu: Optional[str] = None
    dinh_nghia: Optional[str] = None
    example_sentences: List[str] = []


class TranslationUpdateRequest(BaseModel):
    tu_vung: Optional[str] = None
    phien_am: Optional[str] = None
    loai_tu: Optional[str] = None
    dinh_nghia: Optional[str] = None
    da_xac_nhan: Optional[bool] = None
    example_sentences: Optional[List[str]] = None


# ---------------------------------------------------------------------------
# User
# ---------------------------------------------------------------------------

class UserAdminResponse(BaseModel):
    id: int
    ten_dang_nhap: str
    email: str
    ho_ten: Optional[str] = None
    vai_tro: Optional[str] = None
    trang_thai: Optional[str] = None
    ngay_tao: Optional[datetime] = None
    lan_dang_nhap_cuoi: Optional[datetime] = None

    class Config:
        from_attributes = True


class UserRoleUpdate(BaseModel):
    vai_tro_id: int


class UserStatusUpdate(BaseModel):
    trang_thai_id: int


class UserPasswordReset(BaseModel):
    new_password: str

    @field_validator("new_password")
    @classmethod
    def validate_new_password(cls, v: str) -> str:
        return validate_password_strength(v, "Mật khẩu mới")


# ---------------------------------------------------------------------------
# Scan History
# ---------------------------------------------------------------------------

class ScanHistoryAdminItem(BaseModel):
    id: int
    nguoi_dung_id: Optional[int] = None
    username: Optional[str] = None
    doi_tuong_id: Optional[int] = None
    object_code: Optional[str] = None
    url_anh: Optional[str] = None
    do_tin_cay: Optional[float] = None
    thoi_gian: Optional[datetime] = None
    has_pending_prediction: bool = False


# ---------------------------------------------------------------------------
# User Stats
# ---------------------------------------------------------------------------

class UserStatsAdminResponse(BaseModel):
    nguoi_dung_id: int
    total_scans: int = 0
    total_reviews: int = 0
    total_learned: int = 0
    streak_hien_tai: int = 0
    streak_dai_nhat: int = 0
    last_scan_at: Optional[datetime] = None
    last_review_at: Optional[datetime] = None


# ---------------------------------------------------------------------------
# Dashboard
# ---------------------------------------------------------------------------

class DashboardStats(BaseModel):
    total_users: int = 0
    total_objects: int = 0
    total_translations: int = 0
    total_scans: int = 0
    pending_predictions: int = 0
    approved_predictions: int = 0
    rejected_predictions: int = 0
    objects_without_images: int = 0
