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

interface TSLanguageInterface {
    fun getPointer(): Long
    
    fun getName(): String
    
    fun getVersion(): Int {
        return 1 
    }
    
    fun isAvailable(): Boolean {
        return getPointer() != 0L
    }
}

class CustomTSLanguageWrapper(
    private val customLanguage: Any, 
    private val nameProvider: () -> String,
    private val pointerProvider: () -> Long
) : TSLanguageInterface {
    
    override fun getPointer(): Long = pointerProvider()
    
    override fun getName(): String = nameProvider()
    
    override fun isAvailable(): Boolean = getPointer() != 0L
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TSLanguageInterface) return false
        return getPointer() == other.getPointer()
    }
    
    override fun hashCode(): Int {
        return getPointer().hashCode()
    }
    
    override fun toString(): String {
        return "CustomTSLanguage(name=${getName()}, pointer=${getPointer()})"
    }
} 