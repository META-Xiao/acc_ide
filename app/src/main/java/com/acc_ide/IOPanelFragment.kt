package com.acc_ide

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class IOPanelFragment : Fragment() {
    private lateinit var runCodeButton: MaterialButton
    private lateinit var runStatus: TextView
    private lateinit var inputText: TextInputEditText
    private lateinit var actualOutputText: TextInputEditText
    private lateinit var expectedOutputText: TextInputEditText
    
    private var fileName: String = ""
    private var language: String = ""

    // 用于存储IO实例的缓存
    companion object {
        private const val ARG_FILENAME = "filename"
        private const val ARG_LANGUAGE = "language"
        
        // 缓存每个文件的IO实例
        private val ioCache = mutableMapOf<String, IOInstance>()

        @JvmStatic
        fun newInstance(fileName: String, language: String) =
            IOPanelFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILENAME, fileName)
                    putString(ARG_LANGUAGE, language)
                }
            }
    }
    
    // IO实例数据类
    data class IOInstance(
        var input: String = "",
        var actualOutput: String = "",
        var expectedOutput: String = "",
        var status: String = ""
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_io_panel, container, false)
        
        // 获取参数
        arguments?.let {
            fileName = it.getString(ARG_FILENAME, "")
            language = it.getString(ARG_LANGUAGE, "")
        }
        
        // 初始化视图
        runCodeButton = view.findViewById(R.id.run_code_button)
        runStatus = view.findViewById(R.id.run_status)
        inputText = view.findViewById(R.id.input_text)
        actualOutputText = view.findViewById(R.id.actual_output_text)
        expectedOutputText = view.findViewById(R.id.expected_output_text)
        
        // 设置初始状态
        runStatus.text = ""
        
        // 从缓存加载数据
        loadFromCache()
        
        // 设置事件监听
        setupListeners()
        
        return view
    }
    
    private fun setupListeners() {
        // 运行代码按钮
        runCodeButton.setOnClickListener {
            // 模拟代码运行
            simulateCodeExecution()
        }
    }
    
    private fun loadFromCache() {
        // 从缓存加载数据
        val cachedInstance = ioCache[fileName]
        if (cachedInstance != null) {
            inputText.setText(cachedInstance.input)
            actualOutputText.setText(cachedInstance.actualOutput)
            expectedOutputText.setText(cachedInstance.expectedOutput)
            if (cachedInstance.status.isNotEmpty()) {
                runStatus.text = cachedInstance.status
                setStatusColor(cachedInstance.status)
            }
        }
    }
    
    private fun saveToCache() {
        // 保存数据到缓存
        ioCache[fileName] = IOInstance(
            input = inputText.text.toString(),
            actualOutput = actualOutputText.text.toString(),
            expectedOutput = expectedOutputText.text.toString(),
            status = runStatus.text.toString()
        )
    }
    
    private fun simulateCodeExecution() {
        // 获取输入
        val input = inputText.text.toString()
        val expected = expectedOutputText.text.toString()
        
        // 模拟代码运行
        val mainActivity = activity as MainActivity
        val code = mainActivity.files[fileName] ?: ""
        
        // 这里只是模拟，实际应该调用编译器
        val output = when (language) {
            "cpp" -> simulateCppExecution(code, input)
            "python" -> simulatePythonExecution(code, input)
            "java" -> simulateJavaExecution(code, input)
            else -> "不支持的语言: $language"
        }
        
        // 显示输出
        actualOutputText.setText(output)
        
        // 设置运行状态
        setRunStatus(output, expected)
        
        // 保存到缓存
        saveToCache()
    }
    
    private fun simulateCppExecution(code: String, input: String): String {
        // 模拟C++代码执行
        return if (code.contains("cout") || code.contains("printf")) {
            "模拟C++输出:\n" + input.split("\n").joinToString("\n") { "处理: $it" }
        } else {
            "// 编译错误: 代码中没有输出语句"
        }
    }
    
    private fun simulatePythonExecution(code: String, input: String): String {
        // 模拟Python代码执行
        return if (code.contains("print")) {
            "模拟Python输出:\n" + input.split("\n").joinToString("\n") { "处理: $it" }
        } else {
            "# 错误: 代码中没有输出语句"
        }
    }
    
    private fun simulateJavaExecution(code: String, input: String): String {
        // 模拟Java代码执行
        return if (code.contains("System.out")) {
            "模拟Java输出:\n" + input.split("\n").joinToString("\n") { "处理: $it" }
        } else {
            "// 编译错误: 代码中没有输出语句"
        }
    }
    
    private fun setRunStatus(output: String, expected: String) {
        val status = if (output.contains("错误") || output.contains("编译错误")) {
            // 编译错误
            "CE"
        } else if (normalizeOutput(output) == normalizeOutput(expected)) {
            // 答案正确
            "AC"
        } else {
            // 答案错误
            "WA"
        }
        
        runStatus.text = status
        setStatusColor(status)
    }
    
    private fun setStatusColor(status: String) {
        when (status) {
            "CE" -> runStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            "AC" -> runStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            "WA" -> runStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
        }
    }
    
    private fun normalizeOutput(text: String): String {
        // 简单处理输出，忽略前缀和空白
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
    
    override fun onPause() {
        super.onPause()
        // 保存到缓存
        saveToCache()
    }
} 