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
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentPronunciationBinding
import com.duc.objectlanguage.utils.DefinitionFormatter
import java.util.*

class PronunciationFragment : Fragment() {

    private var _binding: FragmentPronunciationBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PronunciationViewModel
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var ttsReady = false
    private var isRecording = false
    private val RECORD_AUDIO_PERMISSION = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPronunciationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[PronunciationViewModel::class.java]

        checkPermissions()
        initTTS()
        initSpeechRecognizer()
        setupObservers()
        setupListeners()

        viewModel.loadPractice()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
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
                val result = tts?.setLanguage(Locale.US) ?: return@TextToSpeech
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (_binding == null) return
                binding.tvRecordingStatus.text = getString(R.string.test_pronunciation_listening)
                binding.tvRecordingStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.success))
            }

            override fun onBeginningOfSpeech() {
                if (_binding == null) return
                binding.tvRecordingStatus.text = getString(R.string.test_pronunciation_speaking_detected)
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (_binding == null) return
                isRecording = false
                binding.btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)
                binding.tvRecordingStatus.text = getString(R.string.test_pronunciation_processing)
                binding.tvRecordingStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }

            override fun onError(error: Int) {
                if (_binding == null) return
                isRecording = false
                binding.btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> getString(R.string.test_pronunciation_error_audio)
                    SpeechRecognizer.ERROR_CLIENT -> getString(R.string.test_pronunciation_error_client)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> getString(R.string.test_pronunciation_error_permissions)
                    SpeechRecognizer.ERROR_NETWORK -> getString(R.string.test_pronunciation_error_network)
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> getString(R.string.test_pronunciation_error_network_timeout)
                    SpeechRecognizer.ERROR_NO_MATCH -> getString(R.string.test_pronunciation_error_no_match)
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> getString(R.string.test_pronunciation_error_busy)
                    SpeechRecognizer.ERROR_SERVER -> getString(R.string.test_pronunciation_error_server)
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.test_pronunciation_error_speech_timeout)
                    else -> getString(R.string.test_pronunciation_error_unknown)
                }
                binding.tvRecordingStatus.text = errorMessage
                binding.tvRecordingStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
            }

            override fun onResults(results: Bundle?) {
                if (_binding == null) return
                isRecording = false
                binding.btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    viewModel.evaluatePronunciation(matches[0])
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun setupObservers() {
        viewModel.currentWord.observe(viewLifecycleOwner) { word ->
            word?.let {
                binding.tvWord.text = it.wordName
                binding.tvPhonetic.text = it.phonetic
                binding.tvDefinition.text = DefinitionFormatter.formatDefinition(requireContext(), it.definition)
                binding.tvRecordingStatus.text = getString(R.string.test_pronunciation_tap_to_practice)
                binding.tvRecordingStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                binding.tvPronunciationScore.visibility = View.GONE
                binding.tvFeedback.visibility = View.GONE
                binding.btnNextWord.visibility = View.GONE
            }
        }

        viewModel.currentIndex.observe(viewLifecycleOwner) { index ->
            val total = viewModel.getTotalWords()
            binding.tvProgress.text = if (total > 0) {
                getString(R.string.test_pronunciation_word_counter, index + 1, total)
            } else {
                ""
            }
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            val total = viewModel.getTotalWords()
            binding.tvScore.text = if (total > 0) {
                getString(R.string.test_score, score, total)
            } else {
                ""
            }
        }

        viewModel.pronunciationResult.observe(viewLifecycleOwner) { result ->
            result?.let { showPronunciationResult(it) }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnRecord.isEnabled = !loading
            binding.btnPlayModel.isEnabled = !loading && ttsReady
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError(it)
                viewModel.clearError()
            }
        }

        viewModel.finished.observe(viewLifecycleOwner) { finished ->
            if (finished && viewModel.getTotalWords() > 0) {
                refreshReviewBadge()
                showResultDialog()
            }
        }
    }

    private fun setupListeners() {
        binding.btnPlayModel.setOnClickListener { playModelPronunciation() }

        binding.btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }

        binding.btnNextWord.setOnClickListener { viewModel.moveToNext() }
    }

    private fun playModelPronunciation() {
        val word = viewModel.currentWord.value?.wordName ?: return
        if (!ttsReady) {
            showError(getString(R.string.test_tts_not_ready))
            return
        }
        tts?.setSpeechRate(0.7f)
        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showError(getString(R.string.test_mic_permission))
            return
        }
        isRecording = true
        binding.btnRecord.setImageResource(android.R.drawable.presence_busy)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopRecording() {
        isRecording = false
        binding.btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)
        speechRecognizer?.stopListening()
    }

    private fun showPronunciationResult(result: PronunciationResult) {
        binding.tvRecordingStatus.text = getString(R.string.test_pronunciation_you_said, result.userSpeech)
        binding.tvPronunciationScore.visibility = View.VISIBLE
        binding.tvPronunciationScore.text = getString(R.string.test_pronunciation_score, result.score)
        binding.tvFeedback.visibility = View.VISIBLE

        val (scoreColor, feedbackText) = when {
            result.score >= 90 -> Pair(R.color.success, getString(R.string.test_pronunciation_feedback_excellent))
            result.score >= 70 -> Pair(R.color.primary, getString(R.string.test_pronunciation_feedback_good))
            result.score >= 50 -> Pair(R.color.warning, getString(R.string.test_pronunciation_feedback_ok))
            else -> Pair(R.color.error, getString(R.string.test_pronunciation_feedback_retry))
        }
        val color = ContextCompat.getColor(requireContext(), scoreColor)
        binding.tvPronunciationScore.setTextColor(color)
        binding.tvFeedback.text = feedbackText
        binding.tvFeedback.setTextColor(color)

        binding.btnNextWord.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.test_error_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.test_ok), null)
            .show()
    }

    private fun refreshReviewBadge() {
        (activity as? com.duc.objectlanguage.ui.MainActivity)?.updateReviewBadge()
    }

    private fun showResultDialog() {
        val score = viewModel.score.value ?: 0
        val total = viewModel.getTotalWords()
        val percentage = if (total > 0) (score * 100) / total else 0

        val message = when {
            percentage >= 90 -> getString(R.string.test_result_excellent)
            percentage >= 70 -> getString(R.string.test_result_great)
            percentage >= 50 -> getString(R.string.test_result_good)
            else -> getString(R.string.test_result_keep_going)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.test_complete_title))
            .setMessage(getString(R.string.test_complete_body, message, score, total, percentage))
            .setPositiveButton(getString(R.string.test_done)) { _, _ ->
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tts?.stop()
        tts?.shutdown()
        tts = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        _binding = null
    }
}
