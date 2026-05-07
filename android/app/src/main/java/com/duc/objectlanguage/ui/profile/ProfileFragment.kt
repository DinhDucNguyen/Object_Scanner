package com.duc.objectlanguage.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentProfileBinding
import com.duc.objectlanguage.utils.LocaleHelper

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as ObjectLanguageApp
        binding.tvUsername.text = app.tokenManager.username ?: getString(R.string.dashboard_greeting)

        updateLangButtons()
        setupClicks()
    }

    // ── Cập nhật trạng thái 2 nút VI / EN ────────────────────────────────────
    private fun updateLangButtons() {
        val isVI = LocaleHelper.getSavedLocale(requireContext()) == "vi"
        val active   = ContextCompat.getColorStateList(requireContext(), R.color.primary)
        val inactive = ContextCompat.getColorStateList(requireContext(), R.color.surface_variant)

        binding.btnLangVI.backgroundTintList = if (isVI) active else inactive
        binding.btnLangEN.backgroundTintList = if (!isVI) active else inactive

        // Cập nhật text label hiện tại
        binding.tvDisplayLang.text = getString(
            if (isVI) R.string.profile_display_lang_vi else R.string.profile_display_lang_en
        )
    }

    // ── Setup click listeners ─────────────────────────────────────────────────
    private fun setupClicks() {
        binding.cardHistory.setOnClickListener    { findNavController().navigate(R.id.historyFragment) }
        binding.cardAnalytics.setOnClickListener  { findNavController().navigate(R.id.analyticsFragment) }
        binding.cardStreak.setOnClickListener     { findNavController().navigate(R.id.streakFragment) }
        binding.cardCollection.setOnClickListener { findNavController().navigate(R.id.collectionListFragment) }


        binding.btnLangVI.setOnClickListener { setDisplayLanguage("vi") }
        binding.btnLangEN.setOnClickListener { setDisplayLanguage("en") }

        binding.btnLogout.setOnClickListener {
            val app = requireActivity().application as ObjectLanguageApp
            app.tokenManager.clear()
            findNavController().navigate(
                R.id.loginFragment, null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true).build()
            )
        }
    }

    // ── Đổi ngôn ngữ hiển thị ────────────────────────────────────────────────
    private fun setDisplayLanguage(lang: String) {
        if (LocaleHelper.getSavedLocale(requireContext()) == lang) return
        LocaleHelper.setLocale(requireContext(), lang)
        val msg = if (lang == "en") "Language: English" else "Ngôn ngữ: Tiếng Việt"
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        requireActivity().recreate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
