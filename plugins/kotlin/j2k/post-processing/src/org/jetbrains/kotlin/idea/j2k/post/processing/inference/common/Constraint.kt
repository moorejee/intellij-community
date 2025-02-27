// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

enum class ConstraintPriority {
    /* order of entries here used when solving system of constraints */
    SUPER_DECLARATION,
    INITIALIZER,
    RETURN,
    ASSIGNMENT,
    PARAMETER,
    RECEIVER_PARAMETER,
    COMPARE_WITH_NULL,
    USE_AS_RECEIVER,
}

sealed class Constraint {
    abstract val priority: ConstraintPriority
}

class SubtypeConstraint(
  var subtype: ConstraintBound,
  var supertype: ConstraintBound,
  override val priority: ConstraintPriority
) : Constraint() {
    operator fun component1() = subtype
    operator fun component2() = supertype
}

class EqualsConstraint(
  var left: ConstraintBound,
  var right: ConstraintBound,
  override val priority: ConstraintPriority
) : Constraint() {
    operator fun component1() = left
    operator fun component2() = right
}

fun Constraint.copy() = when (this) {
    is SubtypeConstraint -> SubtypeConstraint(subtype, supertype, priority)
    is EqualsConstraint -> EqualsConstraint(left, right, priority)
}

sealed class ConstraintBound
class TypeVariableBound(val typeVariable: TypeVariable) : ConstraintBound()
class LiteralBound private constructor(val state: State) : ConstraintBound() {
    companion object {
        val UPPER = LiteralBound(State.UPPER)
        val LOWER = LiteralBound(State.LOWER)
        val UNKNOWN = LiteralBound(State.UNKNOWN)
    }
}

fun State.constraintBound(): LiteralBound? = when (this) {
    State.LOWER -> LiteralBound.LOWER
    State.UPPER -> LiteralBound.UPPER
    State.UNKNOWN -> LiteralBound.UNKNOWN
    State.UNUSED -> null
}

val ConstraintBound.isUnused
    get() = when (this) {
        is TypeVariableBound -> typeVariable.state == State.UNUSED
        is LiteralBound -> state == State.UNUSED
    }
