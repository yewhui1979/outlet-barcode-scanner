package com.outletscanner.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.outletscanner.app.BuildConfig
import com.outletscanner.app.R
import com.outletscanner.app.data.repository.ProductRepository
import com.outletscanner.app.databinding.ActivitySettingsBinding
import com.outletscanner.app.ui.login.LoginActivity
import com.outletscanner.app.util.PrefsManager
import com.outletscanner.app.util.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var userManager: UserManager
    private lateinit var repository: ProductRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager(this)
        userManager = UserManager(this)
        repository = ProductRepository(this)

        setupToolbar()
        setupUI()
        setupRoleBasedVisibility()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupUI() {
        binding.etServerUrl.setText(prefsManager.serverUrl)
        binding.tvCurrentOutlet.text = getString(R.string.outlet_label, prefsManager.selectedOutlet)
        binding.tvVersion.text = getString(R.string.version_info, BuildConfig.VERSION_NAME)

        // Show logged in user info
        val currentUser = userManager.getCurrentUser()
        if (currentUser != null) {
            binding.tvLoggedInAs.text = getString(
                R.string.logged_in_as,
                currentUser.username,
                currentUser.role.replaceFirstChar { it.uppercase() }
            )
            binding.tvLoggedInAs.visibility = View.VISIBLE
        }
    }

    private fun setupRoleBasedVisibility() {
        val role = prefsManager.currentRole

        // Server URL: admin only
        binding.cardServerUrl.visibility =
            if (role == UserManager.ROLE_ADMIN) View.VISIBLE else View.GONE

        // Change Outlet: admin and superuser only
        binding.cardChangeOutlet.visibility =
            if (role == UserManager.ROLE_ADMIN || role == UserManager.ROLE_SUPERUSER) View.VISIBLE else View.GONE

        // Clear Data: admin only
        binding.cardClearData.visibility =
            if (role == UserManager.ROLE_ADMIN) View.VISIBLE else View.GONE

        // Manage Users: admin only
        binding.cardManageUsers.visibility =
            if (role == UserManager.ROLE_ADMIN) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        // Save URL
        binding.btnSaveUrl.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            prefsManager.serverUrl = url
            Toast.makeText(this, "Server URL saved", Toast.LENGTH_SHORT).show()
        }

        // Change Outlet - clear local data so fresh data downloads for the new store
        binding.cardChangeOutlet.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Change Outlet")
                .setMessage("This will clear all cached data and download fresh data for the new outlet. Continue?")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            repository.deleteAll()
                        }
                        prefsManager.lastSyncTimestamp = ""
                        prefsManager.lastBarcodeSyncTimestamp = ""
                        prefsManager.lastStockSyncTimestamp = ""
                        prefsManager.logout()
                        val intent = Intent(this@SettingsActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                }
                .show()
        }

        // Clear Data
        binding.cardClearData.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_data)
                .setMessage(R.string.confirm_clear)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    clearData()
                }
                .show()
        }

        // Manage Users
        binding.cardManageUsers.setOnClickListener {
            startActivity(Intent(this, UserManagementActivity::class.java))
        }

        // Logout
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.confirm_logout)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    performLogout()
                }
                .show()
        }
    }

    private fun clearData() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteAll()
            }
            prefsManager.lastSyncTimestamp = ""
            Toast.makeText(this@SettingsActivity, R.string.data_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private fun performLogout() {
        userManager.clearCurrentUser()
        prefsManager.currentUsername = ""
        prefsManager.currentRole = ""
        prefsManager.logout()

        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
