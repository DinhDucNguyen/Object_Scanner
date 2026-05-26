package com.duc.objectlanguage.ui.auth

import android.os.Bundle
import android.util.Patterns
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
        animateEntrance()

        val repo = (requireActivity().application as ObjectLanguageApp).repository

        binding.btnRegister.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val user = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()
            val confirmPass = binding.etConfirmPassword.text.toString().trim()

            if (fullName.isEmpty() || user.isEmpty() || email.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.auth_fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Regex("^[a-zA-Z0-9_]{3,50}$").matches(user)) {
                Toast.makeText(requireContext(), getString(R.string.auth_invalid_username), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), getString(R.string.auth_invalid_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val passwordError = PasswordValidator.validate(requireContext(), pass)
            if (passwordError != null) {
                Toast.makeText(requireContext(), passwordError, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (pass != confirmPass) {
                Toast.makeText(requireContext(), getString(R.string.auth_password_mismatch), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnRegister.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = repo.register(user, email, pass, fullName)
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                binding.btnRegister.isEnabled = true

                result.fold(
                    onSuccess = { message ->
                        Toast.makeText(requireContext(), getString(R.string.auth_register_success, message), Toast.LENGTH_SHORT).show()
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

    private fun animateEntrance() {
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

}
