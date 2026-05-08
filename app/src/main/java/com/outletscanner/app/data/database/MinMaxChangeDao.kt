package com.outletscanner.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.outletscanner.app.data.model.MinMaxChange

@Dao
interface MinMaxChangeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(change: MinMaxChange)

    @Query("SELECT * FROM minmax_changes WHERE outlet = :outlet ORDER BY changed_at DESC")
    suspend fun getByOutlet(outlet: String): List<MinMaxChange>

    @Query("DELETE FROM minmax_changes WHERE outlet = :outlet")
    suspend fun deleteByOutlet(outlet: String)

    @Query("SELECT COUNT(*) FROM minmax_changes WHERE outlet = :outlet")
    suspend fun getCountByOutlet(outlet: String): Int
}
