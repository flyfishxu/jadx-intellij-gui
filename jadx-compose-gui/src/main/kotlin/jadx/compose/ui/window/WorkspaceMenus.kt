package jadx.compose.ui.window

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import jadx.compose.decompiler.CodeMode
import jadx.compose.decompiler.EntryKind
import jadx.compose.preferences.AppPreferences
import jadx.compose.preferences.Appearance
import jadx.compose.preferences.appearanceLabel
import jadx.compose.ui.model.WorkspaceModel
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun WorkspaceMenus(
	model: WorkspaceModel, prefs: AppPreferences, open: () -> Unit, importMapping: () -> Unit,
	find: () -> Unit, search: () -> Unit, settings: () -> Unit,
	projectVisible: Boolean, toggleProject: () -> Unit, structureVisible: Boolean, toggleStructure: () -> Unit,
) {
	Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
		WorkspaceMenu(prefs.text("File", "文件"), "menu-file") {
			selectableItem(false, AllIconsKeys.Actions.MenuOpen, onClick = open, enabled = !model.busy) { Text(prefs.text("Open…", "打开…")) }
			submenu(enabled = prefs.recent.isNotEmpty() && !model.busy, submenu = {
				prefs.recent.forEach { path -> selectableItem(false, onClick = { model.open(java.io.File(path), prefs::remember) }) { Text(path) } }
			}) { Text(prefs.text("Open recent", "最近打开")) }
			separator()
			selectableItem(false, onClick = importMapping, enabled = model.file != null && !model.busy) { Text(prefs.text("Import mapping…", "导入 mapping…")) }
			selectableItem(false, onClick = { model.importMapping(null) }, enabled = model.mapping != null && !model.busy) { Text(prefs.text("Remove mapping", "移除 mapping")) }
			separator()
			selectableItem(false, onClick = model::closeActiveTab, enabled = model.selected != null || model.settingsSelected) { Text(prefs.text("Close tab", "关闭标签页")) }
			selectableItem(false, AllIconsKeys.General.Settings, onClick = settings) { Text(prefs.text("Settings…", "设置…")) }
		}
		WorkspaceMenu(prefs.text("Search", "搜索"), "menu-search") {
			selectableItem(false, AllIconsKeys.Actions.Find, onClick = search, enabled = model.file != null) { Text(prefs.text("Search everywhere…", "全局搜索…")) }
			selectableItem(false, onClick = find, enabled = model.selected != null) { Text(prefs.text("Find in file…", "在文件中查找…")) }
		}
		WorkspaceMenu(prefs.text("View", "视图"), "menu-view") {
			selectableItem(projectVisible, onClick = toggleProject) { Text(prefs.text("Project", "项目")) }
			selectableItem(structureVisible, onClick = toggleStructure) { Text(prefs.text("Structure", "结构")) }
			separator()
			CodeMode.entries.forEach { mode ->
				selectableItem(model.selected?.mode == mode, onClick = { model.setMode(mode) }, enabled = model.selected?.entry?.kind == EntryKind.CLASS && !model.busy) { Text(if (mode == CodeMode.JAVA) "Java" else "Smali") }
			}
			separator()
			submenu(submenu = {
				Appearance.entries.forEach { mode -> selectableItem(prefs.appearance == mode, onClick = { prefs.appearance(mode) }) { Text(appearanceLabel(mode, prefs)) } }
			}) { Text(prefs.text("Appearance", "外观")) }
		}
	}
}

@Composable
private fun WorkspaceMenu(label: String, tag: String, content: MenuScope.() -> Unit) {
	var expanded by remember { mutableStateOf(false) }
	Box {
		IconButton({ expanded = !expanded }, Modifier.testTag(tag).height(28.dp).widthIn(min = 44.dp)) { Text(label, Modifier.padding(horizontal = 8.dp)) }
		if (expanded) PopupMenu(onDismissRequest = { expanded = false; true }, horizontalAlignment = Alignment.Start, content = content)
	}
}
