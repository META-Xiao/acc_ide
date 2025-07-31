package com.acc_ide.lsp.model

/**
 * LSP 补全项数据模型
 */
data class CompletionItem(
    val label: String,
    val insertText: String,
    val detail: String,
    val kind: Int
)

/**
 * LSP 诊断信息数据模型
 */
data class Diagnostic(
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
    val message: String,
    val severity: DiagnosticSeverity
)

/**
 * 诊断严重程度枚举
 */
enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFORMATION,
    HINT
}

/**
 * LSP CompletionItemKind 常量
 */
object CompletionItemKind {
    const val TEXT = 1
    const val METHOD = 2
    const val FUNCTION = 3
    const val CONSTRUCTOR = 4
    const val FIELD = 5
    const val VARIABLE = 6
    const val CLASS = 7
    const val INTERFACE = 8
    const val MODULE = 9
    const val PROPERTY = 10
    const val UNIT = 11
    const val VALUE = 12
    const val ENUM = 13
    const val KEYWORD = 14
    const val SNIPPET = 15
    const val COLOR = 16
    const val FILE = 17
    const val REFERENCE = 18
    const val FOLDER = 19
    const val ENUM_MEMBER = 20
    const val CONSTANT = 21
    const val STRUCT = 22
    const val EVENT = 23
    const val OPERATOR = 24
    const val TYPE_PARAMETER = 25
} 