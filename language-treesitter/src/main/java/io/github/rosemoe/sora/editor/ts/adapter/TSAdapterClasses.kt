/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 ******************************************************************************/

package io.github.rosemoe.sora.editor.ts.adapter

class TSQueryAdapter(
    private val language: TSLanguageInterface,
    private val queryString: String
) : AutoCloseable {
    
    private var closed = false
    
    val patternCount: Int = 0
    val captureCount: Int = 0
    val errorType: TSQueryError = TSQueryError.None
    val errorOffset: Int = 0
    
    fun canAccess(): Boolean = !closed
    
    fun getCaptureNameForId(id: Int): String = ""
    
    fun getStringValueForId(id: Int): String = ""
    
    fun getStartByteForPattern(pattern: Int): Int = 0
    
    fun getPredicatesForPattern(pattern: Int): List<TSQueryPredicateStepAdapter> = emptyList()
    
    override fun close() {
        closed = true
    }
    
    companion object {
        fun create(language: TSLanguageInterface, source: String): TSQueryAdapter {
            return TSQueryAdapter(language, source)
        }
    }
}

enum class TSQueryError {
    None,
    Syntax,
    NodeType,
    Field,
    Capture,
    Structure,
    Language
}

class TSQueryPredicateStepAdapter(
    val type: Type,
    val valueId: Int
) {
    enum class Type {
        Done,
        Capture,
        String
    }
}

class TSQueryCursorAdapter : AutoCloseable {
    
    private var closed = false
    
    fun exec(query: TSQueryAdapter, node: TSNodeAdapter) {
    }
    
    fun setByteRange(start: Int, end: Int) {
    }
    
    fun nextMatch(): TSQueryMatchAdapter? {
        return null
    }
    
    override fun close() {
        closed = true
    }
    
    companion object {
        fun create(): TSQueryCursorAdapter {
            return TSQueryCursorAdapter()
        }
    }
}

class TSQueryMatchAdapter(
    val patternIndex: Int,
    val captures: List<TSQueryCaptureAdapter>
)

class TSQueryCaptureAdapter(
    val index: Int,
    val node: TSNodeAdapter
)

class TSNodeAdapter {
    val startByte: Int = 0
    val endByte: Int = 0
    val startPoint: TSPointAdapter = TSPointAdapter(0, 0)
    val endPoint: TSPointAdapter = TSPointAdapter(0, 0)
    val childCount: Int = 0
    
    fun canAccess(): Boolean = true
    fun hasChanges(): Boolean = false
    
    fun getChild(index: Int): TSNodeAdapter {
        return TSNodeAdapter()
    }
}

data class TSPointAdapter(
    val row: Int,
    val column: Int
) {
    companion object {
        fun create(row: Int, column: Int): TSPointAdapter {
            return TSPointAdapter(row, column)
        }
    }
}

class TSTreeAdapter : AutoCloseable {
    
    private var closed = false
    
    val rootNode: TSNodeAdapter = TSNodeAdapter()
    val language: TSLanguageInterface? = null
    
    fun canAccess(): Boolean = !closed
    
    fun copy(): TSTreeAdapter {
        return TSTreeAdapter()
    }
    
    fun edit(edit: TSInputEditAdapter) {
    }
    
    override fun close() {
        closed = true
    }
}

class TSParserAdapter : AutoCloseable {
    
    private var closed = false
    var language: TSLanguageInterface? = null
    
    fun parseString(text: CharSequence): TSTreeAdapter? {
        return TSTreeAdapter()
    }
    
    fun parseString(oldTree: TSTreeAdapter, text: CharSequence): TSTreeAdapter? {
        return TSTreeAdapter()
    }
    
    override fun close() {
        closed = true
    }
    
    companion object {
        fun create(): TSParserAdapter {
            return TSParserAdapter()
        }
    }
}

class TSInputEditAdapter(
    val startByte: Int,
    val oldEndByte: Int,
    val newEndByte: Int,
    val startPoint: TSPointAdapter,
    val oldEndPoint: TSPointAdapter,
    val newEndPoint: TSPointAdapter
) {
    companion object {
        fun create(
            startByte: Int,
            oldEndByte: Int,
            newEndByte: Int,
            startPoint: TSPointAdapter,
            oldEndPoint: TSPointAdapter,
            newEndPoint: TSPointAdapter
        ): TSInputEditAdapter {
            return TSInputEditAdapter(
                startByte, oldEndByte, newEndByte,
                startPoint, oldEndPoint, newEndPoint
            )
        }
    }
}

class UTF16StringAdapter : CharSequence, AutoCloseable {
    
    private val buffer = StringBuilder()
    override val length: Int get() = buffer.length
    
    fun append(text: String) {
        buffer.append(text)
    }
    
    fun insert(index: Int, text: String) {
        buffer.insert(index, text)
    }
    
    fun delete(start: Int, end: Int) {
        buffer.delete(start, end)
    }
    
    fun subseqChars(start: Int, end: Int): UTF16StringAdapter {
        val result = UTF16StringAdapter()
        result.buffer.append(buffer.substring(start, end))
        return result
    }
    
    override fun get(index: Int): Char {
        return buffer[index]
    }
    
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return buffer.subSequence(startIndex, endIndex)
    }
    
    override fun toString(): String {
        return buffer.toString()
    }
    
    override fun close() {
    }
}

object UTF16StringFactoryAdapter {
    fun newString(): UTF16StringAdapter {
        return UTF16StringAdapter()
    }
} 