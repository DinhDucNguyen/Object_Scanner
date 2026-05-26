package com.duc.objectlanguage.ui.review

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentListeningTestBinding
import java.util.*

class ListeningTestFragment : Fragment() {

    private var _binding: FragmentListeningTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ListeningTestViewModel
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var hintFirstLetterUsed = false
    private var hintWordLengthUsed = false
    private var repeatCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListeningTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ListeningTestViewModel::class.java]

        initTTS()
        setupObservers()
        setupListeners()

        viewModel.loadTest()
    }

    private fun initTTS() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US) ?: return@TextToSpeech
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    private fun setupObservers() {
        viewModel.currentQuestion.observe(viewLifecycleOwner) { question ->
            question?.let {
                binding.etAnswer.text?.clear()
                binding.etAnswer.isEnabled = true
                binding.btnSubmit.isEnabled = true
                binding.tvHint.text = ""
                binding.tvHint.visibility = View.GONE
                hintFirstLetterUsed = false
                hintWordLengthUsed = false
                repeatCount = 0
                updateRepeatCount()
                binding.btnHintFirstLetter.isEnabled = true
                binding.btnHintWordLength.isEnabled = true
                binding.btnPlayAudio.isEnabled = ttsReady
                binding.btnPlaySlow.isEnabled = ttsReady
                playAudio(normalSpeed = true)
            }
        }

        viewModel.currentIndex.observe(viewLifecycleOwner) { index ->
            val total = viewModel.getTotalQuestions()
            binding.tvProgress.text = if (total > 0)
                getString(R.string.test_question_counter, index + 1, total) else ""
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            val total = viewModel.getTotalQuestions()
            binding.tvScore.text = if (total > 0)
                getString(R.string.test_score, score, total) else ""
        }

        viewModel.answerResult.observe(viewLifecycleOwner) { result ->
            result?.let { showAnswerFeedback(it) }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.etAnswer.isEnabled = !loading
            binding.btnSubmit.isEnabled = !loading
            binding.btnSkip.isEnabled = !loading
            binding.btnHintFirstLetter.isEnabled = !loading
            binding.btnHintWordLength.isEnabled = !loading
            binding.btnPlayAudio.isEnabled = !loading && ttsReady
            binding.btnPlaySlow.isEnabled = !loading && ttsReady
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError(it)
                viewModel.clearError()
            }
        }

        viewModel.finished.observe(viewLifecycleOwner) { finished ->
            if (finished) showResultDialog()
        }
    }

    private fun setupListeners() {
        binding.btnPlayAudio.setOnClickListener { playAudio(normalSpeed = true) }
        binding.btnPlaySlow.setOnClickListener { playAudio(normalSpeed = false) }

        binding.btnSubmit.setOnClickListener {
            val userAnswer = binding.etAnswer.text.toString().trim()
            if (userAnswer.isEmpty()) {
                binding.etAnswer.error = getString(R.string.test_answer_empty)
                return@setOnClickListener
            }
            viewModel.submitAnswer(userAnswer)
        }

        binding.btnSkip.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.test_skip_title))
                .setMessage(getString(R.string.test_skip_message))
                .setPositiveButton(getString(R.string.test_yes)) { _, _ -> viewModel.skipQuestion() }
                .setNegativeButton(getString(R.string.test_no), null)
                .show()
        }

        binding.btnHintFirstLetter.setOnClickListener {
            if (!hintFirstLetterUsed) {
                binding.tvHint.text = getString(R.string.test_hint_first_letter, viewModel.getFirstLetterHint())
                binding.tvHint.visibility = View.VISIBLE
                hintFirstLetterUsed = true
                binding.btnHintFirstLetter.isEnabled = false
            }
        }

        binding.btnHintWordLength.setOnClickListener {
            if (!hintWordLengthUsed) {
                binding.tvHint.text = getString(R.string.test_hint_word_length, viewModel.getWordLengthHint())
                binding.tvHint.visibility = View.VISIBLE
                hintWordLengthUsed = true
                binding.btnHintWordLength.isEnabled = false
            }
        }

        binding.etAnswer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { binding.etAnswer.error = null }
        })
    }

    private fun playAudio(normalSpeed: Boolean) {
        val word = viewModel.currentQuestion.value?.correctAnswer ?: return
        if (!ttsReady) {
            showError(getString(R.string.test_tts_not_ready))
            return
        }
        repeatCount++
        updateRepeatCount()
        tts?.setSpeechRate(if (normalSpeed) 0.8f else 0.5f)
        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun updateRepeatCount() {
        binding.tvRepeatCount.text = getString(R.string.test_listening_played, repeatCount)
    }

    private fun showAnswerFeedback(result: ListeningAnswerResult) {
        binding.etAnswer.isEnabled = false
        binding.btnSubmit.isEnabled = false
        binding.btnPlayAudio.isEnabled = false
        binding.btnPlaySlow.isEnabled = false

        if (result.isCorrect) {
            binding.etAnswer.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            binding.tvHint.text = getString(R.string.test_feedback_correct)
            binding.tvHint.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            binding.tvHint.visibility = View.VISIBLE
        } else {
            binding.etAnswer.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            binding.tvHint.text = getString(R.string.test_feedback_wrong, result.correctAnswer)
            binding.tvHint.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            binding.tvHint.visibility = View.VISIBLE
            tts?.setSpeechRate(0.6f)
            tts?.speak(result.correctAnswer, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        binding.root.postDelayed({
            if (_binding == null) return@postDelayed
            binding.etAnswer.setTextColor(resources.getColor(android.R.color.black, null))
            binding.tvHint.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            viewModel.moveToNext()
        }, 3000)
    }

    private fun showError(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.test_error_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.test_ok), null)
            .show()
    }

    private fun showResultDialog() {
        val score = viewModel.score.value ?: 0
        val total = viewModel.getTotalQuestions()
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
        _binding = null
    }
}
