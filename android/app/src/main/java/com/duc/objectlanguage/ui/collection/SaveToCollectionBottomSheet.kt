package com.duc.objectlanguage.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.Collection
import com.duc.objectlanguage.data.repository.CollectionRepository
import com.duc.objectlanguage.databinding.DialogSaveToCollectionBinding
import com.duc.objectlanguage.databinding.ItemCollectionPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class SaveToCollectionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogSaveToCollectionBinding? = null
    private val binding get() = _binding!!

    private val translationId: Int by lazy { requireArguments().getInt(ARG_TRANSLATION_ID) }
    private lateinit var repo: CollectionRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSaveToCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = CollectionRepository((requireActivity().application as ObjectLanguageApp).tokenManager)
        loadCollections()
    }

    private fun loadCollections() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvCollections.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val result = repo.getCollections()
            binding.progressBar.visibility = View.GONE
            result.fold(
                onSuccess = { collections ->
                    if (collections.isEmpty()) {
                        binding.layoutEmpty.visibility = View.VISIBLE
                    } else {
                        binding.rvCollections.visibility = View.VISIBLE
                        binding.rvCollections.layoutManager = LinearLayoutManager(requireContext())
                        binding.rvCollections.adapter = CollectionPickerAdapter(collections) { saveToCollection(it) }
                    }
                },
                onFailure = {
                    Toast.makeText(requireContext(), getString(R.string.collection_load_error), Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            )
        }
    }

    private fun saveToCollection(collection: Collection) {
        lifecycleScope.launch {
            val result = repo.addToCollection(collection.id, translationId)
            if (result.isSuccess) {
                Toast.makeText(requireContext(), getString(R.string.collection_saved_to, collection.name), Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                Toast.makeText(requireContext(), getString(R.string.collection_save_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TRANSLATION_ID = "translation_id"

        fun newInstance(translationId: Int) = SaveToCollectionBottomSheet().apply {
            arguments = Bundle().apply { putInt(ARG_TRANSLATION_ID, translationId) }
        }
    }
}

private class CollectionPickerAdapter(
    private val items: List<Collection>,
    private val onClick: (Collection) -> Unit
) : RecyclerView.Adapter<CollectionPickerAdapter.VH>() {

    inner class VH(val binding: ItemCollectionPickerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCollectionPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvName.text = item.name
        holder.binding.tvCount.text = holder.itemView.context.getString(R.string.collection_word_count, item.itemCount)
        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
