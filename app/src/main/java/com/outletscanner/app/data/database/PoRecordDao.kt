package com.outletscanner.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.outletscanner.app.data.model.PoRecord

@Dao
interface PoRecordDao {

    @Query("SELECT * FROM po_records WHERE itemcode = :itemcode AND status IN ('DROP_PO', 'PARTIAL_PO', 'PENDING') ORDER BY po_date DESC LIMIT 5")
    suspend fun getOpenPoForItem(itemcode: String): List<PoRecord>

    @Query("SELECT * FROM po_records WHERE itemcode = :itemcode ORDER BY po_date DESC LIMIT 10")
    suspend fun getAllPoForItem(itemcode: String): List<PoRecord>

    @Query("DELETE FROM po_records")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<PoRecord>)

    @Query("SELECT COUNT(*) FROM po_records")
    suspend fun getTotalCount(): Int
}
