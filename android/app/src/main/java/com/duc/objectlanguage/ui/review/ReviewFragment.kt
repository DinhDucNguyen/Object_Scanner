package com.duc.objectlanguage.ui.review

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentReviewBinding
import com.duc.objectlanguage.ui.common.addPulseFeedback
import com.duc.objectlanguage.ui.common.addRaisedButtonFeedback

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

        viewModel.cards.observe(viewLifecycleOwner) { cards ->
            if (cards.isEmpty() && viewModel.isLoading.value == false && viewModel.finished.value != true) {
                binding.cardContent.visibility = View.GONE
                binding.buttonRow.visibility = View.GONE
                binding.layoutFinished.visibility = View.VISIBLE
                binding.tvFinished.text = getString(R.string.review_no_cards_due)
                updateFinishedButtons()
                animateFinished()
            } else {
                updateCard()
            }
        }
        viewModel.currentIndex.observe(viewLifecycleOwner) { updateCard() }
        viewModel.finishedMessage.observe(viewLifecycleOwner) { message ->
            binding.tvFinished.text = message
        }
        viewModel.sessionSummary.observe(viewLifecycleOwner) { summary ->
            updateSessionSummary(summary)
        }

        viewModel.finished.observe(viewLifecycleOwner) { done ->
            if (done) {
                binding.cardContent.visibility = View.GONE
                binding.buttonRow.visibility = View.GONE
                binding.layoutFinished.visibility = View.VISIBLE
                updateFinishedButtons()
                animateFinished()
            }
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }

        binding.btnReveal.addRaisedButtonFeedback()
        binding.btnAgain.addRaisedButtonFeedback()
        binding.btnHard.addRaisedButtonFeedback()
        binding.btnGood.addRaisedButtonFeedback()
        binding.btnEasy.addRaisedButtonFeedback()

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
        binding.btnPlayAudioReview.addPulseFeedback()
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
        binding.btnBackToCollection.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btnRetryCollection.setOnClickListener {
            viewModel.loadCards(collectionId, practice = true, collectionName)
        }

        animateEntrance()
    }

    private fun animateEntrance() {
        binding.cardContent.apply {
            alpha = 0f; translationY = 40f
            animate().alpha(1f).translationY(0f)
                .setDuration(380).setInterpolator(DecelerateInterpolator(1.8f)).setStartDelay(80).start()
        }
    }

    private fun animateFinished() {
        binding.layoutFinished.apply {
            scaleX = 0.85f; scaleY = 0.85f; alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(420).setInterpolator(OvershootInterpolator(1.4f)).start()
        }
    }

    override fun onResume() {
        super.onResume()
        collectionName?.let { requireActivity().title = getString(R.string.review_collection_title, it) }
        if (viewModel.cards.value.isNullOrEmpty() && viewModel.finished.value != true) {
            viewModel.loadCards(collectionId, isPractice, collectionName)
        }
    }

    private fun updateFinishedButtons() {
        val isCollection = (viewModel.activeCollectionId.value ?: 0) > 0
        val name = viewModel.activeCollectionName.value

        if (isCollection) {
            // Hiện nút collection, ẩn nút general
            binding.btnScanMore.visibility = View.GONE
            binding.btnGoToCollections.visibility = View.GONE
            binding.btnBackToCollection.visibility = View.VISIBLE
            binding.btnRetryCollection.visibility = View.VISIBLE
            // Cập nhật text finished với tên collection
            if (!name.isNullOrBlank()) {
                binding.tvFinished.text = getString(R.string.review_finished_collection, name)
            }
        } else {
            binding.btnScanMore.visibility = View.VISIBLE
            binding.btnGoToCollections.visibility = View.VISIBLE
            binding.btnBackToCollection.visibility = View.GONE
            binding.btnRetryCollection.visibility = View.GONE
        }
    }

    private fun submitAnswer(quality: Int) {
        vibrateReview(quality)
        viewModel.submitAnswer(quality)
    }

    private fun updateSessionSummary(summary: ReviewSessionSummary) {
        binding.layoutSessionSummary.visibility = if (summary.total > 0) View.VISIBLE else View.GONE
        binding.tvReviewTotal.text = getString(R.string.review_summary_total_value, summary.total)
        binding.tvReviewRemembered.text = getString(R.string.review_summary_remembered_value, summary.remembered)
        binding.tvReviewNeedsReview.text = getString(R.string.review_summary_needs_review_value, summary.needsReview)
        binding.tvReviewRate.text = getString(R.string.format_percent_int, summary.rememberedPercent)
    }

    private fun vibrateReview(quality: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requireContext().getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService<Vibrator>()
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (quality >= 4) {
                VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), -1)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            if (quality >= 4) vibrator.vibrate(40) else vibrator.vibrate(longArrayOf(0, 30, 60, 30), -1)
        }
    }

    private fun updateCard() {
        val cards = viewModel.cards.value ?: return
        val idx = viewModel.currentIndex.value ?: 0
        val card = cards.getOrNull(idx) ?: return

        // Front: từ tiếng Anh + phiên âm
        binding.tvQuestion.text = card.wordName
        binding.tvProgress.text = getString(R.string.format_fraction_spaced, idx + 1, cards.size)
        binding.tvLangHint.text = getString(R.string.review_lang_hint)

        if (!card.phonetic.isNullOrEmpty()) {
            binding.tvPhonetic.text = card.phonetic
            binding.tvPhonetic.visibility = View.VISIBLE
        } else {
            binding.tvPhonetic.visibility = View.GONE
        }

        // Back: nghĩa tiếng Việt + ví dụ
        val rawDef = card.definition ?: ""
        binding.tvAnswerDefinition.text = rawDef

        val example = card.examples.firstOrNull()?.cauViDu
        if (!example.isNullOrEmpty()) {
            binding.tvAnswerExample.text = getString(R.string.format_quoted_text, example)
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
