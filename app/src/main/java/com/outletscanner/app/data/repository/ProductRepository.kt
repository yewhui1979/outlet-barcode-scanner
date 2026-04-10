package com.outletscanner.app.data.repository

import android.content.Context
import com.outletscanner.app.data.database.AppDatabase
import com.outletscanner.app.data.model.BarcodeMapping
import com.outletscanner.app.data.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class ProductRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.productDao()
    private val barcodeDao = db.barcodeMappingDao()

    /**
     * Two-step lookup: barcode → itemcode (via barcode_mappings) → product (via products).
     * Falls back to direct barcode/itemcode match in products table.
     */
    suspend fun findByBarcode(outlet: String, barcode: String): Product? {
        // Step 1: Try barcode mapping table first
        val itemCode = barcodeDao.findItemCodeByBarcode(barcode)
        if (itemCode != null) {
            val product = dao.findByItemCode(outlet, itemCode)
            if (product != null) return product
        }

        // Step 2: Fall back to direct lookup in products table
        return dao.findByBarcode(outlet, barcode)
    }

    suspend fun findByItemCode(outlet: String, itemCode: String): Product? {
        return dao.findByItemCode(outlet, itemCode)
    }

    suspend fun search(outlet: String, query: String): Product? {
        // Try barcode mapping first
        val itemCode = barcodeDao.findItemCodeByBarcode(query)
        if (itemCode != null) {
            val product = dao.findByItemCode(outlet, itemCode)
            if (product != null) return product
        }

        // Fall back to direct search
        return dao.search(outlet, query)
    }

    suspend fun searchFuzzy(outlet: String, query: String): List<Product> {
        return dao.searchFuzzy(outlet, "%$query%")
    }

    suspend fun getItemCount(outlet: String): Int {
        return dao.getCountForOutlet(outlet)
    }

    suspend fun getBarcodeMappingCount(): Int {
        return barcodeDao.getTotalCount()
    }

    suspend fun deleteByOutlet(outlet: String) {
        dao.deleteByOutlet(outlet)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun deleteAllBarcodeMappings() {
        barcodeDao.deleteAll()
    }

    /**
     * Parse barcode mapping file (Itemcode|Barcode|Description) and bulk insert.
     * Designed for 500K+ rows with batch inserts.
     */
    suspend fun parseAndInsertBarcodeMappings(
        inputStream: InputStream,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        barcodeDao.deleteAll()

        val reader = BufferedReader(InputStreamReader(inputStream), 1024 * 64)
        val headerLine = reader.readLine() ?: return@withContext 0

        val headers = headerLine.split("|").map { it.trim().lowercase().replace(" ", "_") }
        val headerMap = headers.mapIndexed { index, name -> name to index }.toMap()

        val batch = mutableListOf<BarcodeMapping>()
        var totalInserted = 0
        val batchSize = 5000

        var line = reader.readLine()
        while (line != null) {
            if (line.isBlank()) {
                line = reader.readLine()
                continue
            }

            val fields = line.split("|")
            if (fields.size < 2) {
                line = reader.readLine()
                continue
            }

            try {
                val itemCode = getField(fields, headerMap, "itemcode", "")
                val barcode = getField(fields, headerMap, "barcode", "")
                val description = getField(fields, headerMap, "description", "")

                if (barcode.isNotBlank() && itemCode.isNotBlank()) {
                    batch.add(BarcodeMapping(
                        itemCode = itemCode,
                        barcode = barcode,
                        description = description
                    ))
                }
            } catch (e: Exception) {
                // Skip malformed rows
            }

            if (batch.size >= batchSize) {
                barcodeDao.insertAll(batch)
                totalInserted += batch.size
                onProgress?.invoke(totalInserted, -1)
                batch.clear()
            }

            line = reader.readLine()
        }

        if (batch.isNotEmpty()) {
            barcodeDao.insertAll(batch)
            totalInserted += batch.size
            onProgress?.invoke(totalInserted, -1)
        }

        reader.close()
        totalInserted
    }

    /**
     * Parse a pipe-delimited input stream and bulk insert into the database.
     * Designed for efficiency with 400K+ rows - uses batch inserts of 5000 rows.
     * Supports both old and new sync file formats via header-based column mapping.
     */
    suspend fun parseAndInsert(
        inputStream: InputStream,
        outlet: String,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        dao.deleteByOutlet(outlet)

        val reader = BufferedReader(InputStreamReader(inputStream), 1024 * 64)
        var headerLine = reader.readLine() ?: return@withContext 0

        val headers = headerLine.split("|").map { it.trim().lowercase().replace(" ", "_") }
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
                    itemStatus = getField(fields, headerMap, "item_status", ""),
                    packSize = getField(fields, headerMap, "packsize", ""),
                    bulkQty = getField(fields, headerMap, "bulkqty", ""),
                    qoh = getField(fields, headerMap, "qoh", "0"),
                    department = getField(fields, headerMap, "department", ""),
                    subDepartment = getField(fields, headerMap, "sub_department", ""),
                    category = getField(fields, headerMap, "category", ""),
                    price = getField(fields, headerMap, "price", "0.00"),
                    promoId = getField(fields, headerMap, "promoid", ""),
                    promoDateFrom = getField(fields, headerMap, "promodatefrom", ""),
                    promoDateTo = getField(fields, headerMap, "promodateto", ""),
                    promoPrice = getField(fields, headerMap, "promoprice", ""),
                    promoFlag = getField(fields, headerMap, "promoflag", "N"),
                    promoSaving = getField(fields, headerMap, "promosaving", ""),
                    effectivePrice = getField(fields, headerMap, "effectiveprice", ""),
                    retailExt = getField(fields, headerMap, "retail_ext", ""),
                    fifoCost = getField(fields, headerMap, "fifo_cost", ""),
                    fifoTotal = getField(fields, headerMap, "fifo_total", ""),
                    fifoGp = getField(fields, headerMap, "fifo_gp%", ""),
                    lastCost = getField(fields, headerMap, "last_cost", ""),
                    lastCostTotal = getField(fields, headerMap, "lastcost_total", ""),
                    lastCostGp = getField(fields, headerMap, "lastcost_gp%", ""),
                    averageCost = getField(fields, headerMap, "averagecost", ""),
                    listedCost = getField(fields, headerMap, "listedcost", ""),
                    minQty = getField(fields, headerMap, "min_qty", ""),
                    maxQty = getField(fields, headerMap, "max_qty", ""),
                    po = getFieldMulti(fields, headerMap, listOf("qty_po", "po"), "0"),
                    cpo = getField(fields, headerMap, "cpo", "0"),
                    so = getField(fields, headerMap, "so", "0"),
                    ibt = getField(fields, headerMap, "ibt", "0"),
                    dn = getField(fields, headerMap, "dn", "0"),
                    cn = getField(fields, headerMap, "cn", "0"),
                    pos = getField(fields, headerMap, "pos", "0"),
                    qtyReq = getField(fields, headerMap, "qty_req", ""),
                    qtyTbr = getField(fields, headerMap, "qty_tbr", ""),
                    lastGrQty = getField(fields, headerMap, "last_gr_qty", ""),
                    lastGrDate = getField(fields, headerMap, "last_gr_date", ""),
                    lastGrVendor = getField(fields, headerMap, "last_gr_vendor", ""),
                    vendorName = getField(fields, headerMap, "vendor_name", "")
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

    private fun getFieldMulti(
        fields: List<String>,
        headerMap: Map<String, Int>,
        keys: List<String>,
        default: String
    ): String {
        for (key in keys) {
            val index = headerMap[key]
            if (index != null && index < fields.size) {
                return fields[index].trim()
            }
        }
        return default
    }
}
