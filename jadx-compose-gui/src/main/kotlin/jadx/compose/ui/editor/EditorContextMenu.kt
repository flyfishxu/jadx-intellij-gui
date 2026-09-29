@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package jadx.compose.ui.editor

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import jadx.compose.decompiler.CodeReference
import jadx.compose.preferences.AppPreferences
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.ContextMenuDivider
import org.jetbrains.jewel.ui.component.ContextMenuItemOption
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.awt.datatransfer.StringSelection

/** Extends the text field's Jewel menu, retaining its native copy/select-all actions. */
@Composable
internal fun EditorContextMenu(
	prefs: AppPreferences, reference: CodeReference?, navigate: () -> Unit,
	usages: () -> Unit, back: (() -> Unit)?, forward: (() -> Unit)?, content: @Composable () -> Unit
) {
	val clipboard = LocalClipboard.current
	val scope = rememberCoroutineScope()
	ContextMenuDataProvider(items = {
		listOf(
			ContextMenuDivider,
			ContextMenuItemOption(
				icon = AllIconsKeys.Actions.Forward,
				enabled = reference != null,
				label = prefs.text("Go to Declaration", "跳转到声明"),
				action = navigate
			),
			ContextMenuItemOption(
				icon = AllIconsKeys.Actions.Find,
				enabled = reference != null,
				label = prefs.text("Find Usages", "查找用法"),
				action = usages
			),
			ContextMenuItemOption(
				icon = AllIconsKeys.Actions.Copy,
				enabled = reference != null,
				label = prefs.text("Copy Reference", "复制引用"),
				action = {
					reference?.let {
						scope.launch {
							clipboard.setClipEntry(
								ClipEntry(StringSelection(it.name))
							)
						}
					}
				}),
			ContextMenuDivider,
			ContextMenuItemOption(
				icon = AllIconsKeys.Actions.Back,
				enabled = back != null,
				label = prefs.text("Back", "后退"),
				action = { back?.invoke() }),
			ContextMenuItemOption(
				icon = AllIconsKeys.Actions.Forward,
				enabled = forward != null,
				label = prefs.text("Forward", "前进"),
				action = { forward?.invoke() }),
		)
	}, content = content)
}
