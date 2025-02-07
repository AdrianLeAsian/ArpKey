package com.arupine.arpkey

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

class SymbolAdapter(
    private var items: List<SymbolItem>,
    private val onItemClick: (SymbolItem) -> Unit
) : RecyclerView.Adapter<SymbolAdapter.ViewHolder>() {

    private var selectedPosition = 0

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val symbolText: TextView

        init {
            // Create a FrameLayout container
            val container = itemView as FrameLayout
            container.setBackgroundResource(android.R.drawable.list_selector_background)
            
            // Create and add the TextView
            symbolText = TextView(itemView.context).apply {
                textSize = 20f  // Increased text size
                setPadding(16, 16, 16, 16)  // Increased padding
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
                // Set minimum width and height
                minimumWidth = 56
                minimumHeight = 56
            }
            container.addView(symbolText)

            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    updateSelection(position)
                    onItemClick(items[position])
                }
            }
        }

        fun bind(item: SymbolItem, isSelected: Boolean) {
            symbolText.text = item.symbol
            itemView.isSelected = isSelected
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
            // Change text color and background based on selection
            symbolText.setTextColor(if (isSelected) 0xFF2196F3.toInt() else 0xFF000000.toInt())
            itemView.setBackgroundResource(
                if (isSelected) android.R.drawable.list_selector_background 
                else android.R.drawable.btn_default
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val container = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 8, 8, 8)  // Increased padding for better visibility
            minimumWidth = 56  // Increased minimum size for better touch targets
            minimumHeight = 56
            isFocusable = true
            isFocusableInTouchMode = true
        }
        return ViewHolder(container)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position == selectedPosition)
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<SymbolItem>) {
        items = newItems
        selectedPosition = 0
        notifyDataSetChanged()
    }

    fun moveSelection(direction: Int) {
        val newPosition = when {
            direction > 0 -> (selectedPosition + 1).coerceAtMost(itemCount - 1)
            direction < 0 -> (selectedPosition - 1).coerceAtLeast(0)
            else -> selectedPosition
        }
        
        if (newPosition != selectedPosition) {
            val oldPosition = selectedPosition
            selectedPosition = newPosition
            notifyItemChanged(oldPosition)
            notifyItemChanged(newPosition)
        }
    }

    fun getSelectedItem(): SymbolItem? =
        if (selectedPosition in items.indices) items[selectedPosition] else null

    // Add getter for selected position
    fun getSelectedPosition(): Int = selectedPosition

    // Add method to get item by position
    fun getSelectedItem(position: Int): SymbolItem? =
        if (position in items.indices) items[position] else null

    // Add method to update selection directly
    fun updateSelection(newPosition: Int) {
        if (newPosition != selectedPosition && newPosition in 0 until itemCount) {
            val oldPosition = selectedPosition
            selectedPosition = newPosition
            notifyItemChanged(oldPosition)
            notifyItemChanged(newPosition)
        }
    }
} 