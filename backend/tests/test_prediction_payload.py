import json

from app.services.prediction_payload import (
    PREDICTION_PAYLOAD_SCHEMA_VERSION,
    dump_prediction_payload,
    load_prediction_payload,
)


def test_load_prediction_payload_normalizes_current_shape():
    raw = json.dumps(
        {
            "object_code": "Coffee Mug",
            "category": "Kitchen",
            "translations": [
                {
                    "language_code": "EN",
                    "word": "mug",
                    "phien_am": "/mug/",
                    "loai_tu": "noun",
                    "dinh_nghia": "a cup with a handle",
                    "examples": [
                        {"sentence": "This is my mug.", "translation": "Day la coc cua toi."},
                        "The mug is blue.",
                    ],
                }
            ],
        }
    )

    payload = load_prediction_payload(raw)

    assert payload["schema_version"] == PREDICTION_PAYLOAD_SCHEMA_VERSION
    assert payload["object_code"] == "coffee_mug"
    assert payload["category"] == "Kitchen"
    assert payload["translations"][0]["lang_code"] == "en"
    assert payload["translations"][0]["word_name"] == "mug"
    assert payload["translations"][0]["phonetic"] == "/mug/"
    assert payload["translations"][0]["part_of_speech"] == "noun"
    assert payload["translations"][0]["definition"] == "a cup with a handle"
    assert payload["translations"][0]["example_sentences"] == [
        {"en": "This is my mug.", "vi": "Day la coc cua toi."},
        "The mug is blue.",
    ]


def test_load_prediction_payload_supports_legacy_flat_shape():
    raw = json.dumps(
        {
            "word_name": "eraser",
            "phonetic": "/i-rei-ser/",
            "part_of_speech": "noun",
            "definition": "a tool for removing pencil marks",
            "example_sentences": ["I need an eraser."],
        }
    )

    payload = load_prediction_payload(raw, fallback_object_code="school eraser")

    assert payload["object_code"] == "eraser"
    assert payload["translations"] == [
        {
            "lang_code": "en",
            "word_name": "eraser",
            "phonetic": "/i-rei-ser/",
            "part_of_speech": "noun",
            "definition": "a tool for removing pencil marks",
            "example_sentences": ["I need an eraser."],
        }
    ]


def test_load_prediction_payload_returns_minimal_payload_for_invalid_json():
    payload = load_prediction_payload("{not-json", fallback_object_code="Water Bottle")

    assert payload["object_code"] == "water_bottle"
    assert payload["translations"] == []
    assert payload["_payload_error"] == "invalid_json"


def test_dump_prediction_payload_always_writes_normalized_json():
    dumped = dump_prediction_payload({"ma_doi_tuong": "Tea Cup", "word": "cup"})

    assert json.loads(dumped)["object_code"] == "tea_cup"
    assert json.loads(dumped)["translations"][0]["word_name"] == "cup"
