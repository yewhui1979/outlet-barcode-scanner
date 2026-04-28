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
        get() = formatCost(price)

    val formattedEffectivePrice: String
        get() = formatCost(effectivePrice)

    val formattedPromoPrice: String
        get() {
            return try {
                val p = promoPrice.trim().toDouble()
                if (p > 0) String.format("%.2f", p) else ""
            } catch (e: Exception) {
                ""
            }
        }

    val formattedFifoCost: String get() = formatCost(fifoCost)
    val formattedFifoTotal: String get() = formatCost(fifoTotal)
    val formattedLastCost: String get() = formatCost(lastCost)
    val formattedLastCostTotal: String get() = formatCost(lastCostTotal)
    val formattedAverageCost: String get() = formatCost(averageCost)
    val formattedListedCost: String get() = formatCost(listedCost)
    val formattedRetailExt: String get() = formatCost(retailExt)
    val formattedPromoSaving: String get() = formatCost(promoSaving)
    val formattedFifoGp: String get() = formatCost(fifoGp)
    val formattedLastCostGp: String get() = formatCost(lastCostGp)

    val formattedQoh: String get() = formatQty(qoh)
    val formattedBulkQty: String get() = formatQty(bulkQty)
    val formattedMinQty: String get() = formatQty(minQty)
    val formattedMaxQty: String get() = formatQty(maxQty)
    val formattedPo: String get() = formatQty(po)
    val formattedCpo: String get() = formatQty(cpo)
    val formattedSo: String get() = formatQty(so)
    val formattedIbt: String get() = formatQty(ibt)
    val formattedDn: String get() = formatQty(dn)
    val formattedCn: String get() = formatQty(cn)
    val formattedPos: String get() = formatQty(pos)
    val formattedQtyReq: String get() = formatQty(qtyReq)
    val formattedQtyTbr: String get() = formatQty(qtyTbr)
    val formattedLastGrQty: String get() = formatQty(lastGrQty)

    private fun formatCost(value: String): String {
        if (value.isBlank()) return ""
        return try {
            String.format("%.2f", value.trim().toDouble())
        } catch (e: Exception) {
            value
        }
    }

    private fun formatQty(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val d = value.trim().toDouble()
            if (d == d.toLong().toDouble()) d.toLong().toString()
            else String.format("%.0f", d)
        } catch (e: Exception) {
            value
        }
    }
}
