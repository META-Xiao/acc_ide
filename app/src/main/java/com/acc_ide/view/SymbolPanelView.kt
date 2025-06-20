package com.acc_ide.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.viewpager2.widget.ViewPager2
import com.acc_ide.R
import com.google.android.material.button.MaterialButton
import io.github.rosemoe.sora.widget.CodeEditor
import android.widget.TextView
import android.view.Gravity
import android.util.TypedValue

/**
 * 符号面板自定义视图
 * 提供编程符号输入功能，方便用户在移动设备上输入特殊字符
 */
class SymbolPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: LinearLayout
    private var editor: CodeEditor? = null
    private var currentPage = 0
    private val pageCount = 2

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_symbol_panel, this, true)
        
        // 初始化视图
        viewPager = findViewById(R.id.symbol_view_pager)
        pageIndicator = findViewById(R.id.page_indicator)
        
        // 设置ViewPager适配器
        setupViewPager()
    }
    
    /**
     * 设置编辑器实例
     */
    fun setEditor(editor: CodeEditor) {
        this.editor = editor
    }
    
    /**
     * 设置ViewPager
     */
    private fun setupViewPager() {
        viewPager.adapter = SymbolPagerAdapter()
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updatePageIndicator()
            }
        })
        
        // 初始化页面指示器
        setupPageIndicator()
    }
    
    /**
     * 设置页面指示器
     */
    private fun setupPageIndicator() {
        pageIndicator.removeAllViews()
        
        for (i in 0 until pageCount) {
            val indicator = View(context).apply {
                layoutParams = LayoutParams(12, 12).apply {
                    setMargins(6, 0, 6, 0)
                }
                background = context.getDrawable(R.drawable.page_indicator)
                isSelected = i == currentPage
            }
            pageIndicator.addView(indicator)
        }
    }
    
    /**
     * 更新页面指示器
     */
    private fun updatePageIndicator() {
        for (i in 0 until pageIndicator.childCount) {
            val indicator = pageIndicator.getChildAt(i)
            indicator.isSelected = i == currentPage
        }
    }
    
    /**
     * 符号页面适配器
     */
    private inner class SymbolPagerAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<SymbolPagerAdapter.SymbolPageViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolPageViewHolder {
            val pageView = LayoutInflater.from(parent.context)
                .inflate(R.layout.view_symbol_page, parent, false)
                
            // Ensure the page view fills the ViewPager2
            pageView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
                
            return SymbolPageViewHolder(pageView)
        }
        
        override fun onBindViewHolder(holder: SymbolPageViewHolder, position: Int) {
            when (position) {
                0 -> holder.setupFirstPage()
                1 -> holder.setupSecondPage()
            }
        }
        
        override fun getItemCount(): Int = pageCount
        
        /**
         * 符号页面ViewHolder
         */
        inner class SymbolPageViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            private val row1: LinearLayout = itemView.findViewById(R.id.symbol_buttons_row1)
            private val row2: LinearLayout = itemView.findViewById(R.id.symbol_buttons_row2)
            
            /**
             * 设置第一页符号
             */
            fun setupFirstPage() {
                // 清空现有按钮
                row1.removeAllViews()
                row2.removeAllViews()
                
                // 第一行符号
                val row1Symbols = listOf(
                    "TAB" to "\t",
                    "(" to "(",
                    ")" to ")",
                    "[" to "[",
                    "]" to "]",
                    "{" to "{",
                    "}" to "}",
                    "↑" to "UP",
                    "=" to "="
                )
                
                // 第二行符号
                val row2Symbols = listOf(
                    ";" to ";",
                    "<" to "<",
                    ">" to ">",
                    "-" to "-",
                    "+" to "+",
                    "," to ",",
                    "←" to "LEFT",
                    "↓" to "DOWN",
                    "→" to "RIGHT"
                )
                
                // 添加第一行符号按钮
                for ((label, value) in row1Symbols) {
                    addSymbolButton(row1, label, value)
                }
                
                // 添加第二行符号按钮
                for ((label, value) in row2Symbols) {
                    addSymbolButton(row2, label, value)
                }
            }
            
            /**
             * 设置第二页符号
             */
            fun setupSecondPage() {
                // 清空现有按钮
                row1.removeAllViews()
                row2.removeAllViews()
                
                // 第一行符号
                val row1Symbols = listOf(
                    "*" to "*",
                    "/" to "/",
                    "\\" to "\\",
                    "?" to "?",
                    "|" to "|",
                    "," to ",",
                    "." to ".",
                    "^" to "^",
                    "&" to "&"
                )
                
                // 第二行符号
                val row2Symbols = listOf(
                    "Ctrl" to "CTRL",
                    "!" to "!",
                    "'" to "'",
                    "\"" to "\"",
                    "_" to "_",
                    "!" to "!",
                    "$" to "$",
                    "%" to "%",
                    ":" to ":"
                )
                
                // 添加第一行符号按钮
                for ((label, value) in row1Symbols) {
                    addSymbolButton(row1, label, value)
                }
                
                // 添加第二行符号按钮
                for ((label, value) in row2Symbols) {
                    addSymbolButton(row2, label, value)
                }
            }
        }
    }
    
    private fun dp2px(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
    
    /**
     * 添加符号按钮
     */
    private fun addSymbolButton(parent: LinearLayout, label: String, value: String) {
        val textView = TextView(context).apply {
            text = label
            setTextAppearance(R.style.SymbolTextStyle)
            gravity = Gravity.CENTER
            minHeight = dp2px(36)
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
            // 添加水波纹点击反馈
            foreground = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).getDrawable(0)
            setOnClickListener {
                when (value) {
                    "LEFT" -> moveCursor(-1, 0)
                    "RIGHT" -> moveCursor(1, 0)
                    "UP" -> moveCursor(0, -1)
                    "DOWN" -> moveCursor(0, 1)
                    "CTRL" -> handleCtrlKey()
                    else -> insertText(value)
                }
            }
        }
        parent.addView(textView)
    }
    
    /**
     * 处理Ctrl键
     */
    private fun handleCtrlKey() {
        // 可以在这里实现Ctrl键的功能
        // 例如切换特殊模式等
    }
    
    /**
     * 插入文本
     */
    private fun insertText(text: String) {
        try {
            editor?.let { editor ->
                // 获取当前光标位置
                val cursor = editor.cursor
                
                // 插入文本
                val line = cursor.leftLine
                val column = cursor.leftColumn
                editor.text.insert(line, column, text)
            }
        } catch (e: Exception) {
            android.util.Log.e("SymbolPanelView", "插入文本失败: ${e.message}")
        }
    }
    
    /**
     * 移动光标
     */
    private fun moveCursor(deltaX: Int, deltaY: Int) {
        try {
            editor?.let { editor ->
                // 获取当前光标位置
                val cursor = editor.cursor
                
                // 简单移动光标
                if (deltaY != 0) {
                    val newLine = cursor.leftLine + deltaY
                    if (newLine >= 0 && newLine < editor.text.lineCount) {
                        editor.setSelection(newLine, cursor.leftColumn)
                    }
                }
                
                if (deltaX != 0) {
                    val newColumn = cursor.leftColumn + deltaX
                    if (newColumn >= 0) {
                        editor.setSelection(cursor.leftLine, newColumn)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SymbolPanelView", "移动光标失败: ${e.message}")
        }
    }
} 