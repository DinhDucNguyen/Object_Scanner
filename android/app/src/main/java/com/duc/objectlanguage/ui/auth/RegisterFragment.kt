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
import com.duc.objectlanguage.databinding.FragmentRegisterBinding
import com.duc.objectlanguage.utils.PasswordValidator
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repo = (requireActivity().application as ObjectLanguageApp).repository

        binding.btnRegister.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val user = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()
            val confirmPass = binding.etConfirmPassword.text.toString().trim()

            if (fullName.isEmpty() || user.isEmpty() || email.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Regex("^[a-zA-Z0-9_]{3,50}$").matches(user)) {
                Toast.makeText(requireContext(), "Tên đăng nhập phải từ 3-50 ký tự, chỉ gồm chữ, số và underscore", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Email không hợp lệ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val passwordError = PasswordValidator.validate(requireContext(), pass)
            if (passwordError != null) {
                Toast.makeText(requireContext(), passwordError, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (pass != confirmPass) {
                Toast.makeText(requireContext(), "Mật khẩu xác nhận không khớp", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnRegister.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = repo.register(user, email, pass, fullName)
                binding.progressBar.visibility = View.GONE
                binding.btnRegister.isEnabled = true

                result.fold(
                    onSuccess = { message ->
                        Toast.makeText(requireContext(), "$message! Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.action_register_to_login)
                    },
                    onFailure = {
                        Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        binding.tvGoLogin.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
