package com.duc.objectlanguage.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentCollectionListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

/**
 * Fragment showing list of user collections with search/filter
 * Features: Create, delete, search collections
 */
class CollectionListFragment : Fragment() {
    
    private var _binding: FragmentCollectionListBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CollectionViewModel by viewModels()
    private lateinit var adapter: CollectionAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectionListBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearchBar()
        setupFab()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        adapter = CollectionAdapter(
            onCollectionClick = { collection ->
                // Navigate to detail (word list)
                val bundle = Bundle().apply { putInt("collectionId", collection.id) }
                findNavController().navigate(R.id.action_collectionList_to_collectionDetail, bundle)
            },
            onDeleteClick = { collection ->
                showDeleteConfirmation(collection.id, collection.name)
            },
            onInsightsClick = { collection ->
                // Navigate to insights/analytics
                val bundle = Bundle().apply { putInt("collectionId", collection.id) }
                findNavController().navigate(R.id.action_collectionList_to_collectionInsights, bundle)
            }
        )
        
        binding.recyclerViewCollections.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCollections.adapter = adapter
    }
    
    private fun setupSearchBar() {
        binding.searchEditText.addTextChangedListener { text ->
            viewModel.setFilterQuery(text?.toString() ?: "")
        }
    }
    
    private fun setupFab() {
        binding.fabAddCollection.setOnClickListener {
            showCreateCollectionDialog()
        }
    }
    
    private fun observeViewModel() {
        // Filtered collections for display
        viewModel.filteredCollections.observe(viewLifecycleOwner) { collections ->
            adapter.submitList(collections)
            
            // Show/hide empty state
            if (collections.isEmpty()) {
                binding.recyclerViewCollections.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.VISIBLE
            } else {
                binding.recyclerViewCollections.visibility = View.VISIBLE
                binding.emptyStateLayout.visibility = View.GONE
            }
        }
        
        // Loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Error messages
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        
        // Success messages
        viewModel.successMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearSuccessMessage()
            }
        }
    }
    
    private fun showCreateCollectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_collection, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.collectionNameInput)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.collection_dialog_create_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_create) { _, _ ->
                val name = nameInput.text?.toString() ?: ""
                viewModel.createCollection(name, isPublic = false)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showDeleteConfirmation(collectionId: Int, collectionName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.collection_dialog_delete_title)
            .setMessage(getString(R.string.collection_dialog_delete_msg, collectionName))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                viewModel.deleteCollection(collectionId, collectionName)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
