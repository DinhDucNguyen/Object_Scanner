package com.duc.objectlanguage.ui.review

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentTypingTestBinding

class TypingTestFragment : Fragment() {

    private var _binding: FragmentTypingTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TypingTestViewModel

    private var hintFirstLetterUsed = false
    private var hintWordLengthUsed = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTypingTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[TypingTestViewModel::class.java]

        setupObservers()
        setupListeners()

        viewModel.loadTest()
    }

    private fun setupObservers() {
        viewModel.currentQuestion.observe(viewLifecycleOwner) { question ->
            question?.let {
                binding.tvQuestion.text = it.questionWord
                binding.etAnswer.text?.clear()
                binding.etAnswer.isEnabled = true
                binding.btnSubmit.isEnabled = true
                binding.tvHint.text = ""
                binding.tvHint.visibility = View.GONE
                hintFirstLetterUsed = false
                hintWordLengthUsed = false
                binding.btnHintFirstLetter.isEnabled = true
                binding.btnHintWordLength.isEnabled = true
                binding.etAnswer.requestFocus()
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

    private fun showAnswerFeedback(result: TypingAnswerResult) {
        binding.etAnswer.isEnabled = false
        binding.btnSubmit.isEnabled = false

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
        }

        binding.root.postDelayed({
            if (_binding == null) return@postDelayed
            binding.etAnswer.setTextColor(resources.getColor(android.R.color.black, null))
            binding.tvHint.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            viewModel.moveToNext()
        }, 2000)
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
        _binding = null
    }
}
