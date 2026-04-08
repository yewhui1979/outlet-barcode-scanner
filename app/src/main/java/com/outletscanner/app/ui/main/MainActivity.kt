package com.outletscanner.app.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.outletscanner.app.R
import com.outletscanner.app.data.model.Product
import com.outletscanner.app.data.repository.ProductRepository
import com.outletscanner.app.databinding.ActivityMainBinding
import com.outletscanner.app.ui.product.ProductDetailActivity
import com.outletscanner.app.ui.scanner.ScannerActivity
import com.outletscanner.app.ui.settings.SettingsActivity
import com.outletscanner.app.util.DataSyncManager
import com.outletscanner.app.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var repository: ProductRepository
    private lateinit var syncManager: DataSyncManager

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importFileFromUri(it) }
    }

    companion object {
        private const val REQUEST_SCAN = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager(this)
        repository = ProductRepository(this)
        syncManager = DataSyncManager(this)

        setupToolbar()
        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupUI() {
        binding.tvOutletName.text = getString(R.string.outlet_label, prefsManager.selectedOutlet)
        updateLastSynced()
    }

    private fun setupListeners() {
        // Scan button
        binding.btnScan.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_SCAN)
        }

        // Search
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        // Import file button
        binding.btnImportFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("text/plain", "*/*"))
        }

        // Sync button
        binding.btnSync.setOnClickListener {
            performSync()
        }
    }

    private fun importFileFromUri(uri: Uri) {
        val outlet = prefsManager.selectedOutlet
        if (outlet.isBlank()) return

        binding.btnImportFile.isEnabled = false
        binding.progressSync.visibility = View.VISIBLE
        binding.progressSync.isIndeterminate = true
        binding.tvLastSynced.text = "Importing file..."

        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot read file")
                    repository.parseAndInsert(inputStream, outlet) { processed, _ ->
                        launch(Dispatchers.Main) {
                            binding.tvItemCount.text = getString(R.string.item_count, processed)
                        }
                    }
                }

                prefsManager.lastSyncTimestamp = java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
                ).format(java.util.Date())

                Snackbar.make(
                    binding.root,
                    "Imported $count products successfully!",
                    Snackbar.LENGTH_SHORT
                ).show()

                refreshUI()
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    "Import failed: ${e.message ?: "Unknown error"}",
                    Snackbar.LENGTH_LONG
                ).show()
            } finally {
                binding.btnImportFile.isEnabled = true
                binding.progressSync.visibility = View.GONE
            }
        }
    }

    private fun performSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isBlank()) return

        lifecycleScope.launch {
            val product = withContext(Dispatchers.IO) {
                repository.search(prefsManager.selectedOutlet, query)
            }

            if (product != null) {
                startActivity(buildProductDetailIntent(product))
                binding.etSearch.text?.clear()
            } else {
                Snackbar.make(binding.root, R.string.no_product_found, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun performSync() {
        val outlet = prefsManager.selectedOutlet
        if (outlet.isBlank()) return

        binding.btnSync.isEnabled = false
        binding.progressSync.visibility = View.VISIBLE
        binding.progressSync.isIndeterminate = true

        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    syncManager.syncData(outlet) { processed, _ ->
                        launch(Dispatchers.Main) {
                            binding.progressSync.isIndeterminate = false
                            binding.tvLastSynced.text = getString(R.string.sync_progress, 0)
                            // Show count so far
                            binding.tvItemCount.text = getString(R.string.item_count, processed)
                        }
                    }
                }

                Snackbar.make(
                    binding.root,
                    getString(R.string.sync_complete, count),
                    Snackbar.LENGTH_SHORT
                ).show()

                refreshUI()
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.sync_failed, e.message ?: "Unknown error"),
                    Snackbar.LENGTH_LONG
                ).show()
            } finally {
                binding.btnSync.isEnabled = true
                binding.progressSync.visibility = View.GONE
            }
        }
    }

    private fun refreshUI() {
        updateLastSynced()
        updateItemCount()
        binding.tvOutletName.text = getString(R.string.outlet_label, prefsManager.selectedOutlet)
    }

    private fun updateLastSynced() {
        val lastSync = prefsManager.lastSyncTimestamp
        binding.tvLastSynced.text = if (lastSync.isBlank()) {
            getString(R.string.never_synced)
        } else {
            getString(R.string.last_synced, lastSync)
        }
    }

    private fun updateItemCount() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                repository.getItemCount(prefsManager.selectedOutlet)
            }
            binding.tvItemCount.text = if (count > 0) {
                getString(R.string.item_count, count)
            } else {
                getString(R.string.no_items)
            }
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SCAN && resultCode == RESULT_OK) {
            val scannedBarcode = data?.getStringExtra("barcode") ?: return
            lookupBarcode(scannedBarcode)
        }
    }

    private fun buildProductDetailIntent(product: Product): Intent {
        return Intent(this, ProductDetailActivity::class.java).apply {
            putExtra("item_code", product.itemCode)
            putExtra("barcode", product.barcode)
            putExtra("article_no", product.articleNo)
            putExtra("description", product.description)
            putExtra("qoh", product.qoh)
            putExtra("price", product.formattedPrice)
            putExtra("on_order", product.isOnOrder)
            putExtra("item_status", product.itemStatus)
            putExtra("pack_size", product.packSize)
            putExtra("department", product.department)
            putExtra("sub_department", product.subDepartment)
            putExtra("category", product.category)
            putExtra("effective_price", product.formattedEffectivePrice)
            putExtra("promo_price", product.formattedPromoPrice)
            putExtra("promo_date_from", product.promoDateFrom)
            putExtra("promo_date_to", product.promoDateTo)
            putExtra("promo_saving", product.promoSaving)
            putExtra("promo_flag", product.promoFlag)
            putExtra("vendor_name", product.vendorName)
            putExtra("last_gr_date", product.lastGrDate)
            putExtra("last_gr_qty", product.lastGrQty)
            putExtra("last_cost", product.lastCost)
            putExtra("average_cost", product.averageCost)
        }
    }

    private fun lookupBarcode(barcode: String) {
        lifecycleScope.launch {
            val product = withContext(Dispatchers.IO) {
                repository.findByBarcode(prefsManager.selectedOutlet, barcode)
                    ?: repository.findByItemCode(prefsManager.selectedOutlet, barcode)
            }

            if (product != null) {
                startActivity(buildProductDetailIntent(product))
            } else {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Scan Failed")
                    .setMessage("Product not found for barcode: $barcode")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
