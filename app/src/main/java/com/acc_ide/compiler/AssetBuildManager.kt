package com.acc_ide.compiler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import org.json.JSONObject

/**
 * 资产构建管理器
 * 负责协调PowerShell构建脚本和资产包管理
 */
class AssetBuildManager(private val context: Context) {
    
    private val TAG = "AssetBuildManager"
    private val assetPackager = AssetPackager(context)
    private val projectRoot = getProjectRoot()
    
    /**
     * 执行完整的资产构建流程
     */
    suspend fun buildAllAssets(): BuildResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting complete asset build process...")
            
            val results = mutableListOf<String>()
            
            // 1. 检查构建脚本是否存在
            if (!checkBuildScripts()) {
                return@withContext BuildResult.error("构建脚本不存在，请先运行PowerShell脚本创建编译器工具链")
            }
            results.add("✓ 构建脚本检查通过")
            
            // 2. 创建编译器包
            val packages = mutableListOf<PackageResult>()
            
            // 创建TinyCC包
            val tccResult = assetPackager.createTinyCCPackage()
            packages.add(tccResult)
            if (tccResult.success) {
                results.add("✓ TinyCC包创建成功")
            } else {
                results.add("✗ TinyCC包创建失败: ${tccResult.error}")
            }
            
            // 创建Python包  
            val pythonResult = assetPackager.createPythonPackage()
            packages.add(pythonResult)
            if (pythonResult.success) {
                results.add("✓ Python包创建成功")
            } else {
                results.add("✗ Python包创建失败: ${pythonResult.error}")
            }
            
            // 创建Java包
            val javaResult = assetPackager.createJavaPackage()
            packages.add(javaResult)
            if (javaResult.success) {
                results.add("✓ Java包创建成功")
            } else {
                results.add("✗ Java包创建失败: ${javaResult.error}")
            }
            
            // 3. 创建清单文件
            val manifestFile = assetPackager.createManifest(packages.filter { it.success })
            results.add("✓ 清单文件创建完成")
            
            // 4. 验证包完整性
            var verificationPassed = 0
            var totalPackages = 0
            
            packages.filter { it.success }.forEach { pkg ->
                totalPackages++
                if (pkg.file != null && assetPackager.verifyPackage(pkg.file, pkg.checksum)) {
                    verificationPassed++
                    results.add("✓ ${pkg.packageName} 验证通过")
                } else {
                    results.add("✗ ${pkg.packageName} 验证失败")
                }
            }
            
            val summary = BuildSummary(
                totalPackages = totalPackages,
                successfulPackages = packages.count { it.success },
                verificationPassed = verificationPassed,
                manifestCreated = manifestFile.exists(),
                buildSteps = results
            )
            
            if (summary.allSuccessful()) {
                Log.d(TAG, "Asset build completed successfully")
                BuildResult.success("资产构建完成", summary)
            } else {
                Log.w(TAG, "Asset build completed with errors")
                BuildResult.error("资产构建部分失败", summary)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Asset build failed", e)
            BuildResult.error("构建异常: ${e.message}")
        }
    }
    
    /**
     * 检查构建脚本状态
     */
    fun checkBuildScripts(): Boolean {
        val buildScript = File(projectRoot, "build-simple.ps1")
        val buildToolchainScript = File(projectRoot, "build-toolchain.ps1")
        
        return buildScript.exists() || buildToolchainScript.exists()
    }
    
    /**
     * 获取资产构建状态
     */
    suspend fun getBuildStatus(): BuildStatus = withContext(Dispatchers.IO) {
        try {
            val assetsDir = File(context.filesDir, "compiler_assets")
            val manifestFile = File(assetsDir, AssetPackager.MANIFEST_FILE)
            
            if (!manifestFile.exists()) {
                return@withContext BuildStatus(
                    hasAssets = false,
                    packagesCount = 0,
                    lastBuildTime = 0,
                    version = "",
                    packages = emptyList()
                )
            }
            
            val manifest = JSONObject(manifestFile.readText())
            val packages = mutableListOf<String>()
            val packagesArray = manifest.optJSONArray("packages")
            
            if (packagesArray != null) {
                for (i in 0 until packagesArray.length()) {
                    val pkg = packagesArray.getJSONObject(i)
                    packages.add("${pkg.getString("name")} v${pkg.getString("version")}")
                }
            }
            
            BuildStatus(
                hasAssets = true,
                packagesCount = packages.size,
                lastBuildTime = manifest.optLong("created", 0),
                version = manifest.optString("version", "unknown"),
                packages = packages
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get build status", e)
            BuildStatus(hasAssets = false, packagesCount = 0, lastBuildTime = 0, version = "", packages = emptyList())
        }
    }
    
    /**
     * 清理构建产物
     */
    suspend fun cleanAssets(): Boolean = withContext(Dispatchers.IO) {
        try {
            val assetsDir = File(context.filesDir, "compiler_assets")
            if (assetsDir.exists()) {
                assetsDir.deleteRecursively()
                Log.d(TAG, "Assets cleaned successfully")
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean assets", e)
            return@withContext false
        }
    }
    
    private fun getProjectRoot(): File {
        // 尝试获取项目根目录，这在实际Android环境中可能需要调整
        val externalFilesDir = context.getExternalFilesDir(null)
        return externalFilesDir?.parentFile?.parentFile?.parentFile ?: File("/")
    }
}

/**
 * 构建结果数据类
 */
data class BuildResult(
    val success: Boolean,
    val message: String,
    val summary: BuildSummary? = null,
    val error: String? = null
) {
    companion object {
        fun success(message: String, summary: BuildSummary) = BuildResult(true, message, summary)
        fun error(message: String, summary: BuildSummary? = null) = BuildResult(false, message, summary, message)
    }
}

/**
 * 构建总结数据类
 */
data class BuildSummary(
    val totalPackages: Int,
    val successfulPackages: Int,
    val verificationPassed: Int,
    val manifestCreated: Boolean,
    val buildSteps: List<String>
) {
    fun allSuccessful(): Boolean = successfulPackages == totalPackages && 
                                   verificationPassed == totalPackages && 
                                   manifestCreated
                                   
    fun getSuccessRate(): Float = if (totalPackages > 0) {
        successfulPackages.toFloat() / totalPackages.toFloat()
    } else 0f
}

/**
 * 构建状态数据类
 */
data class BuildStatus(
    val hasAssets: Boolean,
    val packagesCount: Int,
    val lastBuildTime: Long,
    val version: String,
    val packages: List<String>
)