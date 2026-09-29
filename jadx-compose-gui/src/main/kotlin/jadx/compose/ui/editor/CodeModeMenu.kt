package jadx.compose.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import jadx.compose.decompiler.CodeMode
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.model.WorkspaceModel
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

@Composable
internal fun CodeModeMenu(model: WorkspaceModel, prefs: AppPreferences) {
	var expanded by remember { mutableStateOf(false) }
	Tooltip({ Text(prefs.text("Code view: Java / Smali", "代码视图：Java / Smali")) }) {
		Box {
			IconButton(
				{ expanded = !expanded },
				Modifier.testTag("code-mode").height(24.dp).width(64.dp),
				enabled = !model.busy
			) {
				Text((if (model.selected?.mode == CodeMode.SMALI) "Smali" else "Java") + " ▾")
			}
			if (expanded) PopupMenu(
				onDismissRequest = { expanded = false; true },
				horizontalAlignment = Alignment.End
			) {
				CodeMode.entries.forEach { mode ->
					selectableItem(
						model.selected?.mode == mode,
						onClick = { model.setMode(mode) }) { Text(if (mode == CodeMode.JAVA) "Java" else "Smali") }
				}
			}
		}
	}
}
