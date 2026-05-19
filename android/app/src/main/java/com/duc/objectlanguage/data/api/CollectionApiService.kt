package com.duc.objectlanguage.data.api

import com.duc.objectlanguage.data.model.*
import com.duc.objectlanguage.data.model.Collection
import retrofit2.http.*

/**
 * Retrofit API service for collections
 */
interface CollectionApiService {
    
    @GET("api/collections")
    suspend fun getCollections(): List<Collection>
    
    @GET("api/collections/{id}")
    suspend fun getCollectionDetail(@Path("id") collectionId: Int): CollectionDetail
    
    @POST("api/collections")
    suspend fun createCollection(@Body request: CreateCollectionRequest): Collection
    
    @DELETE("api/collections/{id}")
    suspend fun deleteCollection(@Path("id") collectionId: Int)
    
    @POST("api/collections/{id}/items")
    suspend fun addToCollection(
        @Path("id") collectionId: Int,
        @Body request: AddToCollectionRequest
    )
    
    @DELETE("api/collections/{collectionId}/items/{translationId}")
    suspend fun removeFromCollection(
        @Path("collectionId") collectionId: Int,
        @Path("translationId") translationId: Int
    )
    
    @GET("api/collections/{id}/insights")
    suspend fun getCollectionInsights(@Path("id") collectionId: Int): CollectionInsights

    @GET("api/collections/{id}/review")
    suspend fun getCollectionReviewCards(@Path("id") collectionId: Int): List<ReviewCardResponse>
}
