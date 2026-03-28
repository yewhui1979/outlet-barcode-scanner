package com.outletscanner.app.ui.settings

import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var repository: ProductRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager(this)
        repository = ProductRepository(this)

        setupToolbar()
        setupUI()
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
    }

    private fun setupListeners() {
        // Save URL
        binding.btnSaveUrl.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            prefsManager.serverUrl = url
            Toast.makeText(this, "Server URL saved", Toast.LENGTH_SHORT).show()
        }

        // Change Outlet
        binding.cardChangeOutlet.setOnClickListener {
            prefsManager.logout()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
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
}
