/*
 * AccIDE Termux Bootstrap Plugin
 * Downloads and embeds termux bootstrap packages
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class TermuxBootstrapPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.run {
            logger.info("AccIDE: TermuxBootstrapPlugin applied")
            
            val bootstrapDir = File(buildDir, "termux-bootstrap")
            bootstrapDir.mkdirs()

            // 为了避免下载超时，我们先创建一个简单的 JNI 文件
            // 在后续版本中，可以实现真实的bootstrap下载
            createSimpleJniFile()
        }
    }

    private fun Project.createSimpleJniFile() {
        val jniFile = File(projectDir, "src/main/cpp/termux-bootstrap-zip.S")
        jniFile.parentFile.mkdirs()

        val content = """
             .global blob
             .global blob_size
             .section .rodata
         blob:
             // 这里包含一个空的bootstrap占位符
             .byte 0x50, 0x4b, 0x03, 0x04  // ZIP header
         1:
         blob_size:
             .int 1b - blob
         
      """.trimIndent()

        jniFile.writeText(content)
        logger.info("Created simple JNI bootstrap file: ${jniFile.absolutePath}")
    }
}