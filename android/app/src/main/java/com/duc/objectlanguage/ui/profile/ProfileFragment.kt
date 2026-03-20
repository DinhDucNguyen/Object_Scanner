package com.duc.objectlanguage.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as ObjectLanguageApp
        
        binding.tvUsername.text = app.tokenManager.username ?: "Bạn"

        // Navigate to Analytics
        binding.cardAnalytics.setOnClickListener {
            findNavController().navigate(R.id.analyticsFragment)
        }

        // Navigate to Streak
        binding.cardStreak.setOnClickListener {
            findNavController().navigate(R.id.streakFragment)
        }

        // Navigate to Settings
        binding.cardSettings.setOnClickListener {
            findNavController().navigate(R.id.notificationSettingsFragment)
        }

        binding.btnLogout.setOnClickListener {
            app.tokenManager.clear()
            findNavController().navigate(R.id.loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
