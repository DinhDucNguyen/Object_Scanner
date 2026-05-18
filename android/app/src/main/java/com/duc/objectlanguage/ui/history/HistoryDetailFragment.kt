package com.duc.objectlanguage.ui.history

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.TranslationResponse
import com.duc.objectlanguage.databinding.FragmentHistoryDetailBinding

class HistoryDetailFragment : Fragment() {

    private var _binding: FragmentHistoryDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryDetailViewModel by viewModels()
    private var currentAudioUrl: String? = null
    private var currentWord: String? = null
    private var currentLanguage: String = "en"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scanId = arguments?.getInt("scanId") ?: 0
        val objectCode = arguments?.getString("objectCode") ?: ""
        val imageUrl = arguments?.getString("imageUrl")
        val scanDate = arguments?.getString("scanDate")

        bindHeader(
            title = objectCode.replace("_", " ").replaceFirstChar { it.uppercase() },
            imageUrl = imageUrl,
            scanDate = scanDate,
            confidence = null,
            status = null,
        )
        setupObservers()
        binding.btnPlayAudioHistory.setOnClickListener {
            viewModel.playAudio(currentAudioUrl, currentWord, currentLanguage)
        }
        viewModel.loadDetail(scanId, objectCode)
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.detail.observe(viewLifecycleOwner) { detail ->
            if (detail == null) return@observe
            val title = detail.wordName
                ?: detail.objectName
                ?: detail.objectCode?.replace("_", " ")
                ?: ""
            bindHeader(
                title = title.replaceFirstChar { it.uppercase() },
                imageUrl = detail.imageUrl,
                scanDate = detail.scanDate,
                confidence = detail.confidenceScore,
                status = detail.status ?: detail.reviewStatus,
            )
        }

        viewModel.translations.observe(viewLifecycleOwner) { translations ->
            bindTranslation(translations)
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun bindHeader(title: String, imageUrl: String?, scanDate: String?, confidence: Float?, status: String?) {
        binding.tvObjectName.text = title.ifBlank { "---" }
        binding.tvScanDate.text = formatDate(scanDate)
        bindReviewStatus(binding.tvReviewStatus, status)
        binding.tvConfidence.apply {
            if (confidence == null) {
                visibility = View.GONE
            } else {
                text = "${(confidence * 100).toInt()}%"
                visibility = View.VISIBLE
            }
        }
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .into(binding.ivScannedImage)
    }

    private fun bindReviewStatus(view: TextView, status: String?) {
        val (label, textColor, bgColor) = when (status) {
            "pending_review", "cho_duyet" -> Triple(
                getString(R.string.history_status_pending),
                R.color.warning,
                R.color.warning_light
            )
            "rejected", "tu_choi" -> Triple(
                getString(R.string.history_status_rejected),
                R.color.error,
                R.color.error_light
            )
            "approved", "da_duyet" -> Triple(
                getString(R.string.history_status_approved),
                R.color.success,
                R.color.success_light
            )
            else -> {
                view.visibility = View.GONE
                return
            }
        }

        val radius = 999f * resources.displayMetrics.density
        view.text = label
        view.setTextColor(ContextCompat.getColor(requireContext(), textColor))
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(ContextCompat.getColor(requireContext(), bgColor))
        }
        view.visibility = View.VISIBLE
    }

    private fun bindTranslation(translations: List<TranslationResponse>) {
        val translation = translations.firstOrNull { it.languageCode == "en" }
            ?: translations.firstOrNull()

        if (translation == null) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.cardTranslation.visibility = View.GONE
            currentAudioUrl = null
            currentWord = null
            currentLanguage = "en"
            binding.btnPlayAudioHistory.visibility = View.GONE
            return
        }

        binding.tvEmpty.visibility = View.GONE
        binding.cardTranslation.visibility = View.VISIBLE
        currentAudioUrl = translation.audioUrl
        currentWord = translation.wordName
        currentLanguage = translation.languageCode ?: "en"

        binding.tvWord.text = translation.wordName
        binding.tvPhonetic.apply {
            text = translation.phonetic ?: ""
            visibility = if (translation.phonetic.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        binding.tvDefinition.apply {
            text = translation.definition ?: ""
            visibility = if (translation.definition.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        binding.tvExample.apply {
            val example = translation.examples.firstOrNull()?.cauViDu
            text = example ?: ""
            visibility = if (example.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        binding.btnPlayAudioHistory.visibility = if (
            translation.audioUrl.isNullOrBlank() && translation.wordName.isBlank()
        ) View.GONE else View.VISIBLE
    }

    private fun formatDate(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        return raw.replace("T", " ").substringBefore(".")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
