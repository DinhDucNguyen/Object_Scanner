package com.duc.objectlanguage.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
    private var isSyncingBottomNavigation = false

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

        // Tab guard: guest chỉ được dùng tab Scan
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (isSyncingBottomNavigation) {
                return@setOnItemSelectedListener true
            }

            val app = application as ObjectLanguageApp
            if (!app.tokenManager.isLoggedIn && item.itemId != R.id.scanFragment) {
                GuestUpsellDialog.show(
                    context = this,
                    reason = GuestUpsellDialog.Reason.OTHER_FEATURE,
                    onLogin = { navController.navigate(R.id.loginFragment) },
                    onRegister = { navController.navigate(R.id.registerFragment) }
                )
                return@setOnItemSelectedListener false
            }
            navigateToBottomDestination(item.itemId, navController)
        }
        binding.bottomNavigation.setOnItemReselectedListener { item ->
            navigateToBottomDestination(item.itemId, navController)
        }

        val hideToolbarDests = setOf(
            R.id.loginFragment, R.id.registerFragment,
            R.id.forgotPasswordFragment, R.id.verifyOtpFragment, R.id.resetPasswordFragment,
            R.id.dashboardFragment, R.id.scanFragment, R.id.reviewFragment,
            R.id.dictionaryFragment, R.id.profileFragment, R.id.exploreFragment,
            R.id.historyFragment, R.id.streakFragment
        )
        val hideBottomNavDests = setOf(
            R.id.loginFragment, R.id.registerFragment,
            R.id.forgotPasswordFragment, R.id.verifyOtpFragment, R.id.resetPasswordFragment
        )

        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.toolbar.visibility =
                if (dest.id in hideToolbarDests) View.GONE else View.VISIBLE
            binding.bottomNavigation.visibility =
                if (dest.id in hideBottomNavDests) View.GONE else View.VISIBLE
            bottomNavItemForDestination(dest.id)?.let(::syncBottomNavigationSelection)
        }

        // Khi app khởi động: đã đăng nhập → dashboard, chưa đăng nhập → ở lại scanFragment (guest).
        // Khi recreate() do đổi ngôn ngữ, savedInstanceState != null → NavController tự khôi phục.
        if (savedInstanceState == null) {
            val app = application as ObjectLanguageApp
            if (app.tokenManager.isLoggedIn) {
                val graph = navController.navInflater.inflate(R.navigation.nav_graph)
                graph.setStartDestination(R.id.dashboardFragment)
                navController.graph = graph
                // Fix: override any pending scan-tab sync from the initial nav_graph startDestination
                binding.bottomNavigation.post {
                    isSyncingBottomNavigation = true
                    try {
                        binding.bottomNavigation.selectedItemId = R.id.dashboardFragment
                    } finally {
                        isSyncingBottomNavigation = false
                    }
                }
            }
        }

        if (intent?.getBooleanExtra("open_review", false) == true) {
            val app = application as ObjectLanguageApp
            if (app.tokenManager.isLoggedIn) {
                binding.bottomNavigation.post {
                    binding.bottomNavigation.selectedItemId = R.id.reviewFragment
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

    private fun syncBottomNavigationSelection(itemId: Int) {
        if (binding.bottomNavigation.selectedItemId == itemId) return
        binding.bottomNavigation.post {
            if (binding.bottomNavigation.selectedItemId == itemId) return@post
            isSyncingBottomNavigation = true
            try {
                binding.bottomNavigation.selectedItemId = itemId
            } finally {
                isSyncingBottomNavigation = false
            }
        }
    }

    private fun navigateToBottomDestination(itemId: Int, navController: NavController): Boolean {
        if (navController.currentDestination?.id == itemId) return true

        if (navController.popBackStack(itemId, false)) {
            return true
        }

        val tabOrder = listOf(
            R.id.dashboardFragment,
            R.id.scanFragment,
            R.id.dictionaryFragment,
            R.id.reviewFragment,
            R.id.profileFragment
        )
        val currentTabId = bottomNavItemForDestination(navController.currentDestination?.id ?: -1) ?: -1
        val fromIndex = tabOrder.indexOf(currentTabId)
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
