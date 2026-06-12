package com.duc.objectlanguage.data.repository

import com.duc.objectlanguage.data.api.RetrofitClient
import com.duc.objectlanguage.data.model.*
import com.duc.objectlanguage.data.model.Collection

/**
 * Repository for collection operations with Result wrapper
 */
class CollectionRepository {
    
    private val api get() = RetrofitClient.collectionApi
    
    
    /**
     * Get all user collections
     */
    suspend fun getCollections(): Result<List<Collection>> = try {
        val collections = api.getCollections()
        Result.success(collections)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }
    
    /**
     * Get collection detail with items
     */
    suspend fun getCollectionDetail(collectionId: Int): Result<CollectionDetail> = try {
        val detail = api.getCollectionDetail(collectionId)
        Result.success(detail)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }
    
    /**
     * Create new collection
     */
    suspend fun createCollection(name: String, isPublic: Boolean): Result<Collection> = try {
        val collection = api.createCollection(CreateCollectionRequest(name, isPublic))
        Result.success(collection)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }

    /**
     * Update collection name
     */
    suspend fun updateCollection(collectionId: Int, name: String): Result<Collection> = try {
        val collection = api.updateCollection(collectionId, UpdateCollectionRequest(name))
        Result.success(collection)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }

    /**
     * Update collection privacy
     */
    suspend fun updateCollectionPrivacy(collectionId: Int, isPublic: Boolean): Result<Collection> = try {
        val collection = api.updateCollectionPrivacy(
            collectionId,
            UpdateCollectionPrivacyRequest(isPublic)
        )
        Result.success(collection)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }
    
    /**
     * Delete collection
     */
    suspend fun deleteCollection(collectionId: Int): Result<Unit> = try {
        api.deleteCollection(collectionId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }
    
    /**
     * Add item to collection
     */
    suspend fun addToCollection(collectionId: Int, translationId: Int): Result<Unit> = try {
        api.addToCollection(collectionId, AddToCollectionRequest(translationId))
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }
    
    /**
     * Remove item from collection using composite PK (collectionId + translationId)
     */
    suspend fun removeFromCollection(collectionId: Int, translationId: Int): Result<Unit> = try {
        api.removeFromCollection(collectionId, translationId)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }
    
    /**
     * Get collection analytics insights
     */
    suspend fun getCollectionInsights(collectionId: Int): Result<CollectionInsights> = try {
        val insights = api.getCollectionInsights(collectionId)
        Result.success(insights)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }

    suspend fun getCollectionReviewCards(collectionId: Int): Result<List<ReviewCardResponse>> = try {
        val cards = api.getCollectionReviewCards(collectionId)
        Result.success(cards)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }

    suspend fun getCollectionReviewCardsPractice(collectionId: Int): Result<List<ReviewCardResponse>> = try {
        val cards = api.getCollectionReviewCards(collectionId, practice = true)
        Result.success(cards)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }

    suspend fun getPublicCollections(): Result<List<Collection>> = try {
        val collections = api.getPublicCollections()
        Result.success(collections)
    } catch (e: Exception) {
        Result.failure(userFacingException(e))
    }
}
