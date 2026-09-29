package jadx.compose.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jadx.compose.decompiler.SourceDocument
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.components.Rule
import jadx.compose.ui.model.NavigationTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.HorizontalScrollbar
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import java.util.concurrent.atomic.AtomicReference

@Composable
internal fun CodeEditor(
	document: SourceDocument,
	prefs: AppPreferences,
	find: Boolean,
	closeFind: () -> Unit,
	jump: NavigationTarget?,
	onNavigate: (Int) -> Unit,
	canNavigate: suspend (Int) -> Boolean = { false },
	onFindUsages: (Int) -> Unit = {},
	focusRequest: Int = 0,
	back: (() -> Unit)? = null,
	forward: (() -> Unit)? = null,
	onCaret: (String, Int) -> Unit
) {
	val dark = JewelTheme.isDark
	var highlighted by remember(document) { mutableStateOf(AnnotatedString(document.code)) }
	LaunchedEffect(document, dark) {
		highlighted = withContext(Dispatchers.Default) {
			decorateCode(
				highlight(
					document.code,
					dark,
					document.mode
				), document.references.references, emptyList(), emptyList(), emptyList(), dark
			)
		}
	}
	var value by rememberSaveable(
		document.viewKey,
		stateSaver = TextFieldValue.Saver
	) { mutableStateOf(TextFieldValue(document.code)) }
	var queryValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
		mutableStateOf(
			TextFieldValue()
		)
	}
	val query = queryValue.text
	var findOptions by rememberSaveable { mutableStateOf(Triple(false, false, false)) }
	var findResult by remember(document) { mutableStateOf(EditorFindResult()) }
	LaunchedEffect(query, findOptions, document) {
		findResult = withContext(Dispatchers.Default) {
			editorFind(
				document.code,
				query,
				EditorFindOptions(findOptions.first, findOptions.second, findOptions.third)
			)
		}
	}
	val matches = findResult.matches
	var matchIndex by remember(query, findOptions) { mutableIntStateOf(-1) }
	var appliedJumpSerial by rememberSaveable(document.viewKey) { mutableIntStateOf(-1) }
	val vertical = rememberScrollState()
	val horizontal = rememberScrollState()
	var revealKeyboardCaret by remember { mutableStateOf(false) }
	val layout = remember { AtomicReference<TextLayoutResult?>() }
	val links =
		rememberCodeLinks(document, vertical.value to horizontal.value, canNavigate, onNavigate)
	var dismissedOccurrences by remember(document) { mutableStateOf<TextRange?>(null) }
	val reference = remember(document, value.selection) {
		val range = value.selection
		val candidate = document.references.at(range.min)
			?: if (range.collapsed) document.references.at(range.min - 1)
				?.takeIf { it.end == range.min } else null
		candidate?.takeIf { range.collapsed || (range.min == it.start && range.max == it.end) }
	}
	val occurrences = remember(document, reference, dismissedOccurrences, value.selection) {
		if (reference != null && dismissedOccurrences != value.selection) document.references.occurrences(
			reference
		) else emptyList()
	}
	var pairs by remember(document) { mutableStateOf(emptyMap<Int, Int>()) }
	LaunchedEffect(document) {
		pairs = withContext(Dispatchers.Default) {
			if (document.language == "Java") bracketPairs(document.code) else emptyMap()
		}
	}
	val brackets = remember(pairs, value.selection) {
		if (!value.selection.collapsed) emptyList() else {
			val offset = value.selection.end.let { if (it in pairs) it else it - 1 }
			pairs[offset]?.let { listOf(offset, it) }.orEmpty()
		}
	}
	val decorations = remember(highlighted, occurrences, matches, find, brackets, dark) {
		decorateCode(
			highlighted,
			emptyList(),
			occurrences,
			if (find) matches else emptyList(),
			brackets,
			dark
		)
	}
	val displayedCode = linkedCode(decorations, links.activeRange)
	val lineStarts = remember(document.code) {
		buildList { add(0); document.code.forEachIndexed { i, c -> if (c == '\n') add(i + 1) } }
	}
	val focus = remember { FocusRequester() }
	var editorFocused by remember { mutableStateOf(false) }
	val selectionScope = rememberCoroutineScope()
	var selectionChange by remember { mutableIntStateOf(0) }
	val searchFocus = remember { FocusRequester() }
	val fontSize = prefs.fontSize.sp
	val lineHeight = (prefs.fontSize * 1.6).sp
	val style = TextStyle(
		fontFamily = FontFamily("JetBrains Mono"), fontSize = fontSize, lineHeight = lineHeight,
		color = if (dark) Color(0xFFBCBEC4) else Color(0xFF080808)
	)
	val gutterColor = if (dark) Color(0xFF6F737A) else Color(0xFF8C8C8C)
	val textMeasurer = rememberTextMeasurer()
	val numberWidth = textMeasurer.measure(lineStarts.size.toString(), style).size.width
	val editorPaddingY = with(LocalDensity.current) { 12.dp.toPx() }
	val editorPaddingX = with(LocalDensity.current) { 8.dp.toPx() }
	val gutterWidth = with(LocalDensity.current) { numberWidth.toDp() } + 16.dp
	LaunchedEffect(find) { if (find) searchFocus.requestFocus() }
	LaunchedEffect(focusRequest) { if (focusRequest > 0) focus.requestFocus() }
	LaunchedEffect(jump) {
		if (jump != null && jump.viewKey == document.viewKey && appliedJumpSerial != jump.serial) {
			appliedJumpSerial = jump.serial
			val offset = jump.offset.coerceIn(0, document.code.length)
			value = value.copy(selection = TextRange(offset))
			focus.requestFocus()
			while (layout.get() == null) withFrameNanos { }
			layout.get()?.let { measured ->
				val line = measured.getLineForOffset(offset)
				vertical.scrollTo((measured.getLineTop(line) - 60).toInt().coerceAtLeast(0))
				horizontal.scrollTo(
					(measured.getCursorRect(offset).left - 40).toInt().coerceAtLeast(0)
				)
			}
		}
	}
	LaunchedEffect(value.selection) {
		if (dismissedOccurrences != value.selection) dismissedOccurrences = null
		val offset = value.selection.end.coerceIn(0, document.code.length)
		val result = lineStarts.binarySearch(offset)
		val line = if (result >= 0) result else -result - 2
		onCaret("${line + 1}:${offset - lineStarts[line] + 1}", offset)
		if (revealKeyboardCaret) {
			revealKeyboardCaret = false
			layout.get()?.getCursorRect(offset)?.let { rect ->
				vertical.reveal(rect.top + editorPaddingY, rect.bottom + editorPaddingY)
				horizontal.reveal(rect.left + editorPaddingX, rect.right + editorPaddingX)
			}
		}
	}
	suspend fun selectMatch(index: Int) {
		if (matches.isEmpty()) return
		matchIndex = Math.floorMod(index, matches.size)
		val range = matches[matchIndex]
		value = value.copy(selection = range)
		layout.get()?.let {
			vertical.scrollTo(
				(it.getLineTop(it.getLineForOffset(range.start)) - 60).toInt().coerceAtLeast(0)
			)
			horizontal.scrollTo((it.getCursorRect(range.start).left - 40).toInt().coerceAtLeast(0))
		}
	}

	var matchRequest by remember { mutableStateOf<Pair<Int, Int>?>(null) }
	LaunchedEffect(matchRequest) { matchRequest?.let { selectMatch(it.first) } }
	fun nextMatch(delta: Int) {
		matchRequest =
			(if (matchIndex < 0 && delta < 0) matches.lastIndex else matchIndex + delta) to ((matchRequest?.second
				?: 0) + 1)
	}
	Column(Modifier.fillMaxSize().onPreviewKeyEvent {
		if (it.type != KeyEventType.KeyDown) false
		else when {
			it.key == Key.F3 -> {
				nextMatch(if (it.isShiftPressed) -1 else 1); true
			}

			else -> false
		}
	}) {
		if (find) {
			FindBar(
				prefs,
				queryValue,
				{ queryValue = it },
				findOptions,
				{ findOptions = it },
				findResult,
				matchIndex,
				searchFocus,
				::nextMatch,
				{ closeFind(); focus.requestFocus() })
			Rule()
		}
		Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().drawBehind {
			val measured = layout.get() ?: return@drawBehind
			val line =
				measured.getLineForOffset(value.selection.end.coerceIn(0, document.code.length))
			val top = measured.getLineTop(line) - vertical.value + 12.dp.toPx()
			drawRect(
				if (dark) Color(0xFF26282E) else Color(0xFFF0F3FA),
				topLeft = Offset(gutterWidth.toPx(), top),
				size = Size(
					size.width - gutterWidth.toPx(),
					measured.getLineBottom(line) - measured.getLineTop(line)
				)
			)
		}.pointerInput(document.viewKey) {
			awaitPointerEventScope {
				while (true) {
					if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) revealKeyboardCaret =
						false
				}
			}
		}.then(links.viewportModifier)) {
			Row(Modifier.fillMaxSize().padding(bottom = 12.dp, end = 17.dp)) {
				Canvas(
					Modifier.width(gutterWidth).fillMaxHeight()
						.background(if (dark) Color(0xFF1E1F22) else Color(0xFFF7F8FA))
				) {
					val measured = layout.get() ?: return@Canvas
					val padding = 12.dp.toPx()
					val first = measured.getLineForVerticalPosition(vertical.value.toFloat())
						.coerceAtLeast(0)
					val last = measured.getLineForVerticalPosition(vertical.value + size.height)
						.coerceAtMost(measured.lineCount - 1)
					for (line in first..last) {
						val number = textMeasurer.measure(
							(line + 1).toString(),
							style.copy(
								color = if (line == measured.getLineForOffset(value.selection.end)) {
									if (dark) Color(0xFFDFE1E5) else Color(0xFF2F65CA)
								} else gutterColor
							)
						)
						drawText(
							number,
							topLeft = Offset(
								size.width - number.size.width - 8.dp.toPx(),
								measured.getLineTop(line) - vertical.value + padding
							)
						)
					}
				}
				CompositionLocalProvider(LocalBringIntoViewSpec provides EditorBringIntoViewSpec) {
					Box(
						Modifier.weight(1f).fillMaxHeight().testTag("editor-scroll")
							.verticalScroll(vertical).horizontalScroll(horizontal)
							.padding(start = 8.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
					) {
						EditorContextMenu(
							prefs,
							reference,
							{ reference?.let { onNavigate(it.start) } },
							{ reference?.let { onFindUsages(it.start) } },
							back,
							forward
						) {
							BasicTextField(
								value = value.copy(annotatedString = displayedCode),
								onValueChange = {
									val before = value.selection
									val next = it.copy(text = document.code)
									val change = ++selectionChange
									if (!before.collapsed && next.selection.collapsed) {
										// Internal blur callbacks run before our focus observer. Wait for that event
										// to finish, then accept an actual click/key collapse only while still focused.
										selectionScope.launch {
											yield()
											if (editorFocused && change == selectionChange && value.selection == before) value =
												next
										}
									} else value = next
								},
								readOnly = true,
								textStyle = style,
								cursorBrush = SolidColor(if (dark) Color(0xFFCED0D6) else Color.Black),
								onTextLayout = { layout.set(it); links.layout = it },
								modifier = Modifier.defaultMinSize(minWidth = 400.dp)
									.focusRequester(focus)
									.onFocusChanged { editorFocused = it.isFocused }
									.testTag("source-code")
									.onPreviewKeyEvent {
										if (it.type != KeyEventType.KeyDown) false
										else when {
											it.key == Key.B && (it.isMetaPressed || it.isCtrlPressed) -> {
												onNavigate(
													reference?.start ?: value.selection.end
												); true
											}

											it.key == Key.F7 && it.isAltPressed -> {
												reference?.let { onFindUsages(it.start) }; true
											}

											it.key in caretNavigationKeys -> {
												revealKeyboardCaret = true; false
											}

											it.key == Key.Escape -> {
												dismissedOccurrences = value.selection; true
											}

											else -> false
										}
									}
									.then(links.textModifier),
							)
						}
					}
				}
			}
			// Overview marks remain visible even for references outside the viewport.
			Canvas(
				Modifier.align(Alignment.CenterEnd).width(4.dp).fillMaxHeight()
					.padding(bottom = 12.dp)
			) {
				val measured = layout.get() ?: return@Canvas
				val offsets =
					if (find && query.isNotEmpty()) matches.map { it.start } else occurrences.map { it.start }
				offsets.map { measured.getLineForOffset(it) }.distinct().forEach { line ->
					val y =
						line.toFloat() / measured.lineCount.coerceAtLeast(1) * (size.height - 3.dp.toPx())
					drawRect(
						if (find && query.isNotEmpty()) Color(0xFFB89B55) else Color(0xFF6B8DBB),
						Offset(0f, y),
						Size(size.width, 3.dp.toPx())
					)
				}
			}
			VerticalScrollbar(
				vertical,
				Modifier.align(Alignment.CenterEnd).fillMaxHeight()
					.padding(bottom = 12.dp, end = 5.dp)
			)
			HorizontalScrollbar(
				horizontal,
				Modifier.align(Alignment.BottomStart).fillMaxWidth()
					.padding(start = gutterWidth, end = 12.dp)
			)
		}
	}
}
