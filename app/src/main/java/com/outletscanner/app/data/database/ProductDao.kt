package com.outletscanner.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.outletscanner.app.data.model.Product

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE outlet = :outlet AND barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(outlet: String, barcode: String): Product?

    @Query("SELECT * FROM products WHERE outlet = :outlet AND itemcode = :itemCode LIMIT 1")
    suspend fun findByItemCode(outlet: String, itemCode: String): Product?

    @Query("SELECT * FROM products WHERE outlet = :outlet AND articleno = :articleNo LIMIT 1")
    suspend fun findByArticleNo(outlet: String, articleNo: String): Product?

    @Query("SELECT * FROM products WHERE outlet = :outlet AND (barcode = :query OR itemcode = :query OR articleno = :query) LIMIT 1")
    suspend fun search(outlet: String, query: String): Product?

    @Query("SELECT * FROM products WHERE outlet = :outlet AND (barcode LIKE :query OR itemcode LIKE :query OR articleno LIKE :query OR description LIKE :query) LIMIT 50")
    suspend fun searchFuzzy(outlet: String, query: String): List<Product>

    @Query("SELECT COUNT(*) FROM products WHERE outlet = :outlet")
    suspend fun getCountForOutlet(outlet: String): Int

    @Query("DELETE FROM products WHERE outlet = :outlet")
    suspend fun deleteByOutlet(outlet: String)

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<Product>)

    @Query("""
        UPDATE products SET
            qoh = :qoh, price = :price, retail_ext = :retailExt,
            fifo_cost = :fifoCost, fifo_total = :fifoTotal, fifo_gp = :fifoGp,
            last_cost = :lastCost, last_cost_total = :lastCostTotal, last_cost_gp = :lastCostGp,
            po = :po, cpo = :cpo, so = :so, ibt = :ibt, dn = :dn, cn = :cn, pos = :pos
        WHERE outlet = :outlet AND itemcode = :itemCode
    """)
    suspend fun updateStockFields(
        outlet: String, itemCode: String,
        qoh: String, price: String, retailExt: String,
        fifoCost: String, fifoTotal: String, fifoGp: String,
        lastCost: String, lastCostTotal: String, lastCostGp: String,
        po: String, cpo: String, so: String, ibt: String, dn: String, cn: String, pos: String
    )

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getTotalCount(): Int
}
