package com.acc_ide.view

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.acc_ide.R

/**
 * Editor search panel custom view for in-file text search
 * 编辑器搜索面板自定义视图 - 提供当前文件内文本搜索交互
 */
class EditorSearchPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    private val searchInput: EditText
    private val searchCounter: TextView
    private val searchPrevButton: ImageButton
    private val searchNextButton: ImageButton
    private val searchCloseButton: ImageButton

    var onQueryChanged: ((String) -> Unit)? = null
    var onSearchAction: ((String) -> Unit)? = null
    var onPreviousClick: (() -> Unit)? = null
    var onNextClick: (() -> Unit)? = null
    var onCloseClick: (() -> Unit)? = null

    init {
        radius = context.resources.getDimension(R.dimen.search_panel_corner_radius)
        cardElevation = context.resources.getDimension(R.dimen.search_panel_elevation)
        setCardBackgroundColor(resolveSurfaceColor())

        LayoutInflater.from(context).inflate(R.layout.view_editor_search_panel, this, true)
        searchInput = findViewById(R.id.search_input)
        searchCounter = findViewById(R.id.search_counter)
        searchPrevButton = findViewById(R.id.search_prev_button)
        searchNextButton = findViewById(R.id.search_next_button)
        searchCloseButton = findViewById(R.id.search_close_button)

        setupListeners()
        visibility = View.GONE
    }

    /**
     * Setup search panel listeners
     * 设置搜索面板监听器
     */
    private fun setupListeners() {
        searchPrevButton.setOnClickListener {
            onPreviousClick?.invoke()
        }
        searchNextButton.setOnClickListener {
            onNextClick?.invoke()
        }
        searchCloseButton.setOnClickListener {
            onCloseClick?.invoke()
        }
        searchInput.setOnEditorActionListener { _, _, _ ->
            onSearchAction?.invoke(searchInput.text?.toString().orEmpty())
            true
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onQueryChanged?.invoke(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    /**
     * Resolve surface color from theme
     * 从主题中解析 surface 颜色
     */
    private fun resolveSurfaceColor(): Int {
        val typedArray = context.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorSurface))
        return try {
            typedArray.getColor(0, 0)
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * Show search panel and focus input
     * 显示搜索面板并聚焦输入框
     */
    fun show() {
        visibility = View.VISIBLE
        searchInput.requestFocus()
        searchInput.post {
            searchInput.setSelection(searchInput.text?.length ?: 0)
        }
    }

    /**
     * Restore focus to search input
     * 恢复搜索输入框焦点
     */
    fun restoreInputFocus() {
        searchInput.post {
            searchInput.requestFocus()
            searchInput.setSelection(searchInput.text?.length ?: 0)
        }
    }

    /**
     * Hide search panel and clear input
     * 隐藏搜索面板并清空输入
     */
    fun hide() {
        visibility = View.GONE
        searchInput.setText("")
        updateCounter(null)
    }

    /**
     * Update current search result counter
     * 更新当前搜索结果计数
     */
    fun updateCounter(counterText: String?) {
        searchCounter.text = counterText.orEmpty()
    }

    /**
     * Get current search query
     * 获取当前搜索关键字
     */
    fun getQuery(): String = searchInput.text?.toString().orEmpty()
}

