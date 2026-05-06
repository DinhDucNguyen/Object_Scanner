package com.duc.objectlanguage.ui.review

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.duc.objectlanguage.databinding.FragmentReviewBinding
import com.duc.objectlanguage.ui.streak.StreakViewModel

class ReviewFragment : Fragment() {

    private var _binding: FragmentReviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReviewViewModel by viewModels()
    private val streakViewModel: StreakViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.cards.observe(viewLifecycleOwner) { updateCard() }
        viewModel.currentIndex.observe(viewLifecycleOwner) { updateCard() }

        viewModel.finished.observe(viewLifecycleOwner) { done ->
            if (done) {
                binding.cardContent.visibility = View.GONE
                binding.buttonRow.visibility = View.GONE
                binding.tvFinished.visibility = View.VISIBLE
            }
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }

        binding.btnReveal.setOnClickListener {
            val flipOut = ObjectAnimator.ofFloat(binding.cardContent, "rotationY", 0f, 90f).apply { duration = 200 }
            val flipIn  = ObjectAnimator.ofFloat(binding.cardContent, "rotationY", -90f, 0f).apply { duration = 200 }
            flipOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.layoutAnswer.visibility = View.VISIBLE
                    binding.btnReveal.visibility = View.GONE
                    binding.buttonRow.visibility = View.VISIBLE
                    flipIn.start()
                }
            })
            flipOut.start()
        }

        binding.btnAgain.setOnClickListener { submitAnswer(1) }
        binding.btnHard.setOnClickListener  { submitAnswer(3) }
        binding.btnGood.setOnClickListener  { submitAnswer(4) }
        binding.btnEasy.setOnClickListener  { submitAnswer(5) }

        viewModel.loadCards()
    }

    private fun submitAnswer(quality: Int) {
        streakViewModel.recordReview()
        viewModel.submitAnswer(quality)
    }

    private fun updateCard() {
        val cards = viewModel.cards.value ?: return
        val idx   = viewModel.currentIndex.value ?: 0
        if (idx >= cards.size) return

        val card = cards[idx]

        // Front: từ tiếng Anh + phiên âm
        binding.tvQuestion.text = card.wordName
        binding.tvProgress.text = "${idx + 1} / ${cards.size}"
        binding.tvLangHint.text = "🇺🇸 English"

        if (!card.phonetic.isNullOrEmpty()) {
            binding.tvPhonetic.text = card.phonetic
            binding.tvPhonetic.visibility = View.VISIBLE
        } else {
            binding.tvPhonetic.visibility = View.GONE
        }

        // Back: nghĩa tiếng Việt + ví dụ
        binding.tvAnswerDefinition.text = card.definition ?: ""

        val example = card.examples.firstOrNull()?.cauViDu
        if (!example.isNullOrEmpty()) {
            binding.tvAnswerExample.text = "\"$example\""
            binding.tvAnswerExample.visibility = View.VISIBLE
        } else {
            binding.tvAnswerExample.visibility = View.GONE
        }

        // Reset về trạng thái front
        binding.cardContent.visibility = View.VISIBLE
        binding.tvFinished.visibility = View.GONE
        binding.layoutAnswer.visibility = View.GONE
        binding.btnReveal.visibility = View.VISIBLE
        binding.buttonRow.visibility = View.GONE
        binding.cardContent.rotationY = 0f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
