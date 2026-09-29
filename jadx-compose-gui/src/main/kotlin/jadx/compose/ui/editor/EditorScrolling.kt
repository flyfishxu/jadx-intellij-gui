package jadx.compose.ui.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.ui.input.key.Key

/** Focus restoration must not reveal the old caret in an externally scrolled source document.
 * Declaration/search jumps scroll explicitly; keyboard selection reveals only the new caret. */
internal object EditorBringIntoViewSpec : BringIntoViewSpec {
	override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

internal val caretNavigationKeys = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
	Key.MoveHome, Key.MoveEnd, Key.PageUp, Key.PageDown)

internal suspend fun ScrollState.reveal(start: Float, end: Float) {
	val target = when {
		start < value -> start.toInt()
		end > value + viewportSize -> (end - viewportSize).toInt()
		else -> return
	}
	scrollTo(target.coerceAtLeast(0))
}
