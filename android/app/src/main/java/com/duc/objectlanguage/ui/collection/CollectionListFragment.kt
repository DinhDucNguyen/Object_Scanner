package com.duc.objectlanguage.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.Collection
import com.duc.objectlanguage.databinding.FragmentCollectionListBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText

class CollectionListFragment : Fragment() {

    private var _binding: FragmentCollectionListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CollectionViewModel by viewModels()
    private lateinit var adapter: CollectionAdapter
    private lateinit var publicAdapter: PublicCollectionAdapter
    private var showingCommunity = false

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
        animateEntrance()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCollections()
    }

    private fun animateEntrance() {
        val interp = DecelerateInterpolator(1.6f)
        val container = binding.root.getChildAt(0) as? android.view.ViewGroup ?: return
        container.alpha = 0f
        container.translationY = 32f
        container.animate().alpha(1f).translationY(0f)
            .setDuration(360).setInterpolator(interp).start()
    }

    private fun setupRecyclerViews() {
        adapter = CollectionAdapter(
            onCollectionClick = { collection ->
                val bundle = Bundle().apply { putInt("collectionId", collection.id) }
                findNavController().navigate(R.id.action_collectionList_to_collectionDetail, bundle)
            },
            onRenameClick = { collection ->
                showRenameDialog(collection)
            },
            onPrivacyClick = { collection ->
                showPrivacyDialog(collection)
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
        showingCommunity = false
        binding.myCollectionsContainer.visibility = View.VISIBLE
        binding.communityContainer.visibility = View.GONE
        updateCreateFabVisibility(viewModel.filteredCollections.value.orEmpty())
    }

    private fun showCommunityCollections() {
        showingCommunity = true
        binding.myCollectionsContainer.visibility = View.GONE
        binding.communityContainer.visibility = View.VISIBLE
        binding.fabAddCollection.hide()
        viewModel.loadPublicCollections()
    }

    private fun setupFab() {
        binding.fabAddCollection.setOnClickListener {
            showCreateCollectionDialog()
        }
        binding.btnEmptyCreateCollection.setOnClickListener {
            showCreateCollectionDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.filteredCollections.observe(viewLifecycleOwner) { collections ->
            val wasEmpty = adapter.itemCount == 0
            adapter.submitList(collections) {
                if (wasEmpty && collections.isNotEmpty()) binding.recyclerViewCollections.scheduleLayoutAnimation()
            }
            binding.recyclerViewCollections.visibility = if (collections.isEmpty()) View.GONE else View.VISIBLE
            binding.emptyStateLayout.visibility = if (collections.isEmpty()) View.VISIBLE else View.GONE
            updateCreateFabVisibility(collections)
        }

        viewModel.publicCollections.observe(viewLifecycleOwner) { collections ->
            val wasEmpty = publicAdapter.itemCount == 0
            publicAdapter.submitList(collections) {
                if (wasEmpty && collections.isNotEmpty()) binding.recyclerViewPublicCollections.scheduleLayoutAnimation()
            }
            binding.recyclerViewPublicCollections.visibility = if (collections.isEmpty()) View.GONE else View.VISIBLE
            binding.communityEmptyState.visibility = if (collections.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.shimmerCollections.visibility = View.VISIBLE
                binding.shimmerCollections.startShimmer()
            } else {
                binding.shimmerCollections.stopShimmer()
                binding.shimmerCollections.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadCollections()
            binding.swipeRefresh.post {
                Snackbar.make(binding.root, R.string.refresh_success, Snackbar.LENGTH_SHORT).show()
            }
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

    private fun updateCreateFabVisibility(collections: List<Collection>) {
        if (showingCommunity || collections.isEmpty()) {
            binding.fabAddCollection.hide()
        } else {
            binding.fabAddCollection.show()
        }
    }

    private fun showCreateCollectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_collection, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.collectionNameInput)
        val switchPublic = dialogView.findViewById<SwitchMaterial>(R.id.switchPublic)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelDialog)
        val btnCreate = dialogView.findViewById<MaterialButton>(R.id.btnCreateDialog)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            val name = nameInput.text?.toString() ?: ""
            viewModel.createCollection(name, isPublic = switchPublic.isChecked)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteConfirmation(collectionId: Int, collectionName: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
        dialogView.findViewById<android.widget.TextView>(R.id.tvDeleteTitle)
            .text = getString(R.string.collection_dialog_delete_title)
        dialogView.findViewById<android.widget.TextView>(R.id.tvDeleteMessage)
            .text = getString(R.string.collection_dialog_delete_msg, collectionName)
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmDelete)
            .setOnClickListener {
                dialog.dismiss()
                viewModel.deleteCollection(collectionId, collectionName)
            }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelDelete)
            .setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showRenameDialog(collection: com.duc.objectlanguage.data.model.Collection) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_collection_rename, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.collectionNameInput)
        val title = dialogView.findViewById<android.widget.TextView>(R.id.tvRenameTitle)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelDialog)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveDialog)

        title.text = getString(R.string.collection_rename_title_for, collection.name)
        nameInput.setText(collection.name)
        nameInput.setSelection(collection.name.length)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            viewModel.updateCollectionName(collection.id, nameInput.text?.toString() ?: "")
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            nameInput.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }

    private fun showPrivacyDialog(collection: com.duc.objectlanguage.data.model.Collection) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_collection_privacy, null)
        val switchPublic = dialogView.findViewById<SwitchMaterial>(R.id.switchPublic)
        val title = dialogView.findViewById<android.widget.TextView>(R.id.tvPrivacyTitle)
        val status = dialogView.findViewById<android.widget.TextView>(R.id.tvPrivacyStatus)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelDialog)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveDialog)

        title.text = getString(R.string.collection_privacy_title_for, collection.name)
        switchPublic.isChecked = collection.isPublic

        fun bindStatus(isPublic: Boolean) {
            status.text = if (isPublic) {
                getString(R.string.collection_privacy_public_desc)
            } else {
                getString(R.string.collection_privacy_private_desc)
            }
        }
        bindStatus(collection.isPublic)
        switchPublic.setOnCheckedChangeListener { _, isChecked -> bindStatus(isChecked) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            viewModel.updateCollectionPrivacy(collection.id, switchPublic.isChecked)
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
