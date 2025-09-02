package com.acc_ide.compiler

/**
 * 支持的编程语言枚举
 */
enum class Language(val displayName: String, val extension: String) {
    C("C", "c"),
    CPP("C++", "cpp"),
    JAVA("Java", "java"),
    PYTHON("Python", "py");
    
    companion object {
        /**
         * 根据文件扩展名推断语言类型
         */
        fun fromExtension(extension: String): Language? {
            return values().find { it.extension.equals(extension, ignoreCase = true) }
        }
        
        /**
         * 根据文件名推断语言类型
         */
        fun fromFileName(fileName: String): Language? {
            val ext = fileName.substringAfterLast('.', "")
            return fromExtension(ext)
        }
    }
}