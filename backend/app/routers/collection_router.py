# pyrefly: ignore [missing-import]
from fastapi import APIRouter, Depends, HTTPException, Query
# pyrefly: ignore [missing-import]
from sqlalchemy.orm import Session
from typing import List

from app.db.session import get_db
from app.services.collection_service import CollectionService
from app.services.learning_service import LearningService
from app.schemas.common import CollectionCreate, CollectionResponse, CollectionItemAdd, CollectionDetailResponse, CollectionInsightsResponse, ReviewCardResponse
from app.dependencies.get_current_user import get_current_user_id

router = APIRouter(prefix="/api/collections", tags=["Collections"])
collection_service = CollectionService()
learning_service = LearningService()


@router.get("", response_model=List[CollectionResponse])
def get_collections(
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return collection_service.get_collections(db, user_id)


@router.post("", response_model=CollectionResponse)
def create_collection(
    data: CollectionCreate, 
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return collection_service.create_collection(db, user_id, data)


@router.post("/{collection_id}/items")
def add_to_collection(
    collection_id: int, 
    data: CollectionItemAdd, 
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return collection_service.add_to_collection(db, collection_id, data, user_id)


@router.get("/explore", response_model=List[CollectionResponse])
def get_public_collections(
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return collection_service.get_public_collections(db, user_id)


@router.get("/{collection_id}", response_model=CollectionDetailResponse)
def get_collection_detail(
    collection_id: int,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    """Get collection detail with all items"""
    return collection_service.get_collection_detail(db, collection_id, user_id)


@router.delete("/{collection_id}")
def delete_collection(
    collection_id: int,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    """Delete a collection"""
    collection_service.delete_collection(db, collection_id, user_id)
    return {"message": "Collection deleted successfully"}


@router.delete("/{collection_id}/items/{translation_id}")
def remove_from_collection(
    collection_id: int,
    translation_id: int,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    """Remove item from collection using composite PK (collection_id + translation_id)"""
    collection_service.remove_from_collection(db, collection_id, translation_id, user_id)
    return {"message": "Item removed from collection"}


@router.get("/{collection_id}/review", response_model=List[ReviewCardResponse])
def get_collection_review(
    collection_id: int,
    practice: bool = Query(False),
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    return learning_service.get_collection_review_cards(db, collection_id, user_id, practice=practice)


@router.get("/{collection_id}/insights", response_model=CollectionInsightsResponse)
def get_collection_insights(
    collection_id: int,
    db: Session = Depends(get_db),
    user_id: int = Depends(get_current_user_id)
):
    """Get analytics and insights for a collection"""
    return collection_service.get_collection_insights(db, collection_id, user_id)
