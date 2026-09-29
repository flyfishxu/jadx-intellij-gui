package jadx.compose.ui.editor

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import jadx.compose.decompiler.CodeMode
import jadx.compose.decompiler.SourceDocument
import jadx.compose.ui.window.LocalForceClick
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.linkStyle

internal val LocalNavigationModifier = staticCompositionLocalOf<MutableState<Boolean>?> { null }

/** Hit testing is in viewport coordinates, so scrolling, clipping and the gutter share one boundary. */
@Stable
internal class CodeLinks(private val document: SourceDocument) {
	var pointer by mutableStateOf<Offset?>(null)
	var viewport: LayoutCoordinates? = null
	var text: LayoutCoordinates? = null
	private var lastGeometry: Pair<Offset, IntSize>? = null
	var geometryRevision by mutableIntStateOf(0)
	var layout: TextLayoutResult? = null
	var activeRange by mutableStateOf<TextRange?>(null)
	var viewportModifier: Modifier = Modifier
	var textModifier: Modifier = Modifier

	fun updateTextCoordinates(coordinates: LayoutCoordinates) {
		text = coordinates
		val viewport = viewport?.takeIf { it.isAttached } ?: return
		val geometry = viewport.localPositionOf(coordinates, Offset.Zero) to coordinates.size
		if (geometry != lastGeometry) {
			lastGeometry = geometry
			if (pointer != null) geometryRevision++
		}
	}

	fun rangeAt(position: Offset?): TextRange? {
		if (position == null || document.mode != CodeMode.JAVA) return null
		val viewport = viewport?.takeIf { it.isAttached } ?: return null
		val text = text?.takeIf { it.isAttached } ?: return null
		val measured = layout ?: return null
		val point = text.localPositionOf(viewport, position)
		var offset = measured.getOffsetForPosition(point)
		if (offset > 0 && measured.getBoundingBox(offset - 1).contains(point)) offset--
		val code = document.code
		if (offset !in code.indices || !measured.getBoundingBox(offset).contains(point) || !Character.isJavaIdentifierPart(code[offset])) return null
		var start = offset
		var end = offset + 1
		while (start > 0 && Character.isJavaIdentifierPart(code[start - 1])) start--
		while (end < code.length && Character.isJavaIdentifierPart(code[end])) end++
		return TextRange(start, end)
	}
}

@Composable
internal fun rememberCodeLinks(
	document: SourceDocument,
	scrollPosition: Pair<Int, Int>,
	canNavigate: suspend (Int) -> Boolean,
	onNavigate: (Int) -> Unit,
): CodeLinks {
	val links = remember(document) { CodeLinks(document) }
	val navigate by rememberUpdatedState(onNavigate)
	val resolve by rememberUpdatedState(canNavigate)
	val window = LocalWindowInfo.current
	val modifiers = window.keyboardModifiers
	val modifierState = LocalNavigationModifier.current
	val held = (modifierState?.value == true || modifiers.isMetaPressed || modifiers.isCtrlPressed) && window.isWindowFocused
	// Read scroll state as well as layout: LayoutCoordinates themselves mutate during scrolling.
	val range = remember(links.pointer, held, scrollPosition, links.geometryRevision) { links.rangeAt(links.pointer) }
	val resolved = remember(document) { mutableMapOf<Int, Boolean>() }
	var verifiedRange by remember(document) { mutableStateOf<TextRange?>(null) }
	LaunchedEffect(range, held) {
		verifiedRange = null
		if (range != null && held) {
			val available = resolved[range.start] ?: run {
				delay(75) // Avoid decompiling every symbol crossed by a moving pointer.
				resolve(range.start).also { resolved[range.start] = it }
			}
			if (available) verifiedRange = range
		}
	}
	val activeRange = verifiedRange?.takeIf { held && it == range }
	SideEffect { links.activeRange = activeRange }
	val forceClick = LocalForceClick.current
	SideEffect { forceClick?.target = range?.let { { navigate(it.start) } } }
	DisposableEffect(links, forceClick) { onDispose { forceClick?.target = null } }
	links.viewportModifier = Modifier.onGloballyPositioned { links.viewport = it }
		.pointerHoverIcon(if (activeRange != null) PointerIcon.Hand else PointerIcon.Text, overrideDescendants = true)
		.pointerInput(links) {
			awaitPointerEventScope {
				var navigating = false
				while (true) {
					val event = awaitPointerEvent(PointerEventPass.Initial)
					modifierState?.value = event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed
					val point = event.changes.firstOrNull()?.position
					links.pointer = if (event.type == PointerEventType.Exit) null else point
					if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed &&
						(event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed ||
							window.keyboardModifiers.isMetaPressed || window.keyboardModifiers.isCtrlPressed)) {
						links.rangeAt(point)?.let { token ->
							navigating = true
							if (resolved[token.start] != false) navigate(token.start)
						}
					}
					// Consume the entire gesture before BasicTextField can move the caret on release.
					if (navigating) {
						event.changes.forEach { it.consume() }
						if (event.type == PointerEventType.Release) navigating = false
					}
				}
			}
		}
	links.textModifier = Modifier.onGloballyPositioned(links::updateTextCoordinates)
	return links
}

@Composable
internal fun linkedCode(highlighted: AnnotatedString, range: TextRange?): AnnotatedString {
	val color = JewelTheme.linkStyle.colors.contentHovered
	return remember(highlighted, range, color) {
		if (range == null) highlighted else AnnotatedString.Builder(highlighted).apply {
			addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline), range.start, range.end)
		}.toAnnotatedString()
	}
}
