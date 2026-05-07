package com.duc.objectlanguage.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.duc.objectlanguage.databinding.FragmentHistoryDetailBinding

class HistoryDetailFragment : Fragment() {

    private var _binding: FragmentHistoryDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryDetailViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val objectCode = arguments?.getString("objectCode") ?: ""
        val imageUrl = arguments?.getString("imageUrl")
        val scanDate = arguments?.getString("scanDate")

        binding.tvObjectName.text = objectCode.replaceFirstChar { it.uppercase() }
        binding.tvScanDate.text = formatDate(scanDate)

        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(android.R.color.darker_gray)
                .into(binding.ivScannedImage)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.translations.observe(viewLifecycleOwner) { translations ->
            val t = translations.firstOrNull { it.languageCode == "en" }
                ?: translations.firstOrNull()

            if (t == null) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.cardTranslation.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.cardTranslation.visibility = View.VISIBLE

                binding.tvWord.text = t.wordName
                binding.tvPhonetic.apply {
                    text = t.phonetic ?: ""
                    visibility = if (t.phonetic.isNullOrEmpty()) View.GONE else View.VISIBLE
                }
                binding.tvDefinition.apply {
                    text = t.definition ?: ""
                    visibility = if (t.definition.isNullOrEmpty()) View.GONE else View.VISIBLE
                }
                binding.tvExample.apply {
                    val ex = t.examples.firstOrNull()?.cauViDu
                    text = ex ?: ""
                    visibility = if (ex.isNullOrEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        viewModel.loadTranslations(objectCode)
    }

    private fun formatDate(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        return try {
            val dt = raw.replace("T", " ").substringBefore(".")
            dt
        } catch (e: Exception) {
            raw
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
