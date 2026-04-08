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

    @ColumnInfo(name = "item_status")
    val itemStatus: String = "",

    @ColumnInfo(name = "pack_size")
    val packSize: String = "",

    @ColumnInfo(name = "bulk_qty")
    val bulkQty: String = "",

    @ColumnInfo(name = "qoh")
    val qoh: String = "0",

    @ColumnInfo(name = "department")
    val department: String = "",

    @ColumnInfo(name = "sub_department")
    val subDepartment: String = "",

    @ColumnInfo(name = "category")
    val category: String = "",

    @ColumnInfo(name = "price")
    val price: String = "0.00",

    @ColumnInfo(name = "promo_id")
    val promoId: String = "",

    @ColumnInfo(name = "promo_date_from")
    val promoDateFrom: String = "",

    @ColumnInfo(name = "promo_date_to")
    val promoDateTo: String = "",

    @ColumnInfo(name = "promo_price")
    val promoPrice: String = "",

    @ColumnInfo(name = "promo_flag")
    val promoFlag: String = "N",

    @ColumnInfo(name = "promo_saving")
    val promoSaving: String = "",

    @ColumnInfo(name = "effective_price")
    val effectivePrice: String = "",

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

    @ColumnInfo(name = "average_cost")
    val averageCost: String = "",

    @ColumnInfo(name = "listed_cost")
    val listedCost: String = "",

    @ColumnInfo(name = "min_qty")
    val minQty: String = "",

    @ColumnInfo(name = "max_qty")
    val maxQty: String = "",

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
    val pos: String = "0",

    @ColumnInfo(name = "qty_req")
    val qtyReq: String = "",

    @ColumnInfo(name = "qty_tbr")
    val qtyTbr: String = "",

    @ColumnInfo(name = "last_gr_qty")
    val lastGrQty: String = "",

    @ColumnInfo(name = "last_gr_date")
    val lastGrDate: String = "",

    @ColumnInfo(name = "last_gr_vendor")
    val lastGrVendor: String = "",

    @ColumnInfo(name = "vendor_name")
    val vendorName: String = ""
) {
    val isOnOrder: Boolean
        get() = po.trim() != "0" && po.trim().isNotEmpty()

    val isOnPromo: Boolean
        get() = promoFlag.trim().uppercase() == "Y" || promoPrice.trim().isNotEmpty()

    val formattedPrice: String
        get() {
            return try {
                val p = price.trim().toDouble()
                String.format("%.2f", p)
            } catch (e: Exception) {
                price
            }
        }

    val formattedEffectivePrice: String
        get() {
            return try {
                val p = effectivePrice.trim().toDouble()
                String.format("%.2f", p)
            } catch (e: Exception) {
                effectivePrice
            }
        }

    val formattedPromoPrice: String
        get() {
            return try {
                val p = promoPrice.trim().toDouble()
                if (p > 0) String.format("%.2f", p) else ""
            } catch (e: Exception) {
                ""
            }
        }
}
