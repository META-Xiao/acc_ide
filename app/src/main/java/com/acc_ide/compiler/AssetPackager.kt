package com.acc_ide.compiler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONArray

/**
 * 编译器资产包管理器
 * 负责创建、验证和管理编译器资产包
 * 
 * 资产包结构：
 * - tinycc-android-arm64.zip (TinyCC编译器)
 * - python311-android-arm64.zip (Python解释器)  
 * - java-ecj.zip (Eclipse Compiler for Java)
 * - manifest.json (包信息和校验)
 */
class AssetPackager(private val context: Context) {
    
    private val TAG = "AssetPackager"
    private val assetsDir = File(context.filesDir, "compiler_assets")
    private val tempDir = File(context.cacheDir, "asset_temp")
    
    companion object {
        // 支持的编译器包
        const val TINYCC_PACKAGE = "tinycc-android-arm64.zip"
        const val PYTHON_PACKAGE = "python311-android-arm64.zip"  
        const val JAVA_PACKAGE = "java-ecj.zip"
        const val MANIFEST_FILE = "manifest.json"
        
        // 包版本信息
        const val TINYCC_VERSION = "0.9.27"
        const val PYTHON_VERSION = "3.11.0"
        const val ECJ_VERSION = "3.33.0"
    }
    
    init {
        assetsDir.mkdirs()
        tempDir.mkdirs()
    }
    
    /**
     * 创建TinyCC编译器包
     */
    suspend fun createTinyCCPackage(): PackageResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating TinyCC package...")
            
            val packageFile = File(assetsDir, TINYCC_PACKAGE)
            val tempTccDir = File(tempDir, "tinycc")
            tempTccDir.mkdirs()
            
            // 创建TinyCC目录结构
            val binDir = File(tempTccDir, "bin")
            val libDir = File(tempTccDir, "lib") 
            val includeDir = File(tempTccDir, "include")
            
            binDir.mkdirs()
            libDir.mkdirs()
            includeDir.mkdirs()
            
            // 创建TinyCC可执行文件的占位符（实际需要从构建脚本获取）
            val tccExe = File(binDir, "tcc")
            tccExe.writeText(createTinyCCStub())
            tccExe.setExecutable(true)
            
            // 创建基本的C头文件
            createBasicCHeaders(includeDir)
            
            // 打包成ZIP
            zipDirectory(tempTccDir, packageFile)
            
            val checksum = calculateChecksum(packageFile)
            Log.d(TAG, "TinyCC package created: ${packageFile.absolutePath}")
            
            PackageResult.success(
                packageName = TINYCC_PACKAGE,
                version = TINYCC_VERSION,
                size = packageFile.length(),
                checksum = checksum,
                file = packageFile
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TinyCC package", e)
            PackageResult.error("TinyCC打包失败: ${e.message}")
        }
    }
    
    /**
     * 创建Python解释器包
     */
    suspend fun createPythonPackage(): PackageResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating Python package...")
            
            val packageFile = File(assetsDir, PYTHON_PACKAGE)
            val tempPythonDir = File(tempDir, "python")
            tempPythonDir.mkdirs()
            
            // 创建Python目录结构
            val binDir = File(tempPythonDir, "bin")
            val libDir = File(tempPythonDir, "lib/python3.11")
            val sitePackagesDir = File(libDir, "site-packages")
            
            binDir.mkdirs()
            libDir.mkdirs()
            sitePackagesDir.mkdirs()
            
            // 创建Python可执行文件占位符
            val pythonExe = File(binDir, "python3.11")
            pythonExe.writeText(createPythonStub())
            pythonExe.setExecutable(true)
            
            // 创建基本Python模块
            createBasicPythonModules(libDir)
            
            // 创建启动脚本
            val pythonScript = File(tempPythonDir, "python")
            pythonScript.writeText(createPythonWrapperScript())
            pythonScript.setExecutable(true)
            
            // 打包成ZIP
            zipDirectory(tempPythonDir, packageFile)
            
            val checksum = calculateChecksum(packageFile)
            Log.d(TAG, "Python package created: ${packageFile.absolutePath}")
            
            PackageResult.success(
                packageName = PYTHON_PACKAGE,
                version = PYTHON_VERSION, 
                size = packageFile.length(),
                checksum = checksum,
                file = packageFile
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Python package", e)
            PackageResult.error("Python打包失败: ${e.message}")
        }
    }
    
    /**
     * 创建Java ECJ编译器包
     */
    suspend fun createJavaPackage(): PackageResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating Java ECJ package...")
            
            val packageFile = File(assetsDir, JAVA_PACKAGE)
            val tempJavaDir = File(tempDir, "java")
            tempJavaDir.mkdirs()
            
            // 创建ECJ jar文件占位符（实际需要下载真实ECJ）
            val ecjJar = File(tempJavaDir, "ecj.jar")
            ecjJar.writeText("ECJ_JAR_PLACEHOLDER") // 实际应该是真实的ECJ jar
            
            // 创建Android兼容的rt.jar占位符
            val androidJar = File(tempJavaDir, "android.jar")
            androidJar.writeText("ANDROID_JAR_PLACEHOLDER")
            
            // 打包成ZIP
            zipDirectory(tempJavaDir, packageFile)
            
            val checksum = calculateChecksum(packageFile)
            Log.d(TAG, "Java package created: ${packageFile.absolutePath}")
            
            PackageResult.success(
                packageName = JAVA_PACKAGE,
                version = ECJ_VERSION,
                size = packageFile.length(), 
                checksum = checksum,
                file = packageFile
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Java package", e)
            PackageResult.error("Java打包失败: ${e.message}")
        }
    }
    
    /**
     * 创建所有包的清单文件
     */
    suspend fun createManifest(packages: List<PackageResult>): File = withContext(Dispatchers.IO) {
        val manifestFile = File(assetsDir, MANIFEST_FILE)
        
        val manifest = JSONObject().apply {
            put("version", "1.0.0")
            put("created", System.currentTimeMillis())
            put("platform", "android-arm64")
            
            val packagesArray = JSONArray()
            packages.forEach { pkg ->
                if (pkg.success) {
                    packagesArray.put(JSONObject().apply {
                        put("name", pkg.packageName)
                        put("version", pkg.version)
                        put("size", pkg.size)
                        put("checksum", pkg.checksum)
                        put("language", pkg.getLanguage())
                    })
                }
            }
            put("packages", packagesArray)
        }
        
        manifestFile.writeText(manifest.toString(2))
        Log.d(TAG, "Manifest created: ${manifestFile.absolutePath}")
        
        manifestFile
    }
    
    /**
     * 验证资产包的完整性
     */
    suspend fun verifyPackage(packageFile: File, expectedChecksum: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!packageFile.exists()) return@withContext false
            
            val actualChecksum = calculateChecksum(packageFile)
            val isValid = actualChecksum == expectedChecksum
            
            Log.d(TAG, "Package verification: ${packageFile.name} -> $isValid")
            return@withContext isValid
            
        } catch (e: Exception) {
            Log.e(TAG, "Package verification failed", e)
            return@withContext false
        }
    }
    
    // 私有辅助方法
    
    private fun createTinyCCStub(): String {
        return """#!/system/bin/sh
# TinyCC ARM64 stub
# This is a placeholder - real TinyCC binary should be obtained from build script
echo "TinyCC v$TINYCC_VERSION for Android ARM64"
echo "Error: Real TinyCC binary not available. Please run build script first."
exit 1
"""
    }
    
    private fun createPythonStub(): String {
        return """#!/system/bin/sh  
# Python 3.11 ARM64 stub
echo "Python $PYTHON_VERSION for Android ARM64"
echo "Error: Real Python binary not available. Please run build script first."
exit 1
"""
    }
    
    private fun createPythonWrapperScript(): String {
        return """#!/system/bin/sh
# Python wrapper script for Android
export PYTHONHOME="/data/data/com.acc_ide/files/toolchain/python"
export PYTHONPATH="${'$'}PYTHONHOME/lib/python3.11"
export LD_LIBRARY_PATH="${'$'}PYTHONHOME/lib"
exec "${'$'}PYTHONHOME/bin/python3.11" "${'$'}@"
"""
    }
    
    private fun createBasicCHeaders(includeDir: File) {
        // 创建基本的C头文件
        File(includeDir, "stdio.h").writeText("""
#ifndef _STDIO_H
#define _STDIO_H
// Basic stdio definitions for TinyCC
int printf(const char *format, ...);
int scanf(const char *format, ...);
#endif
""")
        
        File(includeDir, "stdlib.h").writeText("""
#ifndef _STDLIB_H  
#define _STDLIB_H
// Basic stdlib definitions for TinyCC
void* malloc(size_t size);
void free(void* ptr);
void exit(int status);
#endif
""")
    }
    
    private fun createBasicPythonModules(libDir: File) {
        // 创建基本的Python模块占位符
        File(libDir, "__init__.py").writeText("")
        File(libDir, "sys.py").writeText("# Placeholder for sys module")
        File(libDir, "os.py").writeText("# Placeholder for os module") 
    }
    
    private fun zipDirectory(sourceDir: File, outputZip: File) {
        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                val relativePath = sourceDir.toURI().relativize(file.toURI()).path
                if (relativePath.isNotEmpty()) {
                    val entry = ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    
                    if (file.isFile) {
                        file.inputStream().use { input ->
                            input.copyTo(zos)
                        }
                    }
                    
                    zos.closeEntry()
                }
            }
        }
    }
    
    private fun calculateChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * 资产包结果数据类
 */
data class PackageResult(
    val success: Boolean,
    val packageName: String,
    val version: String,
    val size: Long = 0,
    val checksum: String = "",
    val file: File? = null,
    val error: String? = null
) {
    companion object {
        fun success(packageName: String, version: String, size: Long, checksum: String, file: File) =
            PackageResult(true, packageName, version, size, checksum, file)
            
        fun error(message: String) = 
            PackageResult(false, "", "", error = message)
    }
    
    fun getLanguage(): String {
        return when {
            packageName.contains("tinycc") -> "C/C++"
            packageName.contains("python") -> "Python"
            packageName.contains("java") -> "Java"
            else -> "Unknown"
        }
    }
}