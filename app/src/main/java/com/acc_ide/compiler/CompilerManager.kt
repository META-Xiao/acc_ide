package com.acc_ide.compiler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 编译器管理器 - 负责编译器的下载、安装和管理
 */
class CompilerManager(private val context: Context) {
    
    private val TAG = "CompilerManager"
    private val toolchainDir = File(context.filesDir, "toolchain")
    
    // 编译器下载URL配置
    companion object {
        private const val CLANG_URL = "https://github.com/lzhiyong/android-clang/releases/download/17.0.6/android-clang-17.0.6-aarch64.zip"
        private const val PYTHON_URL = "https://github.com/python/cpython/releases/download/v3.11.0/Python-3.11.0.tgz"
        // 注意：实际项目中需要使用有效的下载链接
    }
    
    init {
        toolchainDir.mkdirs()
    }
    
    /**
     * 检查编译器是否需要安装
     */
    fun needsInstallation(language: Language): Boolean {
        return when (language) {
            Language.C, Language.CPP -> !File(toolchainDir, "bin/clang").exists()
            Language.JAVA -> !File(toolchainDir, "bin/javac").exists()
            Language.PYTHON -> !File(toolchainDir, "bin/python3").exists()
        }
    }
    
    /**
     * 获取编译器安装状态
     */
    fun getInstallationStatus(): Map<Language, Boolean> {
        return Language.values().associateWith { !needsInstallation(it) }
    }
    
    /**
     * 安装编译器
     */
    suspend fun installCompiler(
        language: Language,
        onProgress: (Int) -> Unit = {}
    ): InstallResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Installing compiler for ${language.name}")
            onProgress(0)
            
            when (language) {
                Language.C, Language.CPP -> installClang(onProgress)
                Language.JAVA -> installJava(onProgress)
                Language.PYTHON -> installPython(onProgress)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed for ${language.name}", e)
            InstallResult.error("安装失败: ${e.message}")
        }
    }
    
    /**
     * 从assets安装预打包的编译器
     */
    suspend fun installFromAssets(
        language: Language,
        onProgress: (Int) -> Unit = {}
    ): InstallResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Installing ${language.name} from assets")
            onProgress(10)
            
            val assetFileName = getAssetFileName(language)
            val assetManager = context.assets
            
            // 检查assets文件是否存在
            val assetFiles = assetManager.list("compilers") ?: emptyArray()
            if (!assetFiles.contains(assetFileName)) {
                return@withContext InstallResult.error("编译器包未找到: $assetFileName")
            }
            
            onProgress(20)
            
            // 打开assets文件
            val inputStream = assetManager.open("compilers/$assetFileName")
            
            onProgress(30)
            
            // 解压到toolchain目录
            extractZipStream(inputStream, toolchainDir) { progress ->
                onProgress(30 + (progress * 0.6).toInt())
            }
            
            onProgress(90)
            
            // 设置可执行权限
            setExecutablePermissions(language)
            
            onProgress(100)
            
            InstallResult.success("${language.name} 编译器安装成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "Assets installation failed for ${language.name}", e)
            InstallResult.error("从assets安装失败: ${e.message}")
        }
    }
    
    /**
     * 卸载编译器
     */
    suspend fun uninstallCompiler(language: Language): UninstallResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Uninstalling compiler for ${language.name}")
            
            when (language) {
                Language.C, Language.CPP -> {
                    File(toolchainDir, "bin/clang").delete()
                    File(toolchainDir, "bin/clang++").delete()
                }
                Language.JAVA -> {
                    File(toolchainDir, "bin/javac").delete()
                }
                Language.PYTHON -> {
                    File(toolchainDir, "bin/python3").delete()
                    File(toolchainDir, "bin/python").delete()
                }
            }
            
            UninstallResult.success("${language.name} 编译器已卸载")
            
        } catch (e: Exception) {
            Log.e(TAG, "Uninstallation failed for ${language.name}", e)
            UninstallResult.error("卸载失败: ${e.message}")
        }
    }
    
    /**
     * 获取编译器大小信息
     */
    fun getCompilerSize(language: Language): CompilerSizeInfo {
        val assetFileName = getAssetFileName(language)
        
        return try {
            val inputStream = context.assets.open("compilers/$assetFileName")
            val size = inputStream.available().toLong()
            inputStream.close()
            
            CompilerSizeInfo(
                language = language,
                compressedSize = size,
                estimatedUncompressedSize = size * 3, // 估算解压后大小
                available = true
            )
        } catch (e: Exception) {
            CompilerSizeInfo(
                language = language,
                compressedSize = 0,
                estimatedUncompressedSize = 0,
                available = false
            )
        }
    }
    
    /**
     * 获取编译器信息
     */
    fun getCompilerInfo(language: Language): CompilerInfo {
        val isInstalled = !needsInstallation(language)
        val version = if (isInstalled) getCompilerVersion(language) else "未安装"
        val path = if (isInstalled) getCompilerPath(language) else ""
        
        return CompilerInfo(
            language = language,
            isInstalled = isInstalled,
            version = version,
            path = path
        )
    }
    
    /**
     * 获取编译器版本
     */
    private fun getCompilerVersion(language: Language): String {
        return when (language) {
            Language.C, Language.CPP -> "Clang 17.0.6"
            Language.JAVA -> "OpenJDK 11"
            Language.PYTHON -> "Python 3.11"
        }
    }
    
    /**
     * 获取编译器路径
     */
    private fun getCompilerPath(language: Language): String {
        return when (language) {
            Language.C, Language.CPP -> File(toolchainDir, "bin/clang").absolutePath
            Language.JAVA -> File(toolchainDir, "bin/javac").absolutePath
            Language.PYTHON -> File(toolchainDir, "bin/python3").absolutePath
        }
    }
    
    // 私有方法
    
    private fun getAssetFileName(language: Language): String {
        return when (language) {
            Language.C, Language.CPP -> "clang-toolchain.zip"
            Language.JAVA -> "java-compiler.zip"
            Language.PYTHON -> "python-interpreter.zip"
        }
    }
    
    private suspend fun installClang(onProgress: (Int) -> Unit): InstallResult {
        // 这里应该从预打包的assets或者下载clang工具链
        return installFromAssets(Language.C, onProgress)
    }
    
    private suspend fun installJava(onProgress: (Int) -> Unit): InstallResult {
        // 安装Java编译器
        return installFromAssets(Language.JAVA, onProgress)
    }
    
    private suspend fun installPython(onProgress: (Int) -> Unit): InstallResult {
        // 安装Python解释器
        return installFromAssets(Language.PYTHON, onProgress)
    }
    
    private suspend fun downloadFile(
        url: String,
        outputFile: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection()
            val fileLength = connection.contentLength
            
            connection.getInputStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead = 0
                    var totalBytes = 0
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        
                        if (fileLength > 0) {
                            val progress = (totalBytes * 100) / fileLength
                            onProgress(progress)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw IOException("下载失败: ${e.message}", e)
        }
    }
    
    private suspend fun extractZipStream(
        inputStream: InputStream,
        destDir: File,
        onProgress: (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        
        ZipInputStream(inputStream.buffered()).use { zipStream ->
            var entry = zipStream.nextEntry
            var entryCount = 0
            
            while (entry != null) {
                val destFile = File(destDir, entry.name)
                
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { output ->
                        zipStream.copyTo(output)
                    }
                }
                
                entryCount++
                if (entryCount % 10 == 0) {
                    onProgress(entryCount)
                }
                
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }
    }
    
    private fun setExecutablePermissions(language: Language) {
        when (language) {
            Language.C, Language.CPP -> {
                File(toolchainDir, "bin/clang").setExecutable(true)
                File(toolchainDir, "bin/clang++").setExecutable(true)
            }
            Language.JAVA -> {
                File(toolchainDir, "bin/javac").setExecutable(true)
            }
            Language.PYTHON -> {
                File(toolchainDir, "bin/python3").setExecutable(true)
            }
        }
    }
}

// 数据类

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

data class CompilerSizeInfo(
    val language: Language,
    val compressedSize: Long,
    val estimatedUncompressedSize: Long,
    val available: Boolean
)

data class CompilerInfo(
    val language: Language,
    val isInstalled: Boolean,
    val version: String,
    val path: String
)