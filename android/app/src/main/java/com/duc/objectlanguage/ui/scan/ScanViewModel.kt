package com.duc.objectlanguage.ui.scan

import android.app.Application
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.ScanResponse
import com.duc.objectlanguage.data.model.TranslationResponse
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as ObjectLanguageApp).repository

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _scanResult = MutableLiveData<ScanResponse?>()
    val scanResult: LiveData<ScanResponse?> = _scanResult

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _examples = MutableLiveData<List<String>>()
    val examples: LiveData<List<String>> = _examples

    private val _addedMsg = MutableLiveData<String?>()
    val addedMsg: LiveData<String?> = _addedMsg

    private var mediaPlayer: MediaPlayer? = null

    fun scanImage(imageBytes: ByteArray) {
        if (_isLoading.value == true) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            Log.d("ScanViewModel", "Starting scan with image size: ${imageBytes.size} bytes")
            
            // ✅ IMPLEMENTATION: ML Kit Object Detection (Local first - ưu tiên DB)
            try {
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                
                Log.d("ScanViewModel", "Running ML Kit image labeling...")
                val labels = labeler.process(inputImage).await()
                
                // Lấy label có confidence cao nhất
                if (labels.isNotEmpty()) {
                    val topLabel = labels.maxByOrNull { it.confidence } ?: labels[0]
                    val objectCode = normalizeObjectCode(topLabel.text)
                    val confidence = topLabel.confidence
                    
                    Log.d("ScanViewModel", "ML Kit detected: ${topLabel.text} (${confidence}), normalized: $objectCode")
                    
                    // Nếu confidence > 0.6 → Gọi /api/scan với object_code (check DB trước)
                    if (confidence > 0.6f) {
                        Log.d("ScanViewModel", "High confidence! Calling /api/scan with object_code")
                        val result = repo.scanByCode(objectCode, confidence)
                        result.fold(
                            onSuccess = {
                                if (it.translations.isNotEmpty()) {
                                    // Tìm thấy trong DB! Không cần Gemini
                                    Log.d("ScanViewModel", "✅ Found in DB: ${it.objectCode}")
                                    _scanResult.value = it
                                    val t = it.translations[0]
                                    loadExamples(t.wordName, t.languageCode ?: "en")
                                    _isLoading.value = false
                                    return@launch
                                } else {
                                    Log.d("ScanViewModel", "Object code not in DB, fallback to Gemini")
                                }
                            },
                            onFailure = {
                                Log.w("ScanViewModel", "API scan by code failed: ${it.message}")
                            }
                        )
                    } else {
                        Log.d("ScanViewModel", "Low confidence ($confidence), skip ML Kit result")
                    }
                } else {
                    Log.d("ScanViewModel", "ML Kit returned no labels")
                }
            } catch (e: Exception) {
                Log.e("ScanViewModel", "ML Kit failed: ${e.message}", e)
            }
            
            // Fallback: Gọi Gemini API (khi ML Kit fail hoặc không tìm thấy trong DB)
            Log.d("ScanViewModel", "Fallback to Gemini API...")
            val compressedBytes = compressImageIfNeeded(imageBytes)
            Log.d("ScanViewModel", "Compressed to: ${compressedBytes.size} bytes")
            
            val result = repo.scanByImage(compressedBytes)
            result.fold(
                onSuccess = {
                    Log.d("ScanViewModel", "Scan success: objectCode=${it.objectCode}, source=${it.source}, translations=${it.translations.size}")

                    if (it.source == "gemini_quota_exceeded") {
                        _scanResult.value = null
                        _error.value = "Gemini đã hết quota. Vui lòng thử lại sau khoảng 1 phút hoặc đổi API key/billing."
                        return@fold
                    }

                    if (it.source == "gemini_failed" || it.objectCode == "unknown") {
                        _scanResult.value = null
                        _error.value = "Gemini chưa nhận diện được vật thể. Hãy chụp rõ hơn và thử lại."
                        return@fold
                    }

                    _scanResult.value = it
                    // Auto-load examples cho từ đầu tiên
                    if (it.translations.isNotEmpty()) {
                        val t = it.translations[0]
                        loadExamples(t.wordName, t.languageCode ?: "en")
                    } else {
                        Log.w("ScanViewModel", "No translations found!")
                    }
                },
                onFailure = {
                    Log.e("ScanViewModel", "Scan failed: ${it.message}", it)
                    _error.value = it.message
                }
            )
            _isLoading.value = false
        }
    }
    
    /**
     * ✅ HÀM MỚI: Chuẩn hóa label từ ML Kit → object_code database
     * Xử lý các biến thể tên (e.g., "Mobile phone" → "cell_phone", "Computer mouse" → "mouse")
     */
    private fun normalizeObjectCode(label: String): String {
        val normalized = label.lowercase().trim()
        
        // Map các biến thể ML Kit → DB object_code
        val mappings = mapOf(
            "mobile phone" to "cell_phone",
            "mobile device" to "cell_phone",
            "smartphone" to "cell_phone",
            "cellphone" to "cell_phone",
            "computer mouse" to "mouse",
            "pc mouse" to "mouse",
            "laptop computer" to "laptop",
            "notebook computer" to "laptop",
            "notebook" to "laptop",
            "table" to "chair",  // ML Kit thường nhầm
            "desk" to "chair",
            "tv" to "television",
            "bike" to "bicycle"
        )
        
        // Check exact match trong mapping
        if (mappings.containsKey(normalized)) {
            return mappings[normalized]!!
        }
        
        // Fallback: lowercase + replace spaces/hyphens
        return normalized
            .replace(" ", "_")
            .replace("-", "_")
    }
    
    /**
     * ✅ HÀM MỚI: Compress ảnh xuống 800x800 nếu quá lớn
     */
    private fun compressImageIfNeeded(imageBytes: ByteArray): ByteArray {
        // Nếu file nhỏ hơn 200KB thì không cần compress
        if (imageBytes.size < 200 * 1024) return imageBytes
        
        try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            val maxSize = 800
            val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            
            val resized = if (ratio < 1.0f) {
                val newWidth = (bitmap.width * ratio).toInt()
                val newHeight = (bitmap.height * ratio).toInt()
                android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else bitmap
            
            val stream = java.io.ByteArrayOutputStream()
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
            return stream.toByteArray()
        } catch (e: Exception) {
            // Nếu lỗi thì trả ảnh gốc
            return imageBytes
        }
    }

    fun loadExamples(word: String, lang: String) {
        viewModelScope.launch {
            _examples.value = repo.getExamples(word, lang)
        }
    }

    fun playAudio(word: String, lang: String) {
        viewModelScope.launch {
            val bytes = repo.getTtsAudio(word, lang)
            if (bytes != null) {
                try {
                    val tmpFile = File.createTempFile("tts_", ".mp3", getApplication<ObjectLanguageApp>().cacheDir)
                    tmpFile.writeBytes(bytes)
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tmpFile.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener {
                            release()
                            tmpFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    _error.value = "Không phát được audio"
                }
            } else {
                _error.value = "Không tải được audio"
            }
        }
    }

    fun addToLearning(translationId: Int) {
        viewModelScope.launch {
            val result = repo.addToLearning(translationId)
            result.fold(
                onSuccess = { _addedMsg.value = it },
                onFailure = { _addedMsg.value = "Lỗi: ${it.message}" }
            )
        }
    }

    fun clearResult() {
        _scanResult.value = null
        _examples.value = emptyList()
        _error.value = null
    }

    fun clearAddedMsg() { _addedMsg.value = null }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}
