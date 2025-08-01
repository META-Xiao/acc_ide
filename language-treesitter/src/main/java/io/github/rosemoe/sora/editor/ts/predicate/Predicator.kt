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

package io.github.rosemoe.sora.editor.ts.predicate

import io.github.rosemoe.sora.editor.ts.adapter.TSQueryAdapter
import io.github.rosemoe.sora.editor.ts.adapter.TSQueryMatchAdapter
import io.github.rosemoe.sora.editor.ts.adapter.TSQueryPredicateStepAdapter

/**
 * Predicate runner for tree-sitter
 *
 * @author Rosemoe
 */
class Predicator(private val query: TSQueryAdapter) {

    /**
     * Predicates for patterns
     */
    private val patternPredicates = mutableListOf<List<TsClientPredicateStep>>()

    init {
        for (i in 0 until query.patternCount) {
            patternPredicates.add(query.getPredicatesForPattern(i).map {
                when (it.type) {
                    TSQueryPredicateStepAdapter.Type.String -> TsClientPredicateStep(
                        it.type,
                        query.getStringValueForId(it.valueId)
                    )

                    TSQueryPredicateStepAdapter.Type.Capture -> TsClientPredicateStep(
                        it.type,
                        query.getCaptureNameForId(it.valueId)
                    )

                    else -> TsClientPredicateStep(it.type, "")
                }
            })
        }
    }

    fun doPredicate(
        predicates: List<TsPredicate>,
        text: CharSequence,
        match: TSQueryMatchAdapter,
        syntheticCaptureContainer: TsSyntheticCaptureContainer = TsSyntheticCaptureContainer.EMPTY_IMMUTABLE_CONTAINER
    ): Boolean {
        val description = patternPredicates[match.patternIndex]
        var tail = 0
        for (i in description.indices) {
            if (description[i].predicateType == TSQueryPredicateStepAdapter.Type.Done) {
                // Avoid allocating sublist if possible
                val subPredicateStep =
                    if (tail == 0 && i + 1 == description.size) description else description.subList(
                        tail,
                        i + 1
                    )
                for (j in predicates.indices) {
                    val predicate = predicates[j]
                    when (predicate.doPredicate(query, text, match, subPredicateStep, syntheticCaptureContainer)) {
                        PredicateResult.ACCEPT -> break
                        PredicateResult.REJECT -> return false
                        else -> {}
                    }
                }
                tail = i + 1
            }
        }

        return true
    }

}