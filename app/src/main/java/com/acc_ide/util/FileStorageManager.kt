package com.acc_ide.util

import android.content.Context
import android.os.Environment
import android.util.Log
import com.acc_ide.model.CodeFile
import java.io.File
import java.io.IOException

/**
 * 文件存储管理器，用于处理文件的持久化存储和读取
 */
class FileStorageManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FileStorageManager"
    }
    
    /**
     * 获取存储代码文件的目录
     */
    fun getCodeFilesDir(): File {
        // 直接使用外部存储目录，路径为: /storage/emulated/0/Android/data/com.acc_ide/files
        val dir = context.getExternalFilesDir(null)
            ?: throw IOException("无法获取外部存储目录")
            
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * 从磁盘刷新文件状态
     * 主要用于确保文件系统状态是最新的，特别是在文件删除后
     */
    fun refreshFilesFromDisk() {
        try {
            val filesDir = getCodeFilesDir()
            
            // 确保目录存在
            if (!filesDir.exists()) {
                filesDir.mkdirs()
                Log.d(TAG, "创建代码文件目录: ${filesDir.absolutePath}")
                return
            }
            
            // 列出所有文件
            val allFiles = filesDir.listFiles() ?: return
            Log.d(TAG, "刷新文件系统状态: 发现 ${allFiles.size} 个文件")
            
            // 检查文件状态
            val mainFiles = allFiles.filter { !it.name.endsWith(".meta") }
            val metaFiles = allFiles.filter { it.name.endsWith(".meta") }
            
            Log.d(TAG, "文件系统状态: ${mainFiles.size} 个主文件, ${metaFiles.size} 个元数据文件")
            
            // 检查并清理损坏的文件
            cleanupOrphanedFiles(allFiles)
            
            // 再次列出所有文件，验证清理效果
            val remainingFiles = filesDir.listFiles() ?: return
            Log.d(TAG, "刷新完成: 剩余 ${remainingFiles.size} 个文件")
        } catch (e: Exception) {
            Log.e(TAG, "刷新文件系统状态时出错", e)
        }
    }
    
    /**
     * 将文件存储到内部存储
     * 
     * @param file 要存储的代码文件
     * @return 是否存储成功
     */
    fun saveFile(file: CodeFile): Boolean {
        try {
            val filesDir = getCodeFilesDir()
            val destFile = File(filesDir, sanitizeFileName(file.name))
            
            // 创建目录（如果不存在）
            if (!filesDir.exists()) {
                filesDir.mkdirs()
            }
            
            // 写入文件内容
            destFile.writeText(file.content)
            
            // 写入文件元数据
            val metaFile = File(filesDir, "${sanitizeFileName(file.name)}.meta")
            metaFile.writeText("${file.language}\n${file.lastModified}")
            
            Log.d(TAG, "文件保存成功: ${file.name}")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "文件保存失败: ${file.name}", e)
            return false
        }
    }
    
    /**
     * 删除文件
     * 
     * @param fileName 要删除的文件名
     * @return 是否删除成功
     */
    fun deleteFile(fileName: String): Boolean {
        try {
            Log.d(TAG, "开始删除文件: $fileName")
            val filesDir = getCodeFilesDir()
            val sanitizedFileName = sanitizeFileName(fileName)
            val fileToDelete = File(filesDir, sanitizedFileName)
            val metaFileToDelete = File(filesDir, "${sanitizedFileName}.meta")
            
            var success = true
            
            // 强制刷新文件状态
            fileToDelete.setLastModified(System.currentTimeMillis())
            metaFileToDelete.setLastModified(System.currentTimeMillis())
            
            // 检查文件是否存在
            if (!fileToDelete.exists()) {
                Log.e(TAG, "要删除的文件不存在: $fileName (路径: ${fileToDelete.absolutePath})")
                success = false
            } else {
                // 删除主文件
                for (i in 1..3) {  // 尝试最多3次
                    if (fileToDelete.delete()) {
                        Log.d(TAG, "成功删除主文件: $fileName (第" + i + "次尝试)")
                        break
                    } else {
                        Log.e(TAG, "删除文件失败: $fileName (第" + i + "次尝试)")
                        if (i == 3) success = false
                        // 在尝试之间短暂延迟
                        Thread.sleep(100)
                    }
                }
            }
            
            // 检查元数据文件是否存在
            if (!metaFileToDelete.exists()) {
                Log.e(TAG, "要删除的元数据文件不存在: ${fileName}.meta (路径: ${metaFileToDelete.absolutePath})")
            } else {
                // 删除元数据文件
                for (i in 1..3) {  // 尝试最多3次
                    if (metaFileToDelete.delete()) {
                        Log.d(TAG, "成功删除元数据文件: ${fileName}.meta (第" + i + "次尝试)")
                        break
                    } else {
                        Log.e(TAG, "删除元数据文件失败: ${fileName}.meta (第" + i + "次尝试)")
                        if (i == 3) success = false
                        // 在尝试之间短暂延迟
                        Thread.sleep(100)
                    }
                }
            }
            
            // 额外的文件删除方法 - 强制删除
            if (fileToDelete.exists()) {
                try {
                    Log.d(TAG, "尝试强制删除文件: ${fileToDelete.absolutePath}")
                    // 强制删除文件
                    val runtime = Runtime.getRuntime()
                    runtime.exec("rm -f ${fileToDelete.absolutePath}")
                    
                    // 等待文件系统完成删除操作
                    Thread.sleep(200)
                    
                    if (fileToDelete.exists()) {
                        // 如果仍然存在，尝试使用Java IO的方式强制删除
                        Log.d(TAG, "尝试使用Java IO方式删除文件")
                        if (fileToDelete.setWritable(true) && fileToDelete.delete()) {
                            Log.d(TAG, "Java IO方式删除文件成功")
                        } else {
                            Log.e(TAG, "强制删除文件后文件仍然存在: ${fileToDelete.absolutePath}")
                            success = false
                        }
                    } else {
                        Log.d(TAG, "强制删除文件成功: ${fileToDelete.absolutePath}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "强制删除文件出错: ${e.message}")
                }
            }
            
            if (metaFileToDelete.exists()) {
                try {
                    Log.d(TAG, "尝试强制删除元数据文件: ${metaFileToDelete.absolutePath}")
                    // 强制删除文件
                    val runtime = Runtime.getRuntime()
                    runtime.exec("rm -f ${metaFileToDelete.absolutePath}")
                    
                    // 等待文件系统完成删除操作
                    Thread.sleep(200)
                    
                    if (metaFileToDelete.exists()) {
                        // 如果仍然存在，尝试使用Java IO的方式强制删除
                        Log.d(TAG, "尝试使用Java IO方式删除元数据文件")
                        if (metaFileToDelete.setWritable(true) && metaFileToDelete.delete()) {
                            Log.d(TAG, "Java IO方式删除元数据文件成功")
                        } else {
                            Log.e(TAG, "强制删除元数据文件后文件仍然存在: ${metaFileToDelete.absolutePath}")
                            success = false
                        }
                    } else {
                        Log.d(TAG, "强制删除元数据文件成功: ${metaFileToDelete.absolutePath}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "强制删除元数据文件出错: ${e.message}")
                }
            }
            
            // 验证文件是否已被删除
            if (fileToDelete.exists() || metaFileToDelete.exists()) {
                Log.e(TAG, "删除后文件仍然存在: 文件=${fileToDelete.exists()}, 元数据=${metaFileToDelete.exists()}")
                
                // 最后尝试通过覆盖文件内容来"删除"文件
                if (fileToDelete.exists()) {
                    try {
                        fileToDelete.writeText("")
                        Log.d(TAG, "通过清空内容删除文件: ${fileToDelete.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "清空文件内容失败", e)
                    }
                }
                
                if (metaFileToDelete.exists()) {
                    try {
                        metaFileToDelete.writeText("")
                        Log.d(TAG, "通过清空内容删除元数据文件: ${metaFileToDelete.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "清空元数据文件内容失败", e)
                    }
                }
                
                success = false
            }
            
            // 列出删除后目录中的所有文件
            val remainingFiles = filesDir.listFiles()
            Log.d(TAG, "删除后目录中剩余 ${remainingFiles?.size ?: 0} 个文件")
            remainingFiles?.forEach { file ->
                Log.d(TAG, "  - 剩余文件: ${file.name}")
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "删除文件过程中发生异常: $fileName", e)
            return false
        }
    }
    
    /**
     * 重命名文件
     * 
     * @param oldFileName 旧文件名
     * @param newFileName 新文件名
     * @return 是否重命名成功
     */
    fun renameFile(oldFileName: String, newFileName: String): Boolean {
        try {
            val filesDir = getCodeFilesDir()
            val oldFile = File(filesDir, sanitizeFileName(oldFileName))
            val oldMetaFile = File(filesDir, "${sanitizeFileName(oldFileName)}.meta")
            
            if (!oldFile.exists() || !oldMetaFile.exists()) {
                Log.e(TAG, "要重命名的文件不存在: $oldFileName")
                return false
            }
            
            // 读取旧文件内容和元数据
            val content = oldFile.readText()
            val metaLines = oldMetaFile.readLines()
            
            if (metaLines.size < 2) {
                Log.e(TAG, "元数据格式错误: $oldFileName")
                return false
            }
            
            // 创建新文件对象
            val codeFile = CodeFile(
                name = newFileName,
                content = content,
                language = metaLines[0],
                lastModified = metaLines[1].toLongOrNull() ?: System.currentTimeMillis()
            )
            
            // 保存新文件
            if (!saveFile(codeFile)) {
                return false
            }
            
            // 删除旧文件
            return deleteFile(oldFileName)
        } catch (e: Exception) {
            Log.e(TAG, "文件重命名失败: $oldFileName -> $newFileName", e)
            return false
        }
    }
    
    /**
     * 读取文件内容
     * 
     * @param fileName 文件名
     * @return 代码文件对象，如果读取失败返回null
     */
    fun readFile(fileName: String): CodeFile? {
        try {
            val filesDir = getCodeFilesDir()
            val file = File(filesDir, sanitizeFileName(fileName))
            val metaFile = File(filesDir, "${sanitizeFileName(fileName)}.meta")
            
            if (!file.exists()) {
                Log.e(TAG, "文件不存在: $fileName")
                return null
            }
            
            if (!metaFile.exists()) {
                Log.e(TAG, "元数据文件不存在: $fileName")
                // 创建默认元数据
                val defaultLanguage = when {
                    fileName.endsWith(".cpp") -> "cpp"
                    fileName.endsWith(".py") -> "python"
                    fileName.endsWith(".java") -> "java"
                    else -> "text"
                }
                metaFile.writeText("$defaultLanguage\n${System.currentTimeMillis()}")
            }
            
            // 读取文件内容
            val content = file.readText()
            
            // 读取元数据
            val metaLines = metaFile.readLines()
            val language = if (metaLines.isNotEmpty()) metaLines[0] else "text"
            val lastModified = if (metaLines.size > 1) metaLines[1].toLongOrNull() ?: System.currentTimeMillis() else System.currentTimeMillis()
            
            return CodeFile(
                name = fileName,
                content = content,
                language = language,
                lastModified = lastModified
            )
        } catch (e: Exception) {
            Log.e(TAG, "文件读取失败: $fileName", e)
            return null
        }
    }
    
    /**
     * 获取所有存储的文件
     * 
     * @return 代码文件列表
     */
    fun getAllFiles(): List<CodeFile> {
        val result = mutableListOf<CodeFile>()
        try {
            val filesDir = getCodeFilesDir()
            Log.d(TAG, "正在从目录读取文件: ${filesDir.absolutePath}")
            
            if (!filesDir.exists()) {
                Log.d(TAG, "代码文件目录不存在，正在创建")
                filesDir.mkdirs()
                return emptyList()
            }
            
            // 获取所有文件
            val allFiles = filesDir.listFiles() ?: return emptyList()
            Log.d(TAG, "发现 ${allFiles.size} 个文件")
            
            // 首先进行清理，删除没有配对的文件或元数据文件
            cleanupOrphanedFiles(allFiles)
            
            // 获取清理后的文件列表
            val updatedAllFiles = filesDir.listFiles() ?: return emptyList()
            
            // 过滤出非元数据文件
            val codeFiles = updatedAllFiles.filter { !it.name.endsWith(".meta") }
            Log.d(TAG, "其中 ${codeFiles.size} 个为代码文件")
            
            for (file in codeFiles) {
                val fileName = file.name
                Log.d(TAG, "处理文件: $fileName")
                
                // 确保对应的元数据文件存在
                val metaFile = File(filesDir, "$fileName.meta")
                if (!metaFile.exists()) {
                    Log.w(TAG, "文件 $fileName 没有对应的元数据文件，跳过")
                    continue
                }
                
                try {
                    // 验证文件完整性
                    if (file.length() == 0L) {
                        Log.w(TAG, "文件 $fileName 内容为空，跳过")
                        continue
                    }
                    
                    val codeFile = readFile(fileName)
                    if (codeFile != null) {
                        result.add(codeFile)
                        Log.d(TAG, "已加载文件: $fileName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "读取文件 $fileName 时出错", e)
                }
            }
            
            Log.d(TAG, "成功加载 ${result.size} 个文件")
        } catch (e: Exception) {
            Log.e(TAG, "获取所有文件失败", e)
        }
        
        return result
    }
    
    /**
     * 清理孤立文件（没有配对的主文件或元数据文件）
     */
    private fun cleanupOrphanedFiles(files: Array<File>) {
        try {
            Log.d(TAG, "开始清理孤立文件...")
            
            // 将文件分为主文件和元数据文件
            val mainFiles = files.filter { !it.name.endsWith(".meta") }.map { it.name }
            val metaFiles = files.filter { it.name.endsWith(".meta") }
                .map { it.name.removeSuffix(".meta") }
            
            // 查找孤立的主文件（没有对应元数据文件的主文件）
            val orphanedMainFiles = mainFiles.filter { !metaFiles.contains(it) }
            
            // 查找孤立的元数据文件（没有对应主文件的元数据文件）
            val orphanedMetaFiles = metaFiles.filter { !mainFiles.contains(it) }
            
            // 删除孤立的主文件
            for (fileName in orphanedMainFiles) {
                Log.w(TAG, "发现孤立的主文件: $fileName，尝试删除")
                val file = File(getCodeFilesDir(), fileName)
                if (file.delete()) {
                    Log.d(TAG, "成功删除孤立的主文件: $fileName")
                } else {
                    Log.e(TAG, "无法删除孤立的主文件: $fileName")
                }
            }
            
            // 删除孤立的元数据文件
            for (fileName in orphanedMetaFiles) {
                Log.w(TAG, "发现孤立的元数据文件: $fileName.meta，尝试删除")
                val file = File(getCodeFilesDir(), "$fileName.meta")
                if (file.delete()) {
                    Log.d(TAG, "成功删除孤立的元数据文件: $fileName.meta")
                } else {
                    Log.e(TAG, "无法删除孤立的元数据文件: $fileName.meta")
                }
            }
            
            Log.d(TAG, "清理完成。删除了 ${orphanedMainFiles.size} 个孤立主文件和 ${orphanedMetaFiles.size} 个孤立元数据文件")
        } catch (e: Exception) {
            Log.e(TAG, "清理孤立文件时出错", e)
        }
    }
    
    /**
     * 清除所有文件
     * 仅用于调试和重置应用状态
     */
    fun deleteAllFiles(): Boolean {
        try {
            val filesDir = getCodeFilesDir()
            if (!filesDir.exists()) {
                return true
            }
            
            val allFiles = filesDir.listFiles() ?: return true
            var success = true
            
            for (file in allFiles) {
                if (!file.delete()) {
                    Log.e(TAG, "无法删除文件: ${file.name}")
                    success = false
                }
            }
            
            // 验证删除
            val remainingFiles = filesDir.listFiles()
            if (remainingFiles != null && remainingFiles.isNotEmpty()) {
                Log.e(TAG, "删除所有文件后仍有 ${remainingFiles.size} 个文件")
                success = false
            } else {
                Log.d(TAG, "成功删除所有文件")
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "删除所有文件失败", e)
            return false
        }
    }
    
    /**
     * 清理文件名，确保安全
     */
    fun sanitizeFileName(fileName: String): String {
        // 替换不安全的字符
        return fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    }
    
    /**
     * 检查文件是否存在
     *
     * @param fileName 文件名
     * @return 文件是否存在
     */
    fun fileExists(fileName: String): Boolean {
        try {
            val filesDir = getCodeFilesDir()
            val file = File(filesDir, sanitizeFileName(fileName))
            
            // 刷新文件状态
            file.setLastModified(System.currentTimeMillis())
            
            // 检查文件是否存在
            val exists = file.exists() && file.isFile && file.length() > 0
            
            // 如果文件存在，再检查元数据文件
            if (exists) {
                val metaFile = File(filesDir, "${sanitizeFileName(fileName)}.meta")
                val metaExists = metaFile.exists() && metaFile.isFile
                
                // 记录文件状态
                Log.d(TAG, "检查文件: $fileName - 文件存在: $exists, 元数据存在: $metaExists")
                
                // 如果文件存在但元数据不存在，创建默认元数据
                if (!metaExists) {
                    try {
                        val defaultLanguage = when {
                            fileName.endsWith(".cpp") -> "cpp"
                            fileName.endsWith(".py") -> "python"
                            fileName.endsWith(".java") -> "java"
                            else -> "text"
                        }
                        metaFile.writeText("$defaultLanguage\n${System.currentTimeMillis()}")
                        Log.d(TAG, "为文件 $fileName 创建了默认元数据")
                    } catch (e: Exception) {
                        Log.e(TAG, "创建默认元数据失败", e)
                    }
                }
            }
            
            return exists
        } catch (e: Exception) {
            Log.e(TAG, "检查文件存在状态时出错: $fileName", e)
            return false
        }
    }
    
    /**
     * 获取所有代码文件
     * 
     * @return 代码文件列表
     */
    fun getAllCodeFiles(): List<CodeFile> {
        val files = mutableListOf<CodeFile>()
        try {
            val filesDir = getCodeFilesDir()
            if (!filesDir.exists()) {
                filesDir.mkdirs()
                return files
            }
            
            // 获取所有文件
            val fileList = filesDir.listFiles() ?: return files
            
            // 只处理实际代码文件（非元数据文件）
            val codeFiles = fileList.filter { 
                !it.isDirectory && !it.name.endsWith(".meta") && !it.name.startsWith(".") 
            }
            
            // 将每个文件转换为CodeFile对象
            for (file in codeFiles) {
                val content = file.readText()
                val language = getFileLanguage(file.name)
                val lastModified = file.lastModified()
                
                files.add(CodeFile(file.name, content, language, lastModified))
            }
            
            Log.d(TAG, "已加载 ${files.size} 个代码文件")
        } catch (e: Exception) {
            Log.e(TAG, "获取代码文件列表失败", e)
        }
        return files
    }
    
    /**
     * 根据文件名确定编程语言
     * 
     * @param fileName 文件名
     * @return 对应的编程语言
     */
    private fun getFileLanguage(fileName: String): String {
        return when {
            fileName.endsWith(".java") -> "java"
            fileName.endsWith(".kt") -> "kotlin"
            fileName.endsWith(".py") -> "python"
            fileName.endsWith(".cpp") || fileName.endsWith(".c") -> "cpp"
            fileName.endsWith(".js") -> "javascript"
            fileName.endsWith(".html") -> "html"
            fileName.endsWith(".css") -> "css"
            fileName.endsWith(".xml") -> "xml"
            fileName.endsWith(".json") -> "json"
            fileName.endsWith(".md") -> "markdown"
            fileName.endsWith(".txt") -> "text"
            else -> "text"
        }
    }
} 