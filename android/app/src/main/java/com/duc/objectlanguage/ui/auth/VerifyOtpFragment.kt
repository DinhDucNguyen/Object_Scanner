package com.duc.objectlanguage.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentVerifyOtpBinding
import kotlinx.coroutines.launch

class VerifyOtpFragment : Fragment() {

    private var _binding: FragmentVerifyOtpBinding? = null
    private val binding get() = _binding!!

    private lateinit var email: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVerifyOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = (requireActivity().application as ObjectLanguageApp).repository

        email = arguments?.getString("email") ?: ""
        binding.tvEmailHint.text = "Mã đã gửi đến: $email"

        binding.btnVerify.setOnClickListener {
            val otp = binding.etOtp.text.toString().trim()
            if (otp.length != 6) {
                Toast.makeText(requireContext(), "Vui lòng nhập đủ 6 số", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnVerify.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = repo.verifyOtp(email, otp)
                binding.progressBar.visibility = View.GONE
                binding.btnVerify.isEnabled = true

                result.fold(
                    onSuccess = {
                        val bundle = Bundle().apply {
                            putString("email", email)
                            putString("otpCode", otp)
                        }
                        findNavController().navigate(R.id.action_verifyOtp_to_resetPassword, bundle)
                    },
                    onFailure = {
                        Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        binding.tvResendOtp.setOnClickListener {
            lifecycleScope.launch {
                repo.forgotPassword(email)
                Toast.makeText(requireContext(), "Đã gửi lại mã OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
