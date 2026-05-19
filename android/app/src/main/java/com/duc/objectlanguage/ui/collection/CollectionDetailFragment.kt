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

class CollectionDetailFragment : Fragment() {

    private var _binding: FragmentCollectionDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CollectionViewModel by viewModels()

    private val collectionId: Int by lazy {
        arguments?.getInt("collectionId") ?: 0
    }

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

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.collectionDetail.observe(viewLifecycleOwner) { detail ->
            if (detail == null) return@observe
            binding.tvCollectionName.text = detail.name
            binding.tvWordCount.text = "${detail.items.size} từ"
            if (detail.items.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.recyclerWords.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.recyclerWords.visibility = View.VISIBLE
                adapter.submitList(detail.items)
            }
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
                Toast.makeText(requireContext(), "Bộ sưu tập trống, thêm từ trước nhé!", Toast.LENGTH_SHORT).show()
            } else {
                val args = Bundle().apply {
                    putInt("collectionId", collectionId)
                    putString("collectionName", detail.name)
                }
                findNavController().navigate(R.id.reviewFragment, args)
            }
        }

        viewModel.loadCollectionDetail(collectionId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
