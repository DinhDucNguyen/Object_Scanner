"""
Seed local vocabulary for the YOLOv10 COCO classes used by the Android app.

The script is intentionally idempotent:
- existing objects are reused;
- legacy object codes with spaces are renamed to the canonical Android code
  when that canonical code does not already exist;
- existing translations are completed, confirmed, and never duplicated;
- examples are added only when the same sentence is not already present.

Run from the backend directory:
    python scripts/seed_yolo_coco.py

Optional:
    python scripts/seed_yolo_coco.py --list
    python scripts/seed_yolo_coco.py --dry-run
    python scripts/seed_yolo_coco.py --fetch-dictionary
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib.parse import quote

from sqlalchemy.orm import Session

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from app.db.session import SessionLocal
from app.models.category import Category
from app.models.example import ViDu
from app.models.language import Language
from app.models.object import Object
from app.models.translation import NguonDuLieu, Translation
from app.services.tts_service import TTSService


TTS_SERVICE = TTSService()


PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_DETECTOR = (
    PROJECT_ROOT
    / "android"
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "duc"
    / "objectlanguage"
    / "ui"
    / "scan"
    / "ObjectDetectorHelper.kt"
)


ANDROID_NORMALIZATION_OVERRIDES = {
    "mobile phone": "cell_phone",
    "mobile device": "cell_phone",
    "smartphone": "cell_phone",
    "cellphone": "cell_phone",
    "computer mouse": "mouse",
    "pc mouse": "mouse",
    "laptop computer": "laptop",
    "notebook computer": "laptop",
    "notebook": "laptop",
    "table": "chair",
    "desk": "chair",
    "tv": "television",
    "bike": "bicycle",
}


CATEGORY_DESCRIPTIONS = {
    "Con người": "Người và các vai trò cơ bản",
    "Phương tiện": "Xe cộ và phương tiện di chuyển",
    "Động vật": "Các loài động vật",
    "Phụ kiện": "Vật dụng cá nhân và phụ kiện",
    "Thể thao": "Dụng cụ và vật dụng thể thao",
    "Nhà bếp": "Đồ dùng nhà bếp và thiết bị bếp",
    "Thực phẩm": "Trái cây, rau củ và món ăn",
    "Nội thất": "Đồ dùng trong nhà và nội thất",
    "Điện tử": "Thiết bị điện tử và công nghệ",
    "Đồ gia dụng": "Vật dụng sinh hoạt hằng ngày",
    "Đồ dùng học tập": "Sách vở và dụng cụ học tập",
    "Biển báo & đô thị": "Vật thể thường gặp ở đường phố và nơi công cộng",
}


FALLBACK_PHONETICS = {
    "baseball bat": "/ˈbeɪsbɔːl bæt/",
    "baseball glove": "/ˈbeɪsbɔːl ɡlʌv/",
    "cell phone": "/ˈsel foʊn/",
    "dining table": "/ˈdaɪnɪŋ ˌteɪbəl/",
    "fire hydrant": "/ˈfaɪər ˌhaɪdrənt/",
    "frisbee": "/ˈfrɪzbi/",
    "hair dryer": "/ˈher ˌdraɪər/",
    "kite": "/kaɪt/",
    "mouse": "/maʊs/",
    "parking meter": "/ˈpɑːrkɪŋ ˌmiːtər/",
    "potted plant": "/ˈpɑːtɪd plænt/",
    "skateboard": "/ˈskeɪtbɔːrd/",
    "skis": "/skiːz/",
    "sports ball": "/ˈspɔːrts bɔːl/",
    "stop sign": "/ˈstɑːp saɪn/",
    "tennis racket": "/ˈtenɪs ˌrækɪt/",
    "traffic light": "/ˈtræfɪk laɪt/",
    "wine glass": "/ˈwaɪn ɡlæs/",
}


@dataclass(frozen=True)
class SeedEntry:
    label: str
    vi_name: str
    vi_description: str
    category: str
    word_name: str | None = None
    example_object: str | None = None
    example_subject: str | None = None


SEED_ENTRIES: list[SeedEntry] = [
    SeedEntry("person", "người", "một con người nói chung, có thể là nam, nữ hoặc trẻ em.", "Con người"),
    SeedEntry("bicycle", "xe đạp", "phương tiện hai bánh chạy bằng sức đạp của người dùng.", "Phương tiện"),
    SeedEntry("car", "ô tô", "phương tiện bốn bánh dùng để chở người hoặc hàng hóa trên đường bộ.", "Phương tiện"),
    SeedEntry("motorcycle", "xe máy", "phương tiện hai bánh có động cơ, thường dùng để đi lại hằng ngày.", "Phương tiện"),
    SeedEntry("airplane", "máy bay", "phương tiện bay có cánh, dùng để vận chuyển người hoặc hàng hóa bằng đường hàng không.", "Phương tiện", example_object="an airplane"),
    SeedEntry("bus", "xe buýt", "phương tiện công cộng lớn dùng để chở nhiều hành khách.", "Phương tiện"),
    SeedEntry("train", "tàu hỏa", "phương tiện chạy trên đường ray, dùng để chở khách hoặc hàng hóa.", "Phương tiện"),
    SeedEntry("truck", "xe tải", "phương tiện lớn dùng để vận chuyển hàng hóa.", "Phương tiện"),
    SeedEntry("boat", "thuyền", "phương tiện di chuyển trên nước.", "Phương tiện"),
    SeedEntry("traffic light", "đèn giao thông", "thiết bị đèn tín hiệu dùng để điều khiển giao thông.", "Biển báo & đô thị"),
    SeedEntry("fire hydrant", "trụ nước cứu hỏa", "điểm cấp nước trên đường phố dùng cho công tác chữa cháy.", "Biển báo & đô thị"),
    SeedEntry("stop sign", "biển báo dừng", "biển báo yêu cầu phương tiện dừng lại trước khi tiếp tục di chuyển.", "Biển báo & đô thị"),
    SeedEntry("parking meter", "máy thu phí đỗ xe", "thiết bị dùng để tính hoặc thu phí khi đỗ xe.", "Biển báo & đô thị"),
    SeedEntry("bench", "ghế dài", "ghế dài thường đặt ở công viên, trạm chờ hoặc nơi công cộng.", "Biển báo & đô thị"),
    SeedEntry("bird", "chim", "động vật có lông vũ, cánh và thường có khả năng bay.", "Động vật"),
    SeedEntry("cat", "mèo", "động vật có vú nhỏ, thường được nuôi làm thú cưng trong nhà.", "Động vật"),
    SeedEntry("dog", "chó", "động vật có vú thường được nuôi để giữ nhà, làm bạn hoặc hỗ trợ con người.", "Động vật"),
    SeedEntry("horse", "ngựa", "động vật bốn chân lớn, từng được dùng nhiều để cưỡi và kéo xe.", "Động vật"),
    SeedEntry("sheep", "cừu", "động vật nuôi có lông dày, thường được lấy len, thịt hoặc sữa.", "Động vật", example_subject="Sheep"),
    SeedEntry("cow", "bò", "động vật nuôi lớn, thường được lấy sữa, thịt hoặc dùng trong nông nghiệp.", "Động vật"),
    SeedEntry("elephant", "voi", "động vật lớn có vòi dài và đôi tai rộng.", "Động vật", example_object="an elephant"),
    SeedEntry("bear", "gấu", "động vật có vú lớn, thân chắc khỏe và thường sống trong rừng hoặc vùng lạnh.", "Động vật"),
    SeedEntry("zebra", "ngựa vằn", "động vật họ ngựa có các sọc đen trắng đặc trưng.", "Động vật"),
    SeedEntry("giraffe", "hươu cao cổ", "động vật cổ dài, chân cao, thường sống ở thảo nguyên châu Phi.", "Động vật"),
    SeedEntry("backpack", "ba lô", "túi đeo trên lưng dùng để đựng sách vở, quần áo hoặc vật dụng cá nhân.", "Phụ kiện"),
    SeedEntry("umbrella", "ô", "vật dụng có tán xòe dùng để che mưa hoặc che nắng.", "Phụ kiện", example_object="an umbrella"),
    SeedEntry("handbag", "túi xách", "túi nhỏ hoặc vừa dùng để mang theo đồ cá nhân.", "Phụ kiện"),
    SeedEntry("tie", "cà vạt", "phụ kiện vải đeo quanh cổ, thường dùng với áo sơ mi hoặc vest.", "Phụ kiện"),
    SeedEntry("suitcase", "vali", "hành lý có tay cầm dùng để đựng quần áo và đồ dùng khi đi xa.", "Phụ kiện"),
    SeedEntry("frisbee", "đĩa bay", "đĩa nhựa nhẹ dùng để ném và bắt trong trò chơi ngoài trời.", "Thể thao"),
    SeedEntry("skis", "ván trượt tuyết", "một cặp ván dài gắn vào chân để trượt trên tuyết.", "Thể thao", example_object="a pair of skis", example_subject="Skis"),
    SeedEntry("snowboard", "ván trượt tuyết đơn", "tấm ván dùng để trượt trên tuyết bằng cả hai chân.", "Thể thao"),
    SeedEntry("sports ball", "bóng thể thao", "quả bóng dùng trong các môn thể thao hoặc trò chơi vận động.", "Thể thao"),
    SeedEntry("kite", "diều", "vật nhẹ có khung và dây, được thả bay nhờ gió.", "Thể thao"),
    SeedEntry("baseball bat", "gậy bóng chày", "gậy dùng để đánh bóng trong môn bóng chày.", "Thể thao"),
    SeedEntry("baseball glove", "găng tay bóng chày", "găng tay da dùng để bắt bóng trong môn bóng chày.", "Thể thao"),
    SeedEntry("skateboard", "ván trượt", "tấm ván có bánh xe dùng để trượt hoặc biểu diễn kỹ thuật.", "Thể thao"),
    SeedEntry("surfboard", "ván lướt sóng", "tấm ván dài dùng để lướt trên mặt sóng.", "Thể thao"),
    SeedEntry("tennis racket", "vợt tennis", "dụng cụ có khung và mặt lưới dùng để đánh bóng tennis.", "Thể thao"),
    SeedEntry("bottle", "chai", "vật chứa có cổ hẹp, thường dùng để đựng nước hoặc chất lỏng.", "Nhà bếp"),
    SeedEntry("wine glass", "ly rượu vang", "ly có chân cao dùng để uống rượu vang hoặc đồ uống tương tự.", "Nhà bếp"),
    SeedEntry("cup", "cốc", "vật dùng để uống nước, trà, cà phê hoặc đồ uống khác.", "Nhà bếp"),
    SeedEntry("fork", "nĩa", "dụng cụ ăn uống có các răng nhọn dùng để xiên hoặc giữ thức ăn.", "Nhà bếp"),
    SeedEntry("knife", "dao", "dụng cụ có lưỡi sắc dùng để cắt, thái hoặc gọt.", "Nhà bếp"),
    SeedEntry("spoon", "thìa", "dụng cụ ăn uống có lòng lõm dùng để múc thức ăn hoặc chất lỏng.", "Nhà bếp"),
    SeedEntry("bowl", "bát", "vật chứa dạng lòng sâu dùng để đựng thức ăn.", "Nhà bếp"),
    SeedEntry("banana", "chuối", "trái cây dài, vỏ vàng khi chín và có vị ngọt.", "Thực phẩm"),
    SeedEntry("apple", "táo", "trái cây tròn, thường có màu đỏ, xanh hoặc vàng.", "Thực phẩm", example_object="an apple"),
    SeedEntry("sandwich", "bánh mì sandwich", "món ăn gồm nhân kẹp giữa hai lát bánh mì.", "Thực phẩm"),
    SeedEntry("orange", "cam", "trái cây có vỏ màu cam, mọng nước và giàu vitamin C.", "Thực phẩm", example_object="an orange"),
    SeedEntry("broccoli", "bông cải xanh", "loại rau màu xanh có phần hoa nhỏ mọc thành cụm.", "Thực phẩm"),
    SeedEntry("carrot", "cà rốt", "củ màu cam, thường dùng trong món ăn hoặc nước ép.", "Thực phẩm"),
    SeedEntry("hot dog", "bánh mì xúc xích", "món ăn gồm xúc xích đặt trong bánh mì dài.", "Thực phẩm"),
    SeedEntry("pizza", "bánh pizza", "món bánh dẹt phủ sốt, phô mai và nhiều loại topping.", "Thực phẩm"),
    SeedEntry("donut", "bánh donut", "bánh ngọt thường có hình vòng và được phủ đường hoặc kem.", "Thực phẩm"),
    SeedEntry("cake", "bánh kem", "món bánh ngọt thường dùng trong sinh nhật hoặc dịp đặc biệt.", "Thực phẩm"),
    SeedEntry("chair", "ghế", "đồ nội thất có mặt ngồi và lưng tựa, dùng để ngồi.", "Nội thất"),
    SeedEntry("couch", "ghế sofa", "ghế dài có đệm mềm, thường đặt trong phòng khách.", "Nội thất"),
    SeedEntry("potted plant", "cây trồng trong chậu", "cây được trồng trong chậu để trang trí hoặc chăm sóc trong nhà.", "Nội thất"),
    SeedEntry("bed", "giường", "đồ nội thất dùng để nằm ngủ hoặc nghỉ ngơi.", "Nội thất"),
    SeedEntry("dining table", "bàn ăn", "bàn dùng để bày thức ăn và dùng bữa.", "Nội thất"),
    SeedEntry("toilet", "bồn cầu", "thiết bị vệ sinh dùng trong nhà tắm hoặc nhà vệ sinh.", "Đồ gia dụng"),
    SeedEntry("tv", "ti vi", "thiết bị điện tử có màn hình dùng để xem chương trình, phim hoặc video.", "Điện tử", word_name="television"),
    SeedEntry("laptop", "máy tính xách tay", "máy tính cá nhân nhỏ gọn có thể mang theo.", "Điện tử"),
    SeedEntry("mouse", "chuột máy tính", "thiết bị điều khiển con trỏ trên máy tính.", "Điện tử"),
    SeedEntry("remote", "điều khiển từ xa", "thiết bị dùng để điều khiển TV hoặc thiết bị điện tử từ xa.", "Điện tử"),
    SeedEntry("keyboard", "bàn phím", "thiết bị có các phím dùng để nhập chữ, số và lệnh vào máy tính.", "Điện tử"),
    SeedEntry("cell phone", "điện thoại di động", "thiết bị liên lạc cầm tay dùng để gọi, nhắn tin và truy cập Internet.", "Điện tử"),
    SeedEntry("microwave", "lò vi sóng", "thiết bị nhà bếp dùng sóng vi ba để hâm nóng hoặc nấu thức ăn.", "Nhà bếp"),
    SeedEntry("oven", "lò nướng", "thiết bị dùng để nướng hoặc làm chín thức ăn bằng nhiệt.", "Nhà bếp", example_object="an oven"),
    SeedEntry("toaster", "máy nướng bánh mì", "thiết bị dùng để nướng lát bánh mì.", "Nhà bếp"),
    SeedEntry("sink", "bồn rửa", "chậu có vòi nước dùng để rửa tay, rửa bát hoặc rửa thực phẩm.", "Nhà bếp"),
    SeedEntry("refrigerator", "tủ lạnh", "thiết bị làm lạnh dùng để bảo quản thực phẩm và đồ uống.", "Nhà bếp"),
    SeedEntry("book", "sách", "tập hợp các trang giấy hoặc nội dung số dùng để đọc và học tập.", "Đồ dùng học tập"),
    SeedEntry("clock", "đồng hồ", "thiết bị dùng để hiển thị hoặc đo thời gian.", "Đồ gia dụng"),
    SeedEntry("vase", "bình hoa", "vật chứa trang trí dùng để cắm hoa.", "Đồ gia dụng"),
    SeedEntry("scissors", "kéo", "dụng cụ có hai lưỡi dùng để cắt giấy, vải hoặc vật liệu mỏng.", "Đồ dùng học tập", example_object="a pair of scissors", example_subject="Scissors"),
    SeedEntry("teddy bear", "gấu bông", "đồ chơi nhồi bông có hình con gấu.", "Phụ kiện"),
    SeedEntry("hair drier", "máy sấy tóc", "thiết bị thổi khí nóng dùng để làm khô tóc.", "Đồ gia dụng", word_name="hair dryer"),
    SeedEntry("toothbrush", "bàn chải đánh răng", "dụng cụ có lông chải dùng để làm sạch răng.", "Đồ gia dụng"),
]


def normalize_object_code(label: str) -> str:
    normalized = label.lower().strip()
    if normalized in ANDROID_NORMALIZATION_OVERRIDES:
        return ANDROID_NORMALIZATION_OVERRIDES[normalized]
    return normalized.replace(" ", "_").replace("-", "_")


def extract_yolo_labels() -> list[str]:
    text = ANDROID_DETECTOR.read_text(encoding="utf-8")
    match = re.search(r"COCO_LABELS\s*=\s*listOf\((.*?)\)", text, re.S)
    if not match:
        raise RuntimeError(f"Cannot find COCO_LABELS in {ANDROID_DETECTOR}")
    return re.findall(r'"([^"]+)"', match.group(1))


def ensure_language(db: Session) -> Language:
    lang = db.query(Language).filter(Language.ma_ngon_ngu == "en").first()
    if lang:
        if not lang.dang_hoat_dong:
            lang.dang_hoat_dong = True
        return lang

    lang = Language(ma_ngon_ngu="en", ten_ngon_ngu="English", dang_hoat_dong=True)
    db.add(lang)
    db.flush()
    return lang


def ensure_category(db: Session, name: str, stats: dict[str, int]) -> Category:
    category = db.query(Category).filter(
        Category.ten_danh_muc == name,
        Category.thoi_gian_xoa.is_(None),
    ).first()
    if category:
        if not category.mo_ta:
            category.mo_ta = CATEGORY_DESCRIPTIONS.get(name)
        return category

    category = Category(
        ten_danh_muc=name,
        mo_ta=CATEGORY_DESCRIPTIONS.get(name),
    )
    db.add(category)
    db.flush()
    stats["categories_created"] += 1
    return category


def find_or_create_object(
    db: Session,
    entry: SeedEntry,
    category: Category,
    stats: dict[str, int],
) -> Object:
    object_code = normalize_object_code(entry.label)
    obj = db.query(Object).filter(Object.ma_doi_tuong == object_code).first()
    if obj:
        stats["objects_reused"] += 1
    else:
        legacy_code = entry.label.lower().strip()
        legacy = None
        if legacy_code != object_code:
            legacy = db.query(Object).filter(Object.ma_doi_tuong == legacy_code).first()

        if legacy:
            legacy.ma_doi_tuong = object_code
            obj = legacy
            stats["objects_renamed"] += 1
        else:
            obj = Object(ma_doi_tuong=object_code, muc_do_kho=1)
            db.add(obj)
            db.flush()
            stats["objects_created"] += 1

    if obj.thoi_gian_xoa is not None:
        obj.thoi_gian_xoa = None
    if obj.danh_muc_id is None:
        obj.danh_muc_id = category.id
    if obj.muc_do_kho is None:
        obj.muc_do_kho = 1
    return obj


def dictionary_lookup(word: str) -> dict[str, Any]:
    try:
        import httpx
    except ImportError:
        return {}

    url = f"https://api.dictionaryapi.dev/api/v2/entries/en/{quote(word)}"
    try:
        response = httpx.get(url, timeout=6.0)
        if response.status_code != 200:
            return {}
        payload = response.json()
    except Exception:
        return {}

    if not isinstance(payload, list) or not payload:
        return {}

    first = payload[0]
    phonetic = first.get("phonetic")
    if not phonetic:
        for item in first.get("phonetics") or []:
            if item.get("text"):
                phonetic = item["text"]
                break

    part_of_speech = None
    meanings = first.get("meanings") or []
    if meanings:
        part_of_speech = meanings[0].get("partOfSpeech")

    return {
        "phonetic": phonetic,
        "part_of_speech": part_of_speech,
    }


def english_article(word: str) -> str:
    first = word[:1].lower()
    return "an" if first in {"a", "e", "i", "o", "u"} else "a"


def upper_first(text: str) -> str:
    return text[:1].upper() + text[1:] if text else text


def build_examples(entry: SeedEntry, word_name: str) -> list[tuple[str, str]]:
    object_phrase = entry.example_object or f"{english_article(word_name)} {word_name}"
    subject = entry.example_subject or object_phrase
    return [
        (
            f"I can see {object_phrase} in the picture.",
            f"Tôi có thể thấy {entry.vi_name} trong bức ảnh.",
        ),
        (
            f"{upper_first(subject)} is easy to recognize.",
            f"{upper_first(entry.vi_name)} rất dễ nhận ra.",
        ),
        (
            f'The word "{word_name}" names this object in English.',
            f'Từ "{word_name}" dùng để gọi {entry.vi_name} bằng tiếng Anh.',
        ),
    ]


def ensure_translation(
    db: Session,
    obj: Object,
    lang: Language,
    entry: SeedEntry,
    dictionary_meta: dict[str, Any],
    stats: dict[str, int],
) -> Translation:
    word_name = entry.word_name or entry.label
    definition = f"{entry.vi_name}: {entry.vi_description}"
    phonetic = dictionary_meta.get("phonetic") or FALLBACK_PHONETICS.get(word_name)
    part_of_speech = dictionary_meta.get("part_of_speech") or "noun"
    audio_url = TTS_SERVICE.get_audio_url(word_name, "en")

    trans = db.query(Translation).filter(
        Translation.doi_tuong_id == obj.id,
        Translation.ngon_ngu_id == lang.id,
    ).first()

    if not trans:
        trans = Translation(
            doi_tuong_id=obj.id,
            ngon_ngu_id=lang.id,
            tu_vung=word_name,
            phien_am=phonetic,
            loai_tu=part_of_speech,
            dinh_nghia=definition,
            am_thanh_url=audio_url,
            nguon_du_lieu=NguonDuLieu.thu_cong,
            da_xac_nhan=True,
        )
        db.add(trans)
        db.flush()
        stats["translations_created"] += 1
    else:
        changed = False
        if trans.thoi_gian_xoa is not None:
            trans.thoi_gian_xoa = None
            changed = True
        if not trans.tu_vung:
            trans.tu_vung = word_name
            changed = True
        if not trans.phien_am and phonetic:
            trans.phien_am = phonetic
            changed = True
        if not trans.loai_tu:
            trans.loai_tu = part_of_speech
            changed = True
        if not trans.dinh_nghia:
            trans.dinh_nghia = definition
            changed = True
        if trans.nguon_du_lieu is None:
            trans.nguon_du_lieu = NguonDuLieu.thu_cong
            changed = True
        if not trans.am_thanh_url:
            trans.am_thanh_url = audio_url
            changed = True
        if not trans.da_xac_nhan:
            trans.da_xac_nhan = True
            changed = True
        if changed:
            stats["translations_updated"] += 1

    return trans


def ensure_examples(db: Session, trans: Translation, entry: SeedEntry, stats: dict[str, int]) -> None:
    word_name = entry.word_name or entry.label
    existing = {
        (example.cau_vi_du or "").strip().lower()
        for example in (trans.examples or [])
        if example.cau_vi_du
    }

    for sentence, translated in build_examples(entry, word_name):
        key = sentence.strip().lower()
        if key in existing:
            continue
        db.add(ViDu(
            ban_dich_id=trans.id,
            cau_vi_du=sentence,
            dich_nghia=translated,
            nguon_du_lieu="seed_yolo_coco",
        ))
        existing.add(key)
        stats["examples_created"] += 1


def validate_seed_entries(labels: list[str]) -> None:
    label_set = set(labels)
    seed_labels = {entry.label for entry in SEED_ENTRIES}
    missing = sorted(label_set - seed_labels)
    extra = sorted(seed_labels - label_set)
    if missing or extra:
        problems = []
        if missing:
            problems.append(f"missing seed data for: {', '.join(missing)}")
        if extra:
            problems.append(f"seed data not in YOLO labels: {', '.join(extra)}")
        raise RuntimeError("; ".join(problems))


def print_label_list(labels: list[str]) -> None:
    entries_by_label = {entry.label: entry for entry in SEED_ENTRIES}
    print(f"YOLO COCO classes: {len(labels)}")
    for index, label in enumerate(labels, start=1):
        entry = entries_by_label[label]
        print(
            f"{index:02d}. {label:<16} -> "
            f"{normalize_object_code(label):<16} | {entry.word_name or label:<14} | {entry.vi_name}"
        )


def seed(fetch_dictionary: bool, dry_run: bool) -> dict[str, int]:
    labels = extract_yolo_labels()
    validate_seed_entries(labels)

    stats = {
        "labels": len(labels),
        "categories_created": 0,
        "objects_created": 0,
        "objects_reused": 0,
        "objects_renamed": 0,
        "translations_created": 0,
        "translations_updated": 0,
        "examples_created": 0,
        "dictionary_hits": 0,
    }

    entries_by_label = {entry.label: entry for entry in SEED_ENTRIES}
    db = SessionLocal()
    try:
        lang = ensure_language(db)
        for label in labels:
            entry = entries_by_label[label]
            category = ensure_category(db, entry.category, stats)
            obj = find_or_create_object(db, entry, category, stats)

            meta: dict[str, Any] = {}
            if fetch_dictionary:
                meta = dictionary_lookup(entry.word_name or entry.label)
                if meta:
                    stats["dictionary_hits"] += 1

            trans = ensure_translation(db, obj, lang, entry, meta, stats)
            ensure_examples(db, trans, entry, stats)

        if dry_run:
            db.rollback()
        else:
            db.commit()
        return stats
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed YOLO COCO vocabulary into the local database.")
    parser.add_argument("--list", action="store_true", help="Print YOLO classes and exit.")
    parser.add_argument("--dry-run", action="store_true", help="Run without committing changes.")
    parser.add_argument(
        "--fetch-dictionary",
        action="store_true",
        help="Use the free dictionaryapi.dev endpoint to fill missing IPA/POS where available.",
    )
    args = parser.parse_args()

    labels = extract_yolo_labels()
    validate_seed_entries(labels)
    if args.list:
        print_label_list(labels)
        return

    stats = seed(fetch_dictionary=args.fetch_dictionary, dry_run=args.dry_run)
    mode = "DRY RUN" if args.dry_run else "COMMITTED"
    print(f"Seed YOLO COCO vocabulary: {mode}")
    for key, value in stats.items():
        print(f"{key}: {value}")


if __name__ == "__main__":
    main()
