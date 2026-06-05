package com.duc.objectlanguage.ui.auth

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentLoginBinding
import com.duc.objectlanguage.ui.MainActivity
import com.duc.objectlanguage.ui.common.addRaisedButtonFeedback
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    sendGoogleTokenToBackend(idToken)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.auth_google_token_error), Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Toast.makeText(requireContext(), getString(R.string.auth_google_signin_failed, e.statusCode), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        animateEntrance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.google_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        binding.btnLogin.addRaisedButtonFeedback()
        observeViewModel()

        binding.btnLogin.setOnClickListener {
            val user = binding.etUsername.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.auth_fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.login(user, pass)
        }

        binding.btnGoogleLogin.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        binding.tvGoRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        binding.tvForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_forgotPassword)
        }
    }

    private fun sendGoogleTokenToBackend(idToken: String) {
        viewModel.googleLogin(idToken)
    }

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.btnLogin.isEnabled = !state.isLoading
            binding.btnGoogleLogin.isEnabled = !state.isLoading
        }

        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is LoginEvent.Success -> resetGraphToDashboard()
                is LoginEvent.Error -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_LONG).show()
                null -> Unit
            }
            if (event != null) viewModel.consumeEvent()
        }
    }

    private fun animateEntrance() {
        val container = binding.root.getChildAt(0) as? android.view.ViewGroup ?: return
        val logoZone = container.getChildAt(0) ?: return
        val formCard = container.getChildAt(1) ?: return

        logoZone.alpha = 0f; logoZone.translationY = -40f
        logoZone.animate().alpha(1f).translationY(0f)
            .setDuration(420).setInterpolator(OvershootInterpolator(1.2f)).setStartDelay(60).start()

        formCard.alpha = 0f; formCard.translationY = 60f
        formCard.animate().alpha(1f).translationY(0f)
            .setDuration(380).setInterpolator(DecelerateInterpolator(1.8f)).setStartDelay(160).start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun resetGraphToDashboard() {
        findNavController().navigate(R.id.action_login_to_dashboard)
        (requireActivity() as? MainActivity)?.updateReviewBadge()
    }
}
