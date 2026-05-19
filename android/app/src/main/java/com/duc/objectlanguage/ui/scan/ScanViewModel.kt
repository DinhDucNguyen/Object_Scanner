package com.duc.objectlanguage.ui.scan

import android.app.Application
import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.duc.objectlanguage.R
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.data.model.ScanResponse
import com.duc.objectlanguage.data.model.TranslationResponse
import com.duc.objectlanguage.data.repository.CollectionRepository
import com.duc.objectlanguage.utils.AudioPlayerManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ExampleItem(val en: String, val vi: String?)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ObjectLanguageApp
    private val repo = app.repository
    private val collectionRepo = CollectionRepository(app.tokenManager)
    private val guestSessionManager = app.guestSessionManager

    val isGuest: Boolean get() = !app.tokenManager.isLoggedIn

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _scanResult = MutableLiveData<ScanResponse?>()
    val scanResult: LiveData<ScanResponse?> = _scanResult

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _examples = MutableLiveData<List<ExampleItem>>()
    val examples: LiveData<List<ExampleItem>> = _examples

    private val _addedMsg = MutableLiveData<String?>()
    val addedMsg: LiveData<String?> = _addedMsg

    private val _detectionSource = MutableLiveData<String?>()
    val detectionSource: LiveData<String?> = _detectionSource

    private val _remainingScans = MutableLiveData(guestSessionManager.getRemainingScans())
    val remainingScans: LiveData<Int> = _remainingScans

    private val _showGuestUpsell = MutableLiveData(false)
    val showGuestUpsell: LiveData<Boolean> = _showGuestUpsell

    private val audioPlayer = AudioPlayerManager(application.applicationContext)

    fun scanWithDetection(yoloResult: DetectionResult?, imageBytes: ByteArray) {
        if (_isLoading.value == true) return

        if (isGuest && !guestSessionManager.canScan()) {
            _showGuestUpsell.value = true
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // 1. YOLO — thử DB lookup khi confidence >= 0.80
            Log.d("ScanViewModel", "YOLO result: ${if (yoloResult != null) "${yoloResult.label} (${yoloResult.confidence}) model=${yoloResult.modelName}" else "null — no detection"}")
            if (yoloResult != null && yoloResult.confidence >= 0.80f) {
                val objectCode = normalizeObjectCode(yoloResult.label)
                Log.d("ScanViewModel", "YOLOv10: ${yoloResult.label} (${yoloResult.confidence})")
                val yoloScan = repo.scanByCode(objectCode, yoloResult.confidence)
                yoloScan.onFailure { Log.w("ScanViewModel", "YOLO scanByCode error: ${it.message}") }
                val dbResult = yoloScan.getOrNull()
                Log.d("ScanViewModel", "YOLO DB result: source=${dbResult?.source} translations=${dbResult?.translations?.size}")
                if (dbResult != null && dbResult.translations.isNotEmpty()) {
                    val modelLabel = ObjectDetectorHelper.displayNameForModel(yoloResult.modelName)
                    _detectionSource.value = "$modelLabel · ${(yoloResult.confidence * 100).toInt()}%"
                    _scanResult.value = dbResult
                    loadExamples(dbResult)
                    onScanSuccess(objectCode, yoloResult.confidence, imageBytes)
                    _isLoading.value = false
                    return@launch
                }
                // DB miss — nếu YOLO rất tự tin thì skip ML Kit, dùng Gemini ngay
                if (yoloResult.confidence >= 0.90f) {
                    Log.d("ScanViewModel", "YOLO high-conf DB miss → Gemini")
                    val modelLabel = ObjectDetectorHelper.displayNameForModel(yoloResult.modelName)
                    _detectionSource.value = "$modelLabel + Gemini AI · ${(yoloResult.confidence * 100).toInt()}%"
                    runGemini(imageBytes)
                    return@launch
                }
                Log.d("ScanViewModel", "YOLO low-conf DB miss → try ML Kit")
            }

            // 2. ML Kit — chỉ chạy khi YOLO không detect được
            try {
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: throw IllegalArgumentException("Invalid image")
                val labels = try {
                    ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                        .process(InputImage.fromBitmap(bitmap, 0)).await()
                } finally {
                    bitmap.recycle()
                }
                val topLabel = labels.maxByOrNull { it.confidence }
                if (topLabel != null && topLabel.confidence >= 0.80f) {
                    val objectCode = normalizeObjectCode(topLabel.text)
                    Log.d("ScanViewModel", "ML Kit: ${topLabel.text} (${topLabel.confidence})")
                    val mlScan = repo.scanByCode(objectCode, topLabel.confidence)
                    mlScan.onFailure { Log.w("ScanViewModel", "ML Kit scanByCode error: ${it.message}") }
                    val dbResult = mlScan.getOrNull()
                    Log.d("ScanViewModel", "ML Kit DB result: source=${dbResult?.source} translations=${dbResult?.translations?.size}")
                    if (dbResult != null && dbResult.translations.isNotEmpty()) {
                        _detectionSource.value = "ML Kit · ${(topLabel.confidence * 100).toInt()}%"
                        _scanResult.value = dbResult
                        loadExamples(dbResult)
                        onScanSuccess(objectCode, topLabel.confidence, imageBytes)
                        _isLoading.value = false
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanViewModel", "ML Kit failed: ${e.message}", e)
            }

            // 3. Gemini — fallback cuối cùng
            runGemini(imageBytes)
        }
    }

    private fun onScanSuccess(objectCode: String, confidence: Float, imageBytes: ByteArray) {
        if (isGuest) {
            recordGuestScanIfNeeded()
        } else {
            saveScanAndQueueReview(objectCode, confidence, imageBytes)
        }
    }

    private fun recordGuestScanIfNeeded() {
        if (!isGuest) return
        guestSessionManager.incrementScan()
        _remainingScans.value = guestSessionManager.getRemainingScans()
    }

    private suspend fun runGemini(imageBytes: ByteArray) {
        Log.d("ScanViewModel", "Fallback to Gemini...")
        repo.scanByImage(compressImageIfNeeded(imageBytes)).fold(
            onSuccess = {
                when {
                    it.source == "gemini_quota_exceeded" ->
                        _error.value = "Gemini đã hết quota. Vui lòng thử lại sau."
                    it.source == "gemini_failed" || it.objectCode == "unknown" ->
                        _error.value = "Không nhận diện được vật thể. Hãy chụp rõ hơn."
                    else -> {
                        if (_detectionSource.value == null) _detectionSource.value = "Gemini Vision AI"
                        _scanResult.value = it
                        loadExamples(it)
                        if (it.pendingReview) {
                            recordGuestScanIfNeeded()
                            _addedMsg.value = getApplication<Application>().getString(R.string.scan_pending)
                        } else {
                            onScanSuccess(it.objectCode, 1.0f, imageBytes)
                        }
                    }
                }
            },
            onFailure = { _error.value = it.message }
        )
        _isLoading.value = false
    }

    private fun saveScanAndQueueReview(objectCode: String, confidence: Float, imageBytes: ByteArray) {
        viewModelScope.launch {
            val result = repo.saveLichSuQue(objectCode, confidence, imageBytes)
            result.fold(
                onSuccess = { response ->
                    if (response.learningAdded) {
                        _addedMsg.value = getApplication<Application>().getString(R.string.scan_added_to_review)
                    }
                },
                onFailure = { Log.w("ScanViewModel", "Save scan/learning failed: ${it.message}") }
            )
        }
    }

    private fun loadExamples(result: com.duc.objectlanguage.data.model.ScanResponse) {
        if (result.translations.isEmpty()) return
        val t = result.translations.firstOrNull { it.languageCode == "en" }
            ?: result.translations.first()
        val stored = t.examples
            .filter { !it.cauViDu.isNullOrBlank() }
            .distinctBy { it.cauViDu?.trim() }
            .take(3)
            .map { ExampleItem(en = it.cauViDu!!.trim(), vi = it.dichNghia?.trim()) }
        if (stored.isNotEmpty()) _examples.value = stored
        else if (result.pendingReview) _examples.value = emptyList()
        else loadExamplesFromGemini(t.wordName, t.languageCode ?: "en")
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
            "table" to "dining_table",
            "desk" to "dining_table",
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
                ?: return imageBytes
            val maxSize = 800
            val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            
            val resized = if (ratio < 1.0f) {
                val newWidth = (bitmap.width * ratio).toInt()
                val newHeight = (bitmap.height * ratio).toInt()
                android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else bitmap
            
            val stream = java.io.ByteArrayOutputStream()
            resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
            val compressed = stream.toByteArray()
            if (resized !== bitmap) resized.recycle()
            bitmap.recycle()
            return compressed
        } catch (e: Exception) {
            // Nếu lỗi thì trả ảnh gốc
            return imageBytes
        }
    }

    fun loadExamplesFromGemini(word: String, lang: String) {
        viewModelScope.launch {
            _examples.value = repo.getExamples(word, lang)
                .distinct()
                .take(3)
                .map { ExampleItem(en = it, vi = null) }
        }
    }

    fun playAudio(audioUrl: String?, word: String, lang: String) {
        viewModelScope.launch {
            val bytes = audioUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { repo.getAudioByUrl(it) }
                ?: repo.getTtsAudio(word, lang)
            if (bytes != null) {
                audioPlayer.playMp3(bytes) {
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
        _detectionSource.value = null
    }

    fun clearGuestUpsell() { _showGuestUpsell.value = false }

    fun clearAddedMsg() { _addedMsg.value = null }

    fun showAddToCollectionDialog(translationId: Int, context: Context) {
        viewModelScope.launch {
            val result = collectionRepo.getCollections()
            val collections = result.getOrNull()
            if (collections.isNullOrEmpty()) {
                _addedMsg.value = "Chưa có bộ sưu tập nào. Tạo bộ sưu tập trong mục Tài khoản nhé!"
                return@launch
            }
            val labels = collections.map { it.name }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle("Thêm vào bộ sưu tập")
                .setItems(labels) { _, which ->
                    val collectionId = collections[which].id
                    viewModelScope.launch {
                        val r = collectionRepo.addToCollection(collectionId, translationId)
                        _addedMsg.value = if (r.isSuccess) "Đã thêm vào \"${collections[which].name}\"" else "Lỗi: ${r.exceptionOrNull()?.message}"
                    }
                }
                .setNegativeButton("Huỷ", null)
                .show()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
