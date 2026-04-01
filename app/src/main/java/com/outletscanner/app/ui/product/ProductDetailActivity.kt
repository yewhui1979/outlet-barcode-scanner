package com.outletscanner.app.ui.product

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.outletscanner.app.R
import com.outletscanner.app.data.model.Product
import com.outletscanner.app.data.repository.ProductRepository
import com.outletscanner.app.databinding.ActivityProductDetailBinding
import com.outletscanner.app.ui.scanner.ScannerActivity
import com.outletscanner.app.util.PdfLabelGenerator
import com.outletscanner.app.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private lateinit var repository: ProductRepository
    private lateinit var prefsManager: PrefsManager

    // Current product data
    private var currentItemCode = ""
    private var currentBarcode = ""
    private var currentArticleNo = ""
    private var currentDescription = ""
    private var currentQoh = "0"
    private var currentPrice = "0.00"
    private var currentOnOrder = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ProductRepository(this)
        prefsManager = PrefsManager(this)

        loadFromIntent()
        setupToolbar()
        displayProductInfo()
        setupButtons()
    }

    private fun loadFromIntent() {
        currentItemCode = intent.getStringExtra("item_code") ?: ""
        currentBarcode = intent.getStringExtra("barcode") ?: ""
        currentArticleNo = intent.getStringExtra("article_no") ?: ""
        currentDescription = intent.getStringExtra("description") ?: ""
        currentQoh = intent.getStringExtra("qoh") ?: "0"
        currentPrice = intent.getStringExtra("price") ?: "0.00"
        currentOnOrder = intent.getBooleanExtra("on_order", false)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun displayProductInfo() {
        binding.tvItemCode.text = currentItemCode
        binding.tvBarcode.text = currentBarcode
        binding.tvDescription.text = currentDescription
        binding.tvQoh.text = currentQoh
        binding.tvPrice.text = currentPrice

        if (currentOnOrder) {
            binding.tvOnOrder.text = getString(R.string.yes)
            binding.tvOnOrder.setTextColor(ContextCompat.getColor(this, R.color.success_green))
        } else {
            binding.tvOnOrder.text = getString(R.string.no)
            binding.tvOnOrder.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun setupButtons() {
        binding.btnPrintLabel.setOnClickListener {
            generateAndShareLabel()
        }

        binding.btnScanAnother.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 1001)
        }
    }

    private fun generateAndShareLabel() {
        val product = Product(
            outlet = "",
            itemCode = currentItemCode,
            barcode = currentBarcode,
            articleNo = currentArticleNo,
            description = currentDescription,
            qoh = currentQoh,
            price = currentPrice,
            po = if (currentOnOrder) "1" else "0"
        )

        try {
            val uri = PdfLabelGenerator.generateLabel(this, product)
            if (uri != null) {
                Toast.makeText(this, R.string.label_saved, Toast.LENGTH_SHORT).show()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Print / Share Label"))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error generating label: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val scannedBarcode = data?.getStringExtra("barcode") ?: return
            lookupAndDisplay(scannedBarcode)
        }
    }

    private fun lookupAndDisplay(barcode: String) {
        val outlet = prefsManager.selectedOutlet

        lifecycleScope.launch {
            val product = withContext(Dispatchers.IO) {
                repository.findByBarcode(outlet, barcode)
                    ?: repository.findByItemCode(outlet, barcode)
            }

            if (product != null) {
                // Update current product data and refresh display
                currentItemCode = product.itemCode
                currentBarcode = product.barcode
                currentArticleNo = product.articleNo
                currentDescription = product.description
                currentQoh = product.qoh
                currentPrice = product.formattedPrice
                currentOnOrder = product.isOnOrder
                displayProductInfo()
            } else {
                Snackbar.make(
                    binding.root,
                    "${getString(R.string.no_product_found)}: $barcode",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
}
