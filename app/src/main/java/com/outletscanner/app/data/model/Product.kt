package com.outletscanner.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode"]),
        Index(value = ["itemcode"]),
        Index(value = ["outlet"])
    ]
)
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "outlet")
    val outlet: String,

    @ColumnInfo(name = "itemcode")
    val itemCode: String,

    @ColumnInfo(name = "itemlink")
    val itemLink: String = "",

    @ColumnInfo(name = "barcode")
    val barcode: String,

    @ColumnInfo(name = "articleno")
    val articleNo: String = "",

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "qoh")
    val qoh: String = "0",

    @ColumnInfo(name = "price")
    val price: String = "0.00",

    @ColumnInfo(name = "retail_ext")
    val retailExt: String = "",

    @ColumnInfo(name = "fifo_cost")
    val fifoCost: String = "",

    @ColumnInfo(name = "fifo_total")
    val fifoTotal: String = "",

    @ColumnInfo(name = "fifo_gp")
    val fifoGp: String = "",

    @ColumnInfo(name = "last_cost")
    val lastCost: String = "",

    @ColumnInfo(name = "last_cost_total")
    val lastCostTotal: String = "",

    @ColumnInfo(name = "last_cost_gp")
    val lastCostGp: String = "",

    @ColumnInfo(name = "po")
    val po: String = "0",

    @ColumnInfo(name = "cpo")
    val cpo: String = "0",

    @ColumnInfo(name = "so")
    val so: String = "0",

    @ColumnInfo(name = "ibt")
    val ibt: String = "0",

    @ColumnInfo(name = "dn")
    val dn: String = "0",

    @ColumnInfo(name = "cn")
    val cn: String = "0",

    @ColumnInfo(name = "pos")
    val pos: String = "0"
) {
    val isOnOrder: Boolean
        get() = po.trim() != "0" && po.trim().isNotEmpty()

    val formattedPrice: String
        get() {
            return try {
                val p = price.trim().toDouble()
                String.format("%.2f", p)
            } catch (e: Exception) {
                price
            }
        }
}
