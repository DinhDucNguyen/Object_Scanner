package com.duc.objectlanguage.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentForgotPasswordBinding
import kotlinx.coroutines.launch

class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = (requireActivity().application as ObjectLanguageApp).repository

        binding.btnSendOtp.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Email không hợp lệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSendOtp.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = repo.forgotPassword(email)
                binding.progressBar.visibility = View.GONE
                binding.btnSendOtp.isEnabled = true

                result.fold(
                    onSuccess = { res ->
                        val bundle = Bundle().apply {
                            putString("email", res.email)
                            putString("masked_email", res.maskedEmail)
                        }
                        findNavController().navigate(R.id.action_forgotPassword_to_verifyOtp, bundle)
                    },
                    onFailure = {
                        Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        binding.tvBackToLogin.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
