package com.duc.objectlanguage.ui.review

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.duc.objectlanguage.R
import com.google.android.material.card.MaterialCardView
import java.util.*
import com.duc.objectlanguage.utils.DefinitionFormatter

class PronunciationFragment : Fragment() {

    private lateinit var viewModel: PronunciationViewModel
    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    
    private lateinit var tvProgress: TextView
    private lateinit var tvScore: TextView
    private lateinit var cardWord: MaterialCardView
    private lateinit var tvWord: TextView
    private lateinit var tvPhonetic: TextView
    private lateinit var tvDefinition: TextView
    private lateinit var btnPlayModel: Button
    private lateinit var btnRecord: ImageButton
    private lateinit var tvRecordingStatus: TextView
    private lateinit var tvPronunciationScore: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var btnNextWord: Button
    private lateinit var progressBar: ProgressBar

    private var ttsReady = false
    private var isRecording = false
    private val RECORD_AUDIO_PERMISSION = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pronunciation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[PronunciationViewModel::class.java]

        checkPermissions()
        initTTS()
        initSpeechRecognizer()
        initViews(view)
        setupObservers()
        setupListeners()

        viewModel.loadPractice()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION
            )
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.US)
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                tvRecordingStatus.text = "🎤 Listening..."
                tvRecordingStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            }

            override fun onBeginningOfSpeech() {
                tvRecordingStatus.text = "🎤 Speaking detected..."
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isRecording = false
                btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)
                tvRecordingStatus.text = "Processing..."
                tvRecordingStatus.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            }

            override fun onError(error: Int) {
                isRecording = false
                btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try again!"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input. Try again!"
                    else -> "Unknown error"
                }
                tvRecordingStatus.text = errorMessage
                tvRecordingStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            }

            override fun onResults(results: Bundle?) {
                isRecording = false
                btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)
                
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val userSpeech = matches[0]
                    viewModel.evaluatePronunciation(userSpeech)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun initViews(view: View) {
        tvProgress = view.findViewById(R.id.tvProgress)
        tvScore = view.findViewById(R.id.tvScore)
        cardWord = view.findViewById(R.id.cardWord)
        tvWord = view.findViewById(R.id.tvWord)
        tvPhonetic = view.findViewById(R.id.tvPhonetic)
        tvDefinition = view.findViewById(R.id.tvDefinition)
        btnPlayModel = view.findViewById(R.id.btnPlayModel)
        btnRecord = view.findViewById(R.id.btnRecord)
        tvRecordingStatus = view.findViewById(R.id.tvRecordingStatus)
        tvPronunciationScore = view.findViewById(R.id.tvPronunciationScore)
        tvFeedback = view.findViewById(R.id.tvFeedback)
        btnNextWord = view.findViewById(R.id.btnNextWord)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun setupObservers() {
        viewModel.currentWord.observe(viewLifecycleOwner) { word ->
            word?.let {
                tvWord.text = it.wordName
                tvPhonetic.text = it.phonetic
                tvDefinition.text = DefinitionFormatter.formatDefinition(requireContext(), it.definition)
                tvRecordingStatus.text = "Tap microphone to practice"
                tvRecordingStatus.setTextColor(resources.getColor(android.R.color.darker_gray, null))
                tvPronunciationScore.visibility = View.GONE
                tvFeedback.visibility = View.GONE
                btnNextWord.visibility = View.GONE
            }
        }

        viewModel.currentIndex.observe(viewLifecycleOwner) { index ->
            val total = viewModel.getTotalWords()
            tvProgress.text = "Word ${index + 1}/$total"
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            val total = viewModel.getTotalWords()
            tvScore.text = "Score: $score/$total"
        }

        viewModel.pronunciationResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                showPronunciationResult(it)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            btnRecord.isEnabled = !loading
            btnPlayModel.isEnabled = !loading && ttsReady
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError(it)
                viewModel.clearError()
            }
        }

        viewModel.finished.observe(viewLifecycleOwner) { finished ->
            if (finished) {
                showResultDialog()
            }
        }
    }

    private fun setupListeners() {
        btnPlayModel.setOnClickListener {
            playModelPronunciation()
        }

        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        btnNextWord.setOnClickListener {
            viewModel.moveToNext()
        }
    }

    private fun playModelPronunciation() {
        val word = viewModel.currentWord.value?.wordName ?: return
        
        if (!ttsReady) {
            showError("Text-to-Speech not ready yet")
            return
        }

        tts.setSpeechRate(0.7f) // Slower for learning
        tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showError("Microphone permission required")
            return
        }

        isRecording = true
        btnRecord.setImageResource(android.R.drawable.presence_busy)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer.startListening(intent)
    }

    private fun stopRecording() {
        isRecording = false
        btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)
        speechRecognizer.stopListening()
    }

    private fun showPronunciationResult(result: PronunciationResult) {
        tvRecordingStatus.text = "You said: \"${result.userSpeech}\""
        
        tvPronunciationScore.visibility = View.VISIBLE
        tvPronunciationScore.text = "Score: ${result.score}%"
        
        tvFeedback.visibility = View.VISIBLE
        when {
            result.score >= 90 -> {
                tvPronunciationScore.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                tvFeedback.text = "🌟 Perfect! Excellent pronunciation!"
                tvFeedback.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            }
            result.score >= 70 -> {
                tvPronunciationScore.setTextColor(resources.getColor(android.R.color.holo_blue_bright, null))
                tvFeedback.text = "👍 Good job! Keep practicing!"
                tvFeedback.setTextColor(resources.getColor(android.R.color.holo_blue_bright, null))
            }
            result.score >= 50 -> {
                tvPronunciationScore.setTextColor(resources.getColor(android.R.color.holo_orange_light, null))
                tvFeedback.text = "📖 Not bad! Try listening to the model again."
                tvFeedback.setTextColor(resources.getColor(android.R.color.holo_orange_light, null))
            }
            else -> {
                tvPronunciationScore.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
                tvFeedback.text = "🔄 Keep trying! Listen carefully and repeat."
                tvFeedback.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
            }
        }

        btnNextWord.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showResultDialog() {
        val score = viewModel.score.value ?: 0
        val total = viewModel.getTotalWords()
        val percentage = if (total > 0) (score * 100) / total else 0

        val message = when {
            percentage >= 90 -> "Outstanding pronunciation! 🗣️✨"
            percentage >= 70 -> "Great speaking skills! 🎤"
            percentage >= 50 -> "Good progress! 📢"
            else -> "Keep practicing! 🔊"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Practice Complete!")
            .setMessage("$message\n\nYou scored $score out of $total ($percentage%)")
            .setPositiveButton("Done") { _, _ ->
                requireActivity().onBackPressed()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (this::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }
}
