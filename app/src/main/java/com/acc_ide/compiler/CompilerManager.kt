package com.acc_ide.compiler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 重新设计的编译器管理器 - 基于成功的Android IDE应用实践
 * 参考：CppDroid, C4droid, Termux等成功案例
 */
class CompilerManager(private val context: Context) {
    
    private val TAG = "CompilerManager"
    private val toolchainDir = File(context.filesDir, "toolchain")
    
    companion object {
        // 基于TinyCC的轻量级方案（类似CppDroid）
        const val TINYCC_VERSION = "0.9.27"
        const val PYTHON_VERSION = "3.11"
        const val ECJ_VERSION = "3.33.0"
    }
    
    init {
        toolchainDir.mkdirs()
    }
    
    /**
     * 检查编译器是否已安装（基于实际文件存在）
     */
    fun isCompilerInstalled(language: Language): Boolean {
        return when (language) {
            Language.C, Language.CPP -> {
                // 检查TinyCC二进制文件
                File(toolchainDir, "tinycc/tcc").exists() && 
                File(toolchainDir, "tinycc/tcc").canExecute()
            }
            Language.JAVA -> {
                // 检查ECJ编译器
                File(toolchainDir, "java/ecj.jar").exists()
            }
            Language.PYTHON -> {
                // 检查Python解释器
                File(toolchainDir, "python/bin/python3").exists() && 
                File(toolchainDir, "python/bin/python3").canExecute()
            }
        }
    }
    
    /**
     * 获取编译器大小信息（从assets预检查）
     */
    fun getCompilerSize(language: Language): CompilerSizeInfo {
        return try {
            val assetFileName = getAssetFileName(language)
            val inputStream = context.assets.open("compilers/$assetFileName")
            val size = inputStream.available().toLong()
            inputStream.close()
            
            // 基于实际压缩比估算
            val uncompressedSize = when (language) {
                Language.C, Language.CPP -> size * 4 // TinyCC解压比约1:4
                Language.PYTHON -> size * 8 // Python库较多，约1:8
                Language.JAVA -> size * 2 // ECJ jar包，约1:2
            }
            
            CompilerSizeInfo(language, size, uncompressedSize, true)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot get size for $language: ${e.message}")
            CompilerSizeInfo(language, 0, 0, false)
        }
    }
    
    /**
     * 从assets安装编译器（类似CppDroid首次启动解压SDK的方式）
     */
    suspend fun installFromAssets(
        language: Language,
        onProgress: (Int) -> Unit = {}
    ): InstallResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Installing $language toolchain from assets...")
            onProgress(0)
            
            val assetFileName = getAssetFileName(language)
            val targetDir = File(toolchainDir, language.name.lowercase())
            
            // 清理旧安装
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()
            
            onProgress(10)
            
            // 解压assets中的工具链
            context.assets.open("compilers/$assetFileName").use { inputStream ->
                extractToolchain(inputStream, targetDir) { progress ->
                    onProgress(10 + (progress * 0.7).toInt())
                }
            }
            
            onProgress(80)
            
            // 设置执行权限（关键步骤）
            setExecutablePermissions(language, targetDir)
            
            // Python特殊处理
            if (language == Language.PYTHON) {
                setupPythonEnvironment(targetDir)
            }
            
            onProgress(90)
            
            // 验证安装
            if (verifyInstallation(language, targetDir)) {
                onProgress(100)
                InstallResult.success("${language.displayName} toolchain installed successfully")
            } else {
                InstallResult.error("Installation verification failed")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed for $language", e)
            InstallResult.error("Installation failed: ${e.message}")
        }
    }
    
    /**
     * 卸载编译器
     */
    suspend fun uninstallCompiler(language: Language): UninstallResult = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(toolchainDir, language.name.lowercase())
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
                UninstallResult.success("${language.displayName} toolchain uninstalled")
            } else {
                UninstallResult.success("${language.displayName} toolchain was not installed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uninstall failed for $language", e)
            UninstallResult.error("Uninstall failed: ${e.message}")
        }
    }
    
    /**
     * 获取编译器信息
     */
    fun getCompilerInfo(language: Language): CompilerInfo {
        val isInstalled = isCompilerInstalled(language)
        val version = if (isInstalled) getCompilerVersion(language) else "Not installed"
        val path = if (isInstalled) getCompilerPath(language) else ""
        
        return CompilerInfo(
            language = language,
            isInstalled = isInstalled,
            version = version,
            path = path,
            size = if (isInstalled) getInstalledSize(language) else 0
        )
    }
    
    // 私有方法
    
    private fun getAssetFileName(language: Language): String {
        return when (language) {
            Language.C, Language.CPP -> "tinycc-android-arm64.zip"
            Language.JAVA -> "java-ecj.zip"
            Language.PYTHON -> "python311-android-arm64.zip"
        }
    }
    
    /**
     * 专门为Python设置环境变量和库路径
     */
    private fun setupPythonEnvironment(pythonDir: File) {
        try {
            // 创建Python启动脚本
            val pythonScript = File(pythonDir, "python")
            val pythonContent = """#!/system/bin/sh
# Python wrapper script for Android
export PYTHONHOME="${pythonDir.absolutePath}"
export PYTHONPATH="${'$'}PYTHONHOME/lib/python3.11:${'$'}PYTHONHOME/lib/python3.11/site-packages"
export LD_LIBRARY_PATH="${'$'}PYTHONHOME/lib:${'$'}LD_LIBRARY_PATH"

# 设置临时目录
export TMPDIR="/data/data/${context.packageName}/cache/python_tmp"
mkdir -p "${'$'}TMPDIR"

# 执行Python
exec "${'$'}PYTHONHOME/bin/python3.11" "${'$'}@"
"""
            pythonScript.writeText(pythonContent)
            pythonScript.setExecutable(true, false)
            
            // 创建符合Termux标准的目录结构
            val libDir = File(pythonDir, "lib")
            val binDir = File(pythonDir, "bin")
            val includeDir = File(pythonDir, "include")
            
            libDir.mkdirs()
            binDir.mkdirs()
            includeDir.mkdirs()
            
            Log.d(TAG, "Python environment setup completed")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to setup Python environment", e)
        }
    }
    
    private fun getCompilerVersion(language: Language): String {
        return when (language) {
            Language.C, Language.CPP -> "TinyCC $TINYCC_VERSION"
            Language.JAVA -> "ECJ $ECJ_VERSION"
            Language.PYTHON -> "Python $PYTHON_VERSION"
        }
    }
    
    private fun getCompilerPath(language: Language): String {
        return when (language) {
            Language.C, Language.CPP -> File(toolchainDir, "tinycc/tcc").absolutePath
            Language.JAVA -> File(toolchainDir, "java/ecj.jar").absolutePath
            Language.PYTHON -> File(toolchainDir, "python/bin/python3").absolutePath
        }
    }
    
    private fun getInstalledSize(language: Language): Long {
        val targetDir = File(toolchainDir, language.name.lowercase())
        return if (targetDir.exists()) {
            targetDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } else 0
    }
    
    /**
     * 解压工具链压缩包
     */
    private suspend fun extractToolchain(
        inputStream: InputStream,
        destDir: File,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        
        ZipInputStream(inputStream.buffered()).use { zipStream ->
            var entry = zipStream.nextEntry
            var entryCount = 0
            var totalEntries = 0
            
            // 先计算总条目数（如果可能）
            while (entry != null) {
                totalEntries++
                entry = zipStream.nextEntry
            }
            
            // 重新开始解压
            ZipInputStream(inputStream.buffered()).use { zipStream2 ->
                entry = zipStream2.nextEntry
                
                while (entry != null) {
                    val destFile = File(destDir, entry.name)
                    
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        destFile.outputStream().use { output ->
                            zipStream2.copyTo(output)
                        }
                    }
                    
                    entryCount++
                    if (totalEntries > 0 && entryCount % 10 == 0) {
                        val progress = (entryCount * 100) / totalEntries
                        onProgress(progress)
                    }
                    
                    zipStream2.closeEntry()
                    entry = zipStream2.nextEntry
                }
            }
        }
    }
    
    /**
     * 设置可执行权限（关键步骤，类似Termux的权限设置）
     */
    private fun setExecutablePermissions(language: Language, targetDir: File) {
        val executableFiles = when (language) {
            Language.C, Language.CPP -> listOf("tcc", "tinycc")
            Language.PYTHON -> listOf("python3", "python3.11", "python")
            Language.JAVA -> emptyList() // Java不需要设置执行权限
        }
        
        executableFiles.forEach { fileName ->
            val file = File(targetDir, "bin/$fileName").takeIf { it.exists() }
                ?: File(targetDir, fileName)
            
            if (file.exists()) {
                try {
                    file.setExecutable(true, false) // 设置所有用户可执行
                    Log.d(TAG, "Set executable permission for ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set executable permission for $file", e)
                }
            }
        }
    }
    
    /**
     * 验证安装是否成功
     */
    private fun verifyInstallation(language: Language, targetDir: File): Boolean {
        return when (language) {
            Language.C, Language.CPP -> {
                val tccFile = File(targetDir, "tcc")
                tccFile.exists() && tccFile.canExecute()
            }
            Language.JAVA -> {
                File(targetDir, "ecj.jar").exists()
            }
            Language.PYTHON -> {
                val pythonFile = File(targetDir, "bin/python3")
                pythonFile.exists() && pythonFile.canExecute()
            }
        }
    }
}

// 更新的数据类
data class CompilerInfo(
    val language: Language,
    val isInstalled: Boolean,
    val version: String,
    val path: String,
    val size: Long = 0
)

data class CompilerSizeInfo(
    val language: Language,
    val compressedSize: Long,
    val estimatedUncompressedSize: Long,
    val available: Boolean
)

data class InstallResult(
    val success: Boolean,
    val message: String,
    val error: String? = null
) {
    companion object {
        fun success(message: String) = InstallResult(true, message)
        fun error(message: String) = InstallResult(false, message, message)
    }
}

data class UninstallResult(
    val success: Boolean,
    val message: String
) {
    companion object {
        fun success(message: String) = UninstallResult(true, message)
        fun error(message: String) = UninstallResult(false, message)
    }
}

// 扩展Language枚举
val Language.displayName: String
    get() = when (this) {
        Language.C -> "C"
        Language.CPP -> "C++"
        Language.JAVA -> "Java"
        Language.PYTHON -> "Python"
    }