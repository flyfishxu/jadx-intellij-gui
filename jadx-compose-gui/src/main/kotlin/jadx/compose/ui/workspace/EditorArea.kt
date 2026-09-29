package jadx.compose.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import jadx.compose.decompiler.CodeMode
import jadx.compose.decompiler.EntryKind
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.components.Rule
import jadx.compose.ui.components.editorBackground
import jadx.compose.ui.model.WorkspaceModel
import jadx.compose.ui.settings.SettingsPage
import jadx.compose.ui.search.UsagesPanel
import jadx.compose.ui.editor.CodeEditor
import jadx.compose.ui.editor.StructurePanel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.editorTabStyle

@Composable
internal fun EditorArea(model: WorkspaceModel, prefs: AppPreferences, structure: Boolean, find: Boolean, closeFind: () -> Unit, open: () -> Unit) {
	val editorStates = key(model.projectVersion) { rememberSaveableStateHolder() }
	val document = model.selected
	val currentEditor by rememberUpdatedState<@Composable () -> Unit> {
		Column(Modifier.fillMaxSize().testTag("editor-pane").background(editorBackground)) {
			val tabData = buildList {
				model.tabs.forEach { doc ->
					add(TabData.Editor(
						selected = !model.settingsSelected && doc.entry.id == model.selectedId,
						content = { state ->
							SimpleTabContent(doc.entry.name, state,
								iconKey = if (doc.entry.kind == EntryKind.CLASS) AllIconsKeys.Nodes.Class else AllIconsKeys.FileTypes.Text)
						},
						closable = true,
						onClose = {
							model.closeTab(doc.entry.id)
							CodeMode.entries.forEach { editorStates.removeState("${doc.entry.id}:${it.name}") }
						},
						onClick = { model.selectTab(doc.entry.id) },
					))
				}
				if (model.settingsOpen) {
					add(TabData.Editor(
						selected = model.settingsSelected,
						content = { state ->
							SimpleTabContent(prefs.text("Settings", "设置"), state,
								modifier = Modifier.testTag("settings-tab"), iconKey = AllIconsKeys.General.Settings)
						},
						closable = true,
						onClose = model::closeSettings,
						onClick = model::openSettings,
					))
				}
			}
			if (tabData.isNotEmpty()) {
				TabStrip(tabData, style = JewelTheme.editorTabStyle,
					modifier = Modifier.fillMaxWidth().testTag("editor-tabs"))
				Rule()
			}
			model.error?.let { message ->
				InlineErrorBanner(Modifier.fillMaxWidth(), iconActions = { iconAction(AllIconsKeys.Actions.Close, prefs.text("Dismiss", "关闭")) { model.error = null } }) { Text(message) }
			}
			if (model.settingsSelected) SettingsPage(prefs)
			else if (document == null) Welcome(model, prefs, open)
			else {
				Box(Modifier.weight(1f)) {
					editorStates.SaveableStateProvider(document.viewKey) {
						CodeEditor(document, prefs, find, closeFind, model.jump,
							onNavigate = { model.goToDefinition(document, it) }, canNavigate = { model.canNavigate(document, it) },
							onFindUsages = { model.findUsages(document, it) }, focusRequest = model.editorFocusRequest,
							back = if (model.history.canGoBack && !model.busy) model::goBack else null,
							forward = if (model.history.canGoForward && !model.busy) model::goForward else null,
							onCaret = { caret, offset -> model.updateCaret(document, caret, offset) })
					}
				}
			}
		}
	}
	val editor = remember { movableContentOf { currentEditor() } }
	val currentContent by rememberUpdatedState<@Composable () -> Unit> {
		if (structure && document != null && document.symbols.isNotEmpty()) HorizontalSplitLayout(
			first = editor,
			second = { StructurePanel(document, prefs, model.caretOffset, model::jumpTo) },
			firstPaneMinWidth = 300.dp, secondPaneMinWidth = 170.dp,
			state = rememberSplitLayoutState(.79f), modifier = Modifier.fillMaxSize(),
		) else editor()
	}
	// Moving into/out of the tool-window split must retain editor selection, focus and scroll.
	val content = remember { movableContentOf { currentContent() } }
	if (model.usagesVisible) VerticalSplitLayout(
		first = content, second = { UsagesPanel(model, prefs) }, state = rememberSplitLayoutState(.72f),
		firstPaneMinWidth = 220.dp, secondPaneMinWidth = 120.dp, modifier = Modifier.fillMaxSize(),
	) else content()
}
