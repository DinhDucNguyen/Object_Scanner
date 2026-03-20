package com.duc.objectlanguage.data.model

import java.util.Date

/**
 * User collection data model
 */
data class Collection(
    val id: Int,
    val name: String,
    val isPublic: Boolean,
    val itemCount: Int,
    val createdAt: Date
)

/**
 * Collection with detailed items
 */
data class CollectionDetail(
    val id: Int,
    val name: String,
    val isPublic: Boolean,
    val items: List<CollectionItem>,
    val createdAt: Date
)

/**
 * Item in a collection
 */
data class CollectionItem(
    val id: Int,
    val translationId: Int,
    val objectName: String,
    val translation: String,
    val category: String,
    val imageUrl: String?
)

/**
 * Collection analytics data
 */
data class CollectionInsights(
    val collectionId: Int,
    val collectionName: String,
    val totalItems: Int,
    val reviewedItems: Int,
    val masteredItems: Int,
    val averageQuality: Double,
    val totalReviews: Int,
    val successRate: Double,
    val lastReviewDate: Date?
)

/**
 * Request to create collection
 */
data class CreateCollectionRequest(
    val name: String,
    val isPublic: Boolean = false
)

/**
 * Request to add item to collection
 */
data class AddToCollectionRequest(
    val translationId: Int
)
