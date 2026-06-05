package com.duc.objectlanguage.ui.review

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentQuizBinding

class QuizFragment : Fragment() {

    private var _binding: FragmentQuizBinding? = null
    private val binding get() = _binding!!
    private val viewModel: QuizViewModel by viewModels()
    private var timer: CountDownTimer? = null
    private var advanceRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeViewModel()

        viewModel.loadQuiz()
    }

    private fun setupListeners() {
        binding.btnSubmit.setOnClickListener {
            submitAnswer()
        }

        binding.radioGroupOptions.setOnCheckedChangeListener { _, _ ->
            binding.btnSubmit.isEnabled = true
        }
    }

    private fun submitAnswer() {
        val selectedId = binding.radioGroupOptions.checkedRadioButtonId
        val selectedIndex = when (selectedId) {
            binding.option1.id -> 0
            binding.option2.id -> 1
            binding.option3.id -> 2
            binding.option4.id -> 3
            else -> -1
        }

        if (selectedIndex != -1) {
            timer?.cancel()
            viewModel.submitAnswer(selectedIndex)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            if (loading) {
                binding.quizContainer.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.GONE
            }
        }

        viewModel.currentQuestion.observe(viewLifecycleOwner) { question ->
            question?.let {
                binding.emptyStateLayout.visibility = View.GONE
                binding.quizContainer.visibility = View.VISIBLE
                displayQuestion(it)
                startTimer()
            }
        }

        viewModel.answerResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                highlightAnswer(it)
            }
        }

        viewModel.currentIndex.observe(viewLifecycleOwner) { index ->
            val total = viewModel.getTotalQuestions()
            if (total > 0) {
                binding.tvProgress.text = getString(R.string.test_question_counter, index + 1, total)
            } else {
                binding.tvProgress.text = ""
            }
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            val total = viewModel.getTotalQuestions()
            if (total > 0) {
                binding.tvScore.text = getString(R.string.test_score, score, total)
            } else {
                binding.tvScore.text = ""
            }
        }

        viewModel.finished.observe(viewLifecycleOwner) { finished ->
            if (finished && viewModel.getTotalQuestions() > 0) {
                timer?.cancel()
                refreshReviewBadge()
                showResultDialog()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError(it)
                viewModel.clearError()
            }
        }

        viewModel.emptyMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) showEmptyState(message)
        }
    }

    private fun displayQuestion(question: QuizQuestion) {
        // Reset UI
        binding.radioGroupOptions.clearCheck()
        binding.btnSubmit.isEnabled = false
        resetOptionColors()

        // Display question
        binding.tvQuestion.text = getString(R.string.quiz_question_label, question.questionWord)

        // Display options
        binding.option1.text = question.options[0]
        binding.option2.text = question.options[1]
        binding.option3.text = question.options[2]
        binding.option4.text = question.options[3]

        binding.option1.isEnabled = true
        binding.option2.isEnabled = true
        binding.option3.isEnabled = true
        binding.option4.isEnabled = true
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = getString(R.string.test_timer_seconds, secondsRemaining)

                // Change color when time is running out
                val color = when {
                    secondsRemaining <= 3 -> android.graphics.Color.RED
                    secondsRemaining <= 5 -> android.graphics.Color.parseColor("#FF9800")
                    else -> android.graphics.Color.BLACK
                }
                binding.tvTimer.setTextColor(color)
            }

            override fun onFinish() {
                binding.tvTimer.text = getString(R.string.test_timer_zero)
                // Auto-submit with no answer
                viewModel.submitAnswer(-1)
            }
        }.start()
    }

    private fun highlightAnswer(result: AnswerResult) {
        binding.option1.isEnabled = false
        binding.option2.isEnabled = false
        binding.option3.isEnabled = false
        binding.option4.isEnabled = false
        binding.btnSubmit.isEnabled = false

        val options = listOf(binding.option1, binding.option2, binding.option3, binding.option4)
        val correctView = options[result.correctIndex]

        tintOption(correctView, R.color.success_light, R.color.success)
        bounceView(correctView)

        if (!result.isCorrect && result.selectedIndex >= 0) {
            val wrongView = options[result.selectedIndex]
            tintOption(wrongView, R.color.error_light, R.color.error)
            shakeView(wrongView)
        }

        advanceRunnable?.let { binding.root.removeCallbacks(it) }
        advanceRunnable = Runnable {
            if (_binding != null) {
                viewModel.moveToNext()
                resetOptionColors()
            }
        }
        advanceRunnable?.let { binding.root.postDelayed(it, 1800) }
    }

    private fun tintOption(view: RadioButton, bgColorRes: Int, textColorRes: Int) {
        view.buttonTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), textColorRes))
        view.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), bgColorRes))
        view.setTextColor(ContextCompat.getColor(requireContext(), textColorRes))
    }

    private fun bounceView(view: View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.08f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.08f, 1f),
            )
            duration = 320
            interpolator = OvershootInterpolator(2f)
            start()
        }
    }

    private fun shakeView(view: View) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, -10f, 10f, -8f, 8f, -5f, 5f, 0f)
            .apply { duration = 380; start() }
    }

    private fun resetOptionColors() {
        listOf(binding.option1, binding.option2, binding.option3, binding.option4).forEach { rb ->
            rb.backgroundTintList = null
            rb.buttonTintList = null
            rb.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }
    }

    private fun showEmptyState(message: String) {
        timer?.cancel()
        binding.quizContainer.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.tvEmptyMessage.text = message
        binding.btnEmptyBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showError(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
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
        val total = viewModel.getTotalQuestions()
        val percentage = if (total > 0) (score * 100) / total else 0

        val message = when {
            percentage >= 90 -> getString(R.string.test_result_excellent)
            percentage >= 70 -> getString(R.string.test_result_great)
            percentage >= 50 -> getString(R.string.test_result_good)
            else -> getString(R.string.test_result_keep_going)
        }

        Toast.makeText(
            requireContext(),
            getString(R.string.test_complete_message, score, total, percentage, message),
            Toast.LENGTH_LONG
        ).show()

        // Navigate back
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        advanceRunnable?.let { binding.root.removeCallbacks(it) }
        advanceRunnable = null
        _binding = null
    }
}
