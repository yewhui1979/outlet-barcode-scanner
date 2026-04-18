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

    // Product fields
    private var fOutlet = ""
    private var fItemCode = ""
    private var fItemLink = ""
    private var fBarcode = ""
    private var fArticleNo = ""
    private var fDescription = ""
    private var fItemStatus = ""
    private var fPackSize = ""
    private var fBulkQty = ""
    private var fQoh = "0"
    private var fDepartment = ""
    private var fSubDepartment = ""
    private var fCategory = ""
    private var fPrice = "0.00"
    private var fPromoId = ""
    private var fPromoDateFrom = ""
    private var fPromoDateTo = ""
    private var fPromoPrice = ""
    private var fPromoFlag = ""
    private var fPromoSaving = ""
    private var fEffectivePrice = ""
    private var fRetailExt = ""
    private var fFifoCost = ""
    private var fFifoTotal = ""
    private var fFifoGp = ""
    private var fLastCost = ""
    private var fLastCostTotal = ""
    private var fLastCostGp = ""
    private var fAverageCost = ""
    private var fListedCost = ""
    private var fCpo = "0"
    private var fSo = "0"
    private var fIbt = "0"
    private var fDn = "0"
    private var fCn = "0"
    private var fPos = "0"
    private var fMinQty = ""
    private var fMaxQty = ""
    private var fQtyPo = ""
    private var fQtyReq = ""
    private var fQtyTbr = ""
    private var fLastGrQty = ""
    private var fLastGrDate = ""
    private var fLastGrVendor = ""
    private var fVendorName = ""

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
        setupCostVisibility()
        setupButtons()
    }

    private fun loadFromIntent() {
        fOutlet = intent.getStringExtra("outlet") ?: prefsManager.selectedOutlet
        fItemCode = intent.getStringExtra("item_code") ?: ""
        fItemLink = intent.getStringExtra("item_link") ?: ""
        fBarcode = intent.getStringExtra("barcode") ?: ""
        fArticleNo = intent.getStringExtra("article_no") ?: ""
        fDescription = intent.getStringExtra("description") ?: ""
        fItemStatus = intent.getStringExtra("item_status") ?: ""
        fPackSize = intent.getStringExtra("pack_size") ?: ""
        fBulkQty = intent.getStringExtra("bulk_qty") ?: ""
        fQoh = intent.getStringExtra("qoh") ?: "0"
        fDepartment = intent.getStringExtra("department") ?: ""
        fSubDepartment = intent.getStringExtra("sub_department") ?: ""
        fCategory = intent.getStringExtra("category") ?: ""
        fPrice = intent.getStringExtra("price") ?: "0.00"
        fPromoId = intent.getStringExtra("promo_id") ?: ""
        fPromoDateFrom = intent.getStringExtra("promo_date_from") ?: ""
        fPromoDateTo = intent.getStringExtra("promo_date_to") ?: ""
        fPromoPrice = intent.getStringExtra("promo_price") ?: ""
        fPromoFlag = intent.getStringExtra("promo_flag") ?: ""
        fPromoSaving = intent.getStringExtra("promo_saving") ?: ""
        fEffectivePrice = intent.getStringExtra("effective_price") ?: ""
        fRetailExt = intent.getStringExtra("retail_ext") ?: ""
        fFifoCost = intent.getStringExtra("fifo_cost") ?: ""
        fFifoTotal = intent.getStringExtra("fifo_total") ?: ""
        fFifoGp = intent.getStringExtra("fifo_gp") ?: ""
        fLastCost = intent.getStringExtra("last_cost") ?: ""
        fLastCostTotal = intent.getStringExtra("last_cost_total") ?: ""
        fLastCostGp = intent.getStringExtra("last_cost_gp") ?: ""
        fAverageCost = intent.getStringExtra("average_cost") ?: ""
        fListedCost = intent.getStringExtra("listed_cost") ?: ""
        fCpo = intent.getStringExtra("cpo") ?: "0"
        fSo = intent.getStringExtra("so") ?: "0"
        fIbt = intent.getStringExtra("ibt") ?: "0"
        fDn = intent.getStringExtra("dn") ?: "0"
        fCn = intent.getStringExtra("cn") ?: "0"
        fPos = intent.getStringExtra("pos") ?: "0"
        fMinQty = intent.getStringExtra("min_qty") ?: ""
        fMaxQty = intent.getStringExtra("max_qty") ?: ""
        fQtyPo = intent.getStringExtra("qty_po") ?: ""
        fQtyReq = intent.getStringExtra("qty_req") ?: ""
        fQtyTbr = intent.getStringExtra("qty_tbr") ?: ""
        fLastGrQty = intent.getStringExtra("last_gr_qty") ?: ""
        fLastGrDate = intent.getStringExtra("last_gr_date") ?: ""
        fLastGrVendor = intent.getStringExtra("last_gr_vendor") ?: ""
        fVendorName = intent.getStringExtra("vendor_name") ?: ""
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun displayProductInfo() {
        // Basic Info
        binding.tvOutlet.text = fOutlet
        binding.tvDescription.text = fDescription
        binding.tvPrice.text = fPrice
        binding.tvQoh.text = fQoh
        binding.tvMinQty.text = fMinQty

        // Promotion
        binding.tvPromoDateFrom.text = fPromoDateFrom
        binding.tvPromoDateTo.text = fPromoDateTo
        binding.tvPromoPrice.text = fPromoPrice

        // Pending PO
        binding.tvQtyPo.text = fQtyPo
        binding.tvQtyReq.text = fQtyReq
        binding.tvQtyTbr.text = fQtyTbr

        // Supply Info
        binding.tvLastGrQty.text = fLastGrQty
        binding.tvLastGrDate.text = fLastGrDate
        binding.tvLastGrVendor.text = fLastGrVendor
        binding.tvVendorName.text = fVendorName

        // SKU Info
        binding.tvBarcode.text = fBarcode
        binding.tvArticleNo.text = fArticleNo
        binding.tvPackSize.text = fPackSize
        binding.tvBulkQty.text = fBulkQty
        binding.tvDepartment.text = fDepartment
        binding.tvSubDepartment.text = fSubDepartment
        binding.tvCategory.text = fCategory

        // Item Status with color
        binding.tvItemStatus.text = fItemStatus
        when (fItemStatus.lowercase()) {
            "active" -> binding.tvItemStatus.setTextColor(0xFF2E7D32.toInt())
            "disabled", "delisted" -> binding.tvItemStatus.setTextColor(0xFFC62828.toInt())
            else -> binding.tvItemStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }

        // Cost Info
        binding.tvFifoCost.text = fFifoCost
        binding.tvAvgCost.text = fAverageCost
        binding.tvLastCost.text = fLastCost
    }

    private fun setupCostVisibility() {
        val role = prefsManager.currentRole
        val canSeeCost = role == "admin" || role == "superuser" || role == "buyer"
        binding.cardCostInfo.visibility = if (canSeeCost) View.VISIBLE else View.GONE
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
        if (printerManager.isConnected()) {
            printViaBluetoothDirectly()
            return
        }

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
                    Toast.makeText(this@ProductDetailActivity, "Label printed successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ProductDetailActivity, "Failed to print label", Toast.LENGTH_LONG).show()
                }
            } else {
                progressDialog.dismiss()
                Toast.makeText(this@ProductDetailActivity, "Failed to connect to printer", Toast.LENGTH_LONG).show()
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
                Toast.makeText(this@ProductDetailActivity, "Label printed successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ProductDetailActivity, "Failed to print label. Try reconnecting.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun buildCurrentProduct(): Product {
        return Product(
            outlet = fOutlet,
            itemCode = fItemCode,
            barcode = fBarcode,
            articleNo = fArticleNo,
            description = fDescription,
            qoh = fQoh,
            price = fPrice,
            po = fQtyPo,
            effectivePrice = fEffectivePrice,
            promoPrice = fPromoPrice,
            promoDateFrom = fPromoDateFrom,
            promoDateTo = fPromoDateTo,
            promoSaving = fPromoSaving,
            promoFlag = fPromoFlag
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
                setupCostVisibility()
            } else {
                MaterialAlertDialogBuilder(this@ProductDetailActivity)
                    .setTitle("Scan Failed")
                    .setMessage("Product not found for barcode: $barcode")
                    .setPositiveButton("OK") { _, _ ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun loadFromProduct(product: Product) {
        fOutlet = product.outlet
        fItemCode = product.itemCode
        fItemLink = product.itemLink
        fBarcode = product.barcode
        fArticleNo = product.articleNo
        fDescription = product.description
        fItemStatus = product.itemStatus
        fPackSize = product.packSize
        fBulkQty = product.bulkQty
        fQoh = product.qoh
        fDepartment = product.department
        fSubDepartment = product.subDepartment
        fCategory = product.category
        fPrice = product.formattedPrice
        fPromoId = product.promoId
        fPromoDateFrom = product.promoDateFrom
        fPromoDateTo = product.promoDateTo
        fPromoPrice = product.promoPrice
        fPromoFlag = product.promoFlag
        fPromoSaving = product.promoSaving
        fEffectivePrice = product.effectivePrice
        fRetailExt = product.retailExt
        fFifoCost = product.fifoCost
        fFifoTotal = product.fifoTotal
        fFifoGp = product.fifoGp
        fLastCost = product.lastCost
        fLastCostTotal = product.lastCostTotal
        fLastCostGp = product.lastCostGp
        fAverageCost = product.averageCost
        fListedCost = product.listedCost
        fCpo = product.cpo
        fSo = product.so
        fIbt = product.ibt
        fDn = product.dn
        fCn = product.cn
        fPos = product.pos
        fMinQty = product.minQty
        fMaxQty = product.maxQty
        fQtyPo = product.po
        fQtyReq = product.qtyReq
        fQtyTbr = product.qtyTbr
        fLastGrQty = product.lastGrQty
        fLastGrDate = product.lastGrDate
        fLastGrVendor = product.lastGrVendor
        fVendorName = product.vendorName
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
