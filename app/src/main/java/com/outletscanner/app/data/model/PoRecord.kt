package com.outletscanner.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "po_records",
    indices = [
        Index(value = ["itemcode"]),
        Index(value = ["status"])
    ]
)
data class PoRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "itemcode")
    val itemcode: String,

    @ColumnInfo(name = "po_refno")
    val poRefno: String = "",

    @ColumnInfo(name = "po_date")
    val poDate: String = "",

    @ColumnInfo(name = "po_expiry_date")
    val poExpiryDate: String = "",

    @ColumnInfo(name = "po_qty")
    val poQty: String = "0",

    @ColumnInfo(name = "po_total")
    val poTotal: String = "0",

    @ColumnInfo(name = "gr_refno")
    val grRefno: String = "",

    @ColumnInfo(name = "gr_date")
    val grDate: String = "",

    @ColumnInfo(name = "gr_qty")
    val grQty: String = "0",

    @ColumnInfo(name = "gr_total")
    val grTotal: String = "0",

    @ColumnInfo(name = "short_qty")
    val shortQty: String = "0",

    @ColumnInfo(name = "fulfillment_pct")
    val fulfillmentPct: String = "0",

    @ColumnInfo(name = "status")
    val status: String = "",

    @ColumnInfo(name = "supplier_code")
    val supplierCode: String = "",

    @ColumnInfo(name = "supplier_name")
    val supplierName: String = ""
)
