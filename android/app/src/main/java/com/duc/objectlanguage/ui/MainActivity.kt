package com.duc.objectlanguage.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
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
        binding.bottomNavigation.setupWithNavController(navController)

        // Tab guard: guest chỉ được dùng tab Scan
        binding.bottomNavigation.setOnItemSelectedListener { item ->
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
            NavigationUI.onNavDestinationSelected(item, navController)
        }

        val hideToolbarDests = setOf(
            R.id.loginFragment, R.id.registerFragment,
            R.id.forgotPasswordFragment, R.id.verifyOtpFragment, R.id.resetPasswordFragment,
            R.id.dashboardFragment, R.id.scanFragment, R.id.reviewFragment,
            R.id.dictionaryFragment, R.id.profileFragment, R.id.exploreFragment
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
            bottomNavItemForDestination(dest.id)?.let { itemId ->
                binding.bottomNavigation.menu.findItem(itemId)?.isChecked = true
            }
        }

        // Khi app khởi động: đã đăng nhập → dashboard, chưa đăng nhập → ở lại scanFragment (guest).
        // Khi recreate() do đổi ngôn ngữ, savedInstanceState != null → NavController tự khôi phục.
        if (savedInstanceState == null) {
            val app = application as ObjectLanguageApp
            if (app.tokenManager.isLoggedIn) {
                val graph = navController.navInflater.inflate(R.navigation.nav_graph)
                graph.setStartDestination(R.id.dashboardFragment)
                navController.graph = graph
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
