package com.acc_ide.compiler

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.acc_ide.util.Environment
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Termux编译服务 - 基于AndroidIDE的termux集成实现
 * 提供cpp/python/java程序的本地编译和运行功能
 */
class TermuxCompilerService(private val context: Context) {
    
    private val TAG = "TermuxCompilerService"
    private var isServiceBound = false
    private val compilationTasks = ConcurrentHashMap<String, CompilationTask>()
    
    // 服务连接回调  
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            isServiceBound = true
            
            // 确保必要的目录存在
            ensureDirectoriesExist()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            isServiceBound = false
        }
    }
    
    /**
     * 绑定服务（简化实现，直接标记为已连接）
     */
    fun bindService(): Boolean {
        return try {
            // 简化实现：直接初始化必要组件
            ensureDirectoriesExist()
            isServiceBound = true
            Log.d(TAG, "Service binding simulated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind service", e)
            false
        }
    }
    
    /**
     * 解绑服务
     */
    fun unbindService() {
        isServiceBound = false
        Log.d(TAG, "Service unbound")
    }
    
    /**
     * 编译并运行代码文件
     */
    suspend fun compileAndRun(
        fileName: String,
        content: String,
        language: Language,
        onOutput: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onComplete: (Boolean) -> Unit = {}
    ): CompilationResult = withContext(Dispatchers.IO) {
        
        if (!isServiceBound) {
            val error = "Service not available"
            onError(error)
            return@withContext CompilationResult.error(error)
        }
        
        try {
            val taskId = generateTaskId(fileName)
            val workDir = File(Environment.TMP_DIR, "compile_$taskId")
            workDir.mkdirs()
            
            // 保存源文件
            val sourceFile = File(workDir, fileName)
            sourceFile.writeText(content)
            
            val task = CompilationTask(
                id = taskId,
                fileName = fileName,
                language = language,
                workDir = workDir,
                sourceFile = sourceFile
            )
            
            compilationTasks[taskId] = task
            
            // 根据语言选择编译方式
            when (language) {
                Language.CPP, Language.C -> compileCppCode(task, onOutput, onError, onComplete)
                Language.JAVA -> compileJavaCode(task, onOutput, onError, onComplete)
                Language.PYTHON -> runPythonCode(task, onOutput, onError, onComplete)
            }
            
        } catch (e: Exception) {
            val error = "Compilation failed: ${e.message}"
            Log.e(TAG, error, e)
            onError(error)
            onComplete(false)
            CompilationResult.error(error)
        }
    }
    
    /**
     * 编译C/C++代码
     */
    private suspend fun compileCppCode(
        task: CompilationTask,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: (Boolean) -> Unit
    ): CompilationResult = withContext(Dispatchers.IO) {
        
        val outputFile = File(task.workDir, task.fileName.substringBeforeLast('.'))
        val compileCommand = buildCppCompileCommand(task.sourceFile, outputFile)
        
        onOutput("Compiling ${task.fileName}...\n")
        
        // 执行编译
        val compileResult = executeCommand(compileCommand, task.workDir, onOutput, onError)
        
        if (compileResult.success && outputFile.exists()) {
            onOutput("Compilation successful. Running program...\n")
            
            // 设置可执行权限
            outputFile.setExecutable(true, false)
            
            // 运行程序
            val runCommand = arrayOf("./${outputFile.name}")
            val runResult = executeCommand(runCommand, task.workDir, onOutput, onError)
            
            onComplete(runResult.success)
            if (runResult.success) {
                CompilationResult.success("Program executed successfully")
            } else {
                CompilationResult.error("Runtime error")
            }
        } else {
            onError("Compilation failed\n")
            onComplete(false)
            CompilationResult.error("Compilation failed")
        }
    }
    
    /**
     * 编译Java代码
     */
    private suspend fun compileJavaCode(
        task: CompilationTask,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: (Boolean) -> Unit
    ): CompilationResult = withContext(Dispatchers.IO) {
        
        val className = task.fileName.substringBeforeLast('.')
        val classFile = File(task.workDir, "$className.class")
        
        onOutput("Compiling ${task.fileName}...\n")
        
        // 使用javac编译
        val compileCommand = arrayOf("javac", task.sourceFile.absolutePath)
        val compileResult = executeCommand(compileCommand, task.workDir, onOutput, onError)
        
        if (compileResult.success && classFile.exists()) {
            onOutput("Compilation successful. Running program...\n")
            
            // 运行Java程序
            val runCommand = arrayOf("java", "-cp", task.workDir.absolutePath, className)
            val runResult = executeCommand(runCommand, task.workDir, onOutput, onError)
            
            onComplete(runResult.success)
            if (runResult.success) {
                CompilationResult.success("Java program executed successfully")
            } else {
                CompilationResult.error("Runtime error")
            }
        } else {
            onError("Java compilation failed\n")
            onComplete(false)
            CompilationResult.error("Java compilation failed")
        }
    }
    
    /**
     * 运行Python代码
     */
    private suspend fun runPythonCode(
        task: CompilationTask,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: (Boolean) -> Unit
    ): CompilationResult = withContext(Dispatchers.IO) {
        
        onOutput("Running Python script: ${task.fileName}...\n")
        
        // 直接运行Python脚本
        val runCommand = arrayOf("python3", task.sourceFile.absolutePath)
        val runResult = executeCommand(runCommand, task.workDir, onOutput, onError)
        
        onComplete(runResult.success)
        if (runResult.success) {
            CompilationResult.success("Python script executed successfully")
        } else {
            CompilationResult.error("Python execution failed")
        }
    }
    
    /**
     * 执行shell命令（简化实现，使用ProcessBuilder）
     */
    private suspend fun executeCommand(
        command: Array<String>,
        workDir: File,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): CommandResult = withContext(Dispatchers.IO) {
        
        try {
            Log.d(TAG, "Executing command: ${command.joinToString(" ")}")
            
            val process = ProcessBuilder(*command)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            
            // 输出结果
            if (output.isNotEmpty()) {
                onOutput(output)
            }
            
            if (exitCode == 0) {
                CommandResult.success(output)
            } else {
                onError("Command failed with exit code: $exitCode")
                CommandResult.error("Command failed with exit code: $exitCode")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: ${command.joinToString(" ")}", e)
            onError("Command execution failed: ${e.message}\n")
            CommandResult.error("Command execution failed: ${e.message}")
        }
    }
    
    /**
     * 构建C/C++编译命令
     */
    private fun buildCppCompileCommand(sourceFile: File, outputFile: File): Array<String> {
        return if (sourceFile.name.endsWith(".cpp") || sourceFile.name.endsWith(".cc")) {
            // C++编译
            arrayOf("clang++", "-o", outputFile.absolutePath, sourceFile.absolutePath)
        } else {
            // C编译
            arrayOf("clang", "-o", outputFile.absolutePath, sourceFile.absolutePath)
        }
    }
    
    /**
     * 确保必要的目录存在
     */
    private fun ensureDirectoriesExist() {
        try {
            Environment.TMP_DIR.mkdirs()
            
            // 创建编译缓存目录
            val compileDir = File(Environment.TMP_DIR, "compile")
            compileDir.mkdirs()
            
            Log.d(TAG, "Directories created: ${Environment.TMP_DIR.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create directories", e)
        }
    }
    
    /**
     * 生成任务ID
     */
    private fun generateTaskId(fileName: String): String {
        return "${fileName.hashCode()}_${System.currentTimeMillis()}"
    }
    
    /**
     * 取消编译任务
     */
    fun cancelCompilation(taskId: String) {
        compilationTasks.remove(taskId)?.let { task ->
            try {
                // 清理工作目录
                task.workDir.deleteRecursively()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cleanup work directory", e)
            }
        }
    }
    
    /**
     * 清理所有编译任务
     */
    fun cleanup() {
        compilationTasks.values.forEach { task ->
            try {
                task.workDir.deleteRecursively()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cleanup work directory", e)
            }
        }
        compilationTasks.clear()
        unbindService()
    }
}

// 数据类定义
data class CompilationTask(
    val id: String,
    val fileName: String,
    val language: Language,
    val workDir: File,
    val sourceFile: File
)

data class CompilationResult(
    val success: Boolean,
    val message: String,
    val error: String? = null
) {
    companion object {
        fun success(message: String) = CompilationResult(true, message)
        fun error(message: String) = CompilationResult(false, message, message)
    }
}

data class CommandResult(
    val success: Boolean,
    val output: String = "",
    val error: String? = null
) {
    companion object {
        fun success(output: String) = CommandResult(true, output)
        fun error(message: String) = CommandResult(false, "", message)
    }
}