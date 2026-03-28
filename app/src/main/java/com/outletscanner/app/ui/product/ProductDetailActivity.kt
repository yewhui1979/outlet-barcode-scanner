package com.outletscanner.app.ui.product

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.outletscanner.app.R
import com.outletscanner.app.data.model.Product
import com.outletscanner.app.databinding.ActivityProductDetailBinding
import com.outletscanner.app.ui.scanner.ScannerActivity
import com.outletscanner.app.util.PdfLabelGenerator

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        displayProductInfo()
        setupButtons()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun displayProductInfo() {
        val itemCode = intent.getStringExtra("item_code") ?: ""
        val barcode = intent.getStringExtra("barcode") ?: ""
        val description = intent.getStringExtra("description") ?: ""
        val qoh = intent.getStringExtra("qoh") ?: "0"
        val price = intent.getStringExtra("price") ?: "0.00"
        val onOrder = intent.getBooleanExtra("on_order", false)

        binding.tvItemCode.text = itemCode
        binding.tvBarcode.text = barcode
        binding.tvDescription.text = description
        binding.tvQoh.text = qoh
        binding.tvPrice.text = price

        if (onOrder) {
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
            itemCode = intent.getStringExtra("item_code") ?: "",
            barcode = intent.getStringExtra("barcode") ?: "",
            description = intent.getStringExtra("description") ?: "",
            qoh = intent.getStringExtra("qoh") ?: "0",
            price = intent.getStringExtra("price") ?: "0.00",
            po = if (intent.getBooleanExtra("on_order", false)) "1" else "0"
        )

        try {
            val uri = PdfLabelGenerator.generateLabel(this, product)
            if (uri != null) {
                Toast.makeText(this, R.string.label_saved, Toast.LENGTH_SHORT).show()

                // Offer to share/print
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
            // Return the new barcode to MainActivity for lookup
            val resultIntent = Intent().apply {
                putExtra("barcode", scannedBarcode)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}
