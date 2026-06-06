from app.schemas.admin import ObjectAliasItem


class _Language:
    ma_ngon_ngu = "en"


class _Alias:
    id = 1
    doi_tuong_id = 2
    ma_bi_danh = "cup"
    ten_hien_thi = "Cup"
    language = _Language()


def test_object_alias_item_keeps_standard_pydantic_model_validate():
    item = ObjectAliasItem.model_validate(
        {
            "id": 1,
            "doi_tuong_id": 2,
            "ma_bi_danh": "cup",
            "ten_hien_thi": "Cup",
            "ngon_ngu": "en",
        }
    )

    assert item.ma_bi_danh == "cup"
    assert item.ngon_ngu == "en"


def test_object_alias_item_from_alias_reads_language_relationship():
    item = ObjectAliasItem.from_alias(_Alias())

    assert item.id == 1
    assert item.doi_tuong_id == 2
    assert item.ma_bi_danh == "cup"
    assert item.ten_hien_thi == "Cup"
    assert item.ngon_ngu == "en"
