package com.duc.objectlanguage.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.ObjectData
import com.duc.objectlanguage.databinding.DialogObjectDetailBinding
import com.duc.objectlanguage.databinding.FragmentCategoryDetailBinding
import com.duc.objectlanguage.databinding.SheetCategoryFlashcardBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.duc.objectlanguage.utils.DefinitionFormatter

class CategoryDetailFragment : Fragment() {

    private var _binding: FragmentCategoryDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CategoryDetailViewModel by viewModels()
    private lateinit var adapter: ObjectAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val categoryId = requireArguments().getInt("categoryId")
        val categoryName = requireArguments().getString("categoryName") ?: getString(R.string.category_detail_fallback)
        binding.title.text = categoryName

        adapter = ObjectAdapter { item -> showObjectDetail(item) }
        binding.recyclerViewObjects.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewObjects.adapter = adapter

        viewModel.objects.observe(viewLifecycleOwner) { objects ->
            adapter.submitList(objects)
            binding.emptyStateLayout.visibility = if (objects.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerViewObjects.visibility = if (objects.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            if (!loading) binding.swipeRefresh.isRefreshing = false
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadObjects(categoryId) }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        viewModel.loadObjects(categoryId)

        binding.btnLearnNow.setOnClickListener {
            val items = viewModel.objects.value ?: emptyList()
            if (items.isNotEmpty()) showFlashcardSheet(items, categoryName)
        }
    }

    private fun showFlashcardSheet(items: List<ObjectData>, categoryName: String) {
        val sheet = BottomSheetDialog(requireContext())
        val sheetBinding = SheetCategoryFlashcardBinding.inflate(layoutInflater)
        sheet.setContentView(sheetBinding.root)

        var currentIndex = 0
        var isFlipped = false

        fun updateProgress() {
            sheetBinding.progressFlashcard.max = items.size
            sheetBinding.progressFlashcard.progress = currentIndex + 1
            sheetBinding.tvCardCounter.text = getString(R.string.flashcard_counter, currentIndex + 1, items.size)
        }

        fun showCard(index: Int) {
            val item = items[index]
            isFlipped = false
            sheetBinding.sideFront.visibility = android.view.View.VISIBLE
            sheetBinding.sideBack.visibility = android.view.View.GONE
            sheetBinding.tvWord.text = item.wordName ?: item.objectCode.replace("_", " ")
            sheetBinding.tvPhonetic.text = item.phonetic ?: ""
            sheetBinding.tvPhonetic.visibility = if (item.phonetic.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
            sheetBinding.tvDefinition.text = item.definition ?: ""
            sheetBinding.btnNextCard.text = if (index == items.size - 1)
                getString(R.string.flashcard_finish) else getString(R.string.flashcard_next)
            updateProgress()
        }

        sheetBinding.tvSheetTitle.text = categoryName

        sheetBinding.cardFlashcard.setOnClickListener {
            if (!isFlipped) {
                isFlipped = true
                sheetBinding.cardFlashcard.animate().scaleX(0f).setDuration(120).withEndAction {
                    sheetBinding.sideFront.visibility = android.view.View.GONE
                    sheetBinding.sideBack.visibility = android.view.View.VISIBLE
                    sheetBinding.cardFlashcard.animate().scaleX(1f).setDuration(120).start()
                }.start()
            }
        }

        sheetBinding.btnNextCard.setOnClickListener {
            if (currentIndex < items.size - 1) {
                currentIndex++
                showCard(currentIndex)
            } else {
                sheet.dismiss()
            }
        }

        sheetBinding.btnCloseSheet.setOnClickListener { sheet.dismiss() }
        sheetBinding.btnSpeak.setOnClickListener {
            val word = items[currentIndex].wordName ?: items[currentIndex].objectCode.replace("_", " ")
            viewModel.playAudio(word)
        }

        showCard(0)
        sheet.show()
    }

    private fun showObjectDetail(item: ObjectData) {
        val dialog = BottomSheetDialog(requireContext())
        val detailBinding = DialogObjectDetailBinding.inflate(layoutInflater)
        val wordName = item.wordName ?: item.objectCode.replace("_", " ")
        val categoryName = item.categoryName
            ?: requireArguments().getString("categoryName")
            ?: getString(R.string.category_detail_fallback)

        detailBinding.detailWordName.text = wordName
        val rawDef = item.definition ?: getString(R.string.object_detail_no_definition)
        detailBinding.detailDefinition.text = DefinitionFormatter.formatDefinition(requireContext(), rawDef)

        if (item.phonetic.isNullOrBlank()) {
            detailBinding.detailPhonetic.visibility = View.GONE
        } else {
            detailBinding.detailPhonetic.text = item.phonetic
            detailBinding.detailPhonetic.visibility = View.VISIBLE
        }

        if (item.imageUrl.isNullOrBlank()) {
            detailBinding.detailImage.setImageResource(R.drawable.ic_image_placeholder)
        } else {
            Glide.with(detailBinding.detailImage)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(detailBinding.detailImage)
        }

        detailBinding.btnSpeakDetail.setOnClickListener {
            viewModel.playAudio(wordName)
        }
        detailBinding.btnCloseDetail.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(detailBinding.root)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
