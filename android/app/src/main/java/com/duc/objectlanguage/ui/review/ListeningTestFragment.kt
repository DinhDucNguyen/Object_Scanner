package com.duc.objectlanguage.ui.review

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.duc.objectlanguage.R
import java.util.*

class ListeningTestFragment : Fragment() {

    private lateinit var viewModel: ListeningTestViewModel
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    
    private lateinit var tvProgress: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var btnPlayAudio: ImageButton
    private lateinit var btnPlaySlow: Button
    private lateinit var tvRepeatCount: TextView
    private lateinit var tvHint: TextView
    private lateinit var etAnswer: EditText
    private lateinit var btnHintFirstLetter: Button
    private lateinit var btnHintWordLength: Button
    private lateinit var btnSubmit: Button
    private lateinit var btnSkip: Button
    private lateinit var progressBar: ProgressBar

    private var hintFirstLetterUsed = false
    private var hintWordLengthUsed = false
    private var repeatCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_listening_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ListeningTestViewModel::class.java]

        initTTS()
        initViews(view)
        setupObservers()
        setupListeners()

        viewModel.loadTest()
    }

    private fun initTTS() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.US)
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    private fun initViews(view: View) {
        tvProgress = view.findViewById(R.id.tvProgress)
        tvScore = view.findViewById(R.id.tvScore)
        tvInstruction = view.findViewById(R.id.tvInstruction)
        btnPlayAudio = view.findViewById(R.id.btnPlayAudio)
        btnPlaySlow = view.findViewById(R.id.btnPlaySlow)
        tvRepeatCount = view.findViewById(R.id.tvRepeatCount)
        tvHint = view.findViewById(R.id.tvHint)
        etAnswer = view.findViewById(R.id.etAnswer)
        btnHintFirstLetter = view.findViewById(R.id.btnHintFirstLetter)
        btnHintWordLength = view.findViewById(R.id.btnHintWordLength)
        btnSubmit = view.findViewById(R.id.btnSubmit)
        btnSkip = view.findViewById(R.id.btnSkip)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun setupObservers() {
        viewModel.currentQuestion.observe(viewLifecycleOwner) { question ->
            question?.let {
                etAnswer.text?.clear()
                etAnswer.isEnabled = true
                btnSubmit.isEnabled = true
                tvHint.text = ""
                tvHint.visibility = View.GONE
                hintFirstLetterUsed = false
                hintWordLengthUsed = false
                repeatCount = 0
                updateRepeatCount()
                btnHintFirstLetter.isEnabled = true
                btnHintWordLength.isEnabled = true
                btnPlayAudio.isEnabled = ttsReady
                btnPlaySlow.isEnabled = ttsReady
                
                // Auto-play first time
                playAudio(normalSpeed = true)
            }
        }

        viewModel.currentIndex.observe(viewLifecycleOwner) { index ->
            val total = viewModel.getTotalQuestions()
            if (total > 0) {
                tvProgress.text = "Question ${index + 1}/$total"
            } else {
                tvProgress.text = ""
            }
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            val total = viewModel.getTotalQuestions()
            if (total > 0) {
                tvScore.text = "Score: $score/$total"
            } else {
                tvScore.text = ""
            }
        }

        viewModel.answerResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                showAnswerFeedback(it)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            etAnswer.isEnabled = !loading
            btnSubmit.isEnabled = !loading
            btnSkip.isEnabled = !loading
            btnHintFirstLetter.isEnabled = !loading
            btnHintWordLength.isEnabled = !loading
            btnPlayAudio.isEnabled = !loading && ttsReady
            btnPlaySlow.isEnabled = !loading && ttsReady
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
        btnPlayAudio.setOnClickListener {
            playAudio(normalSpeed = true)
        }

        btnPlaySlow.setOnClickListener {
            playAudio(normalSpeed = false)
        }

        btnSubmit.setOnClickListener {
            val userAnswer = etAnswer.text.toString().trim()
            if (userAnswer.isEmpty()) {
                etAnswer.error = "Please type what you heard"
                return@setOnClickListener
            }
            viewModel.submitAnswer(userAnswer)
        }

        btnSkip.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Skip Question")
                .setMessage("Are you sure you want to skip? It will be marked as incorrect.")
                .setPositiveButton("Yes") { _, _ ->
                    viewModel.skipQuestion()
                }
                .setNegativeButton("No", null)
                .show()
        }

        btnHintFirstLetter.setOnClickListener {
            if (!hintFirstLetterUsed) {
                val hint = viewModel.getFirstLetterHint()
                tvHint.text = "Hint: Starts with \"$hint\""
                tvHint.visibility = View.VISIBLE
                hintFirstLetterUsed = true
                btnHintFirstLetter.isEnabled = false
            }
        }

        btnHintWordLength.setOnClickListener {
            if (!hintWordLengthUsed) {
                val hint = viewModel.getWordLengthHint()
                tvHint.text = "Hint: $hint letters"
                tvHint.visibility = View.VISIBLE
                hintWordLengthUsed = true
                btnHintWordLength.isEnabled = false
            }
        }

        etAnswer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                etAnswer.error = null
            }
        })
    }

    private fun playAudio(normalSpeed: Boolean) {
        val word = viewModel.currentQuestion.value?.correctAnswer ?: return
        
        if (!ttsReady) {
            showError("Text-to-Speech not ready yet")
            return
        }

        repeatCount++
        updateRepeatCount()

        val speed = if (normalSpeed) 0.8f else 0.5f
        tts.setSpeechRate(speed)
        tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun updateRepeatCount() {
        tvRepeatCount.text = "Played: $repeatCount times"
    }

    private fun showAnswerFeedback(result: ListeningAnswerResult) {
        etAnswer.isEnabled = false
        btnSubmit.isEnabled = false
        btnPlayAudio.isEnabled = false
        btnPlaySlow.isEnabled = false

        if (result.isCorrect) {
            // Correct answer
            etAnswer.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            tvHint.text = "✓ Correct!"
            tvHint.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            tvHint.visibility = View.VISIBLE
        } else {
            // Incorrect answer
            etAnswer.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            tvHint.text = "✗ Correct answer: ${result.correctAnswer}"
            tvHint.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            tvHint.visibility = View.VISIBLE
            
            // Play correct answer
            tts.setSpeechRate(0.6f)
            tts.speak(result.correctAnswer, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        // Auto-advance after 3 seconds
        view?.postDelayed({
            etAnswer.setTextColor(resources.getColor(android.R.color.black, null))
            tvHint.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            viewModel.moveToNext()
        }, 3000)
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
        val total = viewModel.getTotalQuestions()
        val percentage = if (total > 0) (score * 100) / total else 0

        val message = when {
            percentage >= 90 -> "Excellent listening! 🎧"
            percentage >= 70 -> "Great job! 👂"
            percentage >= 50 -> "Good effort! 🔊"
            else -> "Keep practicing! 📻"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Test Complete!")
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
    }
}
