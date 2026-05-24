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
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentReviewBinding

class ReviewFragment : Fragment() {

    private var _binding: FragmentReviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReviewViewModel by viewModels()

    private val collectionId: Int by lazy { arguments?.getInt("collectionId") ?: 0 }
    private val collectionName: String? by lazy { arguments?.getString("collectionName") }
    private val isPractice: Boolean by lazy { arguments?.getBoolean("isPractice") ?: false }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            if (loading) {
                binding.cardContent.visibility = View.GONE
                binding.buttonRow.visibility = View.GONE
                binding.layoutFinished.visibility = View.GONE
            }
        }

        viewModel.cards.observe(viewLifecycleOwner) { updateCard() }
        viewModel.currentIndex.observe(viewLifecycleOwner) { updateCard() }
        viewModel.finishedMessage.observe(viewLifecycleOwner) { message ->
            binding.tvFinished.text = message
        }

        viewModel.finished.observe(viewLifecycleOwner) { done ->
            if (done) {
                binding.cardContent.visibility = View.GONE
                binding.buttonRow.visibility = View.GONE
                binding.layoutFinished.visibility = View.VISIBLE
            }
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }

        binding.btnReveal.setOnClickListener {
            val flipOut = ObjectAnimator.ofFloat(binding.cardFlashcard, "rotationY", 0f, 90f).apply { duration = 200 }
            val flipIn  = ObjectAnimator.ofFloat(binding.cardFlashcard, "rotationY", -90f, 0f).apply { duration = 200 }
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
        binding.btnPlayAudioReview.setOnClickListener {
            currentCard()?.let { card ->
                viewModel.playAudio(card.audioUrl, card.wordName, card.languageCode.ifBlank { "en" })
            }
        }
        binding.btnScanMore.setOnClickListener {
            findNavController().navigate(R.id.scanFragment)
        }
        binding.btnGoToCollections.setOnClickListener {
            findNavController().navigate(R.id.collectionListFragment)
        }

    }

    override fun onResume() {
        super.onResume()
        collectionName?.let { requireActivity().title = "Ôn tập: $it" }
        viewModel.loadCards(collectionId, isPractice)
    }

    private fun submitAnswer(quality: Int) {
        viewModel.submitAnswer(quality)
    }

    private fun updateCard() {
        val cards = viewModel.cards.value ?: return
        val idx = viewModel.currentIndex.value ?: 0
        val card = cards.getOrNull(idx) ?: return

        // Front: từ tiếng Anh + phiên âm
        binding.tvQuestion.text = card.wordName
        binding.tvProgress.text = "${idx + 1} / ${cards.size}"
        binding.tvLangHint.text = getString(R.string.review_lang_hint)

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
        binding.layoutFinished.visibility = View.GONE
        binding.layoutAnswer.visibility = View.GONE
        binding.btnReveal.visibility = View.VISIBLE
        binding.buttonRow.visibility = View.GONE
        binding.cardFlashcard.rotationY = 0f
    }

    private fun currentCard() =
        viewModel.cards.value?.getOrNull(viewModel.currentIndex.value ?: 0)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
