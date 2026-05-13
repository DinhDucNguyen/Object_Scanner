from app.models.object_media import ObjectMedia


def pick_primary_object_image(obj) -> str | None:
    """Return the canonical vocabulary image from AnhDoiTuong for an object."""
    if not obj:
        return None

    primary = next(
        (
            media for media in (obj.media or [])
            if getattr(media, "thoi_gian_xoa", None) is None and media.doi_tuong_chinh
        ),
        None,
    )
    if primary:
        return primary.url

    fallback = next(
        (
            media for media in (obj.media or [])
            if getattr(media, "thoi_gian_xoa", None) is None
        ),
        None,
    )
    return fallback.url if fallback else None


def set_primary_object_image(db, obj, media: ObjectMedia) -> None:
    for existing in obj.media or []:
        if getattr(existing, "thoi_gian_xoa", None) is None:
            existing.doi_tuong_chinh = existing.id == media.id
