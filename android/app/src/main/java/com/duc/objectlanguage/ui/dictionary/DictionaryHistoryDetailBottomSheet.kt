package com.duc.objectlanguage.ui.dictionary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.bumptech.glide.Glide
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.DictionaryHistoryItem
import com.duc.objectlanguage.databinding.DialogDictionaryHistoryDetailBinding
import com.duc.objectlanguage.utils.AudioPlayerManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DictionaryHistoryDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogDictionaryHistoryDetailBinding? = null
    private val binding get() = _binding!!

    private var onDeleted: ((Int) -> Unit)? = null
    private lateinit var audioPlayer: AudioPlayerManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun setOnDeletedListener(listener: (Int) -> Unit) {
        onDeleted = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogDictionaryHistoryDetailBinding.inflate(inflater, container, false)
        audioPlayer = AudioPlayerManager(requireContext().applicationContext)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val json = arguments?.getString(ARG_ITEM) ?: return
        val item = Gson().fromJson(json, DictionaryHistoryItem::class.java) ?: return
        bindItem(item)


    }

    private fun bindItem(item: DictionaryHistoryItem) {
        binding.tvDetailWord.text = item.tuTra
        binding.tvDetailTranslation.text = item.ketQuaDich ?: ""
        binding.tvDetailTime.text = formatDateTime(item.lanTraCuoi)

        val fromLang = if (item.denNgonNgu == "vi") "EN" else "VI"
        binding.tvFromLangBadge.text = fromLang
        binding.tvToLangBadge.text = item.denNgonNgu.uppercase()

        // EN→VI: phonetic của từ nguồn tiếng Anh → hiện dưới tvDetailWord
        // VI→EN: phonetic của kết quả tiếng Anh → hiện dưới tvDetailTranslation
        val isViToEn = item.denNgonNgu == "en"
        if (!item.phienAm.isNullOrBlank()) {
            if (isViToEn) {
                binding.tvDetailPhonetic.visibility = View.GONE
                binding.tvPhoneticTranslation.text = item.phienAm
                binding.tvPhoneticTranslation.visibility = View.VISIBLE
            } else {
                binding.tvDetailPhonetic.text = item.phienAm
                binding.tvDetailPhonetic.visibility = View.VISIBLE
                binding.tvPhoneticTranslation.visibility = View.GONE
            }
        } else {
            binding.tvDetailPhonetic.visibility = View.GONE
            binding.tvPhoneticTranslation.visibility = View.GONE
        }

        val repo = (requireActivity().application as ObjectLanguageApp).repository

        // EN→VI: nút phát âm cạnh từ nguồn (tuTra) — chỉ hiện nếu là từ/cụm từ ngắn
        if (item.denNgonNgu == "vi" && item.tuTra.isNotBlank() && item.tuTra.length <= 50) {
            binding.btnPlayAudioWord.visibility = View.VISIBLE
            binding.btnPlayAudioWord.setOnClickListener {
                playWord(repo, item.tuTra, "en", binding.btnPlayAudioWord)
            }
        } else {
            binding.btnPlayAudioWord.visibility = View.GONE
        }

        // VI→EN: nút phát âm cạnh kết quả dịch — chỉ hiện nếu là từ/cụm từ ngắn
        val translation = item.ketQuaDich
        if (item.denNgonNgu == "en" && !translation.isNullOrBlank() && translation.length <= 50) {
            binding.btnPlayAudioTranslation.visibility = View.VISIBLE
            binding.btnPlayAudioTranslation.setOnClickListener {
                playWord(repo, translation, "en", binding.btnPlayAudioTranslation)
            }
        } else {
            binding.btnPlayAudioTranslation.visibility = View.GONE
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

    private fun playWord(
        repo: com.duc.objectlanguage.data.repository.AppRepository,
        word: String,
        lang: String,
        btn: com.google.android.material.button.MaterialButton,
    ) {
        btn.isEnabled = false
        scope.launch {
            val bytes = repo.getTtsAudio(word, lang)
            if (_binding == null) return@launch
            btn.isEnabled = true
            if (bytes != null) {
                audioPlayer.playMp3(bytes) {
                    Toast.makeText(requireContext(), getString(R.string.history_audio_play_error), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.history_audio_load_error), Toast.LENGTH_SHORT).show()
            }
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

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            val maxHeight = (resources.displayMetrics.heightPixels * 0.85).toInt()
            behavior.maxHeight = maxHeight
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioPlayer.release()
        scope.cancel()
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
