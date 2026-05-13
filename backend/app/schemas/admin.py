# pyrefly: ignore [missing-import]
from pydantic import BaseModel
# pyrefly: ignore [missing-import]
from typing import Optional, List
from datetime import datetime



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
    example_sentences: List[str] = []    


class VocabPayloadSchema(BaseModel):
    """Toàn bộ payload parse từ DuDoanAI.mo_ta."""
    object_code: str
    category: Optional[str] = None
    translations: List[VocabTranslationSchema] = []




class PredictionListItem(BaseModel):
    id: int
    scan_id: int
    nhan_du_doan: Optional[str] = None
    do_tin_cay: Optional[float] = None
    trang_thai: str                         
    thoi_gian: Optional[datetime] = None

    class Config:
        from_attributes = True


class PredictionDetailResponse(BaseModel):
    id: int
    scan_id: int
    nhan_du_doan: Optional[str] = None
    do_tin_cay: Optional[float] = None
    trang_thai: str
    thoi_gian: Optional[datetime] = None
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
    difficulty_level: int = 1


class ApproveResponse(BaseModel):
    success: bool
    message: str
    prediction_id: int
    object_id: Optional[int] = None
    translation_id: Optional[int] = None
    examples_created: int = 0


class RejectResponse(BaseModel):
    success: bool
    message: str
    prediction_id: int
