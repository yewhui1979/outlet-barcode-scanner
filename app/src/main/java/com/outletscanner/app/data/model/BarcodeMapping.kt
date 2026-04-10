package com.outletscanner.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "barcode_mappings",
    indices = [
        Index(value = ["barcode"]),
        Index(value = ["itemcode"])
    ]
)
data class BarcodeMapping(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "itemcode")
    val itemCode: String,

    @ColumnInfo(name = "barcode")
    val barcode: String,

    @ColumnInfo(name = "description")
    val description: String = ""
)
