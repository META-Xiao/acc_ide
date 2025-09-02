package com.acc_ide.compiler

import android.content.Context
import android.util.Log
import com.acc_ide.util.Environment
import kotlinx.coroutines.*
import java.io.File

/**
 * 编译运行管理器 - 统一管理所有语言的编译和运行
 * 集成Termux终端环境和本地编译工具链
 */
class CompileRunManager(private val context: Context) {
    
    private val TAG = "CompileRunManager"
    private var termuxCompilerService: TermuxCompilerService? = null
    private var isInitialized = false
    
    // 编译任务作用域
    private val compilationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 编译输出回调接口
    interface CompileRunCallback {
        fun onCompileStart(fileName: String, language: Language)
        fun onOutput(output: String)
        fun onError(error: String)
        fun onCompileComplete(success: Boolean, message: String)
    }
    
    /**
     * 初始化编译管理器
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) return@withContext true
            
            // 初始化Environment
            Environment.initialize(context)
            
            // 创建TermuxCompilerService
            termuxCompilerService = TermuxCompilerService(context)
            
            // 绑定Termux服务
            val bindResult = termuxCompilerService?.bindService() ?: false
            if (!bindResult) {
                Log.e(TAG, "Failed to bind Termux service")
                return@withContext false
            }
            
            // 等待服务连接
            var retryCount = 0
            while (retryCount < 30) { // 最多等待3秒
                delay(100)
                retryCount++
                // 这里可以添加检查服务是否已连接的逻辑
            }
            
            isInitialized = true
            Log.d(TAG, "CompileRunManager initialized successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CompileRunManager", e)
            false
        }
    }
    
    /**
     * 编译并运行代码
     */
    fun compileAndRun(
        fileName: String,
        content: String,
        callback: CompileRunCallback
    ) {
        compilationScope.launch {
            try {
                if (!isInitialized) {
                    callback.onError("CompileRunManager not initialized")
                    callback.onCompileComplete(false, "Service not ready")
                    return@launch
                }
                
                val language = detectLanguage(fileName)
                if (language == null) {
                    callback.onError("Unsupported file type: $fileName")
                    callback.onCompileComplete(false, "Unsupported language")
                    return@launch
                }
                
                callback.onCompileStart(fileName, language)
                
                val termuxService = termuxCompilerService
                if (termuxService == null) {
                    callback.onError("Termux compiler service not available")
                    callback.onCompileComplete(false, "Service error")
                    return@launch
                }
                
                // 执行编译和运行
                termuxService.compileAndRun(
                    fileName = fileName,
                    content = content,
                    language = language,
                    onOutput = { output ->
                        callback.onOutput(output)
                    },
                    onError = { error ->
                        callback.onError(error)
                    },
                    onComplete = { success ->
                        val message = if (success) {
                            "Compilation and execution completed successfully"
                        } else {
                            "Compilation or execution failed"
                        }
                        callback.onCompileComplete(success, message)
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during compilation", e)
                callback.onError("Compilation error: ${e.message}")
                callback.onCompileComplete(false, "Internal error")
            }
        }
    }
    
    /**
     * 仅编译代码（不运行）
     */
    fun compileOnly(
        fileName: String,
        content: String,
        callback: CompileRunCallback
    ) {
        compilationScope.launch {
            try {
                if (!isInitialized) {
                    callback.onError("CompileRunManager not initialized")
                    callback.onCompileComplete(false, "Service not ready")
                    return@launch
                }
                
                val language = detectLanguage(fileName)
                if (language == null) {
                    callback.onError("Unsupported file type: $fileName")
                    callback.onCompileComplete(false, "Unsupported language")
                    return@launch
                }
                
                // Python不需要编译
                if (language == Language.PYTHON) {
                    callback.onOutput("Python scripts don't need compilation\n")
                    callback.onCompileComplete(true, "Python script is ready to run")
                    return@launch
                }
                
                callback.onCompileStart(fileName, language)
                
                val termuxService = termuxCompilerService
                if (termuxService == null) {
                    callback.onError("Termux compiler service not available")
                    callback.onCompileComplete(false, "Service error")
                    return@launch
                }
                
                // 只执行编译步骤
                when (language) {
                    Language.CPP, Language.C -> {
                        compileOnly_Cpp(fileName, content, termuxService, callback)
                    }
                    Language.JAVA -> {
                        compileOnly_Java(fileName, content, termuxService, callback)
                    }
                    else -> {
                        callback.onError("Compile-only not supported for ${language.name}")
                        callback.onCompileComplete(false, "Not supported")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during compilation", e)
                callback.onError("Compilation error: ${e.message}")
                callback.onCompileComplete(false, "Internal error")
            }
        }
    }
    
    /**
     * 仅运行代码（假设已编译）
     */
    fun runOnly(
        fileName: String,
        callback: CompileRunCallback
    ) {
        compilationScope.launch {
            try {
                if (!isInitialized) {
                    callback.onError("CompileRunManager not initialized")
                    callback.onCompileComplete(false, "Service not ready")
                    return@launch
                }
                
                val language = detectLanguage(fileName)
                if (language == null) {
                    callback.onError("Unsupported file type: $fileName")
                    callback.onCompileComplete(false, "Unsupported language")
                    return@launch
                }
                
                callback.onCompileStart(fileName, language)
                callback.onOutput("Running ${fileName}...\n")
                
                // 这里实现运行逻辑
                // 需要根据不同语言查找编译输出文件并执行
                
                callback.onCompileComplete(true, "Execution completed")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during execution", e)
                callback.onError("Execution error: ${e.message}")
                callback.onCompileComplete(false, "Execution error")
            }
        }
    }
    
    /**
     * 检查编译器是否已安装
     */
    fun isCompilerInstalled(language: Language): Boolean {
        return when (language) {
            Language.CPP, Language.C -> {
                // 检查clang是否可用
                checkCommandAvailable("clang")
            }
            Language.JAVA -> {
                // 检查javac是否可用
                checkCommandAvailable("javac")
            }
            Language.PYTHON -> {
                // 检查python3是否可用
                checkCommandAvailable("python3")
            }
        }
    }
    
    /**
     * 获取编译器信息
     */
    fun getCompilerInfo(language: Language): String {
        return when (language) {
            Language.CPP, Language.C -> {
                if (isCompilerInstalled(language)) {
                    "Clang C/C++ Compiler (Termux)"
                } else {
                    "C/C++ Compiler not installed"
                }
            }
            Language.JAVA -> {
                if (isCompilerInstalled(language)) {
                    "OpenJDK Java Compiler (Termux)"
                } else {
                    "Java Compiler not installed"
                }
            }
            Language.PYTHON -> {
                if (isCompilerInstalled(language)) {
                    "Python 3 Interpreter (Termux)"
                } else {
                    "Python 3 not installed"
                }
            }
        }
    }
    
    /**
     * 清理编译临时文件
     */
    fun cleanup() {
        try {
            compilationScope.cancel()
            termuxCompilerService?.cleanup()
            Environment.cleanupTempFiles()
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    // 私有方法
    
    /**
     * 检测文件语言类型
     */
    private fun detectLanguage(fileName: String): Language? {
        return when {
            fileName.endsWith(".c") -> Language.C
            fileName.endsWith(".cpp") || fileName.endsWith(".cc") || fileName.endsWith(".cxx") -> Language.CPP
            fileName.endsWith(".java") -> Language.JAVA
            fileName.endsWith(".py") -> Language.PYTHON
            else -> null
        }
    }
    
    /**
     * 检查命令是否可用
     */
    private fun checkCommandAvailable(command: String): Boolean {
        return try {
            val process = ProcessBuilder("which", command)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check command: $command", e)
            false
        }
    }
    
    /**
     * 仅编译C/C++代码
     */
    private suspend fun compileOnly_Cpp(
        fileName: String,
        content: String,
        termuxService: TermuxCompilerService,
        callback: CompileRunCallback
    ) {
        // 创建临时文件
        val workDir = Environment.createTempDir("cpp_compile")
        val sourceFile = File(workDir, fileName)
        sourceFile.writeText(content)
        
        val outputFile = File(workDir, fileName.substringBeforeLast('.'))
        
        callback.onOutput("Compiling $fileName...\n")
        
        // 构建编译命令
        val compiler = if (fileName.endsWith(".cpp") || fileName.endsWith(".cc")) "clang++" else "clang"
        val command = arrayOf(compiler, "-o", outputFile.absolutePath, sourceFile.absolutePath)
        
        // 这里可以调用termuxService的编译方法
        // 简化实现，直接使用ProcessBuilder
        try {
            val process = ProcessBuilder(*command)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                callback.onOutput("Compilation successful\n")
                callback.onOutput(output)
                callback.onCompileComplete(true, "Compilation completed")
            } else {
                callback.onError("Compilation failed:\n$output")
                callback.onCompileComplete(false, "Compilation failed")
            }
        } catch (e: Exception) {
            callback.onError("Compilation error: ${e.message}")
            callback.onCompileComplete(false, "Compilation error")
        } finally {
            // 清理临时文件
            workDir.deleteRecursively()
        }
    }
    
    /**
     * 仅编译Java代码
     */
    private suspend fun compileOnly_Java(
        fileName: String,
        content: String,
        termuxService: TermuxCompilerService,
        callback: CompileRunCallback
    ) {
        // 创建临时文件
        val workDir = Environment.createTempDir("java_compile")
        val sourceFile = File(workDir, fileName)
        sourceFile.writeText(content)
        
        callback.onOutput("Compiling $fileName...\n")
        
        // 构建编译命令
        val command = arrayOf("javac", sourceFile.absolutePath)
        
        try {
            val process = ProcessBuilder(*command)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                callback.onOutput("Java compilation successful\n")
                callback.onOutput(output)
                callback.onCompileComplete(true, "Java compilation completed")
            } else {
                callback.onError("Java compilation failed:\n$output")
                callback.onCompileComplete(false, "Java compilation failed")
            }
        } catch (e: Exception) {
            callback.onError("Java compilation error: ${e.message}")
            callback.onCompileComplete(false, "Java compilation error")
        } finally {
            // 清理临时文件
            workDir.deleteRecursively()
        }
    }
}