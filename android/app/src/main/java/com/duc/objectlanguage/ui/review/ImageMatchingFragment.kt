package com.duc.objectlanguage.ui.review

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentImageMatchingBinding

class ImageMatchingFragment : Fragment() {

    private var _binding: FragmentImageMatchingBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ImageMatchingViewModel
    private lateinit var adapter: MatchingCardAdapter
    private var timer: CountDownTimer? = null
    private val timeLimit = 120

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageMatchingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ImageMatchingViewModel::class.java]

        setupRecyclerView()
        setupObservers()

        viewModel.loadGame()
    }

    private fun setupRecyclerView() {
        adapter = MatchingCardAdapter { position -> viewModel.onCardClicked(position) }
        binding.rvCards.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvCards.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.cards.observe(viewLifecycleOwner) { cards ->
            adapter.submitList(cards)
        }

        viewModel.currentRound.observe(viewLifecycleOwner) { round ->
            binding.tvProgress.text = getString(R.string.test_matching_round, round)
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            binding.tvScore.text = getString(R.string.test_matching_score, score)
        }

        viewModel.gameStarted.observe(viewLifecycleOwner) { started ->
            if (started) startTimer()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError(it)
                viewModel.clearError()
            }
        }

        viewModel.finished.observe(viewLifecycleOwner) { finished ->
            if (finished) {
                stopTimer()
                showResultDialog()
            }
        }

        viewModel.roundComplete.observe(viewLifecycleOwner) { complete ->
            if (complete) {
                binding.root.postDelayed({
                    if (_binding != null) viewModel.startNextRound()
                }, 1000)
            }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(timeLimit * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (_binding == null) return
                val seconds = millisUntilFinished / 1000
                val minutes = seconds / 60
                val secs = seconds % 60
                binding.tvTimer.text = getString(R.string.test_matching_timer, String.format("%02d:%02d", minutes, secs))

                val color = when {
                    seconds <= 10 -> ContextCompat.getColor(requireContext(), R.color.error)
                    seconds <= 30 -> ContextCompat.getColor(requireContext(), R.color.warning)
                    else -> ContextCompat.getColor(requireContext(), R.color.text_primary)
                }
                binding.tvTimer.setTextColor(color)
            }

            override fun onFinish() {
                if (_binding == null) return
                binding.tvTimer.text = getString(R.string.test_matching_timer_zero)
                binding.tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
                viewModel.onTimeUp()
            }
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.test_error_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.test_ok), null)
            .show()
    }

    private fun showResultDialog() {
        val score = viewModel.score.value ?: 0
        val totalRounds = viewModel.getTotalRounds()

        val message = when {
            score >= totalRounds * 10 -> getString(R.string.test_result_excellent)
            score >= totalRounds * 8 -> getString(R.string.test_result_great)
            score >= totalRounds * 6 -> getString(R.string.test_result_good)
            else -> getString(R.string.test_result_keep_going)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.test_complete_title))
            .setMessage(getString(R.string.test_matching_final_score, message, score))
            .setPositiveButton(getString(R.string.test_done)) { _, _ ->
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTimer()
        _binding = null
    }
}
