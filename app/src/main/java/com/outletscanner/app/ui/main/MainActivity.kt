package com.outletscanner.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
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
import com.outletscanner.app.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsManager: PrefsManager
    private lateinit var repository: ProductRepository

    companion object {
        private const val REQUEST_SCAN = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PrefsManager(this)
        repository = ProductRepository(this)

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

            val barcodeCount = withContext(Dispatchers.IO) {
                repository.getBarcodeMappingCount()
            }
            binding.tvBarcodeCount.text = if (barcodeCount > 0) {
                "Barcode mappings: $barcodeCount"
            } else {
                "No barcode mappings loaded"
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
            putExtra("outlet", product.outlet)
            putExtra("item_code", product.itemCode)
            putExtra("item_link", product.itemLink)
            putExtra("barcode", product.barcode)
            putExtra("article_no", product.articleNo)
            putExtra("description", product.description)
            putExtra("item_status", product.itemStatus)
            putExtra("pack_size", product.packSize)
            putExtra("bulk_qty", product.bulkQty)
            putExtra("qoh", product.qoh)
            putExtra("department", product.department)
            putExtra("sub_department", product.subDepartment)
            putExtra("category", product.category)
            putExtra("price", product.formattedPrice)
            putExtra("promo_id", product.promoId)
            putExtra("promo_date_from", product.promoDateFrom)
            putExtra("promo_date_to", product.promoDateTo)
            putExtra("promo_price", product.promoPrice)
            putExtra("promo_flag", product.promoFlag)
            putExtra("promo_saving", product.promoSaving)
            putExtra("effective_price", product.effectivePrice)
            putExtra("retail_ext", product.retailExt)
            putExtra("fifo_cost", product.fifoCost)
            putExtra("fifo_total", product.fifoTotal)
            putExtra("fifo_gp", product.fifoGp)
            putExtra("last_cost", product.lastCost)
            putExtra("last_cost_total", product.lastCostTotal)
            putExtra("last_cost_gp", product.lastCostGp)
            putExtra("average_cost", product.averageCost)
            putExtra("listed_cost", product.listedCost)
            putExtra("cpo", product.cpo)
            putExtra("so", product.so)
            putExtra("ibt", product.ibt)
            putExtra("dn", product.dn)
            putExtra("cn", product.cn)
            putExtra("pos", product.pos)
            putExtra("min_qty", product.minQty)
            putExtra("max_qty", product.maxQty)
            putExtra("qty_po", product.po)
            putExtra("qty_req", product.qtyReq)
            putExtra("qty_tbr", product.qtyTbr)
            putExtra("last_gr_qty", product.lastGrQty)
            putExtra("last_gr_date", product.lastGrDate)
            putExtra("last_gr_vendor", product.lastGrVendor)
            putExtra("vendor_name", product.vendorName)
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
