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

            // Try server authentication first, fall back to local
            lifecycleScope.launch {
                val user = serverUserManager.authenticate(username, password)
                    ?: userManager.authenticate(username, password)

                if (user == null) {
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = getString(R.string.login)
                    showError(getString(R.string.invalid_credentials))
                    return@launch
                }

                // Authentication successful
                userManager.setCurrentUser(user.username)
                prefsManager.currentUsername = user.username
                prefsManager.currentRole = user.role

                hideError()

                // Show outlet selection phase
                binding.tilUsername.isEnabled = false
                binding.tilPassword.isEnabled = false
                binding.btnLogin.visibility = View.GONE

                setupOutletDropdown()
                binding.tilOutlet.visibility = View.VISIBLE
                binding.btnEnter.visibility = View.VISIBLE

                when (user.role) {
                    ServerUserManager.ROLE_USER -> {
                        // User role: lock to assigned store
                        binding.actvOutlet.setText(user.assignedStore, false)
                        binding.actvOutlet.isEnabled = false
                        binding.tilOutlet.isEnabled = false
                    }
                    else -> {
                        // Admin and Superuser: can select any store
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

            // Load bundled sample data if available for this outlet
            loadBundledData(selectedOutlet)
        }
    }

    private fun showError(message: String) {
        binding.tvLoginError.text = message
        binding.tvLoginError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvLoginError.visibility = View.GONE
    }

    private fun loadBundledData(outlet: String) {
        val repository = ProductRepository(this)

        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                repository.getItemCount(outlet)
            }

            // Only load bundled data if database is empty for this outlet
            if (count == 0) {
                try {
                    val assetFiles = assets.list("") ?: emptyArray()
                    val matchingFile = assetFiles.firstOrNull {
                        it.startsWith("${outlet}_") && it.endsWith(".txt")
                    }

                    if (matchingFile != null) {
                        Toast.makeText(
                            this@LoginActivity,
                            "Loading sample data for $outlet...",
                            Toast.LENGTH_SHORT
                        ).show()

                        withContext(Dispatchers.IO) {
                            val inputStream = assets.open(matchingFile)
                            repository.parseAndInsert(inputStream, outlet)
                        }

                        Toast.makeText(
                            this@LoginActivity,
                            "Sample data loaded!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    // Ignore - sample data is optional
                }
            }

            // Auto sync from server after sign-in
            autoSyncFromServer(outlet)
        }
    }

    private fun autoSyncFromServer(outlet: String) {
        val syncManager = DataSyncManager(this)

        Toast.makeText(this, "Syncing data from server...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val syncCount = withContext(Dispatchers.IO) {
                    syncManager.syncData(outlet)
                }
                Toast.makeText(
                    this@LoginActivity,
                    "Synced $syncCount items",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    "Auto sync skipped - will try later",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Also sync barcode mappings
            try {
                val barcodeCount = withContext(Dispatchers.IO) {
                    syncManager.syncBarcodeMappings()
                }
                Toast.makeText(
                    this@LoginActivity,
                    "Synced $barcodeCount barcode mappings",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                // Barcode sync failed - not critical
            }

            navigateToMain()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
