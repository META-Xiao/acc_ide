package com.acc_ide.compiler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*

/**
 * Java专用编译服务
 * 提供多种Java代码处理方式：
 * 1. ECJ编译检查（语法验证）
 * 2. 简单的Java代码解释执行（有限功能）
 */
class JavaCompilerService(private val context: Context) {
    
    private val TAG = "JavaCompilerService"
    private val workDir = File(context.filesDir, "java_workspace")
    private val toolchainDir = File(context.filesDir, "toolchain")
    
    init {
        workDir.mkdirs()
    }
    
    /**
     * 执行Java代码（多种方式）
     */
    suspend fun executeJava(
        code: String,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): CompileResult = withContext(Dispatchers.IO) {
        
        // 首先尝试ECJ编译检查
        val compileResult = compileWithECJ(code, onOutput, onError)
        if (!compileResult.success) {
            return@withContext compileResult
        }
        
        // 编译成功，尝试简单解释执行（仅支持基本功能）
        return@withContext trySimpleExecution(code, onOutput, onError)
    }
    
    /**
     * 使用ECJ进行编译检查
     */
    private suspend fun compileWithECJ(
        code: String,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): CompileResult {
        val sourceFile = File(workDir, "Main.java")
        sourceFile.writeText(code)
        
        val ecjPath = File(toolchainDir, "java/ecj.jar").absolutePath
        val compileCmd = listOf(
            "java", "-jar", ecjPath,
            "-d", workDir.absolutePath,
            "-classpath", getAndroidClasspath(),
            "-source", "8",
            "-target", "8",
            "-nowarn",
            sourceFile.absolutePath
        )
        
        return try {
            val processBuilder = ProcessBuilder(compileCmd)
            processBuilder.directory(workDir)
            val process = processBuilder.start()
            
            // 读取编译输出
            val outputBuffer = StringBuilder()
            val errorBuffer = StringBuilder()
            
            val outputJob = GlobalScope.async {
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        outputBuffer.appendLine(line)
                        onOutput("$line\n")
                    }
                }
            }
            
            val errorJob = GlobalScope.async {
                process.errorStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        errorBuffer.appendLine(line)
                        onError("$line\n")
                    }
                }
            }
            
            val finished = withTimeoutOrNull(15000) {
                process.waitFor()
                true
            }
            
            if (finished != true) {
                process.destroyForcibly()
                CompileResult.error("编译超时")
            } else {
                outputJob.await()
                errorJob.await()
                
                val exitCode = process.exitValue()
                if (exitCode == 0) {
                    CompileResult.success("Java编译成功")
                } else {
                    CompileResult.error("编译失败", errorBuffer.toString())
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "ECJ compilation failed", e)
            CompileResult.error("编译异常: ${e.message}")
        }
    }
    
    /**
     * 尝试简单的Java代码执行（非常有限）
     * 只支持基本的System.out.println等
     */
    private fun trySimpleExecution(
        code: String,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): CompileResult {
        try {
            // 简单的模式匹配来执行基本的Java语句
            val lines = code.lines()
            var hasMainMethod = false
            val executableLines = mutableListOf<String>()
            
            for (line in lines) {
                val trimmedLine = line.trim()
                
                // 检测main方法
                if (trimmedLine.contains("public static void main")) {
                    hasMainMethod = true
                    continue
                }
                
                // 在main方法内的可执行语句
                if (hasMainMethod && trimmedLine.startsWith("System.out.print")) {
                    executableLines.add(trimmedLine)
                }
                
                // 退出main方法
                if (hasMainMethod && trimmedLine == "}") {
                    break
                }
            }
            
            if (executableLines.isEmpty()) {
                val message = "Java代码编译成功，但无法在Android环境下执行。\n" +
                        "建议：\n" +
                        "1. 使用C++或Python进行算法练习\n" +
                        "2. 将Java代码转换为Android项目\n" +
                        "3. 使用在线Java编译器"
                onOutput(message)
                return CompileResult.success(message)
            }
            
            // 执行简单的打印语句
            for (line in executableLines) {
                if (line.contains("System.out.print")) {
                    val output = extractPrintContent(line)
                    onOutput("$output\n")
                }
            }
            
            val message = "简单Java语句执行完成（仅支持System.out.print）"
            return CompileResult.success(message)
            
        } catch (e: Exception) {
            Log.e(TAG, "Simple Java execution failed", e)
            val message = "Java代码编译成功，但执行失败：${e.message}\n" +
                    "Android环境对Java字节码执行有限制。"
            onError(message)
            return CompileResult.error(message)
        }
    }
    
    /**
     * 从System.out.print语句中提取输出内容
     */
    private fun extractPrintContent(line: String): String {
        return try {
            val start = line.indexOf("(\"") + 2
            val end = line.lastIndexOf("\")")
            if (start > 1 && end > start) {
                line.substring(start, end)
            } else {
                "[无法解析的输出]"
            }
        } catch (e: Exception) {
            "[解析错误]"
        }
    }
    
    /**
     * 获取Android兼容的类路径
     */
    private fun getAndroidClasspath(): String {
        val androidJar = File(toolchainDir, "java/android.jar")
        return if (androidJar.exists()) {
            androidJar.absolutePath
        } else {
            System.getProperty("java.class.path") ?: ""
        }
    }
}