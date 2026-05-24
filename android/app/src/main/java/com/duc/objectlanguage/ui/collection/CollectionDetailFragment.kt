package com.duc.objectlanguage.ui.collection

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
import com.duc.objectlanguage.databinding.FragmentCollectionDetailBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

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

        val adapter = CollectionWordAdapter { translationId ->
            viewModel.removeFromCollection(collectionId, translationId)
        }
        binding.recyclerWords.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerWords.adapter = adapter
        binding.cardAddWord.setOnClickListener { openAddWordSource() }
        binding.fabAddWord.setOnClickListener { openAddWordSource() }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.collectionDetail.observe(viewLifecycleOwner) { detail ->
            if (detail == null) return@observe
            binding.tvCollectionName.text = detail.name
            binding.tvWordCount.text = getString(R.string.collection_item_count_in_detail, detail.items.size)
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
                    putBoolean("isPractice", isPractice)
                }
                findNavController().navigate(R.id.reviewFragment, args)
            }
        }

        viewModel.loadCollectionDetail(collectionId)
    }

    private fun openAddWordSource() {
        Toast.makeText(requireContext(), R.string.collection_add_word_toast, Toast.LENGTH_SHORT).show()
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation)
        if (bottomNav != null) {
            bottomNav.selectedItemId = R.id.scanFragment
        } else {
            findNavController().navigate(R.id.scanFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
