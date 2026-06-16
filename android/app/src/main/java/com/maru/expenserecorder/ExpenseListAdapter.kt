package com.maru.expenserecorder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.maru.expenserecorder.data.Expense
import com.maru.expenserecorder.databinding.ItemExpenseBinding

class ExpenseListAdapter(private val items: List<Expense>) :
    RecyclerView.Adapter<ExpenseListAdapter.VH>() {

    inner class VH(val binding: ItemExpenseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.binding.tvSubject.text = e.subject
        holder.binding.tvDatetime.text = "${e.date}  ${e.time}"
        val amountStr = if (e.amount % 1.0 == 0.0) e.amount.toLong().toString()
                        else "%.2f".format(e.amount)
        holder.binding.tvAmount.text = "₪$amountStr"
    }

    override fun getItemCount() = items.size
}
