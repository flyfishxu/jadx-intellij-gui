package jadx.compose.ui.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jadx.compose.decompiler.SourceDocument
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.components.Muted
import jadx.compose.ui.components.Rule
import jadx.compose.ui.components.ToolAction
import jadx.compose.ui.model.NavigationTarget
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.HorizontalScrollbar
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun CodeEditor(document: SourceDocument, prefs: AppPreferences, find: Boolean, closeFind: () -> Unit,
	jump: NavigationTarget?, onNavigate: (Int) -> Unit, canNavigate: suspend (Int) -> Boolean = { false }, onCaret: (String, Int) -> Unit) {
	val dark = JewelTheme.isDark
	var highlighted by remember(document) { mutableStateOf(AnnotatedString(document.code)) }
	LaunchedEffect(document, dark) { highlighted = withContext(Dispatchers.Default) { highlight(document.code, dark, document.mode) } }
	var value by rememberSaveable(document.viewKey, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(document.code)) }
	var queryValue by remember { mutableStateOf(TextFieldValue()) }
	val query = queryValue.text
	val matches = remember(query, document.code) { findMatches(document.code, query) }
	var matchIndex by remember(query) { mutableIntStateOf(-1) }
	var appliedJumpSerial by rememberSaveable(document.viewKey) { mutableIntStateOf(-1) }
	val vertical = rememberScrollState()
	val horizontal = rememberScrollState()
	val layout = remember { AtomicReference<TextLayoutResult?>() }
	val links = rememberCodeLinks(document, vertical.value to horizontal.value, canNavigate, onNavigate)
	val displayedCode = linkedCode(highlighted, links.activeRange)
	val lineStarts = remember(document.code) {
		buildList { add(0); document.code.forEachIndexed { i, c -> if (c == '\n') add(i + 1) } }
	}
	val focus = remember { FocusRequester() }
	val searchFocus = remember { FocusRequester() }
	val fontSize = prefs.fontSize.sp
	val lineHeight = (prefs.fontSize * 1.6).sp
	val style = TextStyle(fontFamily = FontFamily("JetBrains Mono"), fontSize = fontSize, lineHeight = lineHeight,
		color = if (dark) Color(0xFFBCBEC4) else Color(0xFF080808))
	val gutterColor = if (dark) Color(0xFF6F737A) else Color(0xFF8C8C8C)
	val textMeasurer = rememberTextMeasurer()
	val numberWidth = textMeasurer.measure(lineStarts.size.toString(), style).size.width
	val gutterWidth = with(LocalDensity.current) { numberWidth.toDp() } + 16.dp
	LaunchedEffect(find) { if (find) searchFocus.requestFocus() }
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
				horizontal.scrollTo((measured.getCursorRect(offset).left - 40).toInt().coerceAtLeast(0))
			}
		}
	}
	LaunchedEffect(value.selection) {
		val offset = value.selection.end.coerceIn(0, document.code.length)
		val result = lineStarts.binarySearch(offset)
		val line = if (result >= 0) result else -result - 2
		onCaret("${line + 1}:${offset - lineStarts[line] + 1}", offset)
	}
	suspend fun selectMatch(index: Int) {
		if (matches.isEmpty()) return
		matchIndex = Math.floorMod(index, matches.size)
		val offset = matches[matchIndex]
		value = value.copy(selection = TextRange(offset, offset + query.length))
		layout.get()?.let { vertical.scrollTo((it.getLineTop(it.getLineForOffset(offset)) - 60).toInt().coerceAtLeast(0)) }
	}
	var matchRequest by remember { mutableStateOf<Pair<Int, Int>?>(null) }
	LaunchedEffect(matchRequest) { matchRequest?.let { selectMatch(it.first) } }
	fun nextMatch(delta: Int) { matchRequest = (if (matchIndex < 0 && delta < 0) matches.lastIndex else matchIndex + delta) to ((matchRequest?.second ?: 0) + 1) }
	Column(Modifier.fillMaxSize()) {
		if (find) {
			Row(Modifier.fillMaxWidth().height(40.dp).background(JewelTheme.globalColors.panelBackground).padding(horizontal = 8.dp),
				verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				TextField(queryValue, { queryValue = it }, Modifier.weight(1f).focusRequester(searchFocus).onPreviewKeyEvent {
					when {
						it.type == KeyEventType.KeyDown && it.key == Key.Enter -> { nextMatch(if (it.isShiftPressed) -1 else 1); true }
						it.type == KeyEventType.KeyDown && it.key == Key.Escape -> { closeFind(); focus.requestFocus(); true }
						else -> false
					}
				}, placeholder = { Text(prefs.text("Find in file", "在文件中查找")) })
				Muted(if (matches.isEmpty()) "0" else "${matchIndex + 1} / ${matches.size}")
				ToolAction(prefs.text("Previous match", "上一个"), AllIconsKeys.Actions.PreviousOccurence, matches.isNotEmpty()) { nextMatch(-1) }
				ToolAction(prefs.text("Next match", "下一个"), AllIconsKeys.Actions.NextOccurence, matches.isNotEmpty()) { nextMatch(1) }
				ToolAction(prefs.text("Close find", "关闭查找"), AllIconsKeys.Actions.Close, onClick = closeFind)
			}
			Rule()
		}
		Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().drawBehind {
			val measured = layout.get() ?: return@drawBehind
			val line = measured.getLineForOffset(value.selection.end.coerceIn(0, document.code.length))
			val top = measured.getLineTop(line) - vertical.value + 12.dp.toPx()
			drawRect(if (dark) Color(0xFF26282E) else Color(0xFFF0F3FA),
				topLeft = Offset(gutterWidth.toPx(), top), size = Size(size.width - gutterWidth.toPx(), measured.getLineBottom(line) - measured.getLineTop(line)))
		}.then(links.viewportModifier)) {
			Row(Modifier.fillMaxSize().padding(bottom = 12.dp, end = 12.dp)) {
				Canvas(Modifier.width(gutterWidth).fillMaxHeight().background(if (dark) Color(0xFF1E1F22) else Color(0xFFF7F8FA))) {
					val measured = layout.get() ?: return@Canvas
					val padding = 12.dp.toPx()
					val first = measured.getLineForVerticalPosition(vertical.value.toFloat()).coerceAtLeast(0)
					val last = measured.getLineForVerticalPosition(vertical.value + size.height).coerceAtMost(measured.lineCount - 1)
					for (line in first..last) {
						val number = textMeasurer.measure((line + 1).toString(), style.copy(color = gutterColor))
						drawText(number, topLeft = Offset(size.width - number.size.width - 8.dp.toPx(), measured.getLineTop(line) - vertical.value + padding))
					}
				}
				Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(vertical).horizontalScroll(horizontal).padding(start = 8.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) {
					BasicTextField(
						value = value.copy(annotatedString = displayedCode),
						onValueChange = { value = it.copy(text = document.code) },
						readOnly = true,
						textStyle = style,
						cursorBrush = SolidColor(if (dark) Color(0xFFCED0D6) else Color.Black),
						onTextLayout = { layout.set(it); links.layout = it },
						modifier = Modifier.defaultMinSize(minWidth = 400.dp).focusRequester(focus).testTag("source-code")
							.onPreviewKeyEvent {
								if (it.type == KeyEventType.KeyDown && it.key == Key.B && (it.isMetaPressed || it.isCtrlPressed)) {
									onNavigate(value.selection.end); true
								} else false
							}
							.then(links.textModifier),
					)
				}
			}
			VerticalScrollbar(vertical, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(bottom = 12.dp))
			HorizontalScrollbar(horizontal, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = gutterWidth, end = 12.dp))
		}
	}
}
