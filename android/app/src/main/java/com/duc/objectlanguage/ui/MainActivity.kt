package com.duc.objectlanguage.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.ActivityMainBinding
import com.duc.objectlanguage.ui.common.GuestUpsellDialog
import com.duc.objectlanguage.ui.review.ReviewSessionStore
import com.duc.objectlanguage.utils.LocaleHelper
import android.widget.Toast
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isApplyingAccountLanguage = false
    private var currentTabId = R.id.scanFragment
    private var previousTabId: Int? = null
    private var backPressedTime = 0L
    private var isBackNavigation = false

    private val validatedNetworks = mutableSetOf<Network>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (hasInternet) {
                validatedNetworks.add(network)
            } else {
                validatedNetworks.remove(network)
            }
            runOnUiThread {
                binding.offlineBanner.visibility = if (validatedNetworks.isNotEmpty()) View.GONE else View.VISIBLE
            }
        }
        override fun onLost(network: Network) {
            validatedNetworks.remove(network)
            runOnUiThread {
                binding.offlineBanner.visibility = if (validatedNetworks.isNotEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private data class NavTab(
        val tabView: View,
        val icon: ImageView,
        val label: TextView,
        val destinationId: Int,
        val isScan: Boolean = false,
    )

    // Áp dụng locale đúng cho Activity context (fix ngôn ngữ hệ thống chưa chuyển)
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.dashboardFragment,
                R.id.scanFragment,
                R.id.reviewFragment,
                R.id.dictionaryFragment,
                R.id.profileFragment
            )
        )

        binding.toolbar.setupWithNavController(navController, appBarConfiguration)

        val tabs = listOf(
            NavTab(binding.navTabHome, binding.navIconHome, binding.navLabelHome, R.id.dashboardFragment),
            NavTab(binding.navTabDictionary, binding.navIconDictionary, binding.navLabelDictionary, R.id.dictionaryFragment),
            NavTab(binding.navTabScan, binding.navIconScan, binding.navLabelScan, R.id.scanFragment, isScan = true),
            NavTab(binding.navTabReview, binding.navIconReview, binding.navLabelReview, R.id.reviewFragment),
            NavTab(binding.navTabProfile, binding.navIconProfile, binding.navLabelProfile, R.id.profileFragment),
        )

        tabs.forEach { tab ->
            tab.tabView.setOnClickListener {
                val app = application as ObjectLanguageApp
                if (!app.tokenManager.isLoggedIn && tab.destinationId != R.id.scanFragment) {
                    GuestUpsellDialog.show(
                        context = this,
                        reason = GuestUpsellDialog.Reason.OTHER_FEATURE,
                        onLogin = { navController.navigate(R.id.loginFragment) },
                        onRegister = { navController.navigate(R.id.registerFragment) }
                    )
                    return@setOnClickListener
                }
                navigateToBottomDestination(tab.destinationId, navController, tabs)
            }
        }

        val hideToolbarDests = setOf(
            R.id.loginFragment, R.id.registerFragment,
            R.id.forgotPasswordFragment, R.id.verifyOtpFragment, R.id.resetPasswordFragment,
            R.id.dashboardFragment, R.id.scanFragment, R.id.reviewFragment,
            R.id.dictionaryFragment, R.id.profileFragment, R.id.exploreFragment,
            R.id.historyFragment, R.id.streakFragment, R.id.onboardingFragment
        )
        val hideBottomNavDests = setOf(
            R.id.loginFragment, R.id.registerFragment,
            R.id.forgotPasswordFragment, R.id.verifyOtpFragment, R.id.resetPasswordFragment,
            R.id.onboardingFragment
        )

        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.toolbar.visibility =
                if (dest.id in hideToolbarDests) View.GONE else View.VISIBLE
            binding.customBottomNav.visibility =
                if (dest.id in hideBottomNavDests) View.GONE else View.VISIBLE
            setNavHostBehindBottomNav(dest.id == R.id.scanFragment)

            val app = application as ObjectLanguageApp
            // Sau khi logout về scanFragment, force reset highlight state
            if (dest.id == R.id.scanFragment && !app.tokenManager.isLoggedIn) {
                currentTabId = -1  // force syncTabSelection không bị skip
                syncTabSelection(R.id.scanFragment, tabs)
            } else {
                bottomNavItemForDestination(dest.id)?.let { tabId ->
                    syncTabSelection(tabId, tabs)
                }
            }
        }

        if (savedInstanceState == null) {
            val onboardingDone = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("onboarding_done", false)
            if (!onboardingDone) {
                navController.navigate(R.id.onboardingFragment)
                return
            }

            val app = application as ObjectLanguageApp
            if (app.tokenManager.isLoggedIn) {
                val graph = navController.navInflater.inflate(R.navigation.nav_graph)
                graph.setStartDestination(R.id.dashboardFragment)
                navController.graph = graph
                binding.customBottomNav.post {
                    syncTabSelection(R.id.dashboardFragment, tabs)
                }
            }
        }

        if (intent?.getBooleanExtra("open_review", false) == true) {
            val app = application as ObjectLanguageApp
            if (app.tokenManager.isLoggedIn) {
                binding.customBottomNav.post {
                    navigateToBottomDestination(R.id.reviewFragment, navController, tabs)
                }
            }
        }

        syncAccountLanguage()
        updateReviewBadge()
    }

    fun updateReviewBadge() {
        val app = application as ObjectLanguageApp
        if (!app.tokenManager.isLoggedIn) {
            binding.tvReviewBadge.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            app.repository.getDueCount().onSuccess { count ->
                if (count > 0) {
                    binding.tvReviewBadge.text = if (count > 99) "99+" else count.toString()
                    binding.tvReviewBadge.visibility = View.VISIBLE
                } else {
                    binding.tvReviewBadge.visibility = View.GONE
                }
            }
        }
    }

    private fun bottomNavItemForDestination(destinationId: Int): Int? {
        return when (destinationId) {
            R.id.dashboardFragment,
            R.id.exploreFragment,
            R.id.categoryDetailFragment -> R.id.dashboardFragment

            R.id.scanFragment -> R.id.scanFragment

            R.id.dictionaryFragment -> R.id.dictionaryFragment

            R.id.reviewFragment,
            R.id.quizFragment,
            R.id.typingTestFragment,
            R.id.listeningTestFragment,
            R.id.imageMatchingFragment,
            R.id.pronunciationFragment -> R.id.reviewFragment

            R.id.profileFragment,
            R.id.historyFragment,
            R.id.historyDetailFragment,
            R.id.analyticsFragment,
            R.id.collectionListFragment,
            R.id.collectionDetailFragment,
            R.id.collectionInsightsFragment,
            R.id.streakFragment,
            R.id.notificationSettingsFragment -> R.id.profileFragment

            else -> null
        }
    }

    private fun setNavHostBehindBottomNav(behindBottomNav: Boolean) {
        val params = binding.navHostFragment.layoutParams as ConstraintLayout.LayoutParams
        val targetBottomToBottom = if (behindBottomNav) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
        val targetBottomToTop = if (behindBottomNav) ConstraintLayout.LayoutParams.UNSET else R.id.customBottomNav

        if (params.bottomToBottom == targetBottomToBottom && params.bottomToTop == targetBottomToTop) return

        params.bottomToBottom = targetBottomToBottom
        params.bottomToTop = targetBottomToTop
        binding.navHostFragment.layoutParams = params
    }

    private fun syncTabSelection(activeDestId: Int, tabs: List<NavTab>) {
        if (currentTabId == activeDestId) return
        currentTabId = activeDestId
        val activeColor = ContextCompat.getColor(this, R.color.primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.bottom_nav_inactive)
        val scanActiveColor = ContextCompat.getColor(this, R.color.text_on_primary)
        tabs.forEach { tab ->
            val isActive = tab.destinationId == activeDestId
            tab.tabView.setBackgroundResource(
                if (isActive && !tab.isScan) R.drawable.bg_bottom_nav_item_active else R.drawable.bg_bottom_nav_item_idle
            )
            if (tab.isScan) {
                tab.icon.setBackgroundResource(
                    if (isActive) R.drawable.bg_bottom_nav_scan_button_active else R.drawable.bg_bottom_nav_scan_button_idle
                )
                tab.icon.imageTintList = ColorStateList.valueOf(if (isActive) scanActiveColor else activeColor)
            } else {
                tab.icon.imageTintList = ColorStateList.valueOf(if (isActive) activeColor else inactiveColor)
            }
            tab.label.setTextColor(if (isActive) activeColor else inactiveColor)
            tab.label.alpha = if (isActive) 1f else 0.82f
            tab.label.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
            if (isActive) {
                val activeScale = if (tab.isScan) 1.08f else 1.12f
                tab.icon.animate().scaleX(activeScale).scaleY(activeScale)
                    .setDuration(200).setInterpolator(OvershootInterpolator(2f)).start()
            } else {
                tab.icon.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
    }

    private fun navigateToBottomDestination(itemId: Int, navController: NavController, tabs: List<NavTab>): Boolean {
        if (navController.currentDestination?.id == itemId) return true

        val fromTabId = bottomNavItemForDestination(navController.currentDestination?.id ?: -1) ?: -1

        return try {
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                // Pop hết về start destination để giữ back stack gọn — chỉ 1 bước back giữa tabs
                .setPopUpTo(navController.graph.startDestinationId, false)
                .setEnterAnim(R.anim.nav_tab_fade_in)
                .setExitAnim(R.anim.nav_tab_fade_out)
                .setPopEnterAnim(R.anim.nav_tab_fade_in)
                .setPopExitAnim(R.anim.nav_tab_fade_out)
                .build()
            if (!isBackNavigation) previousTabId = fromTabId.takeIf { it != -1 }
            navController.navigate(itemId, null, options)
            syncTabSelection(itemId, tabs)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val currentDest = navController.currentDestination?.id ?: -1
        val currentTab = bottomNavItemForDestination(currentDest)
        if (handleCollectionReviewBack(currentDest, navController)) {
            return
        }

        // Nếu đang ở sub-screen (quiz, history detail...) thì để NavController xử lý bình thường
        if (currentDest != currentTab) {
            if (!navController.popBackStack()) super.onBackPressed()
            return
        }

        // Đang ở tab root — về tab trước đó nếu có, không thì để super xử lý (thoát app)
        val prev = previousTabId
        if (prev != null && prev != currentTab) {
            previousTabId = null
            val tabs = listOf(
                NavTab(binding.navTabHome, binding.navIconHome, binding.navLabelHome, R.id.dashboardFragment),
                NavTab(binding.navTabDictionary, binding.navIconDictionary, binding.navLabelDictionary, R.id.dictionaryFragment),
                NavTab(binding.navTabScan, binding.navIconScan, binding.navLabelScan, R.id.scanFragment, isScan = true),
                NavTab(binding.navTabReview, binding.navIconReview, binding.navLabelReview, R.id.reviewFragment),
                NavTab(binding.navTabProfile, binding.navIconProfile, binding.navLabelProfile, R.id.profileFragment),
            )
            isBackNavigation = true
            navigateToBottomDestination(prev, navController, tabs)
            isBackNavigation = false
        } else {
            val now = System.currentTimeMillis()
            if (now - backPressedTime < 2000) {
                super.onBackPressed()
            } else {
                backPressedTime = now
                Toast.makeText(this, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleCollectionReviewBack(currentDest: Int, navController: NavController): Boolean {
        if (currentDest != R.id.reviewFragment) return false
        val collectionId = navController.currentBackStackEntry
            ?.arguments
            ?.getInt("collectionId", 0) ?: 0
        val practice = navController.currentBackStackEntry
            ?.arguments
            ?.getBoolean("isPractice", false) ?: false

        if (!ReviewSessionStore.hasActiveSession(collectionId, practice)) {
            if (collectionId > 0) {
                if (!navController.popBackStack()) {
                    navController.navigate(R.id.collectionListFragment)
                }
                return true
            }
            return false
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_review_leave, null)
        dialogView.findViewById<android.widget.TextView>(R.id.tvLeaveTitle)
            .text = getString(R.string.review_leave_collection_title)
        dialogView.findViewById<android.widget.TextView>(R.id.tvLeaveMessage)
            .text = getString(R.string.review_leave_collection_message)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLeaveConfirm).apply {
            text = getString(R.string.review_leave_collection_confirm)
            setOnClickListener {
                dialog.dismiss()
                if (collectionId > 0) {
                    if (!navController.popBackStack()) {
                        navController.navigate(R.id.collectionListFragment)
                    }
                } else {
                    val prev = previousTabId
                    if (prev != null) {
                        previousTabId = null
                        val tabs = listOf(
                            NavTab(binding.navTabHome, binding.navIconHome, binding.navLabelHome, R.id.dashboardFragment),
                            NavTab(binding.navTabDictionary, binding.navIconDictionary, binding.navLabelDictionary, R.id.dictionaryFragment),
                            NavTab(binding.navTabScan, binding.navIconScan, binding.navLabelScan, R.id.scanFragment, isScan = true),
                            NavTab(binding.navTabReview, binding.navIconReview, binding.navLabelReview, R.id.reviewFragment),
                            NavTab(binding.navTabProfile, binding.navIconProfile, binding.navLabelProfile, R.id.profileFragment),
                        )
                        navigateToBottomDestination(prev, navController, tabs)
                    } else {
                        super.onBackPressed()
                    }
                }
            }
        }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLeaveStay).apply {
            text = getString(R.string.review_leave_collection_stay)
            setOnClickListener { dialog.dismiss() }
        }
        dialog.show()
        return true
    }

    override fun onResume() {
        super.onResume()
        validatedNetworks.clear()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, networkCallback)
        val isOnline = cm.activeNetwork?.let { net ->
            cm.getNetworkCapabilities(net)?.let { caps ->
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        } ?: false
        binding.offlineBanner.visibility = if (isOnline) View.GONE else View.VISIBLE
        updateReviewBadge()
    }

    override fun onPause() {
        super.onPause()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.unregisterNetworkCallback(networkCallback)
    }

    private fun syncAccountLanguage() {
        val app = application as ObjectLanguageApp
        if (!app.tokenManager.isLoggedIn || isApplyingAccountLanguage) return

        lifecycleScope.launch {
            app.repository.getUserSettings().onSuccess { settings ->
                // Sync dark mode từ server về local
                val serverDark = settings.darkMode
                val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                val localDark = prefs.getBoolean("dark_mode", false)
                if (serverDark != localDark) {
                    prefs.edit().putBoolean("dark_mode", serverDark).apply()
                    AppCompatDelegate.setDefaultNightMode(
                        if (serverDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    )
                }

                val accountLanguage = settings.displayLanguage
                if (accountLanguage.isNotBlank() && accountLanguage != LocaleHelper.getSavedLocale(this@MainActivity)) {
                    isApplyingAccountLanguage = true
                    LocaleHelper.setLocale(this@MainActivity, accountLanguage)
                    recreate()
                }
            }
        }
    }
}
