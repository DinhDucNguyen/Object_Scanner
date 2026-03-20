package com.duc.objectlanguage.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStartReview.setOnClickListener {
            val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.duc.objectlanguage.R.id.bottomNavigation)
            if (bottomNav != null) {
                bottomNav.selectedItemId = com.duc.objectlanguage.R.id.reviewFragment
            } else {
                findNavController().navigate(com.duc.objectlanguage.R.id.reviewFragment)
            }
        }

        val app = requireActivity().application as ObjectLanguageApp
        binding.tvWelcome.text = "Xin chào, ${app.tokenManager.username ?: "bạn"}!"

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                binding.tvTotalScans.text = "${stats.totalScans}"
                binding.tvTotalLearned.text = "${stats.totalLearned}"
                binding.tvDueToday.text = "${stats.dueToday}"
                binding.tvMastered.text = "${stats.mastered}"

                val total = stats.totalLearned
                val mastered = stats.mastered
                val pct = if (total > 0) (mastered * 100 / total) else 0
                binding.progressMastery.progress = pct
                binding.tvProgressPct.text = "$pct%"
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loadStats()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
