package com.outletscanner.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfLabelGenerator {

    // Label size: 6cm x 3.3cm converted to points (1cm = 28.35 points)
    private const val LABEL_WIDTH = 170   // 6cm * 28.35 ≈ 170 points
    private const val LABEL_HEIGHT = 94   // 3.3cm * 28.35 ≈ 94 points

    /**
     * Generate a shelf label PDF matching the client's retail label format.
     * Size: 6cm x 3.3cm
     * Layout: Barcode (rotated vertical) on left, description + price on right
     */
    fun generateLabel(context: Context, product: Product): Uri? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(LABEL_WIDTH, LABEL_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawShelfLabel(canvas, product, LABEL_WIDTH, LABEL_HEIGHT)

        document.finishPage(page)

        val uri = savePdf(context, document, product)
        document.close()

        return uri
    }

    private fun drawShelfLabel(canvas: Canvas, product: Product, width: Int, height: Int) {
        // White background
        val bgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Thin border
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        canvas.drawRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, borderPaint)

        val padding = 4f
        val barcodeAreaWidth = 45f // Left area for barcode

        // === LEFT SIDE: Barcode (rotated 90° counter-clockwise) ===
        val barcodeBitmap = generateBarcodeBitmap(product.barcode, 70, 30)
        if (barcodeBitmap != null) {
            // Rotate barcode 90° counter-clockwise
            val matrix = Matrix()
            matrix.postRotate(-90f)
            val rotatedBarcode = Bitmap.createBitmap(
                barcodeBitmap, 0, 0,
                barcodeBitmap.width, barcodeBitmap.height,
                matrix, true
            )
            // Draw rotated barcode on left side
            val barcodeX = padding + 2f
            val barcodeY = padding + 2f
            canvas.drawBitmap(rotatedBarcode, barcodeX, barcodeY, null)
            rotatedBarcode.recycle()
            barcodeBitmap.recycle()
        }

        // Barcode number (rotated, below barcode)
        val barcodeNumPaint = Paint().apply {
            color = Color.BLACK
            textSize = 5f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        canvas.save()
        canvas.rotate(-90f, padding + 38f, height / 2f)
        canvas.drawText(product.barcode, padding + 38f - 30f, height / 2f + 2f, barcodeNumPaint)
        canvas.restore()

        // Article number below barcode number
        val articlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 5.5f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.save()
        canvas.rotate(-90f, padding + 43f, height / 2f)
        canvas.drawText(product.articleNo, padding + 43f - 15f, height / 2f + 2f, articlePaint)
        canvas.restore()

        // === RIGHT SIDE: Description + Price ===
        val rightX = barcodeAreaWidth + 4f
        val rightWidth = width - rightX - padding

        // Description (top right)
        val descPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7f
            isAntiAlias = true
            isFakeBoldText = true
        }

        var yPos = padding + 10f

        // Word wrap description into multiple lines
        val descWords = product.description.split(" ")
        var currentLine = StringBuilder()
        var lineCount = 0
        for (word in descWords) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (descPaint.measureText(testLine) > rightWidth && currentLine.isNotEmpty()) {
                canvas.drawText(currentLine.toString(), rightX, yPos, descPaint)
                yPos += 9f
                currentLine = StringBuilder(word)
                lineCount++
                if (lineCount >= 2) break // Max 2 lines for description
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty() && lineCount < 3) {
            canvas.drawText(currentLine.toString(), rightX, yPos, descPaint)
            yPos += 12f
        }

        // Date (top right corner)
        val datePaint = Paint().apply {
            color = Color.BLACK
            textSize = 5f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val dateStr = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
        canvas.drawText(dateStr, width - padding, padding + 8f, datePaint)

        // "RM" currency label (top right area, above price)
        val rmPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("RM", width - padding, yPos + 4f, rmPaint)

        // Price - LARGE and bold
        val pricePaint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val priceX = rightX + rightWidth / 2f
        yPos += 28f
        canvas.drawText(product.formattedPrice, priceX, yPos, pricePaint)
    }

    /**
     * Generate a proper barcode bitmap using ZXing library.
     * Tries EAN-13 first (for 13-digit barcodes), falls back to Code 128.
     */
    private fun generateBarcodeBitmap(barcodeText: String, width: Int, height: Int): Bitmap? {
        if (barcodeText.isBlank()) return null

        try {
            val bitMatrix = try {
                if (barcodeText.length == 13 && barcodeText.all { it.isDigit() }) {
                    EAN13Writer().encode(barcodeText, BarcodeFormat.EAN_13, width, height)
                } else {
                    Code128Writer().encode(barcodeText, BarcodeFormat.CODE_128, width, height)
                }
            } catch (e: Exception) {
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
