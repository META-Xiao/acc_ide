package com.acc_ide.compiler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.TimeUnit

/**
 * 编译器服务 - 负责执行本地编译和运行
 * 基于TinyCC, ECJ, Python的轻量级实现
 */
class CompilerService(private val context: Context) {
    
    private val TAG = "CompilerService"
    private val compilerManager = CompilerManager(context)
    private val workDir = File(context.filesDir, "workspace")
    private val toolchainDir = File(context.filesDir, "toolchain")
    
    // Java专用编译服务
    private val javaCompilerService = JavaCompilerService(context)
    
    init {
        // 确保必要的目录存在
        workDir.mkdirs()
        toolchainDir.mkdirs()
    }
    
    /**
     * 编译并运行代码 - 统一入口
     */
    suspend fun compileAndRun(
        code: String,
        language: Language,
        onOutput: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ): CompileResult = withContext(Dispatchers.IO) {
        
        if (!compilerManager.isCompilerInstalled(language)) {
            return@withContext CompileResult.error("${language.displayName} compiler is not installed")
        }
        
        try {
            when (language) {
                Language.C, Language.CPP -> compileCpp(code, language, onOutput, onError)
                Language.JAVA -> javaCompilerService.executeJava(code, onOutput, onError)
                Language.PYTHON -> runPython(code, onOutput, onError)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Compilation failed for $language", e)
            CompileResult.error("Compilation failed: ${e.message}")
        }
    }
    
    /**
     * TinyCC C/C++ 编译执行
     */
    private suspend fun compileCpp(
        code: String, 
        language: Language, 
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): CompileResult {
        val sourceFile = File(workDir, "main.${language.extension}")
        
        // 写入源代码文件
        sourceFile.writeText(code)
        
        // TinyCC编译命令 - 使用 -run 模式直接执行
        val tccPath = File(toolchainDir, "tinycc/tcc").absolutePath
        val compileCmd = listOf(
            tccPath,
            "-run", // TinyCC的直接运行模式，无需生成可执行文件
            sourceFile.absolutePath
        )
        
        return executeCommand(compileCmd, onOutput, onError)
    }
    
    /**
     * ECJ Java编译执行
     * 注意：Android上不能直接运行标准Java字节码，这里只做编译检查
     */
    private suspend fun compileJava(
        code: String,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): CompileResult {
        val sourceFile = File(workDir, "Main.java")
        
        // 写入Java源代码
        sourceFile.writeText(code)
        
        // ECJ编译 - 只检查语法和编译错误
        val ecjPath = File(toolchainDir, "java/ecj.jar").absolutePath
        val compileCmd = listOf(
            "java", "-jar", ecjPath,
            "-d", workDir.absolutePath,
            "-classpath", getAndroidClasspath(),
            "-source", "8", // 兼容Java 8语法
            "-target", "8",
            "-nowarn", // 减少警告信息
            sourceFile.absolutePath
        )
        
        val compileResult = executeCommand(compileCmd, onOutput, onError)
        
        if (compileResult.success) {
            val successMessage = "Java编译成功！\n注意：Android环境下无法直接执行Java字节码，建议转换为Android项目或使用其他语言。"
            onOutput(successMessage)
            return CompileResult.success(successMessage)
        } else {
            return CompileResult.error("Java编译失败", compileResult.error)
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
            // 回退到基本的Java运行时类
            System.getProperty("java.class.path") ?: ""
        }
    }
    
    /**
     * Python解释器运行
     */
    private suspend fun runPython(
        code: String,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): CompileResult {
        val scriptFile = File(workDir, "script.py")
        scriptFile.writeText(code)
        
        // 使用Python包装器脚本，它会设置正确的环境变量
        val pythonWrapperPath = File(toolchainDir, "python/python").absolutePath
        val directPythonPath = File(toolchainDir, "python/bin/python3").absolutePath
        
        // 优先使用包装器脚本，回退到直接路径
        val pythonPath = if (File(pythonWrapperPath).exists()) {
            pythonWrapperPath
        } else {
            directPythonPath
        }
        
        val runCmd = listOf(pythonPath, scriptFile.absolutePath)
        
        return executeCommand(runCmd, onOutput, onError)
    }
    
    /**
     * 检查编译器是否已安装
     */
    fun isCompilerInstalled(language: Language): Boolean {
        return compilerManager.isCompilerInstalled(language)
    }
    
    /**
     * 获取编译器信息
     */
    suspend fun getCompilerInfo(language: Language): CompilerInfo = withContext(Dispatchers.IO) {
        return@withContext compilerManager.getCompilerInfo(language)
    }
    
    /**
     * 执行系统命令
     */
    private suspend fun executeCommand(
        command: List<String>,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): CompileResult = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(workDir)
            
            // 设置环境变量
            val env = processBuilder.environment()
            env["PATH"] = "${toolchainDir.absolutePath}/bin:${env["PATH"]}"
            env["LD_LIBRARY_PATH"] = "${toolchainDir.absolutePath}/lib:${env["LD_LIBRARY_PATH"] ?: ""}"
            env["TMPDIR"] = File(context.cacheDir, "tmp").apply { mkdirs() }.absolutePath
            
            // Python特殊环境变量设置
            val pythonDir = File(toolchainDir, "python")
            if (pythonDir.exists() && command.firstOrNull()?.contains("python") == true) {
                env["PYTHONHOME"] = pythonDir.absolutePath
                env["PYTHONPATH"] = "${pythonDir.absolutePath}/lib/python3.11:${pythonDir.absolutePath}/lib/python3.11/site-packages"
                env["PYTHONDONTWRITEBYTECODE"] = "1" // 避免写入.pyc文件
                env["PYTHONUNBUFFERED"] = "1" // 确保输出不被缓冲
                
                // 创建Python临时目录
                val pythonTmpDir = File(context.cacheDir, "python_tmp")
                pythonTmpDir.mkdirs()
                env["PYTHONUSERBASE"] = pythonTmpDir.absolutePath
            }
            
            Log.d(TAG, "Executing: ${command.joinToString(" ")}")
            
            val process = processBuilder.start()
            
            // 异步读取输出
            val outputJob = async {
                process.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        onOutput(line!! + "\n")
                    }
                }
            }
            
            val errorJob = async {
                process.errorStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        onError(line!! + "\n")
                    }
                }
            }
            
            // 等待进程完成，最多30秒
            val finished = withTimeoutOrNull(30000) {
                process.waitFor()
                true
            }
            
            if (finished != true) {
                process.destroyForcibly()
                return@withContext CompileResult.error("Process timeout after 30 seconds")
            }
            
            // 等待输出读取完成
            outputJob.await()
            errorJob.await()
            
            val exitCode = process.exitValue()
            if (exitCode == 0) {
                CompileResult.success("Execution completed successfully")
            } else {
                CompileResult.error("Process exited with code $exitCode")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            CompileResult.error("Execution failed: ${e.message}")
        }
    }
}

/**
 * 编译结果数据类
 */
data class CompileResult(
    val success: Boolean,
    val message: String,
    val output: String = "",
    val error: String = ""
) {
    companion object {
        fun success(message: String, output: String = "") = CompileResult(true, message, output)
        fun error(message: String, error: String = "") = CompileResult(false, message, "", error)
    }
}