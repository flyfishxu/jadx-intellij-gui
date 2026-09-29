package jadx.compose.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.components.Muted
import jadx.compose.ui.model.WorkspaceModel
import java.io.File
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun Welcome(model: WorkspaceModel, prefs: AppPreferences, open: () -> Unit) {
	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		Column(Modifier.widthIn(max = 460.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
				Icon(AllIconsKeys.Nodes.PpJdk, null, Modifier.size(32.dp))
				Text("Jadx Compose", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
			}
			Muted(prefs.text("APK · DEX · JAR · CLASS · SMALI", "APK · DEX · JAR · CLASS · SMALI"))
			DefaultButton(open, enabled = !model.busy) { Text(prefs.text("Open file…", "打开文件…")) }
			if (model.file != null) Muted(prefs.text("Double-click a class or resource in the project tree.", "双击项目树中的类或资源以打开。"))
			else if (prefs.recent.isNotEmpty()) {
				Spacer(Modifier.height(8.dp))
				Text(prefs.text("Recent files", "最近打开"), fontWeight = FontWeight.SemiBold)
				prefs.recent.take(5).forEach { path ->
					key(path) {
						RecentFileItem(path, prefs, enabled = !model.busy) { model.open(File(path), prefs::remember) }
					}
				}
			}
			Spacer(Modifier.height(8.dp))
			Muted(prefs.text("⌘ / Ctrl O   Open     ·     ⌘ / Ctrl F   Find", "⌘ / Ctrl O   打开     ·     ⌘ / Ctrl F   查找"))
		}
	}
}

@Composable
private fun RecentFileItem(path: String, prefs: AppPreferences, enabled: Boolean, open: () -> Unit) {
	val interactions = remember { MutableInteractionSource() }
	val hovered by interactions.collectIsHoveredAsState()
	var focused by remember { mutableStateOf(false) }
	Box(Modifier.fillMaxWidth().hoverable(interactions).onFocusChanged { focused = it.hasFocus }
		.clickable(enabled = enabled, onClick = open).padding(vertical = 5.dp)) {
		Column(Modifier.fillMaxWidth().padding(end = 32.dp)) {
			Text(File(path).name)
			Muted(path)
		}
		if (hovered || focused) {
			val label = prefs.text("Remove from recent files", "从最近打开中移除")
			Tooltip({ Text(label) }, Modifier.align(Alignment.TopEnd)) {
				IconButton({ prefs.removeRecent(path) }, Modifier.size(24.dp)) {
					Icon(AllIconsKeys.Actions.Close, label, Modifier.size(16.dp))
				}
			}
		}
	}
}
