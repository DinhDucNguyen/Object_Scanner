package com.duc.objectlanguage.ui.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.local.TokenManager
import com.duc.objectlanguage.data.model.Collection
import com.duc.objectlanguage.data.model.CollectionDetail
import com.duc.objectlanguage.data.repository.CollectionRepository
import com.duc.objectlanguage.utils.AudioPlayerManager
import kotlinx.coroutines.launch

/**
 * ViewModel for collection list and detail screens
 * Manages collections CRUD and filtering
 */
class CollectionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val tokenManager = TokenManager(application)
    private val repo = CollectionRepository(tokenManager)
    private val appRepository = (application as ObjectLanguageApp).repository
    private val audioPlayer = AudioPlayerManager(application.applicationContext)
    
    // Collections list
    private val _collections = MutableLiveData<List<Collection>>()
    val collections: LiveData<List<Collection>> = _collections
    
    // Selected collection detail
    private val _collectionDetail = MutableLiveData<CollectionDetail?>()
    val collectionDetail: LiveData<CollectionDetail?> = _collectionDetail
    
    // Loading states
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    // Success messages
    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage
    
    // Public collections (community tab)
    private val _publicCollections = MutableLiveData<List<Collection>>()
    val publicCollections: LiveData<List<Collection>> = _publicCollections

    // Filter state
    private val _filterQuery = MutableLiveData<String>("")
    val filterQuery: LiveData<String> = _filterQuery
    
    private val _filteredCollections = MutableLiveData<List<Collection>>()
    val filteredCollections: LiveData<List<Collection>> = _filteredCollections
    
    init {
        loadCollections()
    }
    
    /**
     * Load all collections
     */
    fun loadCollections() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getCollections()
            
            result.fold(
                onSuccess = { collections ->
                    _collections.value = collections
                    applyFilter()
                    _error.value = null
                },
                onFailure = { e ->
                    _error.value = e.message
                }
            )
            
            _isLoading.value = false
        }
    }
    
    /**
     * Load collection detail by ID
     */
    fun loadCollectionDetail(collectionId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.getCollectionDetail(collectionId)
            
            result.fold(
                onSuccess = { detail ->
                    _collectionDetail.value = detail
                    _error.value = null
                },
                onFailure = { e ->
                    _error.value = "Lỗi tải chi tiết: ${e.message}"
                }
            )
            
            _isLoading.value = false
        }
    }
    
    /**
     * Create new collection
     */
    fun createCollection(name: String, isPublic: Boolean) {
        if (name.isBlank()) {
            _error.value = getApplication<Application>().getString(R.string.collection_name_empty)
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.createCollection(name, isPublic)
            
            result.fold(
                onSuccess = {
                    _successMessage.value = getApplication<Application>().getString(R.string.collection_created, name)
                    loadCollections()
                },
                onFailure = { e ->
                    _error.value = "Lỗi tạo bộ sưu tập: ${e.message}"
                }
            )
            
            _isLoading.value = false
        }
    }

    /**
     * Update collection name
     */
    fun updateCollectionName(collectionId: Int, name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _error.value = getApplication<Application>().getString(R.string.collection_name_empty)
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.updateCollection(collectionId, trimmedName)

            result.fold(
                onSuccess = {
                    _successMessage.value = getApplication<Application>().getString(
                        R.string.collection_name_updated,
                        trimmedName
                    )
                    loadCollections()
                    loadPublicCollections()
                },
                onFailure = { e ->
                    _error.value = getApplication<Application>().getString(
                        R.string.collection_name_update_error,
                        e.message ?: ""
                    )
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * Update collection privacy
     */
    fun updateCollectionPrivacy(collectionId: Int, isPublic: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.updateCollectionPrivacy(collectionId, isPublic)

            result.fold(
                onSuccess = {
                    _successMessage.value = getApplication<Application>().getString(R.string.collection_privacy_updated)
                    loadCollections()
                    loadPublicCollections()
                },
                onFailure = { e ->
                    _error.value = getApplication<Application>().getString(
                        R.string.collection_privacy_update_error,
                        e.message ?: ""
                    )
                }
            )

            _isLoading.value = false
        }
    }
    
    /**
     * Delete collection
     */
    fun deleteCollection(collectionId: Int, collectionName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.deleteCollection(collectionId)
            
            result.fold(
                onSuccess = {
                    _successMessage.value = getApplication<Application>().getString(R.string.collection_deleted, collectionName)
                    loadCollections()
                },
                onFailure = { e ->
                    _error.value = "Lỗi xoá bộ sưu tập: ${e.message}"
                }
            )
            
            _isLoading.value = false
        }
    }
    
    /**
     * Add item to collection
     */
    fun addToCollection(collectionId: Int, translationId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.addToCollection(collectionId, translationId)
            
            result.fold(
                onSuccess = {
                    _successMessage.value = getApplication<Application>().getString(R.string.collection_added)
                    loadCollectionDetail(collectionId)
                },
                onFailure = { e ->
                    _error.value = "Lỗi thêm từ: ${e.message}"
                }
            )
            
            _isLoading.value = false
        }
    }
    
    /**
     * Remove item from collection
     */
    fun removeFromCollection(collectionId: Int, translationId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repo.removeFromCollection(collectionId, translationId)
            
            result.fold(
                onSuccess = {
                    _successMessage.value = getApplication<Application>().getString(R.string.collection_removed)
                    loadCollectionDetail(collectionId)
                },
                onFailure = { e ->
                    _error.value = "Lỗi xoá từ: ${e.message}"
                }
            )
            
            _isLoading.value = false
        }
    }
    
    fun loadPublicCollections() {
        viewModelScope.launch {
            val result = repo.getPublicCollections()
            result.fold(
                onSuccess = { _publicCollections.value = it },
                onFailure = { _error.value = it.message }
            )
        }
    }

    /**
     * Update filter query
     */
    fun setFilterQuery(query: String) {
        _filterQuery.value = query
        applyFilter()
    }
    
    /**
     * Apply filter to collections
     */
    private fun applyFilter() {
        val query = _filterQuery.value?.lowercase() ?: ""
        val all = _collections.value ?: emptyList()
        
        if (query.isEmpty()) {
            _filteredCollections.value = all
        } else {
            _filteredCollections.value = all.filter { collection ->
                collection.name.lowercase().contains(query)
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Clear success message
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    fun playAudio(audioUrl: String?, word: String, lang: String = "en") {
        viewModelScope.launch {
            val bytes = audioUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { appRepository.getAudioByUrl(it) }
                ?: appRepository.getTtsAudio(word, lang)
            if (bytes != null) {
                audioPlayer.playMp3(bytes) {
                    _error.value = getApplication<Application>().getString(R.string.collection_audio_play_error)
                }
            } else {
                _error.value = getApplication<Application>().getString(R.string.collection_audio_load_error)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
