from sqlalchemy.orm import Session
from sqlalchemy import func
from fastapi import HTTPException
from app.repositories.collection_repo import CollectionRepository
from app.models.user_collection import UserCollection
from app.models.collection_item import CollectionItem
from app.models.learning_progress import LearningProgress
from app.models.translation import Translation
from app.models.object import Object
from app.models.category import Category
from app.schemas.common import (
    CollectionCreate, CollectionResponse, CollectionItemAdd,
    CollectionDetailResponse, CollectionItemResponse, CollectionInsightsResponse
)
from app.core.constants import SM2_MASTERED_MIN_REPETITIONS, SM2_MASTERED_MIN_INTERVAL_DAYS, SM2_BASELINE_EASINESS_FACTOR


class CollectionService:
    def __init__(self):
        self.repo = CollectionRepository()

    def get_collections(self, db: Session, user_id: int):
        collections = self.repo.get_by_user(db, user_id)
        return [
            CollectionResponse(
                id=c.id, name=c.name, is_public=c.is_public,
                item_count=len(c.items),
                created_at=c.created_at
            ) for c in collections
        ]

    def create_collection(self, db: Session, user_id: int, data: CollectionCreate):
        collection = UserCollection(user_id=user_id, name=data.name, is_public=data.is_public)
        result = self.repo.create_collection(db, collection)
        db.commit()
        return result

    def add_to_collection(self, db: Session, collection_id: int, data: CollectionItemAdd):
        existing = self.repo.get_item(db, collection_id, data.translation_id)
        if existing:
            return {"message": "Đã có trong bộ sưu tập"}
        self.repo.add_item(db, CollectionItem(collection_id=collection_id, translation_id=data.translation_id))
        db.commit()
        return {"message": "Đã thêm vào bộ sưu tập"}

    def get_collection_detail(self, db: Session, collection_id: int, user_id: int):
        """Get collection with all items"""
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.user_id != user_id:
            raise HTTPException(status_code=404, detail="Collection not found")
        
        # Use relationships: item → translation → object → category
        item_responses = []
        for item in collection.items:
            t = item.translation
            if not t:
                continue
            obj = t.object
            category = obj.category if obj else None
            
            item_responses.append(CollectionItemResponse(
                id=item.id,
                translation_id=t.id,
                object_name=obj.object_code if obj else "Unknown",
                translation=t.word_name,
                category=category.name if category else "Uncategorized",
                image_url=None
            ))
        
        return CollectionDetailResponse(
            id=collection.id,
            name=collection.name,
            is_public=collection.is_public,
            items=item_responses,
            created_at=collection.created_at
        )

    def delete_collection(self, db: Session, collection_id: int, user_id: int):
        """Delete a collection"""
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.user_id != user_id:
            raise HTTPException(status_code=404, detail="Collection not found")
        
        self.repo.delete_collection(db, collection_id)
        db.commit()

    def remove_from_collection(self, db: Session, collection_id: int, item_id: int, user_id: int):
        """Remove item from collection"""
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.user_id != user_id:
            raise HTTPException(status_code=404, detail="Collection not found")
        
        self.repo.remove_item(db, item_id)
        db.commit()

    def get_collection_insights(self, db: Session, collection_id: int, user_id: int):
        """Get analytics for a collection"""
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.user_id != user_id:
            raise HTTPException(status_code=404, detail="Collection not found")
        
        # Get all translation IDs in collection
        items = db.query(CollectionItem).filter(
            CollectionItem.collection_id == collection_id
        ).all()
        translation_ids = [item.translation_id for item in items]
        
        if not translation_ids:
            return CollectionInsightsResponse(
                collection_id=collection_id,
                collection_name=collection.name,
                total_items=0,
                reviewed_items=0,
                mastered_items=0,
                average_quality=0.0,
                total_reviews=0,
                success_rate=0.0,
                last_review_date=None
            )
        
        # Get learning progress for these translations
        progress_list = db.query(LearningProgress).filter(
            LearningProgress.user_id == user_id,
            LearningProgress.translation_id.in_(translation_ids)
        ).all()
        
        total_items = len(translation_ids)
        reviewed_items = len(progress_list)
        
        # Mastered = repetitions >= threshold with interval > minimum days (consistent with SM-2)
        mastered_items = sum(1 for p in progress_list if p.repetitions >= SM2_MASTERED_MIN_REPETITIONS and p.interval > SM2_MASTERED_MIN_INTERVAL_DAYS)
        
        # Average easiness factor (proxy for quality — 2.5 is baseline, higher = better)
        avg_quality = sum(float(p.easiness_factor) for p in progress_list) / len(progress_list) if progress_list else 0.0
        
        # Total reviews (sum of repetitions)
        total_reviews = sum(p.repetitions for p in progress_list)
        
        # Success rate (easiness_factor >= baseline means learner is performing well in SM-2)
        successful_reviews = sum(1 for p in progress_list if float(p.easiness_factor) >= SM2_BASELINE_EASINESS_FACTOR)
        success_rate = successful_reviews / len(progress_list) if progress_list else 0.0
        
        # Last review date
        last_review = max((p.updated_at for p in progress_list), default=None)
        
        return CollectionInsightsResponse(
            collection_id=collection_id,
            collection_name=collection.name,
            total_items=total_items,
            reviewed_items=reviewed_items,
            mastered_items=mastered_items,
            average_quality=avg_quality,
            total_reviews=total_reviews,
            success_rate=success_rate,
            last_review_date=last_review
        )

