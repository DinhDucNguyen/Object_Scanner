package com.duc.objectlanguage.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.duc.objectlanguage.ObjectLanguageApp
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.ActivityMainBinding
import com.duc.objectlanguage.utils.LocaleHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Áp dụng locale đúng cho Activity context (fix ngôn ngữ hệ thống chưa chuyển)
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // Ẩn bottom nav ở login/register
        navController.addOnDestinationChangedListener { _, dest, _ ->
            when (dest.id) {
                R.id.loginFragment, R.id.registerFragment -> {
                    binding.bottomNavigation.visibility = View.GONE
                    binding.toolbar.visibility = View.GONE
                }
                else -> {
                    binding.bottomNavigation.visibility = View.VISIBLE
                    binding.toolbar.visibility = View.VISIBLE
                }
            }
        }

        // Chỉ navigate khi Activity khởi tạo lần đầu (savedInstanceState == null).
        // Khi recreate() do đổi ngôn ngữ, savedInstanceState != null →
        // NavController tự khôi phục destination cũ, KHÔNG navigate thêm.
        if (savedInstanceState == null) {
            val app = application as ObjectLanguageApp
            if (app.tokenManager.isLoggedIn) {
                navController.navigate(R.id.dashboardFragment)
            }
        }
    }
}
