package com.duc.objectlanguage.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentCollectionListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText

class CollectionListFragment : Fragment() {

    private var _binding: FragmentCollectionListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CollectionViewModel by viewModels()
    private lateinit var adapter: CollectionAdapter
    private lateinit var publicAdapter: PublicCollectionAdapter

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
        setupRecyclerViews()
        setupSearchBar()
        setupTabs()
        setupFab()
        observeViewModel()
    }

    private fun setupRecyclerViews() {
        adapter = CollectionAdapter(
            onCollectionClick = { collection ->
                val bundle = Bundle().apply { putInt("collectionId", collection.id) }
                findNavController().navigate(R.id.action_collectionList_to_collectionDetail, bundle)
            },
            onDeleteClick = { collection ->
                showDeleteConfirmation(collection.id, collection.name)
            }
        )
        binding.recyclerViewCollections.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCollections.adapter = adapter

        publicAdapter = PublicCollectionAdapter(
            onCollectionClick = { collection ->
                val bundle = Bundle().apply {
                    putInt("collectionId", collection.id)
                    putBoolean("isPractice", true)
                }
                findNavController().navigate(R.id.action_collectionList_to_collectionDetail, bundle)
            }
        )
        binding.recyclerViewPublicCollections.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewPublicCollections.adapter = publicAdapter
    }

    private fun setupSearchBar() {
        binding.searchEditText.addTextChangedListener { text ->
            viewModel.setFilterQuery(text?.toString() ?: "")
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> showMyCollections()
                    1 -> showCommunityCollections()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun showMyCollections() {
        binding.myCollectionsContainer.visibility = View.VISIBLE
        binding.communityContainer.visibility = View.GONE
        binding.fabAddCollection.show()
    }

    private fun showCommunityCollections() {
        binding.myCollectionsContainer.visibility = View.GONE
        binding.communityContainer.visibility = View.VISIBLE
        binding.fabAddCollection.hide()
        viewModel.loadPublicCollections()
    }

    private fun setupFab() {
        binding.fabAddCollection.setOnClickListener {
            showCreateCollectionDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.filteredCollections.observe(viewLifecycleOwner) { collections ->
            adapter.submitList(collections)
            binding.recyclerViewCollections.visibility = if (collections.isEmpty()) View.GONE else View.VISIBLE
            binding.emptyStateLayout.visibility = if (collections.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.publicCollections.observe(viewLifecycleOwner) { collections ->
            publicAdapter.submitList(collections)
            binding.recyclerViewPublicCollections.visibility = if (collections.isEmpty()) View.GONE else View.VISIBLE
            binding.communityEmptyState.visibility = if (collections.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

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
        val switchPublic = dialogView.findViewById<SwitchMaterial>(R.id.switchPublic)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.collection_dialog_create_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_create) { _, _ ->
                val name = nameInput.text?.toString() ?: ""
                viewModel.createCollection(name, isPublic = switchPublic.isChecked)
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
