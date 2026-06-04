# pyrefly: ignore [missing-import]
from fastapi import APIRouter, Depends, HTTPException, Query
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import Session
from typing import List

from app.db.session import get_db
from app.services.collection_service import CollectionService
from app.services.learning_service import LearningService
from app.schemas.common import CollectionCreate, CollectionResponse, CollectionItemAdd, CollectionDetailResponse, CollectionInsightsResponse, ReviewCardResponse, CollectionPrivacyUpdate, CollectionUpdate
from app.dependencies.get_current_user import get_current_nguoi_dung_id

router = APIRouter(prefix="/api/collections", tags=["Collections"])
collection_service = CollectionService()
learning_service = LearningService()


@router.get("", response_model=List[CollectionResponse])
def get_collections(
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return collection_service.get_collections(db, nguoi_dung_id)


@router.post("", response_model=CollectionResponse)
def create_collection(
    data: CollectionCreate, 
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return collection_service.create_collection(db, nguoi_dung_id, data)


@router.patch("/{collection_id}", response_model=CollectionResponse)
def update_collection(
    collection_id: int,
    data: CollectionUpdate,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return collection_service.update_collection(db, collection_id, nguoi_dung_id, data)


@router.patch("/{collection_id}/privacy", response_model=CollectionResponse)
def update_collection_privacy(
    collection_id: int,
    data: CollectionPrivacyUpdate,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return collection_service.update_collection_privacy(db, collection_id, nguoi_dung_id, data)


@router.post("/{collection_id}/items")
def add_to_collection(
    collection_id: int, 
    data: CollectionItemAdd, 
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return collection_service.add_to_collection(db, collection_id, data, nguoi_dung_id)


@router.get("/explore", response_model=List[CollectionResponse])
def get_public_collections(
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return collection_service.get_public_collections(db, nguoi_dung_id)


@router.get("/{collection_id}", response_model=CollectionDetailResponse)
def get_collection_detail(
    collection_id: int,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    """Get collection detail with all items"""
    return collection_service.get_collection_detail(db, collection_id, nguoi_dung_id)


@router.delete("/{collection_id}")
def delete_collection(
    collection_id: int,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    """Delete a collection"""
    collection_service.delete_collection(db, collection_id, nguoi_dung_id)
    return {"message": "Đã xóa bộ sưu tập"}


@router.delete("/{collection_id}/items/{translation_id}")
def remove_from_collection(
    collection_id: int,
    translation_id: int,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    """Remove item from collection using composite PK (collection_id + translation_id)"""
    collection_service.remove_from_collection(db, collection_id, translation_id, nguoi_dung_id)
    return {"message": "Đã xóa từ khỏi bộ sưu tập"}


@router.get("/{collection_id}/review", response_model=List[ReviewCardResponse])
def get_collection_review(
    collection_id: int,
    practice: bool = Query(False),
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    return learning_service.get_collection_review_cards(db, collection_id, nguoi_dung_id, practice=practice)


@router.get("/{collection_id}/insights", response_model=CollectionInsightsResponse)
def get_collection_insights(
    collection_id: int,
    db: Session = Depends(get_db),
    nguoi_dung_id: int = Depends(get_current_nguoi_dung_id)
):
    """Get analytics and insights for a collection"""
    return collection_service.get_collection_insights(db, collection_id, nguoi_dung_id)
