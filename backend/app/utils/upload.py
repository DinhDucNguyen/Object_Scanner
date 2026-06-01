from fastapi import HTTPException, UploadFile

from app.core.constants import MAX_IMAGE_UPLOAD_BYTES


async def read_upload_bytes(
    file: UploadFile,
    max_bytes: int = MAX_IMAGE_UPLOAD_BYTES,
    chunk_size: int = 1024 * 1024,
) -> bytes:
    chunks: list[bytes] = []
    total = 0

    while True:
        chunk = await file.read(chunk_size)
        if not chunk:
            break
        total += len(chunk)
        if total > max_bytes:
            max_mb = max_bytes // (1024 * 1024)
            raise HTTPException(400, f"File qua lon, toi da {max_mb}MB")
        chunks.append(chunk)

    return b"".join(chunks)
