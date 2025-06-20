package com.acc_ide.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import androidx.core.view.setMargins
import androidx.recyclerview.widget.RecyclerView
import com.acc_ide.R
import com.google.android.material.button.MaterialButton
import io.github.rosemoe.sora.widget.CodeEditor

class SymbolPagerAdapter(
    private val context: Context,
    private val editor: CodeEditor,
    private val symbolPages: List<List<SymbolItem>>
) : RecyclerView.Adapter<SymbolPagerAdapter.SymbolPageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolPageViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.layout_symbol_page, parent, false)
        return SymbolPageViewHolder(view)
    }

    override fun onBindViewHolder(holder: SymbolPageViewHolder, position: Int) {
        holder.bind(symbolPages[position])
    }

    override fun getItemCount(): Int = symbolPages.size

    inner class SymbolPageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val gridLayout: GridLayout = itemView.findViewById(R.id.symbol_grid)

        fun bind(symbols: List<SymbolItem>) {
            gridLayout.removeAllViews()

            for (symbol in symbols) {
                val button = MaterialButton(context, null, R.style.SymbolButtonStyle).apply {
                    text = symbol.label
                    
                    // Set layout parameters for the button
                    val params = GridLayout.LayoutParams()
                    params.width = 0
                    params.height = GridLayout.LayoutParams.WRAP_CONTENT
                    params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    params.setMargins(4)
                    layoutParams = params

                    // Set click listener
                    setOnClickListener {
                        symbol.action(editor)
                    }
                }
                gridLayout.addView(button)
            }
        }
    }

    data class SymbolItem(
        val label: String,
        val action: (CodeEditor) -> Unit
    )
} 