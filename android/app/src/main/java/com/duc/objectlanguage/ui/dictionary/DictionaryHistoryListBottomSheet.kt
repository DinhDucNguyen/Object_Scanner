package com.duc.objectlanguage.ui.dictionary

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.DictionaryHistoryItem
import com.duc.objectlanguage.databinding.DialogDictionaryHistoryListBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DictionaryHistoryListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogDictionaryHistoryListBinding? = null
    private val binding get() = _binding!!

    private var onItemDeleted: ((Int) -> Unit)? = null
    private var adapter: DictionaryHistoryAdapter? = null
    private val allItems = mutableListOf<DictionaryHistoryItem>()

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

        allItems.clear()
        allItems.addAll(items)

        if (items.isEmpty()) {
            binding.rvHistoryList.visibility = View.GONE
            binding.tvHistoryListEmpty.visibility = View.VISIBLE
            binding.tilHistorySearch.visibility = View.GONE
            return
        }

        binding.tvHistoryListEmpty.visibility = View.GONE
        binding.rvHistoryList.visibility = View.VISIBLE
        binding.rvHistoryList.layoutManager = LinearLayoutManager(requireContext())
        adapter = DictionaryHistoryAdapter(
            onItemClick = { item ->
                DictionaryHistoryDetailBottomSheet.newInstance(item).apply {
                    setOnDeletedListener { id ->
                        onItemDeleted?.invoke(id)
                        removeItem(id)
                    }
                }.show(childFragmentManager, "dict_history_detail")
            },
            onSwipeDelete = { item ->
                confirmDelete(item)
            }
        ).also {
            it.submitList(items.toList())
            binding.rvHistoryList.adapter = it
        }

        setupSearch()
    }

    private fun confirmDelete(item: DictionaryHistoryItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dictionary_history_delete_confirm_title)
            .setMessage(getString(R.string.dictionary_history_delete_confirm_message, item.tuTra))
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                adapter?.closeOpenedItem()
            }
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                adapter?.slideOutOpenedItem {
                    onItemDeleted?.invoke(item.id)
                    removeItem(item.id)
                }
            }
            .show()
    }

    private fun setupSearch() {
        binding.etHistorySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterList(s?.toString().orEmpty())
            }
        })
    }

    private fun filterList(query: String) {
        val filtered = if (query.isBlank()) {
            allItems.toList()
        } else {
            val lowerQuery = query.trim().lowercase()
            allItems.filter { item ->
                item.tuTra.lowercase().contains(lowerQuery) ||
                    item.ketQuaDich?.lowercase()?.contains(lowerQuery) == true
            }
        }

        adapter?.submitList(filtered)

        when {
            filtered.isEmpty() && query.isNotBlank() -> {
                binding.rvHistoryList.visibility = View.GONE
                binding.tvHistoryNoResults.visibility = View.VISIBLE
                binding.tvHistoryListEmpty.visibility = View.GONE
            }
            else -> {
                binding.rvHistoryList.visibility = View.VISIBLE
                binding.tvHistoryNoResults.visibility = View.GONE
                binding.tvHistoryListEmpty.visibility = View.GONE
            }
        }
    }

    private fun removeItem(id: Int) {
        allItems.removeAll { it.id == id }
        val currentQuery = binding.etHistorySearch.text?.toString().orEmpty()
        filterList(currentQuery)
        if (allItems.isEmpty()) {
            binding.rvHistoryList.visibility = View.GONE
            binding.tilHistorySearch.visibility = View.GONE
            binding.tvHistoryNoResults.visibility = View.GONE
            binding.tvHistoryListEmpty.visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        // Tắt window dim — dim mặc định của dialog gây tối toàn bộ content phía trên khi touch
        dialog?.window?.setDimAmount(0f)

        val sheet = (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isDraggable = false
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
