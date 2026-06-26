from sqlalchemy.orm import Session
from fastapi import HTTPException
from app.repositories.collection_repo import CollectionRepository
from app.models.user_collection import UserCollection
from app.models.collection_item import CollectionItem
from app.models.learning_progress import LearningProgress
from app.schemas.common import (
    CollectionCreate, CollectionResponse, CollectionItemAdd,
    CollectionDetailResponse, CollectionItemResponse, CollectionInsightsResponse,
    CollectionPrivacyUpdate, CollectionUpdate
)
from app.services.object_media_service import pick_primary_object_image
from app.core.constants import (
    SM2_COLLECTION_MASTERED_MIN_REPETITIONS,
    SM2_MASTERED_MIN_INTERVAL_DAYS,
    SM2_BASELINE_EASINESS_FACTOR,
)


class CollectionService:
    def __init__(self):
        self.repo = CollectionRepository()

    def get_collections(self, db: Session, nguoi_dung_id: int):
        collections = self.repo.get_by_user(db, nguoi_dung_id)
        return [
            CollectionResponse(
                id=c.id, name=c.ten_bo_suu_tap, is_public=c.cong_khai,
                item_count=len(c.items),
                created_at=c.ngay_tao
            ) for c in collections
        ]

    def create_collection(self, db: Session, nguoi_dung_id: int, data: CollectionCreate):
        collection = UserCollection(nguoi_dung_id=nguoi_dung_id, ten_bo_suu_tap=data.name, cong_khai=data.is_public)
        result = self.repo.create_collection(db, collection)
        db.commit()
        return CollectionResponse(
            id=result.id, name=result.ten_bo_suu_tap, is_public=result.cong_khai,
            item_count=0, created_at=result.ngay_tao
        )

    def update_collection(
        self,
        db: Session,
        collection_id: int,
        nguoi_dung_id: int,
        data: CollectionUpdate
    ):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.nguoi_dung_id != nguoi_dung_id:
            raise HTTPException(status_code=404, detail="Collection not found")

        name = data.name.strip()
        if not name:
            raise HTTPException(status_code=400, detail="Collection name cannot be empty")

        collection.ten_bo_suu_tap = name
        db.commit()
        db.refresh(collection)
        return CollectionResponse(
            id=collection.id,
            name=collection.ten_bo_suu_tap,
            is_public=collection.cong_khai,
            item_count=len(collection.items),
            created_at=collection.ngay_tao
        )

    def update_collection_privacy(
        self,
        db: Session,
        collection_id: int,
        nguoi_dung_id: int,
        data: CollectionPrivacyUpdate
    ):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.nguoi_dung_id != nguoi_dung_id:
            raise HTTPException(status_code=404, detail="Collection not found")

        collection.cong_khai = data.is_public
        db.commit()
        db.refresh(collection)
        return CollectionResponse(
            id=collection.id,
            name=collection.ten_bo_suu_tap,
            is_public=collection.cong_khai,
            item_count=len(collection.items),
            created_at=collection.ngay_tao
        )

    def add_to_collection(self, db: Session, collection_id: int, data: CollectionItemAdd, nguoi_dung_id: int):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.nguoi_dung_id != nguoi_dung_id:
            raise HTTPException(status_code=404, detail="Collection not found")

        existing = self.repo.get_item(db, collection_id, data.translation_id)
        if existing:
            return {"message": "Đã có trong bộ sưu tập", "already_exists": True}
        self.repo.add_item(db, CollectionItem(bo_suu_tap_id=collection_id, ban_dich_id=data.translation_id))
        db.commit()
        return {"message": "Đã thêm vào bộ sưu tập", "already_exists": False}

    def get_public_collections(self, db: Session, nguoi_dung_id: int):
        collections = self.repo.get_public(db, nguoi_dung_id)
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

    def get_collection_detail(self, db: Session, collection_id: int, nguoi_dung_id: int):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or (collection.nguoi_dung_id != nguoi_dung_id and not collection.cong_khai):
            raise HTTPException(status_code=404, detail="Collection not found")
        if collection.nguoi_dung_id != nguoi_dung_id and not collection.items:
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
                language_code=t.language.ma_ngon_ngu if t.language else None,
                image_url=pick_primary_object_image(obj),
                phonetic=t.phien_am,
                part_of_speech=t.loai_tu,
                definition=t.dinh_nghia,
                examples=t.examples or [],
                audio_url=t.am_thanh_url
            ))

        return CollectionDetailResponse(
            id=collection.id,
            name=collection.ten_bo_suu_tap,
            is_public=collection.cong_khai,
            can_edit=collection.nguoi_dung_id == nguoi_dung_id,
            items=item_responses,
            created_at=collection.ngay_tao
        )

    def delete_collection(self, db: Session, collection_id: int, nguoi_dung_id: int):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.nguoi_dung_id != nguoi_dung_id:
            raise HTTPException(status_code=404, detail="Collection not found")
        self.repo.delete_collection(db, collection_id)
        db.commit()

    def remove_from_collection(self, db: Session, collection_id: int, translation_id: int, nguoi_dung_id: int):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.nguoi_dung_id != nguoi_dung_id:
            raise HTTPException(status_code=404, detail="Collection not found")
        self.repo.remove_item(db, collection_id, translation_id)
        db.commit()

    def get_collection_insights(self, db: Session, collection_id: int, nguoi_dung_id: int):
        collection = self.repo.get_by_id(db, collection_id)
        if not collection or collection.nguoi_dung_id != nguoi_dung_id:
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
            LearningProgress.nguoi_dung_id == nguoi_dung_id,
            LearningProgress.ban_dich_id.in_(translation_ids)
        ).all()

        total_items = len(translation_ids)
        reviewed_progress = [p for p in progress_list if (p.so_lan_lap or 0) > 0]
        reviewed_items = len(reviewed_progress)
        mastered_items = sum(
            1 for p in reviewed_progress
            if p.so_lan_lap >= SM2_COLLECTION_MASTERED_MIN_REPETITIONS
            and p.khoang_lap >= SM2_MASTERED_MIN_INTERVAL_DAYS
        )
        avg_quality = (
            sum(float(p.do_de_nho) for p in reviewed_progress) / reviewed_items
            if reviewed_items else 0.0
        )
        total_reviews = sum(p.so_lan_lap for p in progress_list)
        successful = sum(1 for p in reviewed_progress if float(p.do_de_nho) >= SM2_BASELINE_EASINESS_FACTOR)
        success_rate = successful / reviewed_items if reviewed_items else 0.0
        last_review = max((p.lan_on_cuoi for p in reviewed_progress), default=None)

        return CollectionInsightsResponse(
            collection_id=collection_id,
            collection_name=collection.ten_bo_suu_tap,
            total_items=total_items, reviewed_items=reviewed_items,
            mastered_items=mastered_items, average_quality=avg_quality,
            total_reviews=total_reviews, success_rate=success_rate,
            last_review_date=last_review
        )
