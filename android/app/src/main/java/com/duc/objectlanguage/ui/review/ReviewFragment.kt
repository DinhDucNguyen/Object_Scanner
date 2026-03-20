package com.duc.objectlanguage.ui.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.duc.objectlanguage.databinding.FragmentReviewBinding

class ReviewFragment : Fragment() {

    private var _binding: FragmentReviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReviewViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.cards.observe(viewLifecycleOwner) { cards ->
            updateCard()
        }

        viewModel.currentIndex.observe(viewLifecycleOwner) {
            updateCard()
        }

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

        // Reveal answer
        // Reveal answer with flip
        binding.btnReveal.setOnClickListener {
            val flipOut = ObjectAnimator.ofFloat(binding.cardContent, "rotationY", 0f, 90f)
            flipOut.duration = 200
            val flipIn = ObjectAnimator.ofFloat(binding.cardContent, "rotationY", -90f, 0f)
            flipIn.duration = 200

            flipOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.tvAnswer.visibility = View.VISIBLE
                    binding.btnReveal.visibility = View.GONE
                    binding.buttonRow.visibility = View.VISIBLE
                    flipIn.start()
                }
            })
            flipOut.start()
        }

        // Quality buttons (SM-2: 0-5)
        binding.btnAgain.setOnClickListener { viewModel.submitAnswer(1) }
        binding.btnHard.setOnClickListener { viewModel.submitAnswer(3) }
        binding.btnGood.setOnClickListener { viewModel.submitAnswer(4) }
        binding.btnEasy.setOnClickListener { viewModel.submitAnswer(5) }

        viewModel.loadCards()
    }

    private fun updateCard() {
        val cards = viewModel.cards.value ?: return
        val idx = viewModel.currentIndex.value ?: 0
        if (idx >= cards.size) return

        val card = cards[idx]
        binding.cardContent.visibility = View.VISIBLE
        binding.tvFinished.visibility = View.GONE
        binding.tvQuestion.text = card.objectCode.replaceFirstChar { it.uppercase() }
        binding.tvLangHint.text = "🌐 ${card.languageName}"
        binding.tvAnswer.text = buildString {
            appendLine("${card.wordName}")
            if (!card.phonetic.isNullOrEmpty()) appendLine("🔤 ${card.phonetic}")
            if (!card.definition.isNullOrEmpty()) appendLine("📖 ${card.definition}")
            if (!card.exampleSentence.isNullOrEmpty()) appendLine("💬 ${card.exampleSentence}")
        }
        binding.tvAnswer.visibility = View.GONE
        binding.btnReveal.visibility = View.VISIBLE
        binding.buttonRow.visibility = View.GONE
        binding.tvProgress.text = "${idx + 1} / ${cards.size}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
