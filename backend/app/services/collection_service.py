from sqlalchemy.orm import Session
from fastapi import HTTPException
from app.repositories.collection_repo import CollectionRepository
from app.models.user_collection import UserCollection
from app.models.collection_item import CollectionItem
from app.models.learning_progress import LearningProgress
from app.schemas.common import (
    CollectionCreate, CollectionResponse, CollectionItemAdd,
    CollectionDetailResponse, CollectionItemResponse, CollectionInsightsResponse
)
from app.services.object_media_service import pick_primary_object_image
from app.core.constants import SM2_MASTERED_MIN_REPETITIONS, SM2_MASTERED_MIN_INTERVAL_DAYS, SM2_BASELINE_EASINESS_FACTOR


class CollectionService:
    def __init__(self):
        self.repo = CollectionRepository()

    def get_collections(self, db: Session, user_id: int):
        collections = self.repo.get_by_user(db, user_id)
        return [
            CollectionResponse(
                id=c.id, name=c.ten_bo_suu_tap, is_public=c.cong_khai,
                item_count=len(c.items),
                created_at=c.ngay_tao
            ) for c in collections
        ]

    def create_collection(self, db: Session, user_id: int, data: CollectionCreate):
        collection = UserCollection(user_id=user_id, ten_bo_suu_tap=data.name, cong_khai=data.is_public)
        result = self.repo.create_collection(db, collection)
        db.commit()
        return CollectionResponse(
            id=result.id, name=result.ten_bo_suu_tap, is_public=result.cong_khai,
            item_count=0, created_at=result.ngay_tao
        )

    def add_to_collection(self, db: Session, collection_id: int, data: CollectionItemAdd, user_id: int):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.user_id != user_id:
            raise HTTPException(status_code=404, detail="Collection not found")

        existing = self.repo.get_item(db, collection_id, data.translation_id)
        if existing:
            return {"message": "Đã có trong bộ sưu tập"}
        self.repo.add_item(db, CollectionItem(bo_suu_tap_id=collection_id, ban_dich_id=data.translation_id))
        db.commit()
        return {"message": "Đã thêm vào bộ sưu tập"}

    def get_public_collections(self, db: Session, user_id: int):
        collections = self.repo.get_public(db, user_id)
        return [
            CollectionResponse(
                id=c.id, name=c.ten_bo_suu_tap, is_public=c.cong_khai,
                item_count=len(c.items),
                created_at=c.ngay_tao,
                owner_name=(
                    c.user.profile.ho_ten
                    if c.user.profile and c.user.profile.ho_ten
                    else c.user.ten_dang_nhap
                )
            ) for c in collections
        ]

    def get_collection_detail(self, db: Session, collection_id: int, user_id: int):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or (collection.user_id != user_id and not collection.cong_khai):
            raise HTTPException(status_code=404, detail="Collection not found")

        item_responses = []
        for item in collection.items:
            t = item.translation
            if not t:
                continue
            obj = t.object
            category = obj.category if obj else None
            item_responses.append(CollectionItemResponse(
                translation_id=t.id,
                object_name=obj.ma_doi_tuong if obj else "Unknown",
                translation=t.tu_vung,
                category=category.ten_danh_muc if category else "Uncategorized",
                image_url=pick_primary_object_image(obj)
            ))

        return CollectionDetailResponse(
            id=collection.id,
            name=collection.ten_bo_suu_tap,
            is_public=collection.cong_khai,
            items=item_responses,
            created_at=collection.ngay_tao
        )

    def delete_collection(self, db: Session, collection_id: int, user_id: int):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.user_id != user_id:
            raise HTTPException(status_code=404, detail="Collection not found")
        self.repo.delete_collection(db, collection_id)
        db.commit()

    def remove_from_collection(self, db: Session, collection_id: int, translation_id: int, user_id: int):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.user_id != user_id:
            raise HTTPException(status_code=404, detail="Collection not found")
        self.repo.remove_item(db, collection_id, translation_id)
        db.commit()

    def get_collection_insights(self, db: Session, collection_id: int, user_id: int):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.user_id != user_id:
            raise HTTPException(status_code=404, detail="Collection not found")

        items = db.query(CollectionItem).filter(CollectionItem.bo_suu_tap_id == collection_id).all()
        translation_ids = [item.ban_dich_id for item in items]

        if not translation_ids:
            return CollectionInsightsResponse(
                collection_id=collection_id,
                collection_name=collection.ten_bo_suu_tap,
                total_items=0, reviewed_items=0, mastered_items=0,
                average_quality=0.0, total_reviews=0, success_rate=0.0,
                last_review_date=None
            )

        progress_list = db.query(LearningProgress).filter(
            LearningProgress.user_id == user_id,
            LearningProgress.ban_dich_id.in_(translation_ids)
        ).all()

        total_items = len(translation_ids)
        reviewed_items = len(progress_list)
        mastered_items = sum(
            1 for p in progress_list
            if p.so_lan_lap >= SM2_MASTERED_MIN_REPETITIONS and p.khoang_lap > SM2_MASTERED_MIN_INTERVAL_DAYS
        )
        avg_quality = sum(float(p.do_de_nho) for p in progress_list) / len(progress_list) if progress_list else 0.0
        total_reviews = sum(p.so_lan_lap for p in progress_list)
        successful = sum(1 for p in progress_list if float(p.do_de_nho) >= SM2_BASELINE_EASINESS_FACTOR)
        success_rate = successful / len(progress_list) if progress_list else 0.0
        last_review = max((p.lan_on_cuoi for p in progress_list), default=None)

        return CollectionInsightsResponse(
            collection_id=collection_id,
            collection_name=collection.ten_bo_suu_tap,
            total_items=total_items, reviewed_items=reviewed_items,
            mastered_items=mastered_items, average_quality=avg_quality,
            total_reviews=total_reviews, success_rate=success_rate,
            last_review_date=last_review
        )
