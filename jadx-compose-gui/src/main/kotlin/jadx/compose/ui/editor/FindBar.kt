package jadx.compose.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.components.Muted
import jadx.compose.ui.components.ToolAction
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.SelectableIconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.theme.iconButtonStyle

@Composable
internal fun FindBar(
	prefs: AppPreferences,
	value: TextFieldValue,
	onValue: (TextFieldValue) -> Unit,
	options: Triple<Boolean, Boolean, Boolean>,
	onOptions: (Triple<Boolean, Boolean, Boolean>) -> Unit,
	result: EditorFindResult,
	matchIndex: Int,
	focus: FocusRequester,
	next: (Int) -> Unit,
	close: () -> Unit
) {
	Row(
		Modifier.fillMaxWidth().height(40.dp).background(JewelTheme.globalColors.panelBackground)
			.padding(horizontal = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(4.dp)
	) {
		TextField(value, onValue, Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent {
			when {
				it.type == KeyEventType.KeyDown && it.key == Key.Enter -> {
					next(if (it.isShiftPressed) -1 else 1); true
				}

				it.type == KeyEventType.KeyDown && it.key == Key.Escape -> {
					close(); true
				}

				else -> false
			}
		}, placeholder = { Text(prefs.text("Find in file", "在文件中查找")) })
		FindToggle(
			prefs.text("Match case", "区分大小写"),
			AllIconsKeys.Actions.MatchCase,
			options.first
		) { onOptions(options.copy(first = !options.first)) }
		FindToggle(
			prefs.text("Whole words", "全词匹配"),
			AllIconsKeys.Actions.Words,
			options.second
		) { onOptions(options.copy(second = !options.second)) }
		FindToggle(
			prefs.text("Regex", "正则表达式"),
			AllIconsKeys.Actions.Regex,
			options.third
		) { onOptions(options.copy(third = !options.third)) }
		Muted(if (result.matches.isEmpty()) "0" else "${matchIndex + 1} / ${result.matches.size}${if (result.limited) "+" else ""}")
		ToolAction(
			prefs.text("Previous match", "上一个"),
			AllIconsKeys.Actions.PreviousOccurence,
			result.matches.isNotEmpty()
		) { next(-1) }
		ToolAction(
			prefs.text("Next match", "下一个"),
			AllIconsKeys.Actions.NextOccurence,
			result.matches.isNotEmpty()
		) { next(1) }
		ToolAction(
			prefs.text("Close find", "关闭查找"),
			AllIconsKeys.Actions.Close,
			onClick = close
		)
	}
	result.error?.let {
		Text(
			prefs.text("Invalid regular expression", "正则表达式无效"),
			Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
			color = JewelTheme.globalColors.text.error
		)
	}
}

@Composable
private fun FindToggle(label: String, icon: IconKey, selected: Boolean, toggle: () -> Unit) {
	Tooltip({ Text(label) }) {
		SelectableIconButton(selected, toggle, Modifier.size(26.dp)) { state ->
			val tint by JewelTheme.iconButtonStyle.colors.selectableForegroundFor(state)
			Icon(
				icon,
				label,
				Modifier.size(16.dp),
				tint = tint,
				hint = Selected(state.isSelected && state.isActive)
			)
		}
	}
}
