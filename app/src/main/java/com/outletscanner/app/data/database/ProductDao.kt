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

    @Query("UPDATE products SET qoh = :qoh WHERE outlet = :outlet AND itemcode = :itemCode")
    suspend fun updateQoh(outlet: String, itemCode: String, qoh: String)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getTotalCount(): Int

    @Query("SELECT * FROM products WHERE outlet = :outlet AND CAST(qoh AS REAL) = 0 AND item_status = 'Active' AND CAST(bulk_qty AS REAL) <= 1 ORDER BY department, description")
    suspend fun getOutOfStockItems(outlet: String): List<Product>

    @Query("SELECT COUNT(*) FROM products WHERE outlet = :outlet AND CAST(qoh AS REAL) = 0 AND item_status = 'Active' AND CAST(bulk_qty AS REAL) <= 1")
    suspend fun getOutOfStockCount(outlet: String): Int

    @Query("SELECT * FROM products WHERE outlet = :outlet AND CAST(qoh AS REAL) < 0 AND item_status = 'Active' AND CAST(bulk_qty AS REAL) <= 1 ORDER BY CAST(qoh AS REAL) ASC, department, description")
    suspend fun getNegativeStockItems(outlet: String): List<Product>

    @Query("SELECT COUNT(*) FROM products WHERE outlet = :outlet AND CAST(qoh AS REAL) < 0 AND item_status = 'Active' AND CAST(bulk_qty AS REAL) <= 1")
    suspend fun getNegativeStockCount(outlet: String): Int

    @Query("SELECT * FROM products WHERE outlet = :outlet AND CAST(qoh AS REAL) = 0 AND item_status = 'Active' AND CAST(bulk_qty AS REAL) <= 1 AND (:dept = '' OR department = :dept) AND (:subDept = '' OR sub_department = :subDept) ORDER BY department, description")
    suspend fun getOutOfStockItemsFiltered(outlet: String, dept: String, subDept: String): List<Product>

    @Query("SELECT * FROM products WHERE outlet = :outlet AND CAST(qoh AS REAL) < 0 AND item_status = 'Active' AND CAST(bulk_qty AS REAL) <= 1 AND (:dept = '' OR department = :dept) AND (:subDept = '' OR sub_department = :subDept) ORDER BY CAST(qoh AS REAL) ASC, department, description")
    suspend fun getNegativeStockItemsFiltered(outlet: String, dept: String, subDept: String): List<Product>

    @Query("SELECT DISTINCT department FROM products WHERE outlet = :outlet AND department != '' ORDER BY department")
    suspend fun getDistinctDepartments(outlet: String): List<String>

    @Query("SELECT DISTINCT sub_department FROM products WHERE outlet = :outlet AND (:dept = '' OR department = :dept) AND sub_department != '' ORDER BY sub_department")
    suspend fun getDistinctSubDepartments(outlet: String, dept: String): List<String>
}
