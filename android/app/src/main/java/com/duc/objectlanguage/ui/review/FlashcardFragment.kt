package com.duc.objectlanguage.ui.review

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.duc.objectlanguage.databinding.FragmentFlashcardBinding
import kotlin.math.abs

class FlashcardFragment : Fragment() {

    private var _binding: FragmentFlashcardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FlashcardViewModel by viewModels()
    private lateinit var flashcardAdapter: FlashcardPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFlashcardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        setupGestureDetection()
        observeViewModel()

        viewModel.loadDueCards()
    }

    private fun setupViewPager() {
        flashcardAdapter = FlashcardPagerAdapter { position ->
            // Callback when card is flipped
            // No action needed for now
        }
        binding.viewPagerFlashcards.adapter = flashcardAdapter
        binding.viewPagerFlashcards.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.viewPagerFlashcards.isUserInputEnabled = false // Disable default swipe
    }

    private fun setupGestureDetection() {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                return when {
                    // Swipe right (Know it)
                    abs(deltaX) > abs(deltaY) && 
                    abs(deltaX) > SWIPE_THRESHOLD && 
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD && 
                    deltaX > 0 -> {
                        onSwipeRight()
                        true
                    }
                    // Swipe left (Don't know)
                    abs(deltaX) > abs(deltaY) && 
                    abs(deltaX) > SWIPE_THRESHOLD && 
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD && 
                    deltaX < 0 -> {
                        onSwipeLeft()
                        true
                    }
                    // Swipe up (Difficult)
                    abs(deltaY) > abs(deltaX) && 
                    abs(deltaY) > SWIPE_THRESHOLD && 
                    abs(velocityY) > SWIPE_VELOCITY_THRESHOLD && 
                    deltaY < 0 -> {
                        onSwipeUp()
                        true
                    }
                    else -> false
                }
            }
        })

        binding.viewPagerFlashcards.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun onSwipeRight() {
        // Know it - quality 5
        viewModel.markKnown()
        Toast.makeText(requireContext(), "✅ I know it!", Toast.LENGTH_SHORT).show()
    }

    private fun onSwipeLeft() {
        // Don't know - quality 0
        viewModel.markUnknown()
        Toast.makeText(requireContext(), "❌ Don't know", Toast.LENGTH_SHORT).show()
    }

    private fun onSwipeUp() {
        // Difficult - quality 2
        viewModel.markDifficult()
        Toast.makeText(requireContext(), "⚠️ Marked as difficult", Toast.LENGTH_SHORT).show()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.viewPagerFlashcards.visibility = if (loading) View.GONE else View.VISIBLE
        }

        viewModel.cards.observe(viewLifecycleOwner) { cards ->
            flashcardAdapter.submitList(cards)
            updateProgress()
        }

        viewModel.currentIndex.observe(viewLifecycleOwner) { index ->
            if (index < flashcardAdapter.itemCount) {
                binding.viewPagerFlashcards.setCurrentItem(index, true)
            }
            updateProgress()
        }

        viewModel.finished.observe(viewLifecycleOwner) { finished ->
            if (finished) {
                showCompletionDialog()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateProgress() {
        val current = (viewModel.currentIndex.value ?: 0) + 1
        val total = viewModel.cards.value?.size ?: 0
        binding.tvProgress.text = "Progress: $current/$total"
    }

    private fun showCompletionDialog() {
        val score = viewModel.getScore()
        val total = viewModel.cards.value?.size ?: 0
        val percentage = if (total > 0) (score * 100) / total else 0

        Toast.makeText(
            requireContext(),
            "Flashcard Review Complete!\nScore: $score/$total ($percentage%)",
            Toast.LENGTH_LONG
        ).show()

        // Navigate back or refresh
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
