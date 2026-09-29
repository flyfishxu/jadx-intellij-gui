package jadx.compose.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalWindowInfo
import jadx.compose.ui.editor.LocalNavigationModifier
import java.awt.Frame
import jadx.compose.decompiler.EntryKind
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.components.Muted
import jadx.compose.ui.components.VerticalRule
import jadx.compose.ui.components.Rule
import jadx.compose.ui.components.ToolWindowButton
import jadx.compose.ui.editor.CodeModeMenu
import jadx.compose.ui.model.WorkspaceModel
import jadx.compose.ui.project.ProjectPanel
import jadx.compose.ui.search.SearchPanel
import jadx.compose.ui.window.NativeMenuBar
import jadx.compose.ui.window.WorkspaceMenus
import jadx.compose.ui.window.isMacOS
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun Workspace(
	model: WorkspaceModel,
	prefs: AppPreferences,
	open: () -> Unit,
	importMapping: () -> Unit = {},
	nativeWindow: Frame? = null,
) {
	val navigationModifier = remember { mutableStateOf(false) }
	val windowInfo = LocalWindowInfo.current
	LaunchedEffect(windowInfo.isWindowFocused) { if (!windowInfo.isWindowFocused) navigationModifier.value = false }
	var searchVisible by remember { mutableStateOf(false) }
	var projectVisible by remember { mutableStateOf(true) }
	var structureVisible by remember { mutableStateOf(true) }
	var filter by remember { mutableStateOf("") }
	var findVisible by remember { mutableStateOf(false) }
	val projectFocus = remember { FocusRequester() }
	var focusRequest by remember { mutableIntStateOf(0) }
	LaunchedEffect(projectVisible, focusRequest) {
		if (projectVisible && !searchVisible && focusRequest > 0) projectFocus.requestFocus()
	}
	val useSystemMenu = nativeWindow != null && isMacOS
	if (useSystemMenu) NativeMenuBar(requireNotNull(nativeWindow), model, prefs, open, importMapping,
		find = { findVisible = !findVisible }, search = { projectVisible = true; searchVisible = true }, settings = model::openSettings,
		close = model::closeActiveTab,
		projectVisible = projectVisible, toggleProject = { projectVisible = !projectVisible },
		structureVisible = structureVisible, toggleStructure = { structureVisible = !structureVisible })
	CompositionLocalProvider(LocalNavigationModifier provides navigationModifier) {
		Column(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground).onPreviewKeyEvent { e ->
			navigationModifier.value = e.isMetaPressed || e.isCtrlPressed
			if (e.type != KeyEventType.KeyDown) false
			else if ((e.isAltPressed && e.key == Key.DirectionLeft) || ((e.isMetaPressed || e.isCtrlPressed) && e.key == Key.LeftBracket)) { model.goBack(); true }
			else if ((e.isAltPressed && e.key == Key.DirectionRight) || ((e.isMetaPressed || e.isCtrlPressed) && e.key == Key.RightBracket)) { model.goForward(); true }
			else if (!(e.isCtrlPressed || e.isMetaPressed)) false
			else when (e.key) {
				Key.O -> { open(); true }
				Key.W -> { model.closeActiveTab(); true }
				Key.F -> { if (e.isShiftPressed) { searchVisible = true; projectVisible = true } else findVisible = !findVisible; true }
				Key.L -> { searchVisible = false; projectVisible = true; focusRequest++; true }
				Key.Comma -> { model.openSettings(); true }
				else -> false
			}
		}) {
			Rule()
			if (!useSystemMenu) {
				WorkspaceMenus(model, prefs, open, importMapping,
					find = { findVisible = !findVisible }, search = { projectVisible = true; searchVisible = true }, settings = model::openSettings,
					projectVisible = projectVisible, toggleProject = { projectVisible = !projectVisible },
					structureVisible = structureVisible, toggleStructure = { structureVisible = !structureVisible })
				Rule()
			}
			Row(Modifier.weight(1f)) {
				Column(Modifier.width(44.dp).fillMaxHeight().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
					ToolWindowButton(prefs.text("Project", "项目"), AllIconsKeys.Toolwindows.ToolWindowProject, projectVisible && !searchVisible) { if (searchVisible) { searchVisible = false; projectVisible = true } else projectVisible = !projectVisible }
					ToolWindowButton(prefs.text("Search", "搜索"), AllIconsKeys.Actions.Find, projectVisible && searchVisible) {
						if (searchVisible && projectVisible) projectVisible = false
						else { projectVisible = true; searchVisible = true }
					}
					Spacer(Modifier.weight(1f))
					ToolWindowButton(prefs.text("Settings", "设置"), AllIconsKeys.General.Settings, model.settingsSelected) { model.openSettings() }
					Spacer(Modifier.height(4.dp))
				}
				VerticalRule()
				val editor: @Composable () -> Unit = {
					EditorArea(model, prefs, structureVisible, findVisible, { findVisible = false }, open)
				}
				Box(Modifier.weight(1f)) {
					if (projectVisible) HorizontalSplitLayout(
						first = { if (searchVisible) SearchPanel(model, prefs) else ProjectPanel(model, prefs, filter, { filter = it }, projectFocus) },
						second = editor,
						firstPaneMinWidth = 210.dp,
						secondPaneMinWidth = 450.dp,
						state = rememberSplitLayoutState(.23f),
						modifier = Modifier.fillMaxSize(),
					) else editor()
				}
				VerticalRule()
				Column(Modifier.width(44.dp).fillMaxHeight().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
					ToolWindowButton(prefs.text("Structure", "结构"), AllIconsKeys.Toolwindows.ToolWindowStructure, structureVisible) { structureVisible = !structureVisible }
				}
			}
			Rule()
			Row(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				if (model.busy) { CircularProgressIndicator(Modifier.size(14.dp)); Muted(prefs.text("Decompiling…", "正在反编译…")) }
				else Muted(prefs.text("Ready", "就绪"))
				model.file?.let { Muted("${model.entries.count { it.kind == EntryKind.CLASS }} " + prefs.text("classes", "个类")) }
				Spacer(Modifier.weight(1f))
				model.selected?.let {
					if (it.errors > 0) Text("${it.errors} " + prefs.text("errors", "个错误"), color = JewelTheme.globalColors.text.warning)
					Muted(model.caret); Muted("UTF-8")
					if (it.entry.kind == EntryKind.CLASS) CodeModeMenu(model, prefs) else Muted(it.language)
				}
				Muted("jadx")
			}
		}
	}
}
