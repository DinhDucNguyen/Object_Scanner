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
        val categoryName = requireArguments().getString("categoryName") ?: "Chu de"
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
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        viewModel.loadObjects(categoryId)
    }

    private fun showObjectDetail(item: ObjectData) {
        val dialog = BottomSheetDialog(requireContext())
        val detailBinding = DialogObjectDetailBinding.inflate(layoutInflater)
        val wordName = item.wordName ?: item.objectCode.replace("_", " ")
        val categoryName = item.categoryName
            ?: requireArguments().getString("categoryName")
            ?: "Chu de"

        detailBinding.detailWordName.text = wordName
        val rawDef = item.definition ?: "Chua co nghia hien thi"
        detailBinding.detailDefinition.text = DefinitionFormatter.formatDefinition(requireContext(), rawDef)
        detailBinding.detailCategory.text = "Chu de: $categoryName"
        detailBinding.detailObjectCode.text = "Ma tu: ${item.objectCode}"
        detailBinding.detailTranslationCount.text = "${item.translationCount} ban dich"

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

        detailBinding.btnCloseDetail.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(detailBinding.root)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
