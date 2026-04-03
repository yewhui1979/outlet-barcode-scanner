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
        val barcodeAreaWidth = 48f // Right area for barcode
        val textAreaWidth = width - barcodeAreaWidth - padding * 2

        // === LEFT SIDE: Description + Price ===
        val leftX = padding + 2f

        // Description (top left)
        val descPaint = Paint().apply {
            color = Color.BLACK
            textSize = 7f
            isAntiAlias = true
            isFakeBoldText = true
        }

        var yPos = padding + 8f

        // Word wrap description into multiple lines
        val descWords = product.description.split(" ")
        var currentLine = StringBuilder()
        var lineCount = 0
        for (word in descWords) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (descPaint.measureText(testLine) > textAreaWidth && currentLine.isNotEmpty()) {
                canvas.drawText(currentLine.toString(), leftX, yPos, descPaint)
                yPos += 9f
                currentLine = StringBuilder(word)
                lineCount++
                if (lineCount >= 2) break // Max 2 lines for description
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty() && lineCount < 3) {
            canvas.drawText(currentLine.toString(), leftX, yPos, descPaint)
            yPos += 4f
        }

        // Price - LARGE and bold (right after description)
        val pricePaint = Paint().apply {
            color = Color.BLACK
            textSize = 30f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(product.formattedPrice, leftX, height - padding - 16f, pricePaint)

        // "RM" currency label (below price)
        val rmPaint = Paint().apply {
            color = Color.BLACK
            textSize = 6f
            isAntiAlias = true
        }
        canvas.drawText("RM", leftX, height - padding - 8f, rmPaint)

        // Date (bottom of label)
        val datePaint = Paint().apply {
            color = Color.BLACK
            textSize = 5f
            isAntiAlias = true
        }
        val dateStr = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
        canvas.drawText(dateStr, leftX, height - padding, datePaint)

        // === RIGHT SIDE: Barcode (rotated 90° counter-clockwise) ===
        val barcodeStartX = width - barcodeAreaWidth
        val barcodeBitmap = generateBarcodeBitmap(product.barcode, 70, 30)
        if (barcodeBitmap != null) {
            val matrix = Matrix()
            matrix.postRotate(-90f)
            val rotatedBarcode = Bitmap.createBitmap(
                barcodeBitmap, 0, 0,
                barcodeBitmap.width, barcodeBitmap.height,
                matrix, true
            )
            val barcodeX = barcodeStartX + 2f
            val barcodeY = padding + 2f
            canvas.drawBitmap(rotatedBarcode, barcodeX, barcodeY, null)
            rotatedBarcode.recycle()
            barcodeBitmap.recycle()
        }

        // Barcode number (rotated, next to barcode)
        val barcodeNumPaint = Paint().apply {
            color = Color.BLACK
            textSize = 5f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        canvas.save()
        canvas.rotate(-90f, barcodeStartX + 36f, height / 2f)
        canvas.drawText(product.barcode, barcodeStartX + 36f - 30f, height / 2f + 2f, barcodeNumPaint)
        canvas.restore()

        // Article number (rotated, next to barcode number)
        val articlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 5.5f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.save()
        canvas.rotate(-90f, barcodeStartX + 42f, height / 2f)
        canvas.drawText(product.articleNo, barcodeStartX + 42f - 15f, height / 2f + 2f, articlePaint)
        canvas.restore()
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

    // Bitmap label dimensions for 203 DPI thermal printer
    // Reduced width to fit within printable area of Rongta RPP320
    private const val BITMAP_WIDTH = 384   // ~48mm printable width
    private const val BITMAP_HEIGHT = 212  // proportional height for 6:3.3 ratio

    /**
     * Render the shelf label as a Bitmap for direct Bluetooth thermal printing.
     * Same layout as the PDF label but rendered at 203 DPI for the Rongta RPP320.
     * Size: 480 x 264 pixels (6cm x 3.3cm at 203 DPI)
     */
    fun renderLabelBitmap(product: Product): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // White background
        val bgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, BITMAP_WIDTH.toFloat(), BITMAP_HEIGHT.toFloat(), bgPaint)

        // Thin border
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(1f, 1f, BITMAP_WIDTH - 1f, BITMAP_HEIGHT - 1f, borderPaint)

        val padding = 8f
        val barcodeAreaWidth = 110f // Right area for barcode
        val textAreaWidth = BITMAP_WIDTH - barcodeAreaWidth - padding * 2

        // === LEFT SIDE: Description + Price ===
        val leftX = padding + 4f

        // Description (top left)
        val descPaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            isAntiAlias = true
            isFakeBoldText = true
        }

        var yPos = padding + 20f

        // Word wrap description into multiple lines
        val descWords = product.description.split(" ")
        var currentLine = StringBuilder()
        var lineCount = 0
        for (word in descWords) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (descPaint.measureText(testLine) > textAreaWidth && currentLine.isNotEmpty()) {
                canvas.drawText(currentLine.toString(), leftX, yPos, descPaint)
                yPos += 22f
                currentLine = StringBuilder(word)
                lineCount++
                if (lineCount >= 2) break
            } else {
                currentLine = StringBuilder(testLine)
            }
        }
        if (currentLine.isNotEmpty() && lineCount < 3) {
            canvas.drawText(currentLine.toString(), leftX, yPos, descPaint)
        }

        // Price - LARGE and bold
        val pricePaint = Paint().apply {
            color = Color.BLACK
            textSize = 65f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(product.formattedPrice, leftX, BITMAP_HEIGHT - padding - 36f, pricePaint)

        // "RM" currency label (below price)
        val rmPaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            isAntiAlias = true
        }
        canvas.drawText("RM", leftX, BITMAP_HEIGHT - padding - 18f, rmPaint)

        // Date (bottom of label)
        val datePaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            isAntiAlias = true
        }
        val dateStr = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
        canvas.drawText(dateStr, leftX, BITMAP_HEIGHT - padding - 2f, datePaint)

        // === RIGHT SIDE: Barcode (rotated 90 CCW) ===
        val barcodeStartX = BITMAP_WIDTH - barcodeAreaWidth
        // Larger barcode: 180 wide x 60 tall, when rotated becomes 60 wide x 180 tall
        val barcodeBitmap = generateBarcodeBitmap(product.barcode, 180, 60)
        if (barcodeBitmap != null) {
            val matrix = Matrix()
            matrix.postRotate(-90f)
            val rotatedBarcode = Bitmap.createBitmap(
                barcodeBitmap, 0, 0,
                barcodeBitmap.width, barcodeBitmap.height,
                matrix, true
            )
            // Center the barcode vertically in the right area
            val barcodeX = barcodeStartX + 4f
            val barcodeY = (BITMAP_HEIGHT - rotatedBarcode.height) / 2f
            canvas.drawBitmap(rotatedBarcode, barcodeX, barcodeY, null)
            rotatedBarcode.recycle()
            barcodeBitmap.recycle()
        }

        // Barcode number (rotated, next to barcode)
        val barcodeNumPaint = Paint().apply {
            color = Color.BLACK
            textSize = 13f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        canvas.save()
        canvas.rotate(-90f, barcodeStartX + 74f, BITMAP_HEIGHT / 2f)
        canvas.drawText(product.barcode, barcodeStartX + 74f - 80f, BITMAP_HEIGHT / 2f + 4f, barcodeNumPaint)
        canvas.restore()

        // Article number (rotated, next to barcode number)
        val articlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.save()
        canvas.rotate(-90f, barcodeStartX + 92f, BITMAP_HEIGHT / 2f)
        canvas.drawText(product.articleNo, barcodeStartX + 92f - 40f, BITMAP_HEIGHT / 2f + 4f, articlePaint)
        canvas.restore()

        return bitmap
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
