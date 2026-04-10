package com.outletscanner.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.outletscanner.app.R
import com.outletscanner.app.data.repository.ProductRepository
import com.outletscanner.app.databinding.ActivityLoginBinding
import com.outletscanner.app.ui.main.MainActivity
import com.outletscanner.app.util.DataSyncManager
import com.outletscanner.app.util.PrefsManager
import com.outletscanner.app.util.ServerUserManager
import com.outletscanner.app.util.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var userManager: UserManager
    private lateinit var serverUserManager: ServerUserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsManager = PrefsManager(this)
        userManager = UserManager(this)
        serverUserManager = ServerUserManager(this)

        // Skip login if already logged in
        if (prefsManager.isLoggedIn && prefsManager.selectedOutlet.isNotBlank() &&
            prefsManager.currentUsername.isNotBlank()
        ) {
            navigateToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLoginButton()
        setupEnterButton()
    }

    private fun setupOutletDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            PrefsManager.OUTLETS
        )
        binding.actvOutlet.setAdapter(adapter)
    }

    private fun setupLoginButton() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isBlank() || password.isBlank()) {
                showError(getString(R.string.enter_credentials))
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false
            binding.btnLogin.text = "Logging in..."

            lifecycleScope.launch {
                val user = serverUserManager.authenticate(username, password)
                    ?: userManager.authenticate(username, password)

                if (user == null) {
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = getString(R.string.login)
                    showError(getString(R.string.invalid_credentials))
                    return@launch
                }

                userManager.setCurrentUser(user.username)
                prefsManager.currentUsername = user.username
                prefsManager.currentRole = user.role

                hideError()

                binding.tilUsername.isEnabled = false
                binding.tilPassword.isEnabled = false
                binding.btnLogin.visibility = View.GONE

                setupOutletDropdown()
                binding.tilOutlet.visibility = View.VISIBLE
                binding.btnEnter.visibility = View.VISIBLE

                when (user.role) {
                    ServerUserManager.ROLE_USER -> {
                        binding.actvOutlet.setText(user.assignedStore, false)
                        binding.actvOutlet.isEnabled = false
                        binding.tilOutlet.isEnabled = false
                    }
                    else -> {
                        val savedOutlet = prefsManager.selectedOutlet
                        if (savedOutlet.isNotBlank()) {
                            binding.actvOutlet.setText(savedOutlet, false)
                        }
                    }
                }
            }
        }
    }

    private fun setupEnterButton() {
        binding.btnEnter.setOnClickListener {
            val selectedOutlet = binding.actvOutlet.text.toString().trim()

            if (selectedOutlet.isBlank()) {
                showError(getString(R.string.please_select_outlet))
                return@setOnClickListener
            }

            prefsManager.selectedOutlet = selectedOutlet
            prefsManager.isLoggedIn = true

            // Start auto sync after sign-in
            autoSyncFromServer(selectedOutlet)
        }
    }

    private fun showError(message: String) {
        binding.tvLoginError.text = message
        binding.tvLoginError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvLoginError.visibility = View.GONE
    }

    private fun autoSyncFromServer(outlet: String) {
        val syncManager = DataSyncManager(this)
        val repository = ProductRepository(this)

        // Disable buttons, show sync progress
        binding.btnEnter.isEnabled = false
        binding.tilOutlet.isEnabled = false
        binding.layoutSyncProgress.visibility = View.VISIBLE
        binding.progressSync.isIndeterminate = true
        binding.tvSyncStatus.text = "Syncing data..."
        binding.tvSyncDetail.text = "Checking existing data..."

        lifecycleScope.launch {
            // Check if data already exists for this outlet
            val existingCount = withContext(Dispatchers.IO) {
                repository.getItemCount(outlet)
            }
            val existingBarcodeCount = withContext(Dispatchers.IO) {
                repository.getBarcodeMappingCount()
            }

            var productSynced = false
            var barcodeSynced = false

            // Step 1: Sync product/price data
            if (existingCount == 0) {
                // No data - need to sync
                binding.tvSyncStatus.text = "Syncing price data..."
                binding.tvSyncDetail.text = "Downloading..."

                try {
                    // First try bundled data
                    val assetFiles = assets.list("") ?: emptyArray()
                    val matchingFile = assetFiles.firstOrNull {
                        it.startsWith("${outlet}_") && it.endsWith(".txt")
                    }

                    if (matchingFile != null) {
                        binding.tvSyncDetail.text = "Loading bundled data..."
                        withContext(Dispatchers.IO) {
                            val inputStream = assets.open(matchingFile)
                            repository.parseAndInsert(inputStream, outlet)
                        }
                        productSynced = true
                    }

                    // Then try server sync
                    binding.tvSyncDetail.text = "Downloading from server..."
                    binding.progressSync.isIndeterminate = false
                    binding.progressSync.progress = 0

                    val syncCount = withContext(Dispatchers.IO) {
                        syncManager.syncData(outlet) { processed, _ ->
                            launch(Dispatchers.Main) {
                                binding.tvSyncDetail.text = "Price data: $processed items loaded"
                                // Estimate progress (assume ~50K items typical)
                                val pct = (processed * 100 / 50000).coerceAtMost(95)
                                binding.progressSync.progress = pct
                            }
                        }
                    }
                    binding.progressSync.progress = 100
                    binding.tvSyncDetail.text = "Price data: $syncCount items loaded"
                    productSynced = true
                } catch (e: Exception) {
                    if (!productSynced) {
                        binding.tvSyncDetail.text = "Price sync failed - import manually later"
                    }
                }
            } else {
                // Data exists - skip sync
                binding.tvSyncDetail.text = "Price data: $existingCount items (cached)"
                binding.progressSync.isIndeterminate = false
                binding.progressSync.progress = 50
                productSynced = true
            }

            // Step 2: Sync barcode mappings
            if (existingBarcodeCount == 0) {
                binding.tvSyncStatus.text = "Syncing barcode mappings..."
                binding.tvSyncDetail.text = "Downloading barcode file..."
                binding.progressSync.isIndeterminate = false
                binding.progressSync.progress = 50

                try {
                    val barcodeCount = withContext(Dispatchers.IO) {
                        syncManager.syncBarcodeMappings { processed, _ ->
                            launch(Dispatchers.Main) {
                                binding.tvSyncDetail.text = "Barcode mappings: $processed loaded"
                                val pct = 50 + (processed * 50 / 540000).coerceAtMost(49)
                                binding.progressSync.progress = pct
                            }
                        }
                    }
                    binding.progressSync.progress = 100
                    binding.tvSyncDetail.text = "Barcode mappings: $barcodeCount loaded"
                    barcodeSynced = true
                } catch (e: Exception) {
                    if (!barcodeSynced) {
                        binding.tvSyncDetail.text = "Barcode sync failed - import manually later"
                    }
                }
            } else {
                binding.tvSyncDetail.text = "Barcode mappings: $existingBarcodeCount (cached)"
                binding.progressSync.progress = 100
                barcodeSynced = true
            }

            // Step 3: Sync hourly stock data (PS__ file) - always update
            binding.tvSyncStatus.text = "Updating stock data..."
            binding.tvSyncDetail.text = "Downloading hourly stock file..."
            binding.progressSync.isIndeterminate = true

            try {
                val stockCount = withContext(Dispatchers.IO) {
                    syncManager.syncStockData(outlet) { processed, _ ->
                        launch(Dispatchers.Main) {
                            binding.tvSyncDetail.text = "Stock update: $processed items"
                            binding.progressSync.isIndeterminate = false
                            val pct = (processed * 100 / 13000).coerceAtMost(95)
                            binding.progressSync.progress = pct
                        }
                    }
                }
                binding.tvSyncDetail.text = "Stock update: $stockCount items updated"
            } catch (e: Exception) {
                binding.tvSyncDetail.text = "Stock update skipped"
            }

            // Done - show summary briefly then navigate
            binding.tvSyncStatus.text = "Sync complete!"
            binding.progressSync.isIndeterminate = false
            binding.progressSync.progress = 100

            val finalProductCount = withContext(Dispatchers.IO) { repository.getItemCount(outlet) }
            val finalBarcodeCount = withContext(Dispatchers.IO) { repository.getBarcodeMappingCount() }
            binding.tvSyncDetail.text = "$finalProductCount products, $finalBarcodeCount barcode mappings"

            // Brief pause to show completion
            kotlinx.coroutines.delay(800)

            navigateToMain()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
