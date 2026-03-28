package com.outletscanner.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.Code128Writer
import com.google.zxing.oned.EAN13Writer
import com.outletscanner.app.data.model.Product
import java.io.File
import java.io.FileOutputStream

object PdfLabelGenerator {

    // A5 size in points (72 points per inch): 5.83 x 8.27 inches
    private const val A5_WIDTH = 420 // ~5.83 inches * 72
    private const val A5_HEIGHT = 595 // ~8.27 inches * 72

    /**
     * Generate an A5 PDF shelf label for a product.
     * Returns the URI of the saved file.
     */
    fun generateLabel(context: Context, product: Product): Uri? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(A5_WIDTH, A5_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawLabel(canvas, product, A5_WIDTH, A5_HEIGHT)

        document.finishPage(page)

        val uri = savePdf(context, document, product)
        document.close()

        return uri
    }

    private fun drawLabel(canvas: Canvas, product: Product, width: Int, height: Int) {
        val bgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#0D47A1")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(15f, 15f, width - 15f, height - 15f, borderPaint)

        var yPos = 60f
        val leftMargin = 30f
        val contentWidth = width - 60f

        // Item Code
        val labelPaint = Paint().apply {
            color = Color.parseColor("#757575")
            textSize = 16f
            isAntiAlias = true
        }
        canvas.drawText("Item Code", leftMargin, yPos, labelPaint)
        yPos += 24f

        val valuePaint = Paint().apply {
            color = Color.BLACK
            textSize = 22f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText(product.itemCode, leftMargin, yPos, valuePaint)
        yPos += 40f

        // Barcode image
        val barcodeBitmap = generateBarcodeBitmap(product.barcode, (contentWidth).toInt(), 80)
        if (barcodeBitmap != null) {
            canvas.drawBitmap(barcodeBitmap, leftMargin, yPos, null)
            yPos += 90f
        }

        // Barcode number
        val barcodeTextPaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(product.barcode, width / 2f, yPos, barcodeTextPaint)
        yPos += 50f

        // Divider line
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1f
        }
        canvas.drawLine(leftMargin, yPos, width - leftMargin, yPos, dividerPaint)
        yPos += 30f

        // Description
        canvas.drawText("Description", leftMargin, yPos, labelPaint)
        yPos += 26f

        val descPaint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            isAntiAlias = true
            isFakeBoldText = true
        }

        // Word wrap description
        val descWords = product.description.split(" ")
        var currentLine = StringBuilder()
        for (word in descWords) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (descPaint.measureText(testLine) > contentWidth) {
                canvas.drawText(currentLine.toString(), leftMargin, yPos, descPaint)
                yPos += 26f
                currentLine = StringBuilder(word)
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine.toString(), leftMargin, yPos, descPaint)
            yPos += 50f
        }

        // Divider
        canvas.drawLine(leftMargin, yPos, width - leftMargin, yPos, dividerPaint)
        yPos += 40f

        // Price - large and prominent
        canvas.drawText("Price", leftMargin, yPos, labelPaint)
        yPos += 10f

        val pricePaint = Paint().apply {
            color = Color.parseColor("#0D47A1")
            textSize = 72f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        yPos += 70f
        canvas.drawText(product.formattedPrice, width / 2f, yPos, pricePaint)
    }

    /**
     * Generate a proper barcode bitmap using ZXing library.
     * Tries EAN-13 first (for 13-digit barcodes), falls back to Code 128.
     */
    private fun generateBarcodeBitmap(barcodeText: String, width: Int, height: Int): Bitmap? {
        if (barcodeText.isBlank()) return null

        try {
            val bitMatrix = try {
                // Try EAN-13 for 13-digit numeric barcodes
                if (barcodeText.length == 13 && barcodeText.all { it.isDigit() }) {
                    EAN13Writer().encode(barcodeText, BarcodeFormat.EAN_13, width, height)
                } else {
                    Code128Writer().encode(barcodeText, BarcodeFormat.CODE_128, width, height)
                }
            } catch (e: Exception) {
                // Fallback to Code 128 for any barcode
                Code128Writer().encode(barcodeText, BarcodeFormat.CODE_128, width, height)
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            return bitmap
        } catch (e: Exception) {
            return null
        }
    }

    private fun savePdf(context: Context, document: PdfDocument, product: Product): Uri? {
        val fileName = "ShelfLabel_${product.itemCode}_${System.currentTimeMillis()}.pdf"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    document.writeTo(outputStream)
                }
            }

            uri
        } else {
            // Legacy storage for older devices
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { outputStream ->
                document.writeTo(outputStream)
            }
            Uri.fromFile(file)
        }
    }
}
