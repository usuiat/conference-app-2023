package io.github.droidkaigi.confsched2023.sessions.component

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

@Composable
fun rememberTimetableScreenScrollState(): TimetableScreenScrollState {
    return rememberSaveable(saver = TimetableScreenScrollState.Saver) {
        TimetableScreenScrollState()
    }
}

@Stable
class TimetableScreenScrollState(
    initialSheetOffsetLimit: Float = 0f,
    initialSheetScrollOffset: Float = 0f,
) {
    // This value will be like -418.0
    private var sheetScrollOffsetLimit by mutableStateOf(initialSheetOffsetLimit)

    val isScreenLayoutCalculating get() = sheetScrollOffsetLimit == 0f

    private val _sheetScrollOffset = mutableStateOf(initialSheetScrollOffset)

    /**
     * If sheetScrollOffset is 0f, the sheet is fully collapsed.
     * If sheetScrollOffset is sheetScrollOffsetLimit, the sheet is fully expanded.
     */
    var sheetScrollOffset: Float
        get() = _sheetScrollOffset.value
        internal set(newOffset) {
            _sheetScrollOffset.value = newOffset.coerceIn(
                minimumValue = sheetScrollOffsetLimit,
                maximumValue = 0f,
            )
        }

    val isSheetExpandable: Boolean
        get() = sheetScrollOffset > sheetScrollOffsetLimit

    private val isSheetScrolled: Boolean
        get() = sheetScrollOffset != 0f

    val screenNestedScrollConnection: NestedScrollConnection
        get() = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return onPreScrollScreen(available)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                return onPostScrollScreen(available)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                println("*** Screen onPreFling velocity=$available")
                val superConsumed = super.onPreFling(available)
                return superConsumed + onPreFlingScreen(available)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                println("*** Screen onPostFling velocity=$available")
                val superConsumed = super.onPostFling(consumed, available)
                return superConsumed + onPostFlingScreen(available)
            }
        }

    /**
     * This function returns the consumed offset.
     */
    private fun onPreScrollScreen(availableScrollOffset: Offset): Offset {
        if (availableScrollOffset.y >= 0) return Offset.Zero
        // When scrolled upward
        return if (isSheetExpandable && !isScreenLayoutCalculating) {
            // Add offset up to the height of TopAppBar and consume all
            val prevHeightOffset: Float = sheetScrollOffset
            sheetScrollOffset += availableScrollOffset.y
            availableScrollOffset.copy(x = 0f, y = sheetScrollOffset - prevHeightOffset)
        } else {
            Offset.Zero
        }
    }

    /**
     * This function returns the consumed offset.
     */
    private fun onPostScrollScreen(availableScrollOffset: Offset): Offset {
        if (availableScrollOffset.y < 0f) return Offset.Zero
        return if (isSheetScrolled && availableScrollOffset.y > 0) {
            // When scrolling downward and overscroll
            val prevHeightOffset = sheetScrollOffset
            sheetScrollOffset += availableScrollOffset.y
            availableScrollOffset.copy(x = 0f, y = sheetScrollOffset - prevHeightOffset)
        } else {
            Offset.Zero
        }
    }

    private suspend fun onPreFlingScreen(availableScrollVelocity: Velocity): Velocity {
        if (availableScrollVelocity.y >= 0f) return Velocity.Zero

        return if (isSheetExpandable && !isScreenLayoutCalculating) {
            val remainingVelocity = settleScreenOffset(
                initialVelocity = availableScrollVelocity.y,
                isFinished = { !isSheetExpandable },
            )
            Velocity(0f, remainingVelocity)
        } else {
            Velocity.Zero
        }
    }

    private suspend fun onPostFlingScreen(availableScrollVelocity: Velocity): Velocity {
        if (availableScrollVelocity.y <= 0f) return Velocity.Zero

        return if (isSheetScrolled && !isScreenLayoutCalculating) {
            val remainingVelocity = settleScreenOffset(
                initialVelocity = availableScrollVelocity.y,
                isFinished = { !isSheetScrolled },
            )
            Velocity(0f, remainingVelocity)
        } else {
            Velocity.Zero
        }
    }

    private suspend fun settleScreenOffset(
        initialVelocity: Float,
        isFinished: (Float) -> Boolean = { false },
    ): Float {
        var remainingVelocity = initialVelocity
        AnimationState(
            initialValue = sheetScrollOffset,
            initialVelocity = initialVelocity,
        )
            .animateDecay(exponentialDecay()) {
                println("*** Screen value=$value velocity=${this.velocity}")
                sheetScrollOffset = value
                remainingVelocity = this.velocity
                if (isFinished(value)) this.cancelAnimation()
            }
        return remainingVelocity
    }

    fun onHeaderPositioned(headerHeight: Float) {
        sheetScrollOffsetLimit = 0f - abs(headerHeight)
    }

    companion object {
        val Saver: Saver<TimetableScreenScrollState, *> = listSaver(
            save = { listOf(it.sheetScrollOffsetLimit, it.sheetScrollOffset) },
            restore = {
                TimetableScreenScrollState(
                    initialSheetOffsetLimit = it[0],
                    initialSheetScrollOffset = it[1],
                )
            },
        )
    }
}
