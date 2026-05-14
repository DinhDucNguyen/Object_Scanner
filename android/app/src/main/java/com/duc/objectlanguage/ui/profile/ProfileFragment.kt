package com.duc.objectlanguage.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.local.ApiConfig
import com.duc.objectlanguage.databinding.FragmentProfileBinding
import com.duc.objectlanguage.utils.LocaleHelper
import com.duc.objectlanguage.utils.PasswordValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        uploadSelectedAvatar(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as ObjectLanguageApp
        if (!app.tokenManager.isLoggedIn) {
            resetGraphToGuestScan()
            return
        }

        binding.tvUsername.text = app.tokenManager.username ?: getString(R.string.dashboard_default_user)
        updateLangButtons()
        setupClicks()
        observeViewModel()
        viewModel.loadProfile()
    }

    private fun observeViewModel() {
        viewModel.profile.observe(viewLifecycleOwner) { profile ->
            profile ?: return@observe
            binding.tvBio.text = profile.bio?.takeIf { it.isNotBlank() }
                ?: getString(R.string.profile_bio_placeholder)

            val avatarUrl = profile.avatarUrl
            if (!avatarUrl.isNullOrBlank() && avatarUrl != "default_avatar.png") {
                Glide.with(this)
                    .load(resolveMediaUrl(avatarUrl))
                    .transform(CenterCrop())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(binding.ivAvatar)
                binding.ivAvatar.setPadding(0, 0, 0, 0)
                binding.ivAvatar.imageTintList = null
            } else {
                showDefaultAvatar()
            }
        }
        viewModel.message.observe(viewLifecycleOwner) { msg ->
            msg ?: return@observe
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    private fun setupClicks() {
        binding.cardHistory.setOnClickListener { findNavController().navigate(R.id.historyFragment) }
        binding.cardAnalytics.setOnClickListener { findNavController().navigate(R.id.analyticsFragment) }
        binding.cardStreak.setOnClickListener { findNavController().navigate(R.id.streakFragment) }
        binding.cardCollection.setOnClickListener { findNavController().navigate(R.id.collectionListFragment) }
        binding.btnLangVI.setOnClickListener { setDisplayLanguage("vi") }
        binding.btnLangEN.setOnClickListener { setDisplayLanguage("en") }
        binding.frameAvatar.setOnClickListener { pickImage.launch("image/*") }
        binding.tvBio.setOnClickListener { showBioDialog() }
        binding.btnMenu.setOnClickListener { showOverflowMenu() }
        binding.btnLogout.setOnClickListener {
            val app = requireActivity().application as ObjectLanguageApp
            app.repository.logout()
            resetGraphToGuestScan()
        }
    }

    private fun resetGraphToGuestScan() {
        val navController = findNavController()
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(R.id.scanFragment)
        navController.graph = graph
    }

    private fun uploadSelectedAvatar(uri: Uri) {
        val context = context ?: return
        try {
            val bytes = createAvatarBytes(uri)
            if (bytes == null) {
                Toast.makeText(context, getString(R.string.profile_avatar_pick_error), Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.uploadAvatar(bytes, "avatar.jpg")
        } catch (_: Exception) {
            Toast.makeText(context, getString(R.string.profile_avatar_pick_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createAvatarBytes(uri: Uri): ByteArray? {
        val bitmap = decodeBitmap(uri) ?: return null
        val resized = resizeBitmap(bitmap, MAX_AVATAR_SIZE_PX)
        return ByteArrayOutputStream().use { output ->
            resized.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, output)
            output.toByteArray()
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        val resolver = requireContext().contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val largestSide = max(bitmap.width, bitmap.height)
        if (largestSide <= maxSize) return bitmap
        val scale = maxSize.toFloat() / largestSide.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun showBioDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_bio, null)
        val etBio = dialogView.findViewById<TextInputEditText>(R.id.etBio)
        val currentBio = viewModel.profile.value?.bio ?: ""
        etBio.setText(currentBio)
        etBio.setSelection(currentBio.length)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.profile_bio_dialog_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                viewModel.updateBio(etBio.text?.toString()?.trim() ?: "")
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null)
        val tilCurrent = dialogView.findViewById<TextInputLayout>(R.id.tilCurrentPassword)
        val tilNew = dialogView.findViewById<TextInputLayout>(R.id.tilNewPassword)
        val etCurrent = dialogView.findViewById<TextInputEditText>(R.id.etCurrentPassword)
        val etNew = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnPasswordCancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnPasswordSave)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            tilCurrent.error = null
            tilNew.error = null

            val current = etCurrent.text?.toString()?.trim() ?: ""
            val new = etNew.text?.toString()?.trim() ?: ""
            when {
                current.isBlank() -> {
                    tilCurrent.error = getString(R.string.profile_current_password_required)
                    etCurrent.requestFocus()
                    return@setOnClickListener
                }
                new.isBlank() -> {
                    tilNew.error = getString(R.string.profile_new_password_required)
                    etNew.requestFocus()
                    return@setOnClickListener
                }
            }

            val passwordError = PasswordValidator.validate(requireContext(), new)
            if (passwordError != null) {
                tilNew.error = passwordError
                etNew.requestFocus()
                return@setOnClickListener
            }

            viewModel.changePassword(current, new)
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(requireContext(), binding.btnMenu)
        popup.menu.add(0, 1, 0, getString(R.string.profile_menu_change_password))
        popup.menu.add(0, 2, 1, getString(R.string.profile_menu_notifications))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    showChangePasswordDialog()
                    true
                }
                2 -> {
                    findNavController().navigate(R.id.notificationSettingsFragment)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun updateLangButtons() {
        val isVI = LocaleHelper.getSavedLocale(requireContext()) == "vi"
        val active = ContextCompat.getColorStateList(requireContext(), R.color.primary)
        val inactive = ContextCompat.getColorStateList(requireContext(), R.color.surface_variant)
        val activeText = ContextCompat.getColor(requireContext(), R.color.text_on_primary)
        val inactiveText = ContextCompat.getColor(requireContext(), R.color.primary)
        binding.btnLangVI.backgroundTintList = if (isVI) active else inactive
        binding.btnLangEN.backgroundTintList = if (!isVI) active else inactive
        binding.btnLangVI.setTextColor(if (isVI) activeText else inactiveText)
        binding.btnLangEN.setTextColor(if (!isVI) activeText else inactiveText)
        binding.tvDisplayLang.text = getString(
            if (isVI) R.string.profile_display_lang_vi else R.string.profile_display_lang_en
        )
    }

    private fun setDisplayLanguage(lang: String) {
        if (LocaleHelper.getSavedLocale(requireContext()) == lang) return
        val app = requireActivity().application as ObjectLanguageApp
        lifecycleScope.launch {
            app.repository.updateUserSettings(lang).onFailure {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.profile_language_save_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            LocaleHelper.setLocale(requireContext(), lang)
            val msgRes = if (lang == "en") R.string.profile_language_changed_en else R.string.profile_language_changed_vi
            Toast.makeText(requireContext(), getString(msgRes), Toast.LENGTH_SHORT).show()
            requireActivity().recreate()
        }
    }

    private fun resolveMediaUrl(path: String): String {
        val trimmed = path.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "${ApiConfig.baseUrl.trimEnd('/')}/${trimmed.trimStart('/')}"
    }

    private fun showDefaultAvatar() {
        val padding = (20 * resources.displayMetrics.density).toInt()
        binding.ivAvatar.setImageResource(R.drawable.ic_person)
        binding.ivAvatar.setPadding(padding, padding, padding, padding)
        binding.ivAvatar.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.text_on_primary)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val AVATAR_JPEG_QUALITY = 86
        private const val MAX_AVATAR_SIZE_PX = 1024
    }
}
