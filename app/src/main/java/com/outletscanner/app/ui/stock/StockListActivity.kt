package com.outletscanner.app.ui.stock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import com.outletscanner.app.R
import com.outletscanner.app.data.model.Product
import com.outletscanner.app.data.repository.ProductRepository
import com.outletscanner.app.ui.product.ProductDetailActivity
import com.outletscanner.app.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StockListActivity : AppCompatActivity() {

    private lateinit var repository: ProductRepository
    private lateinit var prefsManager: PrefsManager
    private lateinit var adapter: StockItemAdapter

    private lateinit var tvTotalCount: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var actvDept: AutoCompleteTextView
    private lateinit var actvSubDept: AutoCompleteTextView

    private var isOos = true
    private var selectedDept = ""
    private var selectedSubDept = ""

    companion object {
        const val EXTRA_MODE = "stock_mode"
        const val MODE_OUT_OF_STOCK = "oos"
        const val MODE_NEGATIVE_STOCK = "negative"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock_list)

        repository = ProductRepository(this)
        prefsManager = PrefsManager(this)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_OUT_OF_STOCK
        isOos = mode == MODE_OUT_OF_STOCK

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.title = if (isOos) "Out of Stock" else "Negative Stock"
        toolbar.setNavigationOnClickListener { finish() }

        tvTotalCount = findViewById(R.id.tvTotalCount)
        tvEmpty = findViewById(R.id.tvEmpty)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        actvDept = findViewById(R.id.actvDept)
        actvSubDept = findViewById(R.id.actvSubDept)

        val tvTotalLabel = findViewById<TextView>(R.id.tvTotalLabel)
        val tvOutlet = findViewById<TextView>(R.id.tvOutlet)
        val cardTotal = findViewById<MaterialCardView>(R.id.cardTotal)

        tvTotalLabel.text = if (isOos) "Out of Stock SKUs" else "Negative Stock SKUs"
        tvOutlet.text = "Outlet: ${prefsManager.selectedOutlet}"

        if (isOos) {
            cardTotal.setCardBackgroundColor(getColor(R.color.error))
        } else {
            cardTotal.setCardBackgroundColor(getColor(R.color.warning_orange))
        }

        adapter = StockItemAdapter(isOos) { product ->
            startActivity(buildProductDetailIntent(product))
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupFilters()
        loadData()
    }

    private fun setupFilters() {
        val outlet = prefsManager.selectedOutlet

        lifecycleScope.launch {
            val departments = withContext(Dispatchers.IO) {
                repository.getDistinctDepartments(outlet)
            }
            val deptList = mutableListOf("All Departments")
            deptList.addAll(departments)

            val deptAdapter = ArrayAdapter(this@StockListActivity, android.R.layout.simple_dropdown_item_1line, deptList)
            actvDept.setAdapter(deptAdapter)
            actvDept.setText("All Departments", false)

            val subDeptAdapter = ArrayAdapter(this@StockListActivity, android.R.layout.simple_dropdown_item_1line, listOf("All Sub Depts"))
            actvSubDept.setAdapter(subDeptAdapter)
            actvSubDept.setText("All Sub Depts", false)
        }

        actvDept.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as String
            selectedDept = if (position == 0) "" else selected
            selectedSubDept = ""
            actvSubDept.setText("All Sub Depts", false)
            loadSubDepartments()
            loadData()
        }

        actvSubDept.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position) as String
            selectedSubDept = if (position == 0) "" else selected
            loadData()
        }
    }

    private fun loadSubDepartments() {
        val outlet = prefsManager.selectedOutlet
        lifecycleScope.launch {
            val subDepts = withContext(Dispatchers.IO) {
                repository.getDistinctSubDepartments(outlet, selectedDept)
            }
            val subDeptList = mutableListOf("All Sub Depts")
            subDeptList.addAll(subDepts)
            val subDeptAdapter = ArrayAdapter(this@StockListActivity, android.R.layout.simple_dropdown_item_1line, subDeptList)
            actvSubDept.setAdapter(subDeptAdapter)
        }
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val outlet = prefsManager.selectedOutlet
            val items = withContext(Dispatchers.IO) {
                if (isOos) repository.getOutOfStockItemsFiltered(outlet, selectedDept, selectedSubDept)
                else repository.getNegativeStockItemsFiltered(outlet, selectedDept, selectedSubDept)
            }

            progressBar.visibility = View.GONE
            tvTotalCount.text = "${items.size}"

            if (items.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = if (isOos) "No out of stock items found" else "No negative stock items found"
                recyclerView.visibility = View.GONE
                adapter.submitList(null)
            } else {
                tvEmpty.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                adapter.submitList(null)
                adapter.submitList(items.toList())
            }
        }
    }

    private fun buildProductDetailIntent(product: Product): Intent {
        return Intent(this, ProductDetailActivity::class.java).apply {
            putExtra("outlet", product.outlet)
            putExtra("item_code", product.itemCode)
            putExtra("item_link", product.itemLink)
            putExtra("barcode", product.barcode)
            putExtra("article_no", product.articleNo)
            putExtra("description", product.description)
            putExtra("item_status", product.itemStatus)
            putExtra("pack_size", product.packSize)
            putExtra("bulk_qty", product.bulkQty)
            putExtra("qoh", product.qoh)
            putExtra("department", product.department)
            putExtra("sub_department", product.subDepartment)
            putExtra("category", product.category)
            putExtra("price", product.formattedPrice)
            putExtra("promo_id", product.promoId)
            putExtra("promo_date_from", product.promoDateFrom)
            putExtra("promo_date_to", product.promoDateTo)
            putExtra("promo_price", product.promoPrice)
            putExtra("promo_flag", product.promoFlag)
            putExtra("promo_saving", product.promoSaving)
            putExtra("effective_price", product.effectivePrice)
            putExtra("retail_ext", product.retailExt)
            putExtra("fifo_cost", product.fifoCost)
            putExtra("fifo_total", product.fifoTotal)
            putExtra("fifo_gp", product.fifoGp)
            putExtra("last_cost", product.lastCost)
            putExtra("last_cost_total", product.lastCostTotal)
            putExtra("last_cost_gp", product.lastCostGp)
            putExtra("average_cost", product.averageCost)
            putExtra("listed_cost", product.listedCost)
            putExtra("cpo", product.cpo)
            putExtra("so", product.so)
            putExtra("ibt", product.ibt)
            putExtra("dn", product.dn)
            putExtra("cn", product.cn)
            putExtra("pos", product.pos)
            putExtra("min_qty", product.minQty)
            putExtra("max_qty", product.maxQty)
            putExtra("qty_po", product.po)
            putExtra("qty_req", product.qtyReq)
            putExtra("qty_tbr", product.qtyTbr)
            putExtra("last_gr_qty", product.lastGrQty)
            putExtra("last_gr_date", product.lastGrDate)
            putExtra("last_gr_vendor", product.lastGrVendor)
            putExtra("vendor_name", product.vendorName)
        }
    }
}
