package com.outletscanner.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.outletscanner.app.R
import com.outletscanner.app.databinding.ActivityLoginBinding
import com.outletscanner.app.ui.main.MainActivity
import com.outletscanner.app.util.PrefsManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefsManager: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsManager = PrefsManager(this)

        // Skip login if already logged in
        if (prefsManager.isLoggedIn && prefsManager.selectedOutlet.isNotBlank()) {
            navigateToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupOutletDropdown()
        setupEnterButton()
    }

    private fun setupOutletDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            PrefsManager.OUTLETS
        )
        binding.actvOutlet.setAdapter(adapter)

        // Pre-select if previously saved
        val savedOutlet = prefsManager.selectedOutlet
        if (savedOutlet.isNotBlank()) {
            binding.actvOutlet.setText(savedOutlet, false)
        }
    }

    private fun setupEnterButton() {
        binding.btnEnter.setOnClickListener {
            val selectedOutlet = binding.actvOutlet.text.toString().trim()

            if (selectedOutlet.isBlank()) {
                Snackbar.make(binding.root, R.string.please_select_outlet, Snackbar.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            prefsManager.selectedOutlet = selectedOutlet
            prefsManager.isLoggedIn = true
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
