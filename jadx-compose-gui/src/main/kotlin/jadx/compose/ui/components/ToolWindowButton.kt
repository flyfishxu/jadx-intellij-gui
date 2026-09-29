package jadx.compose.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SelectableIconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.theme.iconButtonStyle

@Composable
internal fun ToolWindowButton(
	label: String,
	icon: IconKey,
	selected: Boolean,
	action: () -> Unit,
) {
	Tooltip({ Text(label) }) {
		SelectableIconButton(selected, action, Modifier.size(36.dp)) { state ->
			val tint by JewelTheme.iconButtonStyle.colors.selectableForegroundFor(state)
			Icon(
				icon,
				label,
				Modifier.size(24.dp),
				tint = tint,
				hint = Selected(state.isSelected && state.isActive),
			)
		}
	}
}
