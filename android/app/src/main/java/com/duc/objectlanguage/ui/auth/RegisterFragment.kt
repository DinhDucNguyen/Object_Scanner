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
import com.duc.objectlanguage.databinding.FragmentRegisterBinding
import com.duc.objectlanguage.utils.LocaleHelper
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
            val user = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (user.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnRegister.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = repo.register(user, email, pass)
                binding.progressBar.visibility = View.GONE
                binding.btnRegister.isEnabled = true

                result.fold(
                    onSuccess = {
                        repo.updateUserSettings(LocaleHelper.getSavedLocale(requireContext()))
                        val navController = findNavController()
                        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
                        graph.setStartDestination(R.id.dashboardFragment)
                        navController.graph = graph
                    },
                    onFailure = {
                        Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        binding.tvGoLogin.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
