package com.duc.objectlanguage.ui.dictionary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.duc.objectlanguage.data.model.DictionaryHistoryItem
import com.duc.objectlanguage.databinding.DialogDictionaryHistoryListBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DictionaryHistoryListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogDictionaryHistoryListBinding? = null
    private val binding get() = _binding!!

    private var onItemDeleted: ((Int) -> Unit)? = null
    private var adapter: DictionaryHistoryAdapter? = null
    private val currentItems = mutableListOf<DictionaryHistoryItem>()

    fun setOnItemDeletedListener(listener: (Int) -> Unit) {
        onItemDeleted = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogDictionaryHistoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val json = arguments?.getString(ARG_ITEMS) ?: return
        val type = object : TypeToken<List<DictionaryHistoryItem>>() {}.type
        val items: List<DictionaryHistoryItem> = Gson().fromJson(json, type) ?: emptyList()

        currentItems.clear()
        currentItems.addAll(items)

        if (items.isEmpty()) {
            binding.rvHistoryList.visibility = View.GONE
            binding.tvHistoryListEmpty.visibility = View.VISIBLE
            return
        }

        binding.tvHistoryListEmpty.visibility = View.GONE
        binding.rvHistoryList.visibility = View.VISIBLE
        binding.rvHistoryList.layoutManager = LinearLayoutManager(requireContext())
        adapter = DictionaryHistoryAdapter { item ->
            DictionaryHistoryDetailBottomSheet.newInstance(item).apply {
                setOnDeletedListener { id ->
                    onItemDeleted?.invoke(id)
                    removeItem(id)
                }
            }.show(childFragmentManager, "dict_history_detail")
        }.also {
            it.submitList(items.toList())
            binding.rvHistoryList.adapter = it
        }
    }

    private fun removeItem(id: Int) {
        currentItems.removeAll { it.id == id }
        adapter?.submitList(currentItems.toList())
        if (currentItems.isEmpty()) {
            binding.rvHistoryList.visibility = View.GONE
            binding.tvHistoryListEmpty.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
        _binding = null
    }

    companion object {
        private const val ARG_ITEMS = "arg_items"

        fun newInstance(items: List<DictionaryHistoryItem>): DictionaryHistoryListBottomSheet {
            return DictionaryHistoryListBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ITEMS, Gson().toJson(items))
                }
            }
        }
    }
}
