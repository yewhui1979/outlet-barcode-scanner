package com.outletscanner.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.outletscanner.app.data.model.BarcodeMapping

@Dao
interface BarcodeMappingDao {

    @Query("SELECT itemcode FROM barcode_mappings WHERE barcode = :barcode LIMIT 1")
    suspend fun findItemCodeByBarcode(barcode: String): String?

    @Query("SELECT * FROM barcode_mappings WHERE barcode = :barcode")
    suspend fun findByBarcode(barcode: String): List<BarcodeMapping>

    @Query("SELECT COUNT(*) FROM barcode_mappings")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM barcode_mappings")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<BarcodeMapping>)
}
