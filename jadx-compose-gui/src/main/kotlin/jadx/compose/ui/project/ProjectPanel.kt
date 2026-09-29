package jadx.compose.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jadx.compose.decompiler.EntryKind
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.components.Muted
import jadx.compose.ui.components.Rule
import jadx.compose.ui.components.ToolAction
import jadx.compose.ui.model.WorkspaceModel
import org.jetbrains.jewel.foundation.lazy.tree.rememberTreeState
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Selected

@Composable
internal fun ProjectPanel(
	model: WorkspaceModel,
	prefs: AppPreferences,
	filter: String,
	onFilter: (String) -> Unit,
	focus: FocusRequester
) {
	val listState = rememberLazyListState()
	val treeState = rememberTreeState(lazyListState = listState)
	var filterValue by remember { mutableStateOf(TextFieldValue(filter)) }
	LaunchedEffect(filter) { if (filterValue.text != filter) filterValue = TextFieldValue(filter) }
	val filtered = remember(
		model.entries,
		filter
	) {
		model.entries.filter {
			filter.isBlank() || (it.path.contains(
				filter,
				ignoreCase = true
			) || it.id.contains(filter, ignoreCase = true))
		}
	}
	val directoryDepths = remember(filtered) {
		filtered.flatMap {
			it.ancestorKeys().withIndex().map { (depth, id) -> id to depth }
		}.toMap()
	}
	val tree = remember(filtered, prefs.chinese) {
		projectTree(
			filtered,
			prefs.text("Sources", "源代码"),
			prefs.text("Resources", "资源")
		)
	}
	LaunchedEffect(model.projectVersion, filter) {
		treeState.openNodes = EntryKind.entries.map { it.name }.toSet() +
				if (filter.isNotBlank() || model.entries.size < 80) filtered.flatMap { it.ancestorKeys() }
					.toSet() else emptySet()
	}
	LaunchedEffect(model.selectedId, model.projectVersion) {
		model.selectedId?.let { id ->
			treeState.selectedKeys = setOf(id)
			model.entries.firstOrNull { it.id == id }
				?.let { treeState.openNodes += it.ancestorKeys() }
		}
	}
	Column(Modifier.fillMaxSize()) {
		Row(
			Modifier.fillMaxWidth().height(36.dp).padding(start = 12.dp, end = 4.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(prefs.text("Project", "项目"), fontWeight = FontWeight.SemiBold)
			Spacer(Modifier.weight(1f))
			ToolAction(
				prefs.text("Collapse all", "全部折叠"),
				AllIconsKeys.Actions.Collapseall
			) { treeState.openNodes = emptySet() }
		}
		Rule()
		TextField(
			filterValue,
			{ filterValue = it; onFilter(it.text) },
			Modifier.fillMaxWidth().padding(8.dp).focusRequester(focus),
			placeholder = { Text(prefs.text("Find class or resource", "查找类或资源")) },
			leadingIcon = { Icon(AllIconsKeys.Actions.Find, null) })
		model.file?.let { file ->
			Row(
				Modifier.height(30.dp).padding(horizontal = 12.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(6.dp)
			) {
				Icon(AllIconsKeys.FileTypes.Archive, null, Modifier.size(16.dp))
				Text(
					file.name,
					fontWeight = FontWeight.Medium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
		if (filtered.isEmpty()) Box(
			Modifier.fillMaxWidth().padding(16.dp)
		) { Muted(prefs.text("No files", "没有文件")) }
		LazyTree(
			tree,
			Modifier.fillMaxSize().projectTreeClicks(
				treeState,
				listState,
				directoryDepths
			) { id -> model.entries.firstOrNull { it.id == id }?.let(model::show) }
				.testTag("project-tree").onPreviewKeyEvent {
				if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
					model.entries.firstOrNull { entry -> entry.id in treeState.selectedKeys }
						?.let(model::show)
					true
				} else false
			},
			treeState = treeState,
			onElementDoubleClick = { it.data.entry?.let(model::show) }) { node ->
			Row(
				Modifier.fillMaxWidth().semantics { selected = isSelected },
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(6.dp)
			) {
				val icon = when {
					node.data.entry?.kind == EntryKind.CLASS -> AllIconsKeys.Nodes.Class
					node.data.entry != null -> AllIconsKeys.FileTypes.Text
					node.data.kind == EntryKind.CLASS -> AllIconsKeys.Nodes.Package
					else -> AllIconsKeys.Nodes.Folder
				}
				Icon(icon, null, Modifier.size(16.dp), hint = Selected(isSelected && isActive))
				Text(node.data.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		}
	}
}
