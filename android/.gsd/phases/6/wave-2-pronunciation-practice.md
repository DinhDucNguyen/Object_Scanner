# Wave 2: Pronunciation Practice

> **Duration**: 3 ngày  
> **Dependencies**: Wave 1 complete  
> **Focus**: Speech recognition, pronunciation scoring, user feedback

---

## Objectives

Enable users to **practice and improve pronunciation** through:
- Recording their voice
- Comparing với native pronunciation
- Receiving AI-powered scoring (0-100%)
- Getting actionable feedback

---

## Technical Architecture

```
User speaks → Android SpeechRecognizer API
                ↓
         Recognized text
                ↓
    Compare với expected word
                ↓
    Calculate similarity score (Levenshtein distance)
                ↓
         Display feedback
```

---

## Task 2.1: Speech Recognition Setup (1 ngày)

### Step 1: Permissions & Setup (1 giờ)

**AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

**Request permission in Fragment:**
```kotlin
// PronunciationFragment.kt
private val requestAudioPermission = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) {
        initializeSpeechRecognizer()
    } else {
        showPermissionDeniedDialog()
    }
}

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    if (ContextCompat.checkSelfPermission(requireContext(), 
            Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
        initializeSpeechRecognizer()
    } else {
        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
}
```

### Step 2: SpeechRecognizer Integration (2 giờ)

```kotlin
// PronunciationHelper.kt
class PronunciationHelper(
    private val context: Context,
    private val onResult: (recognizedText: String, confidence: Float) -> Unit,
    private val onError: (errorCode: Int) -> Unit
) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    
    fun initialize() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        } else {
            onError(-1) // Recognition not available
        }
    }
    
    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            
            if (!matches.isNullOrEmpty()) {
                val bestMatch = matches[0]
                val confidence = confidences?.getOrNull(0) ?: 0f
                onResult(bestMatch, confidence)
            }
        }
        
        override fun onError(error: Int) {
            onError(error)
        }
        
        override fun onReadyForSpeech(params: Bundle?) {
            // UI: Show "Listening..." indicator
        }
        
        override fun onBeginningOfSpeech() {
            // UI: Animate microphone
        }
        
        override fun onEndOfSpeech() {
            // UI: Stop animation, show "Processing..."
        }
        
        // Other callbacks...
    }
    
    fun startListening(language: String = "en-US") {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000)
        }
        speechRecognizer?.startListening(intent)
    }
    
    fun stopListening() {
        speechRecognizer?.stopListening()
    }
    
    fun destroy() {
        speechRecognizer?.destroy()
    }
}
```

### Step 3: Error Handling (1 giờ)

```kotlin
// SpeechRecognizerErrors.kt
fun getErrorMessage(errorCode: Int): String = when (errorCode) {
    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
    SpeechRecognizer.ERROR_CLIENT -> "Client side error. Please try again."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
    SpeechRecognizer.ERROR_NETWORK -> "Network error. Check connection."
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout. Try again."
    SpeechRecognizer.ERROR_NO_MATCH -> "No match found. Speak clearly and try again."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy. Wait a moment."
    SpeechRecognizer.ERROR_SERVER -> "Server error. Try again later."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
    else -> "Unknown error occurred."
}
```

### Verification

```bash
✅ Microphone permission requested correctly
✅ SpeechRecognizer initializes without crash
✅ Error messages display appropriately
✅ Recognition works on multiple devices
```

---

## Task 2.2: Pronunciation UI & Recording (1 ngày)

### Step 1: PronunciationFragment Layout (1 giờ)

```xml
<!-- fragment_pronunciation.xml -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp">
    
    <!-- Word to practice -->
    <TextView
        android:id="@+id/tvTargetWord"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Apple"
        android:textSize="32sp"
        android:textStyle="bold"
        android:layout_gravity="center" />
    
    <!-- Phonetic -->
    <TextView
        android:id="@+id/tvPhonetic"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="/ˈæp.əl/"
        android:textSize="18sp"
        android:layout_gravity="center" />
    
    <!-- Native audio player -->
    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="32dp">
        
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:padding="16dp">
            
            <ImageButton
                android:id="@+id/btnPlayNative"
                android:src="@drawable/ic_play"
                android:contentDescription="Play native pronunciation" />
            
            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Native Speaker"
                android:layout_marginStart="16dp" />
            
            <ImageButton
                android:id="@+id/btnPlaySlow"
                android:src="@drawable/ic_slow"
                android:contentDescription="Play slow" />
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>
    
    <!-- Waveform visualization (optional) -->
    <com.github.mikephil.charting.charts.LineChart
        android:id="@+id/waveformChart"
        android:layout_width="match_parent"
        android:layout_height="100dp"
        android:layout_marginTop="16dp" />
    
    <!-- Recording section -->
    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="32dp">
        
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="24dp"
            android:gravity="center">
            
            <!-- Microphone button -->
            <com.airbnb.lottie.LottieAnimationView
                android:id="@+id/micAnimation"
                android:layout_width="120dp"
                android:layout_height="120dp"
                app:lottie_rawRes="@raw/microphone_animation"
                app:lottie_loop="true" />
            
            <Button
                android:id="@+id/btnRecord"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="🎤 Record"
                style="@style/Widget.Material3.Button.ElevatedButton" />
            
            <TextView
                android:id="@+id/tvRecordingStatus"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Press to speak"
                android:layout_marginTop="8dp" />
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>
    
    <!-- Results section (hidden initially) -->
    <com.google.android.material.card.MaterialCardView
        android:id="@+id/resultsCard"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:visibility="gone">
        
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">
            
            <TextView
                android:text="Your Score"
                android:textStyle="bold"
                android:textSize="16sp" />
            
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="8dp">
                
                <TextView
                    android:id="@+id/tvScore"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="85"
                    android:textSize="48sp"
                    android:textColor="@color/success_green" />
                
                <TextView
                    android:text="/100"
                    android:layout_marginStart="4dp"
                    android:textSize="24sp" />
                
                <TextView
                    android:id="@+id/tvStars"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="⭐⭐⭐⭐"
                    android:textSize="24sp"
                    android:gravity="end" />
            </LinearLayout>
            
            <TextView
                android:id="@+id/tvFeedback"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Great job! 👍"
                android:layout_marginTop="8dp" />
            
            <TextView
                android:id="@+id/tvRecognizedText"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Recognized: apple ✅"
                android:layout_marginTop="4dp"
                android:textStyle="italic" />
            
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginTop="16dp">
                
                <Button
                    android:id="@+id/btnTryAgain"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="Try Again"
                    android:layout_marginEnd="8dp" />
                
                <Button
                    android:id="@+id/btnNext"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="Next Word"
                    style="@style/Widget.Material3.Button" />
            </LinearLayout>
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>
</LinearLayout>
```

### Step 2: Recording Flow Implementation (2 giờ)

```kotlin
// PronunciationFragment.kt
class PronunciationFragment : Fragment() {
    
    private lateinit var binding: FragmentPronunciationBinding
    private val viewModel: PronunciationViewModel by viewModels()
    private lateinit var pronunciationHelper: PronunciationHelper
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        pronunciationHelper = PronunciationHelper(
            context = requireContext(),
            onResult = { text, confidence ->
                viewModel.processRecognition(text, confidence)
            },
            onError = { errorCode ->
                showError(getErrorMessage(errorCode))
            }
        )
        
        binding.btnRecord.setOnClickListener {
            startRecording()
        }
        
        binding.btnPlayNative.setOnClickListener {
            viewModel.playNativeAudio(speed = 1.0f)
        }
        
        binding.btnPlaySlow.setOnClickListener {
            viewModel.playNativeAudio(speed = 0.75f)
        }
        
        observeViewModel()
    }
    
    private fun startRecording() {
        binding.btnRecord.isEnabled = false
        binding.tvRecordingStatus.text = "🎤 Listening..."
        binding.micAnimation.playAnimation()
        
        pronunciationHelper.startListening(viewModel.currentWord.value?.languageCode ?: "en-US")
        
        // Auto-stop after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            pronunciationHelper.stopListening()
        }, 5000)
    }
    
    private fun observeViewModel() {
        viewModel.pronunciationResult.observe(viewLifecycleOwner) { result ->
            binding.micAnimation.pauseAnimation()
            binding.btnRecord.isEnabled = true
            binding.tvRecordingStatus.text = "Press to speak"
            
            // Show results card
            binding.resultsCard.visibility = View.VISIBLE
            binding.tvScore.text = result.score.toString()
            binding.tvStars.text = getStarRating(result.score)
            binding.tvFeedback.text = result.feedback
            binding.tvRecognizedText.text = "Recognized: ${result.recognizedText} ${if (result.isCorrect) "✅" else "❌"}"
            
            // Color code score
            binding.tvScore.setTextColor(when {
                result.score >= 80 -> Color.GREEN
                result.score >= 60 -> Color.YELLOW
                else -> Color.RED
            })
        }
        
        viewModel.error.observe(viewLifecycleOwner) { error ->
            showError(error)
        }
    }
    
    private fun getStarRating(score: Int) = when {
        score >= 90 -> "⭐⭐⭐⭐⭐"
        score >= 75 -> "⭐⭐⭐⭐"
        score >= 60 -> "⭐⭐⭐"
        score >= 40 -> "⭐⭐"
        else -> "⭐"
    }
}
```

### Verification

```bash
✅ Record button triggers speech recognition
✅ Microphone animation plays during recording
✅ Status text updates appropriately
✅ Results card displays after recognition
✅ Score and stars show correctly
```

---

## Task 2.3: Scoring & Feedback System (1 ngày)

### Step 1: Similarity Calculation (1.5 giờ)

```kotlin
// PronunciationScorer.kt
object PronunciationScorer {
    
    /**
     * Calculate pronunciation score based on:
     * 1. Levenshtein distance (word similarity)
     * 2. Speech recognition confidence
     * 3. Word length penalty
     */
    fun calculateScore(
        expectedWord: String,
        recognizedWord: String,
        confidence: Float
    ): PronunciationResult {
        val expected = expectedWord.lowercase().trim()
        val recognized = recognizedWord.lowercase().trim()
        
        // Exact match
        if (expected == recognized) {
            return PronunciationResult(
                score = 100,
                isCorrect = true,
                feedback = getExcellentFeedback(),
                recognizedText = recognizedWord
            )
        }
        
        // Calculate Levenshtein distance
        val distance = levenshteinDistance(expected, recognized)
        val maxLength = maxOf(expected.length, recognized.length)
        val similarityRatio = 1.0f - (distance.toFloat() / maxLength)
        
        // Combine similarity with confidence
        val baseScore = (similarityRatio * 0.7f + confidence * 0.3f) * 100
        val finalScore = baseScore.toInt().coerceIn(0, 100)
        
        return PronunciationResult(
            score = finalScore,
            isCorrect = finalScore >= 80,
            feedback = getFeedbackMessage(finalScore, expected, recognized),
            recognizedText = recognizedWord
        )
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[s1.length][s2.length]
    }
    
    private fun getFeedbackMessage(score: Int, expected: String, recognized: String): String {
        return when {
            score >= 90 -> """
                Perfect! Your pronunciation is excellent! 🎉
                Keep up the great work!
            """.trimIndent()
            
            score >= 75 -> """
                Great job! Your pronunciation is very good! ⭐⭐⭐⭐
                Just a bit more practice and you'll master it!
            """.trimIndent()
            
            score >= 60 -> """
                Good effort! You're on the right track! 👍
                Try emphasizing the stressed syllables.
            """.trimIndent()
            
            score >= 40 -> """
                Keep practicing! 💪
                Listen to the native audio carefully and try again.
            """.trimIndent()
            
            else -> """
                Don't give up! 🌟
                Expected: "$expected"
                You said: "$recognized"
                Take your time and speak clearly.
            """.trimIndent()
        }
    }
    
    private fun getExcellentFeedback() = listOf(
        "Perfect! You're a natural! 🎉",
        "Excellent pronunciation! Native-like! 🌟",
        "Wow! That was spot on! 👏",
        "Amazing! You nailed it! 🎯"
    ).random()
}

data class PronunciationResult(
    val score: Int,
    val isCorrect: Boolean,
    val feedback: String,
    val recognizedText: String
)
```

### Step 2: ViewModel Integration (1 giờ)

```kotlin
// PronunciationViewModel.kt
class PronunciationViewModel(
    private val repository: AppRepository
) : ViewModel() {
    
    private val _currentWord = MutableLiveData<LearningItem>()
    val currentWord: LiveData<LearningItem> = _currentWord
    
    private val _pronunciationResult = MutableLiveData<PronunciationResult>()
    val pronunciationResult: LiveData<PronunciationResult> = _pronunciationResult
    
    private val scoredWords = mutableMapOf<Int, Int>() // wordId → best score
    
    fun loadWord(wordId: Int) {
        viewModelScope.launch {
            val word = repository.getTranslationById(wordId)
            _currentWord.value = word
        }
    }
    
    fun processRecognition(recognizedText: String, confidence: Float) {
        val word = _currentWord.value ?: return
        
        val result = PronunciationScorer.calculateScore(
            expectedWord = word.englishWord,
            recognizedWord = recognizedText,
            confidence = confidence
        )
        
        // Save best score
        val currentBest = scoredWords[word.id] ?: 0
        if (result.score > currentBest) {
            scoredWords[word.id] = result.score
        }
        
        _pronunciationResult.value = result
    }
    
    fun playNativeAudio(speed: Float = 1.0f) {
        val word = _currentWord.value ?: return
        viewModelScope.launch {
            repository.playAudio(word.englishWord, "en", speed)
        }
    }
    
    fun saveProgress() {
        viewModelScope.launch {
            scoredWords.forEach { (wordId, score) ->
                // Store pronunciation score in backend
                repository.savePronunciationScore(wordId, score)
            }
        }
    }
}
```

### Step 3: Phoneme Analysis (Advanced - Optional, 30 min)

```kotlin
// PhonemeAnalyzer.kt
object PhonemeAnalyzer {
    
    // Map common phoneme mistakes
    private val commonMistakes = mapOf(
        "th" to listOf("s", "t", "d"),
        "r" to listOf("l", "w"),
        "v" to listOf("b", "w"),
        "l" to listOf("r")
    )
    
    fun analyzePhonemes(expected: String, recognized: String): String? {
        // Detect specific phoneme errors
        for ((target, mistakes) in commonMistakes) {
            if (expected.contains(target) && mistakes.any { recognized.contains(it) }) {
                return "Tip: Focus on the '$target' sound. It's different from '${mistakes[0]}'."
            }
        }
        return null
    }
}
```

### Verification

```bash
✅ Exact match → 100 score
✅ Close pronunciation (1-2 char diff) → 80-90 score
✅ Moderate difference → 60-75 score
✅ Poor match → < 60 score
✅ Feedback messages appropriate for score ranges
✅ Best score saved per word
```

---

## Wave 2 Completion Checklist

### Functional
- [ ] Microphone permission flow working
- [ ] Speech recognition captures user voice
- [ ] Scoring algorithm calculates accurately
- [ ] Feedback messages display appropriately
- [ ] Native audio playback functional
- [ ] Slow playback option works
- [ ] Results persist across app restarts

### Quality
- [ ] Recognition works trong noisy environments (reasonable threshold)
- [ ] Handles recognition errors gracefully
- [ ] UI responsive during recording
- [ ] Animations smooth (Lottie microphone)
- [ ] No memory leaks (SpeechRecognizer destroyed properly)

### Testing
- [ ] Test với 20 common words
- [ ] Test trên 3+ devices (Samsung, Xiaomi, Google Pixel)
- [ ] Test với non-native accents
- [ ] Test error cases (no speech, background noise)
- [ ] Verify scoring consistency (same word, same score)

---

## Next: Wave 3

Proceed to **Wave 3: Visual Analytics Dashboard** with MPAndroidChart integration.
