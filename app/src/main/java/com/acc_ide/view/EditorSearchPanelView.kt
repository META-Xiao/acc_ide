package com.acc_ide.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
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
    private val replaceInput: EditText
    private val searchCounter: TextView
    private val searchPrevButton: ImageButton
    private val searchNextButton: ImageButton
    private val searchCloseButton: ImageButton
    private val toggleReplaceButton: ImageButton
    private val replaceContainer: LinearLayout
    private val replaceButton: ImageButton
    private val replaceAllButton: ImageButton
    private var isReplaceVisible = false

    var onQueryChanged: ((String) -> Unit)? = null
    var onSearchAction: ((String) -> Unit)? = null
    var onPreviousClick: (() -> Unit)? = null
    var onNextClick: (() -> Unit)? = null
    var onCloseClick: (() -> Unit)? = null
    var onReplaceClick: ((String) -> Unit)? = null
    var onReplaceAllClick: ((String) -> Unit)? = null

    init {
        radius = context.resources.getDimension(R.dimen.search_panel_corner_radius)
        cardElevation = context.resources.getDimension(R.dimen.search_panel_elevation)
        setCardBackgroundColor(resolveSurfaceColor())

        LayoutInflater.from(context).inflate(R.layout.view_editor_search_panel, this, true)
        searchInput = findViewById(R.id.search_input)
        replaceInput = findViewById(R.id.replace_input)
        searchCounter = findViewById(R.id.search_counter)
        searchPrevButton = findViewById(R.id.search_prev_button)
        searchNextButton = findViewById(R.id.search_next_button)
        searchCloseButton = findViewById(R.id.search_close_button)
        toggleReplaceButton = findViewById(R.id.toggle_replace_button)
        replaceContainer = findViewById(R.id.replace_container)
        replaceButton = findViewById(R.id.replace_button)
        replaceAllButton = findViewById(R.id.replace_all_button)

        setupListeners()
        applyThemeColors()
        updateReplaceVisibility(false)
        visibility = View.GONE
    }

    /**
     * Setup search panel listeners
     * 设置搜索面板监听器
     */
    private fun setupListeners() {
        toggleReplaceButton.setOnClickListener {
            updateReplaceVisibility(!isReplaceVisible)
        }
        searchPrevButton.setOnClickListener {
            onPreviousClick?.invoke()
        }
        searchNextButton.setOnClickListener {
            onNextClick?.invoke()
        }
        replaceButton.setOnClickListener {
            onReplaceClick?.invoke(replaceInput.text?.toString().orEmpty())
        }
        replaceAllButton.setOnClickListener {
            onReplaceAllClick?.invoke(replaceInput.text?.toString().orEmpty())
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
     * Update replace panel visibility state
     * 更新替换区域显示状态
     */
    private fun updateReplaceVisibility(visible: Boolean) {
        isReplaceVisible = visible
        replaceContainer.visibility = if (visible) View.VISIBLE else View.GONE
        toggleReplaceButton.setImageResource(
            if (visible) R.drawable.baseline_keyboard_arrow_up_24
            else R.drawable.baseline_keyboard_arrow_down_24
        )
        tintIcon(toggleReplaceButton)
    }

    fun applyThemeColors() {
        val surfaceColor = resolveThemeColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
        val hintColor = resolveThemeColor(android.R.attr.textColorHint)

        setCardBackgroundColor(surfaceColor)
        searchInput.setTextColor(onSurfaceColor)
        replaceInput.setTextColor(onSurfaceColor)
        searchInput.setHintTextColor(hintColor)
        replaceInput.setHintTextColor(hintColor)
        searchCounter.setTextColor(onSurfaceColor)

        listOf(
            searchPrevButton,
            searchNextButton,
            searchCloseButton,
            toggleReplaceButton,
            replaceButton,
            replaceAllButton
        ).forEach(::tintIcon)
    }

    private fun tintIcon(button: ImageButton) {
        val color = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
        button.imageTintList = ColorStateList.valueOf(color)
        button.drawable?.mutate()?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun resolveThemeColor(attr: Int): Int {
        val typedArray = context.obtainStyledAttributes(intArrayOf(attr))
        return try {
            typedArray.getColor(0, 0)
        } finally {
            typedArray.recycle()
        }
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
        updateReplaceVisibility(false)
        searchInput.setText("")
        replaceInput.setText("")
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
     * Set search query text
     * 设置搜索关键字
     */
    fun setQuery(query: String) {
        searchInput.setText(query)
        searchInput.setSelection(searchInput.text?.length ?: 0)
    }

    /**
     * Get current search query
     * 获取当前搜索关键字
     */
    fun getQuery(): String = searchInput.text?.toString().orEmpty()
}
