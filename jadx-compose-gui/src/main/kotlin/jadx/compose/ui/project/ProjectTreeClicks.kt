package jadx.compose.ui.project

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.treeStyle

/** One owner for mouse gestures across text, icons, indentation and row padding. */
@Composable
internal fun Modifier.projectTreeClicks(
	state: TreeState,
	listState: LazyListState,
	directoryDepths: Map<String, Int>,
	openFile: (Any) -> Unit
): Modifier {
	val open by rememberUpdatedState(openFile)
	val depths by rememberUpdatedState(directoryDepths)
	val focus = remember { FocusRequester() }
	val metrics = JewelTheme.treeStyle.metrics
	val direction = LocalLayoutDirection.current
	val density = LocalDensity.current
	val inset = with(density) {
		(metrics.simpleListItemMetrics.outerPadding.calculateLeftPadding(direction) +
				metrics.simpleListItemMetrics.innerPadding.calculateLeftPadding(direction)).toPx()
	}
	val indent = with(density) { metrics.indentSize.toPx() }
	// Jewel's tree chevron uses the standard 16 dp icon slot.
	val chevronWidth = with(density) { 16.dp.toPx() }
	return focusRequester(focus).pointerInput(state, listState, inset, indent, chevronWidth) {
		awaitPointerEventScope {
			var pressedKey: Any? = null
			var pressPosition = Offset.Zero
			var pressTime = 0L
			var lastKey: Any? = null
			var lastPosition = Offset.Zero
			var lastTime = 0L
			var dragged = false
			fun keyAt(point: Offset): Any? = listState.layoutInfo.visibleItemsInfo
				.firstOrNull { point.y >= it.offset && point.y < it.offset + it.size }?.key
			while (true) {
				val event = awaitPointerEvent(PointerEventPass.Initial)
				val change = event.changes.firstOrNull() ?: continue
				when (event.type) {
					PointerEventType.Press -> {
						if (!event.buttons.isPrimaryPressed) {
							lastKey = null; continue
						}
						pressedKey = keyAt(change.position)
						pressPosition = change.position
						pressTime = change.uptimeMillis
						dragged = false
						focus.requestFocus()
						// Jewel still handles selection on press, including Shift/Cmd modifiers.
						change.consume()
					}

					PointerEventType.Move -> if (pressedKey != null && (change.position - pressPosition).getDistance() > viewConfiguration.touchSlop) dragged =
						true

					PointerEventType.Scroll -> {
						pressedKey = null; lastKey = null
					}

					PointerEventType.Release -> {
						val key = pressedKey ?: continue
						pressedKey = null
						change.consume()
						if (dragged || keyAt(change.position) != key || change.uptimeMillis - pressTime > viewConfiguration.longPressTimeoutMillis) {
							lastKey = null
							continue
						}
						val depth = depths[key]
						val chevronLeft = depth?.let { inset + it * indent }
						if (chevronLeft != null && pressPosition.x >= chevronLeft && pressPosition.x < chevronLeft + chevronWidth) {
							state.toggleNode(key)
							lastKey = null
						} else if (lastKey == key && pressTime - lastTime <= viewConfiguration.doubleTapTimeoutMillis &&
							(change.position - lastPosition).getDistance() <= viewConfiguration.touchSlop
						) {
							lastKey = null
							if (depth != null) state.toggleNode(key) else open(key)
						} else {
							lastKey = key
							lastTime = change.uptimeMillis
							lastPosition = change.position
						}
					}
				}
			}
		}
	}
}
