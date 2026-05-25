package com.duc.objectlanguage.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        animateEntrance()

        val app = requireActivity().application as ObjectLanguageApp
        val repo = app.repository

        binding.btnLogin.setOnClickListener {
            val user = binding.etUsername.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = repo.login(user, pass)
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true

                result.fold(
                    onSuccess = {
                        resetGraphToDashboard()
                    },
                    onFailure = {
                        Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        binding.tvGoRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        binding.tvForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_forgotPassword)
        }
    }

    private fun animateEntrance() {
        // ScrollView > LinearLayout > [logoZone(0), formCard(1)]
        val container = binding.root.getChildAt(0) as? android.view.ViewGroup ?: return
        val logoZone = container.getChildAt(0) ?: return
        val formCard = container.getChildAt(1) ?: return

        logoZone.alpha = 0f
        logoZone.translationY = -40f
        logoZone.animate()
            .alpha(1f).translationY(0f)
            .setDuration(420).setInterpolator(OvershootInterpolator(1.2f))
            .setStartDelay(60).start()

        formCard.alpha = 0f
        formCard.translationY = 60f
        formCard.animate()
            .alpha(1f).translationY(0f)
            .setDuration(380).setInterpolator(DecelerateInterpolator(1.8f))
            .setStartDelay(160).start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun resetGraphToDashboard() {
        findNavController().navigate(R.id.action_login_to_dashboard)
    }
}
