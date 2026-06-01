package com.duc.objectlanguage.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.local.ApiConfig
import com.duc.objectlanguage.data.model.CollectionItem
import com.duc.objectlanguage.databinding.DialogCollectionWordDetailBinding
import com.duc.objectlanguage.databinding.FragmentCollectionDetailBinding
import com.duc.objectlanguage.utils.DefinitionFormatter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class CollectionDetailFragment : Fragment() {

    private var _binding: FragmentCollectionDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CollectionViewModel by viewModels()

    private val collectionId: Int by lazy { arguments?.getInt("collectionId") ?: 0 }
    private val isPractice: Boolean by lazy { arguments?.getBoolean("isPractice") ?: false }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCollectionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CollectionWordAdapter(
            onRemove = { translationId ->
                viewModel.removeFromCollection(collectionId, translationId)
            },
            onOpenDetail = { item ->
                showWordDetail(item)
            }
        )
        adapter.setCanEdit(!isPractice)
        binding.recyclerWords.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerWords.adapter = adapter
        applyEditMode(!isPractice)
        binding.cardAddWord.setOnClickListener { openAddWordSource() }
        binding.fabAddWord.setOnClickListener { openAddWordSource() }
        binding.btnEmptyAddWord.setOnClickListener { openAddWordSource() }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            if (!loading) binding.swipeRefresh.isRefreshing = false
        }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadCollectionDetail(collectionId)
            binding.swipeRefresh.post {
                Snackbar.make(binding.root, R.string.refresh_success, Snackbar.LENGTH_SHORT).show()
            }
        }

        viewModel.collectionDetail.observe(viewLifecycleOwner) { detail ->
            if (detail == null) return@observe
            binding.tvCollectionName.text = detail.name
            binding.tvWordCount.text = getString(R.string.collection_item_count_in_detail, detail.items.size)
            adapter.setCanEdit(detail.canEdit)
            applyEditMode(detail.canEdit)
            adapter.submitList(detail.items)
            binding.recyclerWords.visibility = View.VISIBLE
            binding.layoutEmpty.visibility = if (detail.items.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearSuccessMessage()
                viewModel.loadCollectionDetail(collectionId)
            }
        }

        binding.btnReviewCollection.setOnClickListener {
            val detail = viewModel.collectionDetail.value
            if (detail == null || detail.items.isEmpty()) {
                Toast.makeText(requireContext(), R.string.collection_empty_words, Toast.LENGTH_SHORT).show()
            } else {
                val args = Bundle().apply {
                    putInt("collectionId", collectionId)
                    putString("collectionName", detail.name)
                    putBoolean("isPractice", true)
                }
                findNavController().navigate(R.id.reviewFragment, args)
            }
        }

        viewModel.loadCollectionDetail(collectionId)
        animateEntrance()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCollectionDetail(collectionId)
    }

    private fun animateEntrance() {
        val interp = DecelerateInterpolator(1.6f)
        binding.tvCollectionName.apply {
            alpha = 0f; translationY = -20f
            animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(OvershootInterpolator(1.2f)).setStartDelay(40).start()
        }
        binding.btnReviewCollection.apply {
            alpha = 0f; translationX = 30f
            animate().alpha(1f).translationX(0f).setDuration(300).setInterpolator(interp).setStartDelay(100).start()
        }
        binding.recyclerWords.apply {
            alpha = 0f; translationY = 32f
            animate().alpha(1f).translationY(0f).setDuration(360).setInterpolator(interp).setStartDelay(160).start()
        }
    }

    private fun applyEditMode(canEdit: Boolean) {
        binding.cardAddWord.visibility = if (canEdit) View.VISIBLE else View.GONE
        binding.btnEmptyAddWord.visibility = if (canEdit) View.VISIBLE else View.GONE
        binding.fabAddWord.visibility = View.GONE
    }

    private fun openAddWordSource() {
        if (viewModel.collectionDetail.value?.canEdit != true) return
        val collectionName = viewModel.collectionDetail.value?.name ?: ""
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_add_word_source, binding.root, false)
        sheet.setContentView(view)

        view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardScan)
            .setOnClickListener {
                sheet.dismiss()
                findNavController().navigate(R.id.scanFragment)
            }
        view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardHistory)
            .setOnClickListener {
                sheet.dismiss()
                val args = Bundle().apply {
                    putInt("targetCollectionId", collectionId)
                    putString("targetCollectionName", collectionName)
                }
                findNavController().navigate(R.id.action_collectionDetail_to_history, args)
            }

        sheet.show()
    }

    private fun showWordDetail(item: CollectionItem) {
        val sheet = BottomSheetDialog(requireContext())
        val detailBinding = DialogCollectionWordDetailBinding.inflate(layoutInflater)

        detailBinding.tvWordTitle.text = item.translation
        detailBinding.tvWordCategory.text = item.category.uppercase(Locale.getDefault())
        detailBinding.tvObjectName.text = item.objectName.replace("_", " ")
            .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        if (item.phonetic.isNullOrBlank()) {
            detailBinding.tvWordPhonetic.visibility = View.GONE
        } else {
            detailBinding.tvWordPhonetic.text = item.phonetic
            detailBinding.tvWordPhonetic.visibility = View.VISIBLE
        }

        val definition = item.definition?.takeIf { it.isNotBlank() }
            ?: getString(R.string.object_detail_no_definition)
        detailBinding.tvWordDefinition.text =
            DefinitionFormatter.formatDefinition(requireContext(), definition)

        val exampleText = item.examples
            .filter { !it.cauViDu.isNullOrBlank() }
            .take(3)
            .joinToString(separator = "\n\n") { example ->
                buildString {
                    append("\"")
                    append(example.cauViDu?.trim())
                    append("\"")
                    example.dichNghia?.takeIf { it.isNotBlank() }?.let { translation ->
                        append("\n")
                        append(translation.trim())
                    }
                }
            }
        detailBinding.tvWordExample.visibility =
            if (exampleText.isBlank()) View.GONE else View.VISIBLE
        detailBinding.tvWordExample.text = exampleText

        if (item.imageUrl.isNullOrBlank()) {
            detailBinding.ivWordImage.setImageResource(R.drawable.ic_image_placeholder)
        } else {
            Glide.with(detailBinding.ivWordImage)
                .load(resolveMediaUrl(item.imageUrl))
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(detailBinding.ivWordImage)
        }

        detailBinding.btnSpeakWordDetail.setOnClickListener {
            viewModel.playAudio(
                audioUrl = item.audioUrl,
                word = item.translation,
                lang = item.languageCode ?: "en"
            )
        }
        detailBinding.btnCloseWordDetail.setOnClickListener { sheet.dismiss() }
        sheet.setContentView(detailBinding.root)
        sheet.show()
    }

    private fun resolveMediaUrl(path: String): String {
        val trimmed = path.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "${ApiConfig.baseUrl.trimEnd('/')}/${trimmed.trimStart('/')}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
