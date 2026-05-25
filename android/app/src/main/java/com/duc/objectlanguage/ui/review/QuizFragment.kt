package com.duc.objectlanguage.ui.review

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
            binding.quizContainer.visibility = if (loading) View.GONE else View.VISIBLE
        }

        viewModel.currentQuestion.observe(viewLifecycleOwner) { question ->
            question?.let {
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
                binding.tvProgress.text = "Question ${index + 1}/$total"
            } else {
                binding.tvProgress.text = ""
            }
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            val total = viewModel.getTotalQuestions()
            if (total > 0) {
                binding.tvScore.text = "Score: $score/$total"
            } else {
                binding.tvScore.text = ""
            }
        }

        viewModel.finished.observe(viewLifecycleOwner) { finished ->
            if (finished) {
                timer?.cancel()
                showResultDialog()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayQuestion(question: QuizQuestion) {
        // Reset UI
        binding.radioGroupOptions.clearCheck()
        binding.btnSubmit.isEnabled = false
        resetOptionColors()

        // Display question
        binding.tvQuestion.text = "Vietnamese: ${question.questionWord}"

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
                binding.tvTimer.text = "⏱️ ${secondsRemaining}s"

                // Change color when time is running out
                val color = when {
                    secondsRemaining <= 3 -> android.graphics.Color.RED
                    secondsRemaining <= 5 -> android.graphics.Color.parseColor("#FF9800")
                    else -> android.graphics.Color.BLACK
                }
                binding.tvTimer.setTextColor(color)
            }

            override fun onFinish() {
                binding.tvTimer.text = "⏱️ 0s"
                // Auto-submit with no answer
                viewModel.submitAnswer(-1)
            }
        }.start()
    }

    private fun highlightAnswer(result: AnswerResult) {
        // Disable all options
        binding.option1.isEnabled = false
        binding.option2.isEnabled = false
        binding.option3.isEnabled = false
        binding.option4.isEnabled = false
        binding.btnSubmit.isEnabled = false

        val options = listOf(binding.option1, binding.option2, binding.option3, binding.option4)

        // Highlight correct answer
        options[result.correctIndex].setBackgroundColor(
            ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
        )

        // Highlight wrong answer if selected
        if (!result.isCorrect && result.selectedIndex >= 0) {
            options[result.selectedIndex].setBackgroundColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
            )
        }

        // Show feedback
        val feedback = if (result.isCorrect) "✅ Correct!" else "❌ Wrong!"
        Toast.makeText(requireContext(), feedback, Toast.LENGTH_SHORT).show()

        // Move to next question after delay
        binding.root.postDelayed({
            viewModel.moveToNext()
            resetOptionColors()
        }, 2000)
    }

    private fun resetOptionColors() {
        val defaultColor = ContextCompat.getColor(requireContext(), android.R.color.transparent)
        binding.option1.setBackgroundColor(defaultColor)
        binding.option2.setBackgroundColor(defaultColor)
        binding.option3.setBackgroundColor(defaultColor)
        binding.option4.setBackgroundColor(defaultColor)
    }

    private fun showResultDialog() {
        val score = viewModel.score.value ?: 0
        val total = viewModel.getTotalQuestions()
        val percentage = if (total > 0) (score * 100) / total else 0

        val message = when {
            percentage >= 90 -> "Amazing! You're a vocabulary master! 🏆"
            percentage >= 70 -> "Great job! Keep it up! ⭐"
            percentage >= 50 -> "Good effort! Practice makes perfect! 👍"
            else -> "Don't give up! Review and try again! 💪"
        }

        Toast.makeText(
            requireContext(),
            "Quiz Complete!\nScore: $score/$total ($percentage%)\n$message",
            Toast.LENGTH_LONG
        ).show()

        // Navigate back
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        _binding = null
    }
}
