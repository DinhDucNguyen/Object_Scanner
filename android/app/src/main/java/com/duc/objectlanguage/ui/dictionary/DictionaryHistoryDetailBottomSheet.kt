package com.duc.objectlanguage.ui.dictionary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.duc.objectlanguage.data.model.DictionaryHistoryItem
import com.duc.objectlanguage.databinding.DialogDictionaryHistoryDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DictionaryHistoryDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogDictionaryHistoryDetailBinding? = null
    private val binding get() = _binding!!

    private var onDeleted: ((Int) -> Unit)? = null

    fun setOnDeletedListener(listener: (Int) -> Unit) {
        onDeleted = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogDictionaryHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val json = arguments?.getString(ARG_ITEM) ?: return
        val item = Gson().fromJson(json, DictionaryHistoryItem::class.java) ?: return
        bindItem(item)

        binding.btnDeleteHistory.setOnClickListener {
            onDeleted?.invoke(item.id)
            dismiss()
        }
    }

    private fun bindItem(item: DictionaryHistoryItem) {
        binding.tvDetailWord.text = item.tuTra
        binding.tvDetailTranslation.text = item.ketQuaDich ?: ""
        binding.tvDetailTime.text = formatDateTime(item.lanTraCuoi)

        val fromLang = if (item.denNgonNgu == "vi") "EN" else "VI"
        binding.tvFromLangBadge.text = fromLang
        binding.tvToLangBadge.text = item.denNgonNgu.uppercase()

        if (!item.phienAm.isNullOrBlank()) {
            binding.tvDetailPhonetic.text = item.phienAm
            binding.tvDetailPhonetic.visibility = View.VISIBLE
        } else {
            binding.tvDetailPhonetic.visibility = View.GONE
        }

        if (!item.objectCode.isNullOrBlank()) {
            binding.layoutObjectCode.visibility = View.VISIBLE
            binding.tvObjectCode.text = item.objectCode
        } else {
            binding.layoutObjectCode.visibility = View.GONE
        }

        if (!item.imageUrl.isNullOrBlank()) {
            binding.layoutImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(item.imageUrl)
                .centerCrop()
                .into(binding.ivDetailImage)
        } else {
            binding.layoutImage.visibility = View.GONE
        }
    }

    private fun formatDateTime(isoTime: String?): String {
        if (isoTime.isNullOrBlank()) return ""
        return try {
            val dt = parseIsoLikeTime(isoTime) ?: return ""
            SimpleDateFormat("HH:mm, dd/MM/yyyy", Locale.getDefault()).format(dt)
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseIsoLikeTime(value: String): Date? {
        val normalized = value.take(19).replace(' ', 'T')
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(normalized)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ITEM = "arg_item"

        fun newInstance(item: DictionaryHistoryItem): DictionaryHistoryDetailBottomSheet {
            return DictionaryHistoryDetailBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ITEM, Gson().toJson(item))
                }
            }
        }
    }
}
