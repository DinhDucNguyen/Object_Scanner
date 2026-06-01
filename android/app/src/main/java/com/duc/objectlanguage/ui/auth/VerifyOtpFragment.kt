package com.duc.objectlanguage.ui.auth

import android.os.Bundle
import android.os.CountDownTimer
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
    private var mode: String = "reset_password"
    private var countDownTimer: CountDownTimer? = null
    private var otpExpired = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVerifyOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = (requireActivity().application as ObjectLanguageApp).repository

        email = arguments?.getString("email") ?: ""
        mode = arguments?.getString("mode") ?: "reset_password"
        val displayEmail = arguments?.getString("masked_email")?.takeIf { it.isNotEmpty() }
            ?: maskEmail(email)
        binding.tvEmailHint.text = getString(R.string.auth_otp_sent_to, displayEmail)

        startCountdown()

        binding.btnVerify.setOnClickListener {
            if (otpExpired) {
                Toast.makeText(requireContext(), getString(R.string.otp_expired), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val otp = binding.etOtp.text.toString().trim()
            if (otp.length != 6) {
                Toast.makeText(requireContext(), getString(R.string.auth_otp_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnVerify.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = if (mode == "register") {
                    repo.verifyRegistrationOtp(email, otp)
                } else {
                    repo.verifyOtp(email, otp)
                }
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                binding.btnVerify.isEnabled = !otpExpired

                result.fold(
                    onSuccess = {
                        countDownTimer?.cancel()
                        if (mode == "register") {
                            Toast.makeText(requireContext(), getString(R.string.auth_registration_verified), Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_verifyOtp_to_login)
                        } else {
                            val bundle = Bundle().apply {
                                putString("email", email)
                                putString("otpCode", otp)
                            }
                            findNavController().navigate(R.id.action_verifyOtp_to_resetPassword, bundle)
                        }
                    },
                    onFailure = {
                        Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        binding.tvResendOtp.setOnClickListener {
            binding.tvResendOtp.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                val result = if (mode == "register") {
                    repo.resendRegistrationOtp(email)
                } else {
                    repo.forgotPassword(email).map { it.message }
                }
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                result.fold(
                    onSuccess = {
                        Toast.makeText(requireContext(), getString(R.string.auth_otp_resent), Toast.LENGTH_SHORT).show()
                        otpExpired = false
                        binding.etOtp.text?.clear()
                        startCountdown()
                    },
                    onFailure = {
                        binding.tvResendOtp.isEnabled = true
                        Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun startCountdown() {
        countDownTimer?.cancel()
        otpExpired = false
        binding.tvResendOtp.visibility = View.GONE
        binding.btnVerify.isEnabled = true
        binding.tvCountdown.setTextColor(requireContext().getColor(R.color.text_muted))

        countDownTimer = object : CountDownTimer(60_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                if (_binding == null) return
                val secs = (millisUntilFinished / 1000).toInt()
                binding.tvCountdown.text = getString(R.string.otp_countdown, secs)
            }

            override fun onFinish() {
                if (_binding == null) return
                otpExpired = true
                binding.tvCountdown.text = getString(R.string.otp_expired)
                binding.tvCountdown.setTextColor(
                    requireContext().getColor(R.color.error)
                )
                binding.btnVerify.isEnabled = false
                binding.tvResendOtp.visibility = View.VISIBLE
            }
        }.start()
    }

    override fun onDestroyView() {
        countDownTimer?.cancel()
        super.onDestroyView()
        _binding = null
    }

    private fun maskEmail(email: String): String {
        val atIndex = email.indexOf('@')
        if (atIndex < 2) return email
        val local = email.substring(0, atIndex)
        val domain = email.substring(atIndex)
        val masked = local.first() + "*".repeat(local.length - 2) + local.last()
        return "$masked$domain"
    }
}
