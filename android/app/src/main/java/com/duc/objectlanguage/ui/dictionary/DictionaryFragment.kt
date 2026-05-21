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
import com.duc.objectlanguage.data.model.DictionaryHistoryItem
import com.duc.objectlanguage.databinding.FragmentDictionaryBinding
import com.duc.objectlanguage.ui.collection.SaveToCollectionBottomSheet
import com.google.android.material.chip.Chip

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
        viewModel.loadHistory()
        arguments?.getString("initialText")?.takeIf { it.isNotBlank() }?.let {
            binding.etInput.setText(it)
            binding.etInput.setSelection(binding.etInput.text?.length ?: 0)
        }
    }

    private fun applyDisplayLanguage() {
        binding.tvTitle.text = getString(R.string.dictionary_title)
        binding.etInput.hint = getString(R.string.dictionary_input_hint)
    }

    private fun setupListeners() {
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                binding.btnClearInput.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                if (text.isEmpty()) {
                    viewModel.clearResult()
                    binding.cardResult.visibility = View.GONE
                    binding.groupEmptyState.visibility = View.VISIBLE
                    binding.layoutRecentChips.visibility =
                        if (binding.chipGroupRecent.childCount > 0) View.VISIBLE else View.GONE
                } else {
                    binding.layoutRecentChips.visibility = View.GONE
                    binding.groupEmptyState.visibility = View.GONE
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

        binding.btnSaveWord.setOnClickListener {
            val translationId = viewModel.result.value?.translationId ?: return@setOnClickListener
            SaveToCollectionBottomSheet.newInstance(translationId)
                .show(childFragmentManager, "save_to_collection")
        }

        binding.btnPlayAudio.setOnClickListener {
            viewModel.playAudio(viewModel.pronunciationTarget(viewModel.result.value))
        }

        binding.btnPlayAudioResult.setOnClickListener {
            viewModel.playAudio(viewModel.pronunciationTarget(viewModel.result.value))
        }

        binding.btnShowHistory.setOnClickListener {
            val items = viewModel.history.value ?: return@setOnClickListener
            val listSheet = DictionaryHistoryListBottomSheet.newInstance(items)
            listSheet.setOnItemDeletedListener { id ->
                viewModel.deleteHistoryItem(id)
            }
            listSheet.show(childFragmentManager, "dict_history_list")
        }
    }

    private fun setupObservers() {
        viewModel.fromLang.observe(viewLifecycleOwner) { from ->
            val to = viewModel.toLang.value ?: "vi"
            updateLanguageLabels(from, to)
        }

        viewModel.toLang.observe(viewLifecycleOwner) { to ->
            val from = viewModel.fromLang.value ?: "en"
            updateLanguageLabels(from, to)
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
                viewModel.loadHistory()

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

                val hasPronunciation = viewModel.pronunciationTarget(result) != null
                val enIsSource = result.fromLang.lowercase() == "en"
                binding.btnPlayAudio.visibility =
                    if (hasPronunciation && enIsSource) View.VISIBLE else View.GONE
                binding.btnPlayAudioResult.visibility =
                    if (hasPronunciation && !enIsSource) View.VISIBLE else View.GONE
                binding.btnSaveWord.visibility = if (result.canSave && result.translationId != null) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            } else if (viewModel.isLoading.value != true) {
                binding.cardResult.visibility = View.GONE
                binding.btnPlayAudio.visibility = View.GONE
                binding.btnPlayAudioResult.visibility = View.GONE
                binding.btnSaveWord.visibility = View.GONE
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

        viewModel.message.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }

        viewModel.history.observe(viewLifecycleOwner) { items ->
            updateRecentChips(items)
        }
    }

    private fun updateLanguageLabels(from: String, to: String) {
        binding.tvFromLang.text = langLabel(from)
        binding.tvToLang.text = langLabel(to)
        binding.btnSwapLang.text = getString(
            R.string.dictionary_swap_format,
            langLabel(from),
            langLabel(to)
        )
    }

    private fun langLabel(code: String): String = when (code.lowercase()) {
        "vi" -> "VI"
        "en" -> "EN"
        else -> code.uppercase()
    }

    private fun updateRecentChips(items: List<DictionaryHistoryItem>) {
        binding.chipGroupRecent.removeAllViews()
        if (items.isEmpty()) {
            binding.layoutRecentChips.visibility = View.GONE
            return
        }
        items.take(3).forEach { item ->
            val chip = Chip(requireContext()).apply {
                text = item.tuTra
                isClickable = true
                isCheckable = false
                isCloseIconVisible = true
                chipBackgroundColor = requireContext().getColorStateList(R.color.surface)
                setTextColor(requireContext().getColor(R.color.text_primary))
                chipStrokeWidth = 1f
                setChipStrokeColorResource(R.color.divider)
                chipCornerRadius = 20f
            }
            chip.setOnClickListener {
                DictionaryHistoryDetailBottomSheet.newInstance(item).apply {
                    setOnDeletedListener { id -> viewModel.deleteHistoryItem(id) }
                }.show(childFragmentManager, "dict_history_detail_${item.id}")
            }
            chip.setOnCloseIconClickListener {
                viewModel.deleteHistoryItem(item.id)
            }
            binding.chipGroupRecent.addView(chip)
        }
        if (binding.etInput.text.isNullOrEmpty()) {
            binding.layoutRecentChips.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.etInput.text.isNullOrEmpty()) {
            viewModel.loadHistory()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
