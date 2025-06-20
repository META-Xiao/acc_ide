package com.acc_ide.model

/**
 * 表示一个代码文件的数据类
 *
 * @property name 文件名
 * @property content 文件内容
 * @property language 编程语言类型
 * @property lastModified 最后修改时间
 */
data class CodeFile(
    val name: String,
    val content: String,
    val language: String,
    val lastModified: Long = System.currentTimeMillis()
) 