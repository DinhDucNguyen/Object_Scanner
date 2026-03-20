package com.duc.objectlanguage.ui.review

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.duc.objectlanguage.R

class TypingTestFragment : Fragment() {

    private lateinit var viewModel: TypingTestViewModel
    private lateinit var tvProgress: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvQuestion: TextView
    private lateinit var tvHint: TextView
    private lateinit var etAnswer: EditText
    private lateinit var btnHintFirstLetter: Button
    private lateinit var btnHintWordLength: Button
    private lateinit var btnSubmit: Button
    private lateinit var btnSkip: Button
    private lateinit var progressBar: ProgressBar

    private var hintFirstLetterUsed = false
    private var hintWordLengthUsed = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_typing_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[TypingTestViewModel::class.java]

        initViews(view)
        setupObservers()
        setupListeners()

        viewModel.loadTest()
    }

    private fun initViews(view: View) {
        tvProgress = view.findViewById(R.id.tvProgress)
        tvScore = view.findViewById(R.id.tvScore)
        tvQuestion = view.findViewById(R.id.tvQuestion)
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
                tvQuestion.text = it.questionWord
                etAnswer.text?.clear()
                etAnswer.isEnabled = true
                btnSubmit.isEnabled = true
                tvHint.text = ""
                tvHint.visibility = View.GONE
                hintFirstLetterUsed = false
                hintWordLengthUsed = false
                btnHintFirstLetter.isEnabled = true
                btnHintWordLength.isEnabled = true
                etAnswer.requestFocus()
            }
        }

        viewModel.currentIndex.observe(viewLifecycleOwner) { index ->
            val total = viewModel.getTotalQuestions()
            tvProgress.text = "Question ${index + 1}/$total"
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            val total = viewModel.getTotalQuestions()
            tvScore.text = "Score: $score/$total"
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
        btnSubmit.setOnClickListener {
            val userAnswer = etAnswer.text.toString().trim()
            if (userAnswer.isEmpty()) {
                etAnswer.error = "Please type an answer"
                return@setOnClickListener
            }
            viewModel.submitAnswer(userAnswer)
        }

        btnSkip.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Skip Question")
                .setMessage("Are you sure you want to skip this question? It will be marked as incorrect.")
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

    private fun showAnswerFeedback(result: TypingAnswerResult) {
        etAnswer.isEnabled = false
        btnSubmit.isEnabled = false

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
        }

        // Auto-advance after 2 seconds
        view?.postDelayed({
            etAnswer.setTextColor(resources.getColor(android.R.color.black, null))
            tvHint.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            viewModel.moveToNext()
        }, 2000)
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
            percentage >= 90 -> "Outstanding! 🌟"
            percentage >= 70 -> "Great work! 💪"
            percentage >= 50 -> "Good effort! 👍"
            else -> "Keep practicing! 📚"
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
}
