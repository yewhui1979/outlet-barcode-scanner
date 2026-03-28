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

    @Query("SELECT * FROM products WHERE outlet = :outlet AND (barcode = :query OR itemcode = :query) LIMIT 1")
    suspend fun search(outlet: String, query: String): Product?

    @Query("SELECT * FROM products WHERE outlet = :outlet AND (barcode LIKE :query OR itemcode LIKE :query OR description LIKE :query) LIMIT 50")
    suspend fun searchFuzzy(outlet: String, query: String): List<Product>

    @Query("SELECT COUNT(*) FROM products WHERE outlet = :outlet")
    suspend fun getCountForOutlet(outlet: String): Int

    @Query("DELETE FROM products WHERE outlet = :outlet")
    suspend fun deleteByOutlet(outlet: String)

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<Product>)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getTotalCount(): Int
}
