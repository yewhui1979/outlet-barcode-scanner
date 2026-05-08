package com.outletscanner.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "minmax_changes")
data class MinMaxChange(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "outlet")
    val outlet: String,

    @ColumnInfo(name = "itemcode")
    val itemcode: String,

    @ColumnInfo(name = "min_qty")
    val minQty: String = "",

    @ColumnInfo(name = "max_qty")
    val maxQty: String = "",

    @ColumnInfo(name = "changed_by")
    val changedBy: String = "",

    @ColumnInfo(name = "changed_at")
    val changedAt: String = ""
)
