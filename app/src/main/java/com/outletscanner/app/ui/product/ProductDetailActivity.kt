package com.outletscanner.app.ui.product

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.outletscanner.app.R
import com.outletscanner.app.data.model.Product
import com.outletscanner.app.data.repository.ProductRepository
import com.outletscanner.app.databinding.ActivityProductDetailBinding
import com.outletscanner.app.ui.scanner.ScannerActivity
import com.outletscanner.app.util.BluetoothPrinterManager
import com.outletscanner.app.util.PdfLabelGenerator
import com.outletscanner.app.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private lateinit var repository: ProductRepository
    private lateinit var prefsManager: PrefsManager
    private lateinit var printerManager: BluetoothPrinterManager

    // Current product data
    private var currentItemCode = ""
    private var currentBarcode = ""
    private var currentArticleNo = ""
    private var currentDescription = ""
    private var currentQoh = "0"
    private var currentPrice = "0.00"
    private var currentOnOrder = false
    private var currentItemStatus = ""
    private var currentPackSize = ""
    private var currentDepartment = ""
    private var currentSubDepartment = ""
    private var currentCategory = ""
    private var currentEffectivePrice = ""
    private var currentPromoPrice = ""
    private var currentPromoDateFrom = ""
    private var currentPromoDateTo = ""
    private var currentPromoSaving = ""
    private var currentPromoFlag = "N"
    private var currentVendorName = ""
    private var currentLastGrDate = ""
    private var currentLastGrQty = ""
    private var currentLastCost = ""
    private var currentAverageCost = ""

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            showBluetoothPrinterDialog()
        } else {
            Toast.makeText(this, "Bluetooth permission required for printing", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ProductRepository(this)
        prefsManager = PrefsManager(this)
        printerManager = BluetoothPrinterManager(this)

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
        currentItemStatus = intent.getStringExtra("item_status") ?: ""
        currentPackSize = intent.getStringExtra("pack_size") ?: ""
        currentDepartment = intent.getStringExtra("department") ?: ""
        currentSubDepartment = intent.getStringExtra("sub_department") ?: ""
        currentCategory = intent.getStringExtra("category") ?: ""
        currentEffectivePrice = intent.getStringExtra("effective_price") ?: ""
        currentPromoPrice = intent.getStringExtra("promo_price") ?: ""
        currentPromoDateFrom = intent.getStringExtra("promo_date_from") ?: ""
        currentPromoDateTo = intent.getStringExtra("promo_date_to") ?: ""
        currentPromoSaving = intent.getStringExtra("promo_saving") ?: ""
        currentPromoFlag = intent.getStringExtra("promo_flag") ?: "N"
        currentVendorName = intent.getStringExtra("vendor_name") ?: ""
        currentLastGrDate = intent.getStringExtra("last_gr_date") ?: ""
        currentLastGrQty = intent.getStringExtra("last_gr_qty") ?: ""
        currentLastCost = intent.getStringExtra("last_cost") ?: ""
        currentAverageCost = intent.getStringExtra("average_cost") ?: ""
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

        // Item Status badge
        if (currentItemStatus.isNotBlank()) {
            binding.tvItemStatus.text = currentItemStatus
            binding.tvItemStatus.visibility = View.VISIBLE
            when (currentItemStatus.lowercase()) {
                "active" -> {
                    binding.tvItemStatus.setTextColor(0xFF2E7D32.toInt())
                    binding.tvItemStatus.setBackgroundColor(0xFFE8F5E9.toInt())
                }
                "disabled", "delisted" -> {
                    binding.tvItemStatus.setTextColor(0xFFC62828.toInt())
                    binding.tvItemStatus.setBackgroundColor(0xFFFFEBEE.toInt())
                }
                else -> {
                    binding.tvItemStatus.setTextColor(0xFF757575.toInt())
                    binding.tvItemStatus.setBackgroundColor(0xFFF5F5F5.toInt())
                }
            }
        } else {
            binding.tvItemStatus.visibility = View.GONE
        }

        // Effective Price
        if (currentEffectivePrice.isNotBlank() && currentEffectivePrice != "0" && currentEffectivePrice != currentPrice) {
            binding.rowEffectivePrice.visibility = View.VISIBLE
            binding.tvEffectivePrice.text = currentEffectivePrice
        }

        // Promo card
        val isPromo = currentPromoFlag.trim().uppercase() == "Y" || currentPromoPrice.isNotBlank()
        if (isPromo) {
            binding.cardPromo.visibility = View.VISIBLE

            if (currentPromoPrice.isNotBlank()) {
                binding.rowPromoPrice.visibility = View.VISIBLE
                binding.tvPromoPrice.text = "RM $currentPromoPrice"
            }
            if (currentPromoDateFrom.isNotBlank() || currentPromoDateTo.isNotBlank()) {
                binding.rowPromoPeriod.visibility = View.VISIBLE
                binding.tvPromoPeriod.text = "$currentPromoDateFrom - $currentPromoDateTo"
            }
            if (currentPromoSaving.isNotBlank() && currentPromoSaving != "0" && currentPromoSaving != "0.0") {
                binding.rowPromoSaving.visibility = View.VISIBLE
                binding.tvPromoSaving.text = "RM $currentPromoSaving"
            }
        }

        // Classification card
        val hasClassification = currentDepartment.isNotBlank() || currentSubDepartment.isNotBlank() ||
                currentCategory.isNotBlank() || currentPackSize.isNotBlank()
        if (hasClassification) {
            binding.cardClassification.visibility = View.VISIBLE

            if (currentDepartment.isNotBlank()) {
                binding.rowDepartment.visibility = View.VISIBLE
                binding.tvDepartment.text = currentDepartment
            }
            if (currentSubDepartment.isNotBlank()) {
                binding.rowSubDepartment.visibility = View.VISIBLE
                binding.tvSubDepartment.text = currentSubDepartment
            }
            if (currentCategory.isNotBlank()) {
                binding.rowCategory.visibility = View.VISIBLE
                binding.tvCategory.text = currentCategory
            }
            if (currentPackSize.isNotBlank() && currentPackSize != "0" && currentPackSize != "1.0") {
                binding.rowPackSize.visibility = View.VISIBLE
                binding.tvPackSize.text = currentPackSize
            }
        }

        // Supply card
        val hasSupply = currentVendorName.isNotBlank() || currentLastGrDate.isNotBlank() ||
                currentLastCost.isNotBlank() || currentAverageCost.isNotBlank()
        if (hasSupply) {
            binding.cardSupply.visibility = View.VISIBLE

            if (currentVendorName.isNotBlank()) {
                binding.rowVendor.visibility = View.VISIBLE
                binding.tvVendor.text = currentVendorName
            }
            if (currentLastGrDate.isNotBlank() && !currentLastGrDate.startsWith("1000")) {
                binding.rowLastGrDate.visibility = View.VISIBLE
                binding.tvLastGrDate.text = currentLastGrDate
            }
            if (currentLastGrQty.isNotBlank() && currentLastGrQty != "0" && currentLastGrQty != "0.0") {
                binding.rowLastGrQty.visibility = View.VISIBLE
                binding.tvLastGrQty.text = currentLastGrQty
            }
            if (currentLastCost.isNotBlank() && currentLastCost != "0") {
                binding.rowLastCost.visibility = View.VISIBLE
                binding.tvLastCost.text = currentLastCost
            }
            if (currentAverageCost.isNotBlank() && currentAverageCost != "0") {
                binding.rowAvgCost.visibility = View.VISIBLE
                binding.tvAvgCost.text = currentAverageCost
            }
        }
    }

    private fun setupButtons() {
        binding.btnPrintLabel.setOnClickListener {
            showPrintOptionsDialog()
        }

        binding.btnScanAnother.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 1001)
        }
    }

    // ── Print Options Dialog ────────────────────────────────────────────

    private fun showPrintOptionsDialog() {
        val options = arrayOf(
            "Save as PDF",
            "Print to Bluetooth Printer"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Print Shelf Label")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> generateAndShareLabel()
                    1 -> startBluetoothPrint()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── PDF Label (existing behavior) ───────────────────────────────────

    private fun generateAndShareLabel() {
        val product = buildCurrentProduct()

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

    // ── Bluetooth Printing ──────────────────────────────────────────────

    private fun startBluetoothPrint() {
        if (!printerManager.isBluetoothAvailable()) {
            Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (!printerManager.isBluetoothEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }

        // Check permissions (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val neededPermissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (neededPermissions.isNotEmpty()) {
                bluetoothPermissionLauncher.launch(neededPermissions.toTypedArray())
                return
            }
        }

        showBluetoothPrinterDialog()
    }

    private fun showBluetoothPrinterDialog() {
        // If already connected, print directly
        if (printerManager.isConnected()) {
            printViaBluetoothDirectly()
            return
        }

        // If we have a saved printer, try to connect to it first
        val savedAddress = prefsManager.savedPrinterAddress
        val savedName = prefsManager.savedPrinterName
        if (savedAddress.isNotEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Bluetooth Printer")
                .setMessage("Connect to saved printer \"$savedName\"?")
                .setPositiveButton("Connect & Print") { _, _ ->
                    connectAndPrint(savedAddress)
                }
                .setNeutralButton("Choose Different") { _, _ ->
                    showDeviceSelectionDialog()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        showDeviceSelectionDialog()
    }

    @SuppressLint("MissingPermission")
    private fun showDeviceSelectionDialog() {
        val pairedDevices = printerManager.getPairedDevices()

        if (pairedDevices.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("No Paired Devices")
                .setMessage("No paired Bluetooth devices found. Please pair your printer in Android Bluetooth settings first.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val deviceNames = pairedDevices.map { device ->
            "${device.name ?: "Unknown"}\n${device.address}"
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Bluetooth Printer")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = pairedDevices[which]
                // Save selected printer
                prefsManager.savedPrinterAddress = selectedDevice.address
                prefsManager.savedPrinterName = selectedDevice.name ?: "Unknown"
                connectAndPrint(selectedDevice.address)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun connectAndPrint(address: String) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Bluetooth Printer")
            setMessage("Connecting to printer...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            val connected = printerManager.connectByAddress(address)

            if (connected) {
                progressDialog.setMessage("Printing label...")

                val product = buildCurrentProduct()
                val bitmap = withContext(Dispatchers.Default) {
                    PdfLabelGenerator.renderLabelBitmap(product)
                }

                val printed = printerManager.printBitmap(bitmap)
                bitmap.recycle()

                progressDialog.dismiss()

                if (printed) {
                    Toast.makeText(
                        this@ProductDetailActivity,
                        "Label printed successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@ProductDetailActivity,
                        "Failed to print label",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                progressDialog.dismiss()
                Toast.makeText(
                    this@ProductDetailActivity,
                    "Failed to connect to printer",
                    Toast.LENGTH_LONG
                ).show()
                // Clear saved printer since it failed
                showDeviceSelectionDialog()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun printViaBluetoothDirectly() {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Printing")
            setMessage("Printing label...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            val product = buildCurrentProduct()
            val bitmap = withContext(Dispatchers.Default) {
                PdfLabelGenerator.renderLabelBitmap(product)
            }

            val printed = printerManager.printBitmap(bitmap)
            bitmap.recycle()

            progressDialog.dismiss()

            if (printed) {
                Toast.makeText(
                    this@ProductDetailActivity,
                    "Label printed successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@ProductDetailActivity,
                    "Failed to print label. Try reconnecting.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun buildCurrentProduct(): Product {
        return Product(
            outlet = "",
            itemCode = currentItemCode,
            barcode = currentBarcode,
            articleNo = currentArticleNo,
            description = currentDescription,
            qoh = currentQoh,
            price = currentPrice,
            po = if (currentOnOrder) "1" else "0",
            effectivePrice = currentEffectivePrice,
            promoPrice = currentPromoPrice,
            promoDateFrom = currentPromoDateFrom,
            promoDateTo = currentPromoDateTo,
            promoSaving = currentPromoSaving,
            promoFlag = currentPromoFlag
        )
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
                loadFromProduct(product)
                displayProductInfo()
            } else {
                MaterialAlertDialogBuilder(this@ProductDetailActivity)
                    .setTitle("Scan Failed")
                    .setMessage("Product not found for barcode: $barcode")
                    .setPositiveButton("OK") { _, _ ->
                        finish() // Go back to main screen
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun loadFromProduct(product: Product) {
        currentItemCode = product.itemCode
        currentBarcode = product.barcode
        currentArticleNo = product.articleNo
        currentDescription = product.description
        currentQoh = product.qoh
        currentPrice = product.formattedPrice
        currentOnOrder = product.isOnOrder
        currentItemStatus = product.itemStatus
        currentPackSize = product.packSize
        currentDepartment = product.department
        currentSubDepartment = product.subDepartment
        currentCategory = product.category
        currentEffectivePrice = product.formattedEffectivePrice
        currentPromoPrice = product.formattedPromoPrice
        currentPromoDateFrom = product.promoDateFrom
        currentPromoDateTo = product.promoDateTo
        currentPromoSaving = product.promoSaving
        currentPromoFlag = product.promoFlag
        currentVendorName = product.vendorName
        currentLastGrDate = product.lastGrDate
        currentLastGrQty = product.lastGrQty
        currentLastCost = product.lastCost
        currentAverageCost = product.averageCost
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't disconnect on destroy - keep the connection alive for next print
    }
}
