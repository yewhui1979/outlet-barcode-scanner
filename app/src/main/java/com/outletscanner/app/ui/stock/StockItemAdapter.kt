package com.outletscanner.app.ui.stock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.outletscanner.app.R
import com.outletscanner.app.data.model.Product

class StockItemAdapter(
    private val isOos: Boolean,
    private val onClick: (Product) -> Unit
) : ListAdapter<Product, StockItemAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(a: Product, b: Product) = a.id == b.id
            override fun areContentsTheSame(a: Product, b: Product) = a == b
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItemCode: TextView = view.findViewById(R.id.tvItemCode)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvDepartment: TextView = view.findViewById(R.id.tvDepartment)
        val tvQoh: TextView = view.findViewById(R.id.tvQoh)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stock, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvItemCode.text = item.itemCode
        holder.tvDescription.text = item.description
        holder.tvDepartment.text = item.department
        holder.tvQoh.text = "QOH: ${item.formattedQoh}"
        holder.tvPrice.text = "RM ${item.formattedPrice}"

        if (!isOos) {
            holder.tvQoh.setTextColor(holder.itemView.context.getColor(R.color.warning_orange))
        } else {
            holder.tvQoh.setTextColor(holder.itemView.context.getColor(R.color.error))
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }
}
