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
import com.duc.objectlanguage.databinding.FragmentResetPasswordBinding
import com.duc.objectlanguage.utils.PasswordValidator
import kotlinx.coroutines.launch

class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val repo = (requireActivity().application as ObjectLanguageApp).repository

        val email = arguments?.getString("email") ?: ""
        val otpCode = arguments?.getString("otpCode") ?: ""

        binding.btnResetPassword.setOnClickListener {
            val newPass = binding.etNewPassword.text.toString()
            val confirmPass = binding.etConfirmPassword.text.toString()

            val passwordError = PasswordValidator.validate(requireContext(), newPass)
            if (passwordError != null) {
                Toast.makeText(requireContext(), passwordError, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (newPass != confirmPass) {
                Toast.makeText(requireContext(), "Mật khẩu xác nhận không khớp", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnResetPassword.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = repo.resetPassword(email, otpCode, newPass)
                binding.progressBar.visibility = View.GONE
                binding.btnResetPassword.isEnabled = true

                result.fold(
                    onSuccess = {
                        Toast.makeText(requireContext(), "Đặt lại mật khẩu thành công!", Toast.LENGTH_LONG).show()
                        findNavController().navigate(R.id.action_resetPassword_to_login)
                    },
                    onFailure = {
                        Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
