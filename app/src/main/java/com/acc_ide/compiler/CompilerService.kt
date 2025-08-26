package com.acc_ide.compiler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.TimeUnit

/**
 * 编译器服务 - 负责管理各种编译器的调用和执行
 */
class CompilerService(private val context: Context) {
    
    private val TAG = "CompilerService"
    private val toolchainDir = File(context.filesDir, "toolchain")
    private val tempDir = File(context.cacheDir, "compile_temp")
    
    init {
        // 确保必要的目录存在
        tempDir.mkdirs()
        toolchainDir.mkdirs()
    }
    
    /**
     * 编译C/C++代码
     */
    suspend fun compileC(sourceCode: String, language: Language = Language.C): CompileResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Compiling ${language.name} code")
            
            // 生成临时文件名
            val extension = if (language == Language.C) "c" else "cpp"
            val sourceFile = File(tempDir, "main.$extension")
            val outputFile = File(tempDir, "main.out")
            
            // 清理之前的文件
            sourceFile.delete()
            outputFile.delete()
            
            // 写入源码
            sourceFile.writeText(sourceCode)
            
            // 选择编译器
            val compiler = getCompilerPath(language)
            if (!File(compiler).exists()) {
                return@withContext CompileResult.error("编译器未安装: $compiler")
            }
            
            // 构建编译命令
            val command = buildCompileCommand(compiler, sourceFile.absolutePath, outputFile.absolutePath, language)
            
            // 执行编译
            val compileResult = executeCommand(command, tempDir, timeout = 30000)
            
            if (compileResult.exitCode == 0) {
                CompileResult.success(
                    message = "编译成功",
                    outputFile = outputFile.absolutePath,
                    compileOutput = compileResult.output
                )
            } else {
                CompileResult.error(
                    message = "编译失败",
                    details = compileResult.error
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Compile error", e)
            CompileResult.error("编译异常: ${e.message}")
        }
    }
    
    /**
     * 编译Java代码
     */
    suspend fun compileJava(sourceCode: String): CompileResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Compiling Java code")
            
            // 提取类名
            val className = extractJavaClassName(sourceCode) ?: "Main"
            val sourceFile = File(tempDir, "$className.java")
            val classFile = File(tempDir, "$className.class")
            
            // 清理之前的文件
            sourceFile.delete()
            classFile.delete()
            
            // 写入源码
            sourceFile.writeText(sourceCode)
            
            // 获取Java编译器路径
            val javacPath = getJavacPath()
            if (!File(javacPath).exists()) {
                return@withContext CompileResult.error("Java编译器未找到")
            }
            
            // 构建编译命令
            val command = listOf(
                javacPath,
                "-cp", getAndroidJarPath(),
                sourceFile.absolutePath
            )
            
            // 执行编译
            val compileResult = executeCommand(command, tempDir, timeout = 30000)
            
            if (compileResult.exitCode == 0 && classFile.exists()) {
                CompileResult.success(
                    message = "Java编译成功",
                    outputFile = classFile.absolutePath,
                    compileOutput = compileResult.output
                )
            } else {
                CompileResult.error(
                    message = "Java编译失败",
                    details = compileResult.error
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Java compile error", e)
            CompileResult.error("Java编译异常: ${e.message}")
        }
    }
    
    /**
     * 运行Python代码
     */
    suspend fun runPython(sourceCode: String): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Running Python code")
            
            val sourceFile = File(tempDir, "main.py")
            sourceFile.writeText(sourceCode)
            
            val pythonPath = getPythonPath()
            if (!File(pythonPath).exists()) {
                return@withContext ExecutionResult.error("Python解释器未安装")
            }
            
            val command = listOf(pythonPath, sourceFile.absolutePath)
            val result = executeCommand(command, tempDir, timeout = 30000)
            
            ExecutionResult(
                success = result.exitCode == 0,
                output = result.output,
                error = result.error,
                exitCode = result.exitCode
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Python execution error", e)
            ExecutionResult.error("Python执行异常: ${e.message}")
        }
    }
    
    /**
     * 运行编译后的程序
     */
    suspend fun runProgram(executablePath: String): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Running program: $executablePath")
            
            val execFile = File(executablePath)
            if (!execFile.exists()) {
                return@withContext ExecutionResult.error("可执行文件不存在: $executablePath")
            }
            
            // 确保文件有执行权限
            execFile.setExecutable(true)
            
            val command = listOf(executablePath)
            val result = executeCommand(command, tempDir, timeout = 30000)
            
            ExecutionResult(
                success = result.exitCode == 0,
                output = result.output,
                error = result.error,
                exitCode = result.exitCode
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Program execution error", e)
            ExecutionResult.error("程序执行异常: ${e.message}")
        }
    }
    
    /**
     * 检查编译器是否已安装
     */
    fun isCompilerInstalled(language: Language): Boolean {
        val compilerPath = getCompilerPath(language)
        return File(compilerPath).exists()
    }
    
    /**
     * 获取编译器信息
     */
    suspend fun getCompilerInfo(language: Language): CompilerInfo = withContext(Dispatchers.IO) {
        val compilerPath = getCompilerPath(language)
        val isInstalled = File(compilerPath).exists()
        
        val version = if (isInstalled) {
            try {
                val versionCommand = listOf(compilerPath, "--version")
                val result = executeCommand(versionCommand, tempDir, timeout = 5000)
                if (result.exitCode == 0) {
                    result.output.lines().firstOrNull() ?: "Unknown"
                } else "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }
        } else "Not installed"
        
        CompilerInfo(
            language = language,
            isInstalled = isInstalled,
            version = version,
            path = compilerPath
        )
    }
    
    // 私有辅助方法
    
    private fun getCompilerPath(language: Language): String {
        return when (language) {
            Language.C -> "$toolchainDir/bin/clang"
            Language.CPP -> "$toolchainDir/bin/clang++"
            Language.JAVA -> getJavacPath()
            Language.PYTHON -> getPythonPath()
        }
    }
    
    private fun getJavacPath(): String {
        // 尝试多个可能的路径
        val possiblePaths = listOf(
            "$toolchainDir/bin/javac",
            "/system/bin/javac",
            "javac" // 系统PATH中的javac
        )
        
        for (path in possiblePaths) {
            if (File(path).exists()) {
                return path
            }
        }
        
        return "$toolchainDir/bin/javac" // 默认路径
    }
    
    private fun getPythonPath(): String {
        val possiblePaths = listOf(
            "$toolchainDir/bin/python3",
            "$toolchainDir/bin/python",
            "/system/bin/python3",
            "python3"
        )
        
        for (path in possiblePaths) {
            if (File(path).exists()) {
                return path
            }
        }
        
        return "$toolchainDir/bin/python3"
    }
    
    private fun getAndroidJarPath(): String {
        // Android SDK的android.jar路径
        return "$toolchainDir/platforms/android.jar"
    }
    
    private fun buildCompileCommand(
        compiler: String,
        sourceFile: String,
        outputFile: String,
        language: Language
    ): List<String> {
        val command = mutableListOf(compiler)
        
        // 添加通用参数
        command.addAll(listOf("-o", outputFile, sourceFile))
        
        // 添加包含路径
        val includePath = "$toolchainDir/include"
        if (File(includePath).exists()) {
            command.addAll(listOf("-I", includePath))
        }
        
        // 添加库路径
        val libPath = "$toolchainDir/lib"
        if (File(libPath).exists()) {
            command.addAll(listOf("-L", libPath))
        }
        
        // 语言特定参数
        when (language) {
            Language.C -> {
                command.add("-std=c11")
            }
            Language.CPP -> {
                command.add("-std=c++17")
                command.add("-lstdc++")
            }
            else -> { /* 其他语言不需要特殊参数 */ }
        }
        
        return command
    }
    
    private fun extractJavaClassName(sourceCode: String): String? {
        // 简单的类名提取
        val classPattern = Regex("""public\s+class\s+(\w+)""")
        val match = classPattern.find(sourceCode)
        return match?.groups?.get(1)?.value
    }
    
    private suspend fun executeCommand(
        command: List<String>,
        workingDir: File,
        timeout: Long = 30000
    ): CommandResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing command: ${command.joinToString(" ")}")
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(workingDir)
            processBuilder.redirectErrorStream(false)
            
            val process = processBuilder.start()
            
            // 读取输出
            val outputBuffer = StringBuilder()
            val errorBuffer = StringBuilder()
            
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            // 启动读取线程
            val outputJob = async {
                outputReader.use { reader ->
                    reader.lineSequence().forEach { line ->
                        outputBuffer.appendLine(line)
                    }
                }
            }
            
            val errorJob = async {
                errorReader.use { reader ->
                    reader.lineSequence().forEach { line ->
                        errorBuffer.appendLine(line)
                    }
                }
            }
            
            // 等待进程完成或超时
            val completed = process.waitFor(timeout, TimeUnit.MILLISECONDS)
            
            if (!completed) {
                process.destroyForcibly()
                return@withContext CommandResult(-1, "", "进程超时")
            }
            
            // 等待输出读取完成
            outputJob.await()
            errorJob.await()
            
            CommandResult(
                exitCode = process.exitValue(),
                output = outputBuffer.toString().trim(),
                error = errorBuffer.toString().trim()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Command execution error", e)
            CommandResult(-1, "", "执行异常: ${e.message}")
        }
    }
}

// 数据类定义

enum class Language {
    C, CPP, JAVA, PYTHON
}

data class CompileResult(
    val success: Boolean,
    val message: String,
    val outputFile: String? = null,
    val compileOutput: String? = null,
    val error: String? = null
) {
    companion object {
        fun success(message: String, outputFile: String? = null, compileOutput: String? = null) =
            CompileResult(true, message, outputFile, compileOutput)
        
        fun error(message: String, details: String? = null) =
            CompileResult(false, message, error = details)
    }
}

data class ExecutionResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val exitCode: Int = 0
) {
    companion object {
        fun error(message: String) = ExecutionResult(false, "", message, -1)
    }
}

private data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String
)