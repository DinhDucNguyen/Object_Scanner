import json
from typing import Any

from app.repositories.object_repo import normalize_object_code


PREDICTION_PAYLOAD_SCHEMA_VERSION = 2


def load_prediction_payload(raw_text: str | None, fallback_object_code: str | None = None) -> dict:
    if not raw_text:
        return _minimal_payload(fallback_object_code)
    try:
        raw = json.loads(raw_text)
    except Exception:
        payload = _minimal_payload(fallback_object_code)
        payload["_payload_error"] = "invalid_json"
        return payload
    if not isinstance(raw, dict):
        payload = _minimal_payload(fallback_object_code)
        payload["_payload_error"] = "not_object"
        return payload
    return normalize_prediction_payload(raw, fallback_object_code)


def dump_prediction_payload(payload: dict, fallback_object_code: str | None = None) -> str:
    normalized = normalize_prediction_payload(payload, fallback_object_code)
    return json.dumps(normalized, ensure_ascii=False)


def normalize_prediction_payload(raw: dict, fallback_object_code: str | None = None) -> dict:
    payload = dict(raw)
    object_code = normalize_object_code(
        payload.get("object_code")
        or payload.get("ma_doi_tuong")
        or payload.get("label")
        or payload.get("word_name")
        or fallback_object_code
        or "unknown"
    )
    if not object_code:
        object_code = "unknown"

    translations = _normalize_translations(payload, object_code)
    payload["schema_version"] = int(payload.get("schema_version") or PREDICTION_PAYLOAD_SCHEMA_VERSION)
    payload["object_code"] = object_code
    payload["category"] = payload.get("category") or payload.get("ten_danh_muc")
    payload["translations"] = translations
    return payload


def _minimal_payload(fallback_object_code: str | None) -> dict:
    object_code = normalize_object_code(fallback_object_code or "unknown") or "unknown"
    return {
        "schema_version": PREDICTION_PAYLOAD_SCHEMA_VERSION,
        "object_code": object_code,
        "category": None,
        "translations": [],
    }


def _normalize_translations(payload: dict, object_code: str) -> list[dict]:
    translations_raw = payload.get("translations")
    if not isinstance(translations_raw, list):
        translations_raw = []

    normalized: list[dict] = []
    for item in translations_raw:
        translation = _normalize_translation(item, object_code)
        if translation:
            normalized.append(translation)

    if not normalized:
        fallback = _normalize_translation(payload, object_code)
        if fallback:
            normalized.append(fallback)
    return normalized


def _normalize_translation(item: Any, object_code: str) -> dict | None:
    if not isinstance(item, dict):
        return None

    word_name = _clean_text(
        item.get("word_name")
        or item.get("word")
        or item.get("tu_vung")
        or item.get("translation")
    )
    if not word_name:
        return None

    lang_code = _clean_text(
        item.get("lang_code")
        or item.get("language_code")
        or item.get("ma_ngon_ngu")
        or "en"
    ) or "en"
    lang_code = lang_code.lower()[:10]

    return {
        "lang_code": lang_code,
        "word_name": word_name,
        "phonetic": _clean_text(item.get("phonetic") or item.get("phien_am")),
        "part_of_speech": _clean_text(item.get("part_of_speech") or item.get("loai_tu")),
        "definition": _clean_text(item.get("definition") or item.get("dinh_nghia")),
        "example_sentences": _normalize_examples(
            item.get("example_sentences")
            or item.get("examples")
            or item.get("vi_du")
            or item.get("sentences")
            or []
        ),
    }


def _normalize_examples(items: Any) -> list[str | dict]:
    if not isinstance(items, list):
        return []
    result: list[str | dict] = []
    for item in items[:3]:
        if isinstance(item, str):
            text = _clean_text(item)
            if text:
                result.append(text)
            continue
        if isinstance(item, dict):
            en = _clean_text(item.get("en") or item.get("sentence") or item.get("cau_vi_du"))
            vi = _clean_text(item.get("vi") or item.get("translation") or item.get("dich_nghia"))
            if en:
                result.append({"en": en, "vi": vi})
    return result


def _clean_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None
