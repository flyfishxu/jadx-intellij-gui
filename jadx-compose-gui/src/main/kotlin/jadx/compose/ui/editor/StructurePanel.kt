package jadx.compose.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jadx.compose.decompiler.SourceDocument
import jadx.compose.decompiler.SymbolKind
import jadx.compose.decompiler.CodeMode
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.components.Rule
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.theme.simpleListItemStyle
import org.jetbrains.jewel.ui.theme.treeStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle

@Composable
internal fun StructurePanel(document: SourceDocument, prefs: AppPreferences, caretOffset: Int, jump: (Int) -> Unit) {
	var active by remember { mutableStateOf(false) }
	var clicked by remember(document.viewKey) { mutableStateOf<Pair<String, Int>?>(null) }
	val listState = rememberLazyListState()
	val colors = JewelTheme.simpleListItemStyle.colors
	val treeColors = JewelTheme.treeStyle.colors
	val listStyle = SimpleListItemStyle(
		SimpleListItemColors(colors.background, colors.backgroundActive, treeColors.backgroundSelected,
			treeColors.backgroundSelectedActive, colors.content, colors.contentActive, colors.contentSelected, colors.contentSelectedActive),
		JewelTheme.simpleListItemStyle.metrics,
	)
	val current = remember(document, caretOffset, clicked) {
		clicked?.takeIf { it.second == caretOffset }?.let { selection -> document.symbols.firstOrNull { it.key == selection.first } }
			?: document.symbols.filter { it.offset >= 0 && it.offset <= caretOffset }.maxByOrNull { it.offset }
	}
	LaunchedEffect(current?.key) {
		val index = document.symbols.indexOf(current)
		if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
	}
	Column(Modifier.fillMaxSize().testTag("structure-panel").onFocusChanged { active = it.hasFocus }.background(JewelTheme.globalColors.panelBackground)) {
		Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
			Text(prefs.text("Structure", "结构"), fontWeight = FontWeight.SemiBold)
		}
		Rule()
		LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp), state = listState) {
			items(document.symbols, key = { it.key }) { symbol ->
				val selected = current?.key == symbol.key
				SimpleListItem(symbol.label, selected = selected, active = active, style = listStyle,
					modifier = Modifier.fillMaxWidth().selectable(selected, onClick = {
						val hasPosition = symbol.offset in document.code.indices && (symbol.offset > 0 || document.mode == CodeMode.SMALI)
						clicked = symbol.key to if (hasPosition) symbol.offset else caretOffset
						if (hasPosition) jump(symbol.offset)
					}),
					icon = when (symbol.kind) {
						SymbolKind.CLASS -> AllIconsKeys.Nodes.Class
						SymbolKind.FIELD -> AllIconsKeys.Nodes.Field
						SymbolKind.METHOD -> AllIconsKeys.Nodes.Method
					}, painterHints = arrayOf(Selected(selected && active)))
			}
		}
	}
}
