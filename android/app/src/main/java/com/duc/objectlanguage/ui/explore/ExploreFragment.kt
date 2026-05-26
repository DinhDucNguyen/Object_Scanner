package com.duc.objectlanguage.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentExploreBinding
import com.google.android.material.snackbar.Snackbar

class ExploreFragment : Fragment() {

    private var _binding: FragmentExploreBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExploreViewModel by viewModels()
    private lateinit var adapter: CategoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = CategoryAdapter { category ->
            val bundle = Bundle().apply {
                putInt("categoryId", category.id)
                putString("categoryName", category.name)
            }
            findNavController().navigate(R.id.action_explore_to_categoryDetail, bundle)
        }
        binding.recyclerViewCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCategories.adapter = adapter

        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            val wasEmpty = adapter.itemCount == 0
            adapter.submitList(categories) {
                if (wasEmpty && categories.isNotEmpty()) binding.recyclerViewCategories.scheduleLayoutAnimation()
            }
            binding.emptyStateLayout.visibility = if (categories.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerViewCategories.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            if (!loading) binding.swipeRefresh.isRefreshing = false
        }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadCategories()
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
        viewModel.loadCategories()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
