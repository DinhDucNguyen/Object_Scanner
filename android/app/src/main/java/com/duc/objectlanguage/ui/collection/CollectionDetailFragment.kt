package com.duc.objectlanguage.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentCollectionDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

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
            if (!loading) binding.swipeRefresh.isRefreshing = false
        }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadCollectionDetail(collectionId)
            binding.swipeRefresh.post {
                Snackbar.make(binding.root, R.string.refresh_success, Snackbar.LENGTH_SHORT).show()
            }
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
                    putBoolean("isPractice", true)
                }
                findNavController().navigate(R.id.reviewFragment, args)
            }
        }

        viewModel.loadCollectionDetail(collectionId)
        animateEntrance()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCollectionDetail(collectionId)
    }

    private fun animateEntrance() {
        val interp = DecelerateInterpolator(1.6f)
        binding.tvCollectionName.apply {
            alpha = 0f; translationY = -20f
            animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(OvershootInterpolator(1.2f)).setStartDelay(40).start()
        }
        binding.btnReviewCollection.apply {
            alpha = 0f; translationX = 30f
            animate().alpha(1f).translationX(0f).setDuration(300).setInterpolator(interp).setStartDelay(100).start()
        }
        binding.recyclerWords.apply {
            alpha = 0f; translationY = 32f
            animate().alpha(1f).translationY(0f).setDuration(360).setInterpolator(interp).setStartDelay(160).start()
        }
    }

    private fun openAddWordSource() {
        val collectionName = viewModel.collectionDetail.value?.name ?: ""
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_add_word_source, null)
        sheet.setContentView(view)

        view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardScan)
            .setOnClickListener {
                sheet.dismiss()
                findNavController().navigate(R.id.scanFragment)
            }
        view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardHistory)
            .setOnClickListener {
                sheet.dismiss()
                val args = Bundle().apply {
                    putInt("targetCollectionId", collectionId)
                    putString("targetCollectionName", collectionName)
                }
                findNavController().navigate(R.id.action_collectionDetail_to_history, args)
            }

        sheet.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
