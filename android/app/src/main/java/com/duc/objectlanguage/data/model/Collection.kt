package com.duc.objectlanguage.data.model

import com.google.gson.annotations.SerializedName

data class Collection(
    val id: Int,
    val name: String,
    @SerializedName("is_public") val isPublic: Boolean,
    @SerializedName("item_count") val itemCount: Int,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("owner_name") val ownerName: String? = null
)

data class CollectionDetail(
    val id: Int,
    val name: String,
    @SerializedName("is_public") val isPublic: Boolean,
    @SerializedName("can_edit") val canEdit: Boolean = false,
    val items: List<CollectionItem>,
    @SerializedName("created_at") val createdAt: String?
)

data class CollectionItem(
    @SerializedName("translation_id") val translationId: Int,
    @SerializedName("object_name") val objectName: String,
    val translation: String,
    val category: String,
    @SerializedName("language_code") val languageCode: String? = null,
    @SerializedName("image_url") val imageUrl: String?,
    val phonetic: String? = null,
    @SerializedName("part_of_speech") val partOfSpeech: String? = null,
    val definition: String? = null,
    val examples: List<ViDuResponse> = emptyList(),
    @SerializedName("audio_url") val audioUrl: String? = null
)

data class CollectionInsights(
    @SerializedName("collection_id") val collectionId: Int,
    @SerializedName("collection_name") val collectionName: String,
    @SerializedName("total_items") val totalItems: Int,
    @SerializedName("reviewed_items") val reviewedItems: Int,
    @SerializedName("mastered_items") val masteredItems: Int,
    @SerializedName("average_quality") val averageQuality: Double,
    @SerializedName("total_reviews") val totalReviews: Int,
    @SerializedName("success_rate") val successRate: Double,
    @SerializedName("last_review_date") val lastReviewDate: String?
)

data class CreateCollectionRequest(
    val name: String,
    @SerializedName("is_public") val isPublic: Boolean = false
)

data class UpdateCollectionRequest(
    val name: String
)

data class UpdateCollectionPrivacyRequest(
    @SerializedName("is_public") val isPublic: Boolean
)

data class AddToCollectionRequest(
    @SerializedName("translation_id") val translationId: Int
)

data class AddToCollectionResponse(
    val message: String,
    @SerializedName("already_exists") val alreadyExists: Boolean = false
)
