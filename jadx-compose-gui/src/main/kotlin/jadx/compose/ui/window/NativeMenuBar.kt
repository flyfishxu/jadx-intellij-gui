package jadx.compose.ui.window

import androidx.compose.runtime.*
import jadx.compose.decompiler.CodeMode
import jadx.compose.decompiler.EntryKind
import jadx.compose.preferences.AppPreferences
import jadx.compose.preferences.Appearance
import jadx.compose.preferences.appearanceLabel
import jadx.compose.ui.model.WorkspaceModel
import java.awt.CheckboxMenuItem
import java.awt.Frame
import java.awt.Menu
import java.awt.MenuBar
import java.awt.MenuItem
import java.awt.MenuShortcut
import java.awt.event.KeyEvent

internal val isMacOS = System.getProperty("os.name").startsWith("Mac")

/** AWT menus are installed in the macOS system menu bar, outside the Compose window. */
@Composable
internal fun NativeMenuBar(
	window: Frame, model: WorkspaceModel, prefs: AppPreferences,
	open: () -> Unit, importMapping: () -> Unit, find: () -> Unit, search: () -> Unit,
	settings: () -> Unit, close: () -> Unit,
	projectVisible: Boolean, toggleProject: () -> Unit, structureVisible: Boolean, toggleStructure: () -> Unit,
) {
	val actions by rememberUpdatedState(MenuActions(open, importMapping, find, search, settings, close, toggleProject, toggleStructure))
	val menuBar = remember(window, prefs.chinese, prefs.recent, prefs.appearance, model.busy, model.file, model.mapping,
		model.selectedId, model.selected?.mode, model.settingsSelected, model.history.canGoBack, model.history.canGoForward, projectVisible, structureVisible) {
		MenuBar().apply {
			add(Menu(prefs.text("File", "文件")).apply {
				action(prefs.text("Open…", "打开…"), !model.busy, MenuShortcut(KeyEvent.VK_O)) { actions.open() }
				add(Menu(prefs.text("Open Recent", "最近打开")).apply {
					isEnabled = prefs.recent.isNotEmpty() && !model.busy
					prefs.recent.forEach { path -> action(path) { model.open(java.io.File(path), prefs::remember) } }
				})
				addSeparator()
				action(prefs.text("Import Mapping…", "导入 mapping…"), model.file != null && !model.busy) { actions.importMapping() }
				action(prefs.text("Remove Mapping", "移除 mapping"), model.mapping != null && !model.busy) { model.importMapping(null) }
				addSeparator()
				action(prefs.text("Close Tab", "关闭标签页"), shortcut = MenuShortcut(KeyEvent.VK_W)) { actions.close() }
				action(prefs.text("Settings…", "设置…"), shortcut = MenuShortcut(KeyEvent.VK_COMMA)) { actions.settings() }
			})
			add(Menu(prefs.text("Search", "搜索")).apply {
				action(prefs.text("Find in File…", "在文件中查找…"), model.selected != null, MenuShortcut(KeyEvent.VK_F)) { actions.find() }
				action(prefs.text("Search Everywhere…", "全局搜索…"), model.file != null, MenuShortcut(KeyEvent.VK_F, true)) { actions.search() }
			})
			add(Menu(prefs.text("Navigate", "导航")).apply {
				action(prefs.text("Back", "后退"), model.history.canGoBack && !model.busy, MenuShortcut(KeyEvent.VK_OPEN_BRACKET)) { model.goBack() }
				action(prefs.text("Forward", "前进"), model.history.canGoForward && !model.busy, MenuShortcut(KeyEvent.VK_CLOSE_BRACKET)) { model.goForward() }
				addSeparator()
				action(prefs.text("Go to Declaration", "跳转到声明"), model.selected != null && !model.busy, MenuShortcut(KeyEvent.VK_B)) { model.selected?.let { model.goToDefinition(it, model.caretOffset) } }
				action(prefs.text("Find Usages", "查找用法"), model.selected != null && !model.busy) { model.selected?.let { model.findUsages(it, model.caretOffset) } }
			})
			add(Menu(prefs.text("View", "视图")).apply {
				check(prefs.text("Project", "项目"), projectVisible) { actions.toggleProject() }
				check(prefs.text("Structure", "结构"), structureVisible) { actions.toggleStructure() }
				addSeparator()
				CodeMode.entries.forEach { mode ->
					check(if (mode == CodeMode.JAVA) "Java" else "Smali", model.selected?.mode == mode,
						model.selected?.entry?.kind == EntryKind.CLASS && !model.busy) { model.setMode(mode) }
				}
				addSeparator()
				add(Menu(prefs.text("Appearance", "外观")).apply {
					Appearance.entries.forEach { mode -> check(appearanceLabel(mode, prefs), prefs.appearance == mode) { prefs.appearance(mode) } }
				})
			})
			add(Menu(prefs.text("Window", "窗口")).apply {
				action(prefs.text("Minimize", "最小化"), shortcut = MenuShortcut(KeyEvent.VK_M)) { window.extendedState = window.extendedState or Frame.ICONIFIED }
				action(prefs.text("Zoom", "缩放")) { window.extendedState = if (window.extendedState == Frame.MAXIMIZED_BOTH) Frame.NORMAL else Frame.MAXIMIZED_BOTH }
			})
		}
	}
	DisposableEffect(window, menuBar) {
		window.menuBar = menuBar
		onDispose { if (window.menuBar === menuBar) window.menuBar = null }
	}
}

private data class MenuActions(
	val open: () -> Unit, val importMapping: () -> Unit, val find: () -> Unit, val search: () -> Unit,
	val settings: () -> Unit, val close: () -> Unit, val toggleProject: () -> Unit, val toggleStructure: () -> Unit,
)

private fun Menu.action(label: String, enabled: Boolean = true, shortcut: MenuShortcut? = null, action: () -> Unit) {
	add(MenuItem(label, shortcut).apply { isEnabled = enabled; addActionListener { action() } })
}

private fun Menu.check(label: String, selected: Boolean, enabled: Boolean = true, action: () -> Unit) {
	add(CheckboxMenuItem(label, selected).apply { isEnabled = enabled; addItemListener { action() } })
}
