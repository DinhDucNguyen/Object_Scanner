package com.duc.objectlanguage.ui.dictionary

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentDictionaryBinding
import com.duc.objectlanguage.utils.LocaleHelper

class DictionaryFragment : Fragment() {

    private var _binding: FragmentDictionaryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DictionaryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDictionaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyDisplayLanguage()
        viewModel.loadDefaultLangs()
        setupListeners()
        setupObservers()
    }

    private fun applyDisplayLanguage() {
        val isEn = LocaleHelper.getSavedLocale(requireContext()) == "en"
        binding.tvTitle.text = if (isEn) "Translate" else "Dịch"
        binding.etInput.hint = if (isEn) "Enter text to translate..." else "Nhập văn bản để dịch..."
    }

    private fun setupListeners() {
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                binding.btnClearInput.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                if (text.isEmpty()) {
                    viewModel.clearResult()
                    binding.cardResult.visibility = View.GONE
                    binding.groupEmptyState.visibility = View.VISIBLE
                } else {
                    viewModel.translate(text)
                }
            }
        })

        binding.btnClearInput.setOnClickListener {
            binding.etInput.text?.clear()
        }

        binding.btnSwapLang.setOnClickListener {
            val currentText = binding.etInput.text?.toString() ?: ""
            viewModel.swapLanguages(currentText)
        }

        // Audio playback reserved for future TTS integration
    }

    private fun setupObservers() {
        viewModel.fromLang.observe(viewLifecycleOwner) { from ->
            val to = viewModel.toLang.value ?: "vi"
            binding.tvFromLang.text = langLabel(from)
            binding.btnSwapLang.text = "${langLabel(from)} ⇌ ${langLabel(to)}"
        }

        viewModel.toLang.observe(viewLifecycleOwner) { to ->
            val from = viewModel.fromLang.value ?: "en"
            binding.tvToLang.text = langLabel(to)
            binding.btnSwapLang.text = "${langLabel(from)} ⇌ ${langLabel(to)}"
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.loadingRow.visibility = if (loading) View.VISIBLE else View.GONE
            if (loading) {
                binding.cardResult.visibility = View.GONE
                binding.groupEmptyState.visibility = View.GONE
            }
        }

        viewModel.result.observe(viewLifecycleOwner) { result ->
            if (result != null) {
                binding.groupEmptyState.visibility = View.GONE
                binding.cardResult.visibility = View.VISIBLE

                binding.tvTranslation.text = result.translation
                binding.tvToLang.text = langLabel(result.toLang)

                val showPhonetic = !result.phonetic.isNullOrEmpty()
                    && result.toLang !in listOf("vi", "zh", "fr")
                if (showPhonetic) {
                    binding.tvPhonetic.text = result.phonetic
                    binding.tvPhonetic.visibility = View.VISIBLE
                } else {
                    binding.tvPhonetic.visibility = View.GONE
                }

                // Definitions (for single words)
                binding.containerDefinitions.removeAllViews()
                if (result.definitions.isNotEmpty()) {
                    binding.dividerDefs.visibility = View.VISIBLE
                    binding.containerDefinitions.visibility = View.VISIBLE
                    result.definitions.forEachIndexed { i, def ->
                        val tv = TextView(requireContext()).apply {
                            text = "${i + 1}. $def"
                            textSize = 14f
                            setTextColor(resources.getColor(R.color.text_secondary, null))
                            setPadding(0, 0, 0, 8)
                        }
                        binding.containerDefinitions.addView(tv)
                    }
                } else {
                    binding.dividerDefs.visibility = View.GONE
                    binding.containerDefinitions.visibility = View.GONE
                }

                // Audio button (only available for single words via TTS)
                binding.btnPlayAudio.visibility = View.GONE
            } else if (viewModel.isLoading.value != true) {
                binding.cardResult.visibility = View.GONE
                if (binding.etInput.text.isNullOrEmpty()) {
                    binding.groupEmptyState.visibility = View.VISIBLE
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun langLabel(code: String): String = when (code.lowercase()) {
        "vi" -> "VI 🇻🇳"
        "en" -> "EN 🇺🇸"
        else -> code.uppercase()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
