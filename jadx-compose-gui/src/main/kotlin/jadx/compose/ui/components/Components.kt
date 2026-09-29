package jadx.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icon.IconKey

internal val editorBackground: Color
	@Composable get() = if (JewelTheme.isDark) Color(0xFF1E1F22) else Color.White

@Composable
internal fun Rule() = Box(Modifier.fillMaxWidth().height(1.dp).background(JewelTheme.globalColors.borders.normal))

@Composable
internal fun VerticalRule() = Box(Modifier.fillMaxHeight().width(1.dp).background(JewelTheme.globalColors.borders.normal))

@Composable
internal fun Muted(value: String, modifier: Modifier = Modifier) = Text(value, modifier, color = JewelTheme.globalColors.text.info, maxLines = 1)

@Composable
internal fun ToolAction(label: String, icon: IconKey, enabled: Boolean = true, onClick: () -> Unit) {
	Tooltip({ Text(label) }) {
		IconButton(onClick, Modifier.size(30.dp), enabled = enabled) { Icon(icon, label, Modifier.size(16.dp)) }
	}
}
