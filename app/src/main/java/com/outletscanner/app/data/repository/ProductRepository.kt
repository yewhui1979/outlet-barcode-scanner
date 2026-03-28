package com.outletscanner.app.data.repository

import android.content.Context
import com.outletscanner.app.data.database.AppDatabase
import com.outletscanner.app.data.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class ProductRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.productDao()

    suspend fun findByBarcode(outlet: String, barcode: String): Product? {
        return dao.findByBarcode(outlet, barcode)
    }

    suspend fun findByItemCode(outlet: String, itemCode: String): Product? {
        return dao.findByItemCode(outlet, itemCode)
    }

    suspend fun search(outlet: String, query: String): Product? {
        return dao.search(outlet, query)
    }

    suspend fun searchFuzzy(outlet: String, query: String): List<Product> {
        return dao.searchFuzzy(outlet, "%$query%")
    }

    suspend fun getItemCount(outlet: String): Int {
        return dao.getCountForOutlet(outlet)
    }

    suspend fun deleteByOutlet(outlet: String) {
        dao.deleteByOutlet(outlet)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    /**
     * Parse a pipe-delimited input stream and bulk insert into the database.
     * Designed for efficiency with 400K+ rows - uses batch inserts of 5000 rows.
     * Returns the total number of rows inserted.
     * The onProgress callback receives (rowsProcessed, estimatedTotal) where
     * estimatedTotal may be -1 if unknown.
     */
    suspend fun parseAndInsert(
        inputStream: InputStream,
        outlet: String,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        // First, clear existing data for this outlet
        dao.deleteByOutlet(outlet)

        val reader = BufferedReader(InputStreamReader(inputStream), 1024 * 64) // 64KB buffer
        var headerLine = reader.readLine() ?: return@withContext 0

        // Validate header
        val headers = headerLine.split("|").map { it.trim().lowercase() }
        val headerMap = headers.mapIndexed { index, name -> name to index }.toMap()

        val batch = mutableListOf<Product>()
        var totalInserted = 0
        val batchSize = 5000

        var line = reader.readLine()
        while (line != null) {
            if (line.isBlank()) {
                line = reader.readLine()
                continue
            }

            val fields = line.split("|")
            if (fields.size < 7) {
                line = reader.readLine()
                continue
            }

            try {
                val product = Product(
                    outlet = getField(fields, headerMap, "outlet", outlet),
                    itemCode = getField(fields, headerMap, "itemcode", ""),
                    itemLink = getField(fields, headerMap, "itemlink", ""),
                    barcode = getField(fields, headerMap, "barcode", ""),
                    articleNo = getField(fields, headerMap, "articleno", ""),
                    description = getField(fields, headerMap, "description", ""),
                    qoh = getField(fields, headerMap, "qoh", "0"),
                    price = getField(fields, headerMap, "price", "0.00"),
                    retailExt = getField(fields, headerMap, "retail_ext", ""),
                    fifoCost = getField(fields, headerMap, "fifo cost", ""),
                    fifoTotal = getField(fields, headerMap, "fifo total", ""),
                    fifoGp = getField(fields, headerMap, "fifo gp%", ""),
                    lastCost = getField(fields, headerMap, "last cost", ""),
                    lastCostTotal = getField(fields, headerMap, "lastcost total", ""),
                    lastCostGp = getField(fields, headerMap, "lastcost gp%", ""),
                    po = getField(fields, headerMap, "po", "0"),
                    cpo = getField(fields, headerMap, "cpo", "0"),
                    so = getField(fields, headerMap, "so", "0"),
                    ibt = getField(fields, headerMap, "ibt", "0"),
                    dn = getField(fields, headerMap, "dn", "0"),
                    cn = getField(fields, headerMap, "cn", "0"),
                    pos = getField(fields, headerMap, "pos", "0")
                )

                if (product.barcode.isNotBlank() || product.itemCode.isNotBlank()) {
                    batch.add(product)
                }
            } catch (e: Exception) {
                // Skip malformed rows
            }

            if (batch.size >= batchSize) {
                dao.insertAll(batch)
                totalInserted += batch.size
                onProgress?.invoke(totalInserted, -1)
                batch.clear()
            }

            line = reader.readLine()
        }

        // Insert remaining
        if (batch.isNotEmpty()) {
            dao.insertAll(batch)
            totalInserted += batch.size
            onProgress?.invoke(totalInserted, -1)
        }

        reader.close()
        totalInserted
    }

    private fun getField(
        fields: List<String>,
        headerMap: Map<String, Int>,
        key: String,
        default: String
    ): String {
        val index = headerMap[key] ?: return default
        return if (index < fields.size) fields[index].trim() else default
    }
}
