package com.outletscanner.app.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.outletscanner.app.R
import com.outletscanner.app.databinding.ActivityScannerBinding
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner
    private val isScanned = AtomicBoolean(false)
    private val zxingReader = MultiFormatReader()
    private var lastFrameTime = 0L
    private val FRAME_INTERVAL_MS = 500L // Process frames every 500ms to reduce sensitivity

    companion object {
        private const val TAG = "ScannerActivity"
        private const val CAMERA_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Configure ML Kit barcode scanner - allow ALL formats for maximum compatibility
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX
            )
            .build()

        barcodeScanner = BarcodeScanning.getClient(options)

        // Configure ZXing as fallback
        val hints = mapOf(
            com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(
                com.google.zxing.BarcodeFormat.EAN_13,
                com.google.zxing.BarcodeFormat.EAN_8,
                com.google.zxing.BarcodeFormat.UPC_A,
                com.google.zxing.BarcodeFormat.UPC_E,
                com.google.zxing.BarcodeFormat.CODE_128,
                com.google.zxing.BarcodeFormat.CODE_39,
                com.google.zxing.BarcodeFormat.CODE_93,
                com.google.zxing.BarcodeFormat.ITF,
                com.google.zxing.BarcodeFormat.CODABAR,
                com.google.zxing.BarcodeFormat.QR_CODE
            ),
            com.google.zxing.DecodeHintType.TRY_HARDER to true
        )
        zxingReader.setHints(hints)

        binding.btnClose.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        // Manual barcode entry
        binding.btnManualSubmit.setOnClickListener {
            val manualBarcode = binding.etManualBarcode.text.toString().trim()
            if (manualBarcode.isNotBlank() && isScanned.compareAndSet(false, true)) {
                onBarcodeDetected(manualBarcode)
            }
        }

        binding.etManualBarcode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                binding.btnManualSubmit.performClick()
                true
            } else false
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }

                // Use a reasonable resolution for barcode scanning
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImage(imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                // Enable auto-focus
                camera.cameraControl.enableTorch(false)

            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                Toast.makeText(this, "Camera failed to start: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        if (isScanned.get()) {
            imageProxy.close()
            return
        }

        // Throttle frame processing to reduce sensitivity
        val now = System.currentTimeMillis()
        if (now - lastFrameTime < FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastFrameTime = now

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // Try ML Kit first
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    // Reject if multiple barcodes detected (ambiguous scan)
                    val validBarcodes = barcodes.filter { !it.rawValue.isNullOrBlank() }
                    if (validBarcodes.size > 1) {
                        Log.w(TAG, "Multiple barcodes detected (${validBarcodes.size}), rejecting")
                        return@addOnSuccessListener
                    }

                    if (validBarcodes.size == 1) {
                        val value = validBarcodes[0].rawValue
                        if (!value.isNullOrBlank() && isScanned.compareAndSet(false, true)) {
                            Log.d(TAG, "ML Kit detected barcode: $value")
                            onBarcodeDetected(value)
                            return@addOnSuccessListener
                        }
                    }

                    // ML Kit didn't find anything, try ZXing as fallback
                    if (!isScanned.get()) {
                        tryZxingDecode(imageProxy)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit scan failed, trying ZXing", e)
                    // ML Kit failed, try ZXing
                    if (!isScanned.get()) {
                        tryZxingDecode(imageProxy)
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun tryZxingDecode(imageProxy: ImageProxy) {
        try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val width = imageProxy.width
            val height = imageProxy.height

            // Crop to center region matching the blue guide lines
            // Scan only the middle 60% width and 40% height of the frame
            val cropWidth = (width * 0.6).toInt()
            val cropHeight = (height * 0.4).toInt()
            val cropLeft = (width - cropWidth) / 2
            val cropTop = (height - cropHeight) / 2

            val source = PlanarYUVLuminanceSource(
                bytes, width, height,
                cropLeft, cropTop, cropWidth, cropHeight,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = zxingReader.decodeWithState(binaryBitmap)

            if (result != null && result.text.isNotBlank() && isScanned.compareAndSet(false, true)) {
                Log.d(TAG, "ZXing detected barcode: ${result.text}")
                onBarcodeDetected(result.text)
            }
        } catch (e: Exception) {
            // No barcode found in this frame - normal
        } finally {
            zxingReader.reset()
        }
    }

    private fun onBarcodeDetected(barcode: String) {
        runOnUiThread {
            // Vibrate feedback
            try {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            } catch (_: Exception) {}

            val resultIntent = Intent().apply {
                putExtra("barcode", barcode)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        barcodeScanner.close()
    }
}
