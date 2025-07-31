package com.acc_ide.lsp

import android.content.Context
import androidx.lifecycle.lifecycleScope
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.launch

/**
 * LSP使用示例
 * LSP Usage Example
 */
class LspUsageExample(private val context: Context) {
    
    private val lspManager = LspManager.getInstance(context)
    
    /**
     * 为编辑器启用Java LSP支持的示例
     */
    fun enableJavaLsp(editor: CodeEditor, fileName: String = "Main.java") {
        // 检查是否支持Java
        if (lspManager.isLanguageSupported("java")) {
            // 启用LSP支持
            val success = lspManager.enableLspForEditor(editor, "java")
            if (success) {
                println("Java LSP enabled successfully for $fileName")
            } else {
                println("Failed to enable Java LSP for $fileName")
            }
        } else {
            println("Java language is not supported for LSP")
        }
    }
    
    /**
     * 为编辑器启用Python LSP支持的示例
     */
    fun enablePythonLsp(editor: CodeEditor, fileName: String = "main.py") {
        if (lspManager.isLanguageSupported("python")) {
            val success = lspManager.enableLspForEditor(editor, "python")
            if (success) {
                println("Python LSP enabled successfully for $fileName")
            } else {
                println("Failed to enable Python LSP for $fileName")
            }
        }
    }
    
    /**
     * 为编辑器启用C++ LSP支持的示例
     */
    fun enableCppLsp(editor: CodeEditor, fileName: String = "main.cpp") {
        if (lspManager.isLanguageSupported("cpp")) {
            val success = lspManager.enableLspForEditor(editor, "cpp")
            if (success) {
                println("C++ LSP enabled successfully for $fileName")
            } else {
                println("Failed to enable C++ LSP for $fileName")
            }
        }
    }
    
    /**
     * 设置编辑器内容并启用相应的LSP支持
     */
    fun setupEditorWithLsp(editor: CodeEditor, content: String, language: String, fileName: String) {
        // 设置编辑器内容
        editor.setText(content)
        
        // 启用LSP支持
        when (language.lowercase()) {
            "java" -> enableJavaLsp(editor, fileName)
            "python", "py" -> enablePythonLsp(editor, fileName)
            "cpp", "c" -> enableCppLsp(editor, fileName)
            else -> println("Unsupported language: $language")
        }
    }
    
    /**
     * 获取LSP状态信息
     */
    fun getLspStatus() {
        val javaStatus = lspManager.getLspServerStatus("java")
        val pythonStatus = lspManager.getLspServerStatus("python")
        val cppStatus = lspManager.getLspServerStatus("cpp")
        
        println("LSP Status:")
        println("  Java: $javaStatus")
        println("  Python: $pythonStatus")
        println("  C++: $cppStatus")
        
        // 显示各语言的端口信息作为会话状态
        val javaPort = lspManager.getLspPort("java")
        val pythonPort = lspManager.getLspPort("python") 
        val cppPort = lspManager.getLspPort("cpp")
        println("LSP Ports:")
        println("  Java: $javaPort")
        println("  Python: $pythonPort")
        println("  C++: $cppPort")
    }
    
    /**
     * 清理LSP资源
     */
    fun cleanup() {
        lspManager.dispose()
        println("LSP resources cleaned up")
    }
} 