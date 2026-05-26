package com.duc.objectlanguage.ui

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
import com.duc.objectlanguage.utils.LocaleHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isApplyingAccountLanguage = false
    private var currentTabId = R.id.scanFragment

    private data class NavTab(
        val tabView: LinearLayout,
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

    private fun syncTabSelection(activeDestId: Int, tabs: List<NavTab>) {
        if (currentTabId == activeDestId) return
        currentTabId = activeDestId
        val activeColor = ContextCompat.getColor(this, R.color.primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.bottom_nav_inactive)
        tabs.forEach { tab ->
            val isActive = tab.destinationId == activeDestId
            if (!tab.isScan) tab.icon.imageTintList = ColorStateList.valueOf(if (isActive) activeColor else inactiveColor)
            tab.label.setTextColor(if (isActive) activeColor else inactiveColor)
            if (isActive) {
                tab.icon.animate().scaleX(1.15f).scaleY(1.15f)
                    .setDuration(200).setInterpolator(OvershootInterpolator(2f)).start()
            } else {
                tab.icon.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
    }

    private fun navigateToBottomDestination(itemId: Int, navController: NavController, tabs: List<NavTab>): Boolean {
        if (navController.currentDestination?.id == itemId) return true

        if (navController.popBackStack(itemId, false)) {
            return true
        }

        val tabOrder = listOf(
            R.id.dashboardFragment,
            R.id.dictionaryFragment,
            R.id.scanFragment,
            R.id.reviewFragment,
            R.id.profileFragment
        )
        val fromTabId = bottomNavItemForDestination(navController.currentDestination?.id ?: -1) ?: -1
        val fromIndex = tabOrder.indexOf(fromTabId)
        val toIndex = tabOrder.indexOf(itemId)
        val goingRight = toIndex > fromIndex

        return try {
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.graph.startDestinationId, false)
                .setEnterAnim(if (goingRight) R.anim.nav_slide_in_right else R.anim.nav_slide_in_left)
                .setExitAnim(if (goingRight) R.anim.nav_slide_out_left else R.anim.nav_slide_out_right)
                .setPopEnterAnim(R.anim.nav_slide_in_left)
                .setPopExitAnim(R.anim.nav_slide_out_right)
                .build()
            navController.navigate(itemId, null, options)
            syncTabSelection(itemId, tabs)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun syncAccountLanguage() {
        val app = application as ObjectLanguageApp
        if (!app.tokenManager.isLoggedIn || isApplyingAccountLanguage) return

        lifecycleScope.launch {
            app.repository.getUserSettings().onSuccess { settings ->
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
