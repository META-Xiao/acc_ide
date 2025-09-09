package com.acc_ide.ui.terminal

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.content.res.ResourcesCompat
import com.acc_ide.R
import com.acc_ide.databinding.FragmentTerminalBinding
import kotlin.math.max
import kotlin.math.min

/**
 * Terminal Fragment for ACC IDE
 * Provides a terminal interface with Termux-like functionality
 */
class TerminalFragment : Fragment() {

    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!
    
    private var terminalTypeface: Typeface? = null
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentFontSize = 14f
    private var currentInput = ""
    private var prompt = "acc_ide:~$ "

    companion object {
        fun newInstance() = TerminalFragment()
        
        private const val TAG = "TerminalFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadTerminalFont()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTerminalView()
        setupInputHandling()
        setupGestureDetector()
        
        // 确保隐藏输入框在布局完成后处于适当状态
        view.post {
            binding.hiddenInput.clearFocus()
        }
    }

    private fun loadTerminalFont() {
        try {
            // 从 assets 加载字体
            terminalTypeface = Typeface.createFromAsset(
                requireContext().assets,
                "fonts/AgaveNerdFontMono-Regular.ttf"
            )
        } catch (e: Exception) {
            // 如果加载失败，使用系统等宽字体
            terminalTypeface = Typeface.MONOSPACE
        }
    }

    private fun setupTerminalView() {
        // 设置终端字体
        binding.terminalView.typeface = terminalTypeface
        
        // 设置字体大小 - 与 Termux 相同
        currentFontSize = 14f
        binding.terminalView.textSize = currentFontSize
        
        // 应用主题颜色
        applyThemeColors()
        
        // 初始显示欢迎信息
        showWelcomeMessage()
    }
    
    private fun applyThemeColors() {
        // 使用主题中定义的颜色
        val backgroundColor = ResourcesCompat.getColor(resources, android.R.color.black, null)
        val textColor = ResourcesCompat.getColor(resources, android.R.color.white, null)
        
        binding.terminalView.setBackgroundColor(backgroundColor)
        binding.terminalScrollView.setBackgroundColor(backgroundColor)
        binding.terminalView.setTextColor(textColor)
    }

    private fun setupInputHandling() {
        // 点击终端区域弹出键盘
        binding.terminalView.setOnClickListener {
            showKeyboard()
        }
        
        // 隐藏输入框处理实际的键盘输入
        binding.hiddenInput.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                
                val newText = s?.toString() ?: ""
                
                // 检查是否包含换行符（回车键）
                if (newText.contains('\n')) {
                    isUpdating = true
                    val commandText = newText.replace('\n', ' ').trim()
                    currentInput = commandText
                    executeCurrentCommand()
                    binding.hiddenInput.text.clear()
                    isUpdating = false
                } else {
                    // 正常输入，更新当前输入
                    currentInput = newText
                    updateTerminalDisplay()
                }
            }
        })
        
        // 处理特殊按键（如回车）
        binding.hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN) {
                currentInput = binding.hiddenInput.text.toString()
                executeCurrentCommand()
                true
            } else {
                false
            }
        }
        
        // 长按终端显示菜单
        binding.terminalView.setOnLongClickListener {
            showTerminalMenu()
            true
        }
    }

    private fun showWelcomeMessage() {
        val welcomeText = buildString {
            appendLine("┌──────────────────────────────────────────────────┐")
            appendLine("│                ACC IDE Terminal                  │")
            appendLine("├──────────────────────────────────────────────────┤")
            appendLine("│ Welcome to the integrated terminal environment   │")
            appendLine("│ This terminal will support Termux functionality │")
            appendLine("│ once the full integration is complete.          │")
            appendLine("└──────────────────────────────────────────────────┘")
            appendLine()
            appendLine("Available commands:")
            appendLine("  help        - Show available commands")
            appendLine("  clear       - Clear terminal screen") 
            appendLine("  font+       - Increase font size")
            appendLine("  font-       - Decrease font size")
            appendLine("  info        - Show system information")
            appendLine("  demo        - Run a demo compilation")
            appendLine()
            append(prompt)
        }
        
        binding.terminalView.text = welcomeText
        
        // 自动滚动到底部
        binding.terminalScrollView.post {
            binding.terminalScrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun showKeyboard() {
        // 延迟执行，确保 View 完全渲染
        binding.root.post {
            binding.hiddenInput.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.hiddenInput, InputMethodManager.SHOW_FORCED)
        }
    }
    
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.hiddenInput.windowToken, 0)
    }
    
    
    private fun executeCurrentCommand() {
        if (currentInput.isNotEmpty()) {
            executeCommand(currentInput)
            currentInput = ""
        } else {
            // 空命令只显示新的提示符
            val currentText = binding.terminalView.text.toString()
            binding.terminalView.text = currentText + "\n" + prompt
            scrollToBottom()
        }
        // 清空输入框，准备下一次输入
        binding.hiddenInput.text.clear()
    }
    
    private fun updateTerminalDisplay() {
        val currentText = binding.terminalView.text.toString()
        
        // 找到最后一个提示符的位置
        val lastPromptIndex = currentText.lastIndexOf(prompt)
        if (lastPromptIndex != -1) {
            // 移除之前的光标
            var cleanText = currentText
            if (cleanText.endsWith("_")) {
                cleanText = cleanText.substring(0, cleanText.length - 1)
            }
            
            // 更新显示：提示符 + 当前输入 + 光标
            val beforePrompt = cleanText.substring(0, lastPromptIndex + prompt.length)
            val displayText = if (currentInput.isNotEmpty()) {
                beforePrompt + currentInput + "_"
            } else {
                beforePrompt + "_"
            }
            binding.terminalView.text = displayText
        }
        scrollToBottom()
    }

    private fun showTerminalMenu() {
        val items = arrayOf(
            "New Session",
            "Font Size: ${currentFontSize.toInt()}sp", 
            "Reset Terminal",
            "Hide Keyboard",
            "Close Terminal"
        )
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Terminal Menu")
            .setItems(items) { dialog, which ->
                when (which) {
                    0 -> createNewSession()
                    1 -> adjustFontSize()
                    2 -> resetTerminal()
                    3 -> hideKeyboard()
                    4 -> closeTerminal()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun createNewSession() {
        // 添加新会话的文本
        val currentText = binding.terminalView.text.toString()
        val newSessionText = buildString {
            append(currentText)
            if (!currentText.endsWith("\n")) appendLine()
            appendLine()
            appendLine("═══════════════ New Session Started ═══════════════")
            appendLine("Session ID: ${System.currentTimeMillis()}")
            appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("═══════════════════════════════════════════════════")
            append(prompt)
        }
        
        binding.terminalView.text = newSessionText
        scrollToBottom()
    }
    
    private fun adjustFontSize() {
        val sizes = arrayOf("10sp", "12sp", "14sp", "16sp", "18sp", "20sp", "22sp", "24sp")
        val currentIndex = when (currentFontSize.toInt()) {
            10 -> 0; 12 -> 1; 14 -> 2; 16 -> 3
            18 -> 4; 20 -> 5; 22 -> 6; 24 -> 7
            else -> 2
        }
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Font Size")
            .setSingleChoiceItems(sizes, currentIndex) { dialog, which ->
                val newSize = (10 + which * 2).toFloat()
                currentFontSize = newSize
                binding.terminalView.textSize = newSize
                dialog.dismiss()
                Toast.makeText(context, "Font size set to ${newSize.toInt()}sp", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun resetTerminal() {
        binding.terminalView.text = ""
        currentInput = ""
        showWelcomeMessage()
        Toast.makeText(context, "Terminal reset", Toast.LENGTH_SHORT).show()
    }

    private fun closeTerminal() {
        hideKeyboard()
        parentFragmentManager.popBackStack()
    }

    private fun setupGestureDetector() {
        // 创建缩放手势检测器
        scaleGestureDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    val newSize = currentFontSize * scaleFactor
                    
                    // 限制字体大小范围 (8sp - 24sp)
                    currentFontSize = max(8f, min(newSize, 24f))
                    binding.terminalView.textSize = currentFontSize
                    
                    return true
                }
            }
        )
        
        // 设置终端区域的手势处理
        binding.terminalView.setOnTouchListener { view: View, event: MotionEvent ->
            scaleGestureDetector.onTouchEvent(event)
            
            // 如果不是缩放手势，处理单击事件
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    if (!scaleGestureDetector.isInProgress) {
                        view.performClick()
                        return@setOnTouchListener true
                    }
                }
            }
            true
        }
        
        // 设置滚动视图的触摸处理
        binding.terminalScrollView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            false // 让滚动视图继续处理滚动事件
        }
    }
    

    private fun scrollToBottom() {
        binding.terminalScrollView.post {
            binding.terminalScrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }
    
    // 模拟命令执行（用于演示）
    private fun executeCommand(command: String) {
        val currentText = binding.terminalView.text.toString()
        val result = when (command.trim().lowercase(java.util.Locale.getDefault())) {
            "help" -> """
                |Available commands:
                |  help        - Show this help message
                |  clear       - Clear the terminal
                |  font+       - Increase font size
                |  font-       - Decrease font size  
                |  info        - Show system information
                |  demo        - Run a demo compilation
                |  pwd         - Show current directory
                |  ls          - List directory contents
                |  date        - Show current date and time
                """.trimMargin()
                
            "clear" -> {
                binding.terminalView.text = ""
                showWelcomeMessage()
                return
            }
            
            "font+" -> {
                if (currentFontSize < 24f) {
                    currentFontSize += 2f
                    binding.terminalView.textSize = currentFontSize
                    "Font size increased to ${currentFontSize.toInt()}sp"
                } else {
                    "Font size is already at maximum (24sp)"
                }
            }
            
            "font-" -> {
                if (currentFontSize > 8f) {
                    currentFontSize -= 2f
                    binding.terminalView.textSize = currentFontSize
                    "Font size decreased to ${currentFontSize.toInt()}sp"
                } else {
                    "Font size is already at minimum (8sp)"
                }
            }
            
            "info" -> """
                |System Information:
                |Android Version: ${android.os.Build.VERSION.RELEASE}
                |Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
                |Architecture: ${android.os.Build.CPU_ABI}
                |ACC IDE Version: 1.4.0
                """.trimMargin()
                
            "demo" -> """
                |Running demo compilation...
                |[INFO] Compiling hello.cpp
                |[INFO] g++ -o hello hello.cpp
                |[INFO] Compilation successful
                |[INFO] Running ./hello
                |Hello, ACC IDE Terminal!
                |[INFO] Process finished with exit code 0
                """.trimMargin()
                
            "pwd" -> "/data/data/com.acc_ide/files/home"
            
            "ls" -> """
                |total 4
                |drwxr-xr-x 2 acc_ide acc_ide 4096 projects/
                |drwxr-xr-x 2 acc_ide acc_ide 4096 .acc_ide/
                |-rw-r--r-- 1 acc_ide acc_ide  128 hello.cpp
                |-rw-r--r-- 1 acc_ide acc_ide   64 main.py
                """.trimMargin()
                
            "date" -> java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            
            "" -> ""
            
            else -> "$command: command not found. Type 'help' for available commands."
        }
        
        val newText = buildString {
            append(currentText)
            // 移除光标
            if (endsWith("_")) {
                setLength(length - 1)
            }
            
            if (command.isNotEmpty()) {
                appendLine(command)
            }
            if (result.isNotEmpty()) {
                appendLine(result)
            }
            append(prompt)
        }
        
        binding.terminalView.text = newText
        scrollToBottom()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
