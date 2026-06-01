package com.duc.objectlanguage.ui.profile

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.duc.objectlanguage.ui.common.navigateWithSlide
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.local.ApiConfig
import com.duc.objectlanguage.databinding.FragmentProfileBinding
import com.duc.objectlanguage.utils.LocaleHelper
import com.duc.objectlanguage.utils.PasswordValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.yalantis.ucrop.UCrop
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        launchAvatarCrop(uri)
    }

    private val cropAvatar = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            UCrop.getOutput(result.data!!)?.let { croppedUri ->
                uploadSelectedAvatar(croppedUri)
            }
            return@registerForActivityResult
        }

        val error = result.data?.let { UCrop.getError(it) }
        if (error != null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.profile_avatar_crop_error, error.message ?: ""),
                Toast.LENGTH_SHORT
            ).show()
        }
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

        val initialUsername = app.tokenManager.username ?: getString(R.string.dashboard_default_user)
        binding.tvUsername.text = initialUsername
        binding.tvUsernameHandle.text = getString(R.string.profile_username_handle, initialUsername)
        showAvatarLoading()
        updateLangButtons()
        setupDarkModeSwitch()
        setupClicks()
        observeViewModel()
        viewModel.loadProfile()
        viewModel.loadStats()
        viewModel.loadCollections()
        animateEntrance()
        restorePendingScrollPosition()
    }

    private fun animateEntrance() {
        val interp = DecelerateInterpolator(1.6f)
        val container = (binding.root as? android.view.ViewGroup)?.getChildAt(0) as? android.view.ViewGroup
        container?.getChildAt(0)?.apply {
            alpha = 0f; translationY = -30f
            animate().alpha(1f).translationY(0f)
                .setDuration(380).setInterpolator(OvershootInterpolator(1.2f)).setStartDelay(40).start()
        }
        val cards = listOf(
            binding.cardHistory,
            binding.cardAnalytics,
            binding.cardStreak,
            binding.cardCollection,
        )
        cards.forEachIndexed { i, card ->
            card.alpha = 0f; card.translationY = 36f
            card.animate().alpha(1f).translationY(0f)
                .setDuration(300).setStartDelay(140L + i * 70L)
                .setInterpolator(interp).start()
        }
    }

    private fun observeViewModel() {
        viewModel.profile.observe(viewLifecycleOwner) { profile ->
            profile ?: return@observe
            val app = requireActivity().application as ObjectLanguageApp
            val username = profile.username?.takeIf { it.isNotBlank() }
                ?: app.tokenManager.username
                ?: getString(R.string.dashboard_default_user)
            val displayName = profile.fullName?.takeIf { it.isNotBlank() } ?: username
            binding.tvUsername.text = displayName
            binding.tvUsernameHandle.text = getString(
                R.string.profile_username_handle,
                username
            )
            binding.tvBio.text = profile.bio?.takeIf { it.isNotBlank() }
                ?: getString(R.string.profile_bio_placeholder)

            bindProfileAvatar(profile.avatarUrl)
        }
        viewModel.message.observe(viewLifecycleOwner) { msg ->
            msg ?: return@observe
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            binding.tvProfileScanCount.text = (stats?.dueToday ?: 0).toString()
            binding.tvProfileDueCount.text = (stats?.currentStreak ?: 0).toString()
        }
        viewModel.collectionCount.observe(viewLifecycleOwner) { count ->
            binding.tvProfileLearnedCount.text = count.toString()
        }
    }

    private fun setupClicks() {
        binding.cardHistory.setOnClickListener { findNavController().navigateWithSlide(R.id.historyFragment) }
        binding.cardAnalytics.setOnClickListener { findNavController().navigateWithSlide(R.id.analyticsFragment) }
        binding.cardStreak.setOnClickListener { findNavController().navigateWithSlide(R.id.streakFragment) }
        binding.cardCollection.setOnClickListener { findNavController().navigateWithSlide(R.id.collectionListFragment) }
        binding.statCollections.setOnClickListener { findNavController().navigateWithSlide(R.id.collectionListFragment) }
        binding.statDueReviews.setOnClickListener { findNavController().navigateWithSlide(R.id.reviewFragment) }
        binding.statStreak.setOnClickListener { findNavController().navigateWithSlide(R.id.streakFragment) }
        binding.btnLangVI.setOnClickListener { setDisplayLanguage("vi") }
        binding.btnLangEN.setOnClickListener { setDisplayLanguage("en") }
        binding.frameAvatar.setOnClickListener { showAvatarOptions() }
        binding.tvUsername.setOnClickListener { showFullNameDialog() }
        binding.btnEditFullName.setOnClickListener { showFullNameDialog() }
        binding.tvUsernameHandle.setOnClickListener { showUsernameDialog() }
        binding.tvBio.setOnClickListener { showBioDialog() }
        binding.btnMenu.setOnClickListener { showOverflowMenu() }
        binding.btnLogout.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_logout_confirm, null)
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create()
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmLogout)
                .setOnClickListener {
                    dialog.dismiss()
                    val app = requireActivity().application as ObjectLanguageApp
                    app.repository.logout()
                    resetGraphToGuestScan()
                }
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelLogout)
                .setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }

    private fun showDeleteAccountDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_account, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        val etPassword = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword)

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmDelete)
            .setOnClickListener {
                val password = etPassword.text.toString()
                if (password.isEmpty()) {
                    etPassword.error = getString(R.string.profile_delete_account_password_hint)
                    return@setOnClickListener
                }
                val app = requireActivity().application as ObjectLanguageApp
                val btn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmDelete)
                btn.isEnabled = false
                lifecycleScope.launch {
                    val result = app.repository.deleteAccount(password)
                    if (_binding == null) return@launch
                    result.fold(
                        onSuccess = {
                            dialog.dismiss()
                            Toast.makeText(requireContext(), getString(R.string.profile_delete_account_success), Toast.LENGTH_SHORT).show()
                            resetGraphToGuestScan()
                        },
                        onFailure = {
                            btn.isEnabled = true
                            Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelDelete)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showAvatarOptions() {
        if (!hasCustomAvatar()) {
            pickImage.launch("image/*")
            return
        }

        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_avatar_options, binding.root, false)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.cardViewAvatar).setOnClickListener {
            sheet.dismiss()
            showAvatarPreview()
        }
        view.findViewById<View>(R.id.cardChangeAvatar).setOnClickListener {
            sheet.dismiss()
            pickImage.launch("image/*")
        }

        sheet.show()
        sheet.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun hasCustomAvatar(): Boolean {
        val avatarUrl = viewModel.profile.value?.avatarUrl
        return !avatarUrl.isNullOrBlank() && avatarUrl != "default_avatar.png"
    }

    private fun launchAvatarCrop(sourceUri: Uri) {
        val destination = Uri.fromFile(
            File(requireContext().cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
        )
        val options = UCrop.Options().apply {
            setCircleDimmedLayer(true)
            setShowCropFrame(false)
            setShowCropGrid(false)
            setFreeStyleCropEnabled(false)
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(AVATAR_JPEG_QUALITY)
            setToolbarTitle(getString(R.string.profile_avatar_crop_title))
            setToolbarColor(ContextCompat.getColor(requireContext(), R.color.primary))
            setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.primary_dark))
            setActiveControlsWidgetColor(ContextCompat.getColor(requireContext(), R.color.primary))
        }
        val intent = UCrop.of(sourceUri, destination)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(MAX_AVATAR_SIZE_PX, MAX_AVATAR_SIZE_PX)
            .withOptions(options)
            .getIntent(requireContext())
        cropAvatar.launch(intent)
    }

    private fun showAvatarPreview() {
        val dialog = Dialog(requireContext())
        val container = FrameLayout(requireContext()).apply {
            setBackgroundColor(Color.BLACK)
        }
        val image = ImageView(requireContext()).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }

        container.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val closeButton = ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_profile_edit_button)
            contentDescription = getString(R.string.btn_cancel)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
        container.addView(
            closeButton,
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(24)
                marginEnd = dp(18)
            }
        )

        val avatarUrl = viewModel.profile.value?.avatarUrl
        if (!avatarUrl.isNullOrBlank() && avatarUrl != "default_avatar.png") {
            Glide.with(this)
                .load(resolveMediaUrl(avatarUrl))
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(image)
        } else {
            image.setImageResource(R.drawable.ic_person)
            image.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_on_primary))
            image.setPadding(dp(96), dp(96), dp(96), dp(96))
        }

        dialog.setContentView(container)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun resetGraphToGuestScan() {
        val navController = findNavController()
        // Pop toàn bộ back stack về graph root, rồi navigate tới scanFragment
        // Dùng cách này thay vì reset graph để tránh IllegalStateException khi Fragment đang transition
        try {
            val options = androidx.navigation.NavOptions.Builder()
                .setPopUpTo(navController.graph.id, true)
                .setLaunchSingleTop(true)
                .build()
            navController.navigate(R.id.scanFragment, null, options)
        } catch (_: Exception) {
            // Fallback: reset graph nếu navigate thất bại
            val graph = navController.navInflater.inflate(R.navigation.nav_graph)
            graph.setStartDestination(R.id.scanFragment)
            navController.graph = graph
        }
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
            .setView(dialogView)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                viewModel.updateBio(etBio.text?.toString()?.trim() ?: "")
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showFullNameDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_full_name, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.tilFullName)
        val input = dialogView.findViewById<TextInputEditText>(R.id.etFullName)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnFullNameCancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnFullNameSave)
        val currentName = viewModel.profile.value?.fullName
            ?.takeIf { it.isNotBlank() }
            ?: binding.tvUsername.text.toString()
        input.setText(currentName)
        input.setSelection(input.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val fullName = input.text?.toString()?.trim().orEmpty()
            if (fullName.isBlank()) {
                inputLayout.error = getString(R.string.profile_name_required)
                input.requestFocus()
                return@setOnClickListener
            }
            inputLayout.error = null
            viewModel.updateFullName(fullName)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showUsernameDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_username, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.tilUsername)
        val input = dialogView.findViewById<TextInputEditText>(R.id.etUsername)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnUsernameCancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnUsernameSave)
        input.setText(viewModel.profile.value?.username ?: binding.tvUsernameHandle.text.toString().removePrefix("@"))
        input.setSelection(input.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val username = input.text?.toString()?.trim().orEmpty()
            if (username.isBlank()) {
                inputLayout.error = getString(R.string.profile_username_required)
                input.requestFocus()
                return@setOnClickListener
            }
            if (!USERNAME_PATTERN.matches(username)) {
                inputLayout.error = getString(R.string.auth_invalid_username)
                input.requestFocus()
                return@setOnClickListener
            }
            inputLayout.error = null
            viewModel.updateUsername(username)
            dialog.dismiss()
        }
        dialog.show()
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

            btnSave.isEnabled = false
            btnCancel.isEnabled = false
            val app = requireActivity().application as ObjectLanguageApp
            lifecycleScope.launch {
                val result = app.repository.changePassword(current, new)
                if (_binding == null) return@launch
                result.fold(
                    onSuccess = {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    },
                    onFailure = {
                        btnSave.isEnabled = true
                        btnCancel.isEnabled = true
                        tilCurrent.error = it.message
                    }
                )
            }
        }
        dialog.show()
    }

    private fun showOverflowMenu() {
        val popup = PopupMenu(requireContext(), binding.btnMenu)
        popup.menu.add(0, 1, 0, getString(R.string.profile_menu_change_password))
        popup.menu.add(0, 2, 1, getString(R.string.profile_menu_notifications))
        popup.menu.add(0, 3, 2, getString(R.string.profile_menu_delete_account))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { showChangePasswordDialog(); true }
                2 -> { findNavController().navigateWithSlide(R.id.notificationSettingsFragment); true }
                3 -> { showDeleteAccountDialog(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun setupDarkModeSwitch() {
        val prefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)
        // Tắt listener trước khi set checked để tránh trigger giả
        binding.switchDarkMode.setOnCheckedChangeListener(null)
        binding.switchDarkMode.isChecked = isDark
        binding.switchDarkMode.setOnCheckedChangeListener { _, checked ->
            saveScrollPositionForRecreate()
            prefs.edit().putBoolean("dark_mode", checked).apply()
            val app = requireActivity().application as ObjectLanguageApp
            if (app.tokenManager.isLoggedIn) {
                app.applicationScope.launch {
                    app.repository.updateDarkMode(checked)
                }
            }
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun saveScrollPositionForRecreate() {
        val prefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_RESTORE_PROFILE_SCROLL, true)
            .putInt(KEY_PROFILE_SCROLL_Y, binding.root.scrollY)
            .apply()
    }

    private fun restorePendingScrollPosition() {
        val prefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RESTORE_PROFILE_SCROLL, false)) return
        val scrollY = prefs.getInt(KEY_PROFILE_SCROLL_Y, 0)
        prefs.edit()
            .remove(KEY_RESTORE_PROFILE_SCROLL)
            .remove(KEY_PROFILE_SCROLL_Y)
            .apply()

        binding.root.post {
            binding.root.post {
                binding.root.scrollTo(0, scrollY)
            }
        }
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
            saveScrollPositionForRecreate()
            requireActivity().recreate()
        }
    }

    private fun resolveMediaUrl(path: String): String {
        val trimmed = path.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "${ApiConfig.baseUrl.trimEnd('/')}/${trimmed.trimStart('/')}"
    }

    private fun showDefaultAvatar() {
        val padding = (22 * resources.displayMetrics.density).toInt()
        Glide.with(binding.ivAvatar).clear(binding.ivAvatar)
        binding.ivAvatar.scaleType = ImageView.ScaleType.CENTER
        binding.ivAvatar.setImageResource(R.drawable.ic_person)
        binding.ivAvatar.setPadding(padding, padding, padding, padding)
        binding.ivAvatar.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
    }

    private fun showAvatarLoading() {
        Glide.with(binding.ivAvatar).clear(binding.ivAvatar)
        binding.ivAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
        binding.ivAvatar.setImageDrawable(null)
        binding.ivAvatar.setPadding(0, 0, 0, 0)
        binding.ivAvatar.imageTintList = null
        binding.ivAvatar.clearColorFilter()
    }

    private fun bindProfileAvatar(avatarUrl: String?) {
        if (avatarUrl.isNullOrBlank() || avatarUrl == "default_avatar.png") {
            showDefaultAvatar()
            return
        }

        binding.ivAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
        binding.ivAvatar.setPadding(0, 0, 0, 0)
        binding.ivAvatar.imageTintList = null
        binding.ivAvatar.clearColorFilter()

        Glide.with(this)
            .load(resolveMediaUrl(avatarUrl))
            .transform(CenterCrop())
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    showDefaultAvatar()
                    return true
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.ivAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
                    binding.ivAvatar.setPadding(0, 0, 0, 0)
                    binding.ivAvatar.imageTintList = null
                    binding.ivAvatar.clearColorFilter()
                    return false
                }
            })
            .into(binding.ivAvatar)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val AVATAR_JPEG_QUALITY = 86
        private const val MAX_AVATAR_SIZE_PX = 1024
        private const val KEY_RESTORE_PROFILE_SCROLL = "restore_profile_scroll_after_recreate"
        private const val KEY_PROFILE_SCROLL_Y = "profile_scroll_y"
        private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,50}$")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
