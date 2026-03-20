package com.duc.objectlanguage.ui.review

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.R

class ImageMatchingFragment : Fragment() {

    private lateinit var viewModel: ImageMatchingViewModel
    private lateinit var tvProgress: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvScore: TextView
    private lateinit var rvCards: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: MatchingCardAdapter
    private var timer: CountDownTimer? = null
    private var timeLimit = 120 // 2 minutes

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_image_matching, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ImageMatchingViewModel::class.java]

        initViews(view)
        setupRecyclerView()
        setupObservers()

        viewModel.loadGame()
    }

    private fun initViews(view: View) {
        tvProgress = view.findViewById(R.id.tvProgress)
        tvTimer = view.findViewById(R.id.tvTimer)
        tvScore = view.findViewById(R.id.tvScore)
        rvCards = view.findViewById(R.id.rvCards)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun setupRecyclerView() {
        adapter = MatchingCardAdapter { position ->
            viewModel.onCardClicked(position)
        }
        rvCards.layoutManager = GridLayoutManager(requireContext(), 3)
        rvCards.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.cards.observe(viewLifecycleOwner) { cards ->
            adapter.submitList(cards)
        }

        viewModel.currentRound.observe(viewLifecycleOwner) { round ->
            tvProgress.text = "Round $round"
        }

        viewModel.score.observe(viewLifecycleOwner) { score ->
            tvScore.text = "Score: $score"
        }

        viewModel.gameStarted.observe(viewLifecycleOwner) { started ->
            if (started) {
                startTimer()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
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
                // Short delay before next round
                view?.postDelayed({
                    viewModel.startNextRound()
                }, 1000)
            }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(timeLimit * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val minutes = seconds / 60
                val secs = seconds % 60
                tvTimer.text = "⏱️ ${String.format("%02d:%02d", minutes, secs)}"

                // Color warning
                when {
                    seconds <= 10 -> tvTimer.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
                    seconds <= 30 -> tvTimer.setTextColor(resources.getColor(android.R.color.holo_orange_light, null))
                    else -> tvTimer.setTextColor(resources.getColor(android.R.color.black, null))
                }
            }

            override fun onFinish() {
                tvTimer.text = "⏱️ 00:00"
                tvTimer.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                viewModel.onTimeUp()
            }
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showResultDialog() {
        val score = viewModel.score.value ?: 0
        val totalRounds = viewModel.getTotalRounds()

        val message = when {
            score >= totalRounds * 10 -> "Perfect memory! 🧠✨"
            score >= totalRounds * 8 -> "Excellent matching! 🎯"
            score >= totalRounds * 6 -> "Great work! 💪"
            else -> "Keep practicing! 🎮"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Game Complete!")
            .setMessage("$message\n\nFinal Score: $score")
            .setPositiveButton("Done") { _, _ ->
                requireActivity().onBackPressed()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }
}
