package jadx.compose.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jadx.compose.decompiler.EntryKind
import jadx.compose.decompiler.SearchRequest
import jadx.compose.decompiler.SearchScope
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.components.Muted
import jadx.compose.ui.components.Rule
import jadx.compose.ui.model.WorkspaceModel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun SearchPanel(model: WorkspaceModel, prefs: AppPreferences) {
	var query by remember { mutableStateOf(TextFieldValue(model.lastSearchRequest.query)) }
	var path by remember { mutableStateOf(TextFieldValue(model.lastSearchRequest.pathFilter)) }
	var scope by remember { mutableStateOf(model.lastSearchRequest.scope) }
	var matchCase by remember { mutableStateOf(model.lastSearchRequest.matchCase) }
	var regex by remember { mutableStateOf(model.lastSearchRequest.regex) }
	val focus = remember { FocusRequester() }
	val submit = { model.search(SearchRequest(query.text, scope, matchCase, regex, path.text)) }
	LaunchedEffect(Unit) { focus.requestFocus() }
	Column(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
		Row(
			Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(prefs.text("Search", "搜索"), fontWeight = FontWeight.SemiBold)
			Spacer(Modifier.weight(1f))
			if (model.searchBusy) CircularProgressIndicator(Modifier.size(14.dp))
		}
		Rule()
		Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			TextField(
				query,
				{ query = it },
				Modifier.fillMaxWidth().testTag("project-search-input").focusRequester(focus)
					.onPreviewKeyEvent {
						if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
							submit(); true
						} else false
					},
				placeholder = { Text(prefs.text("Search project", "搜索项目")) },
				leadingIcon = { Icon(AllIconsKeys.Actions.Find, null) })
			Dropdown(Modifier.fillMaxWidth().testTag("project-search-scope"), menuContent = {
				SearchScope.entries.forEach { item ->
					selectableItem(
						item == scope,
						onClick = { scope = item }) { Text(searchLabel(item, prefs)) }
				}
			}) { Text(searchLabel(scope, prefs)) }
			Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				CheckboxRow(prefs.text("Match case", "区分大小写"), matchCase, { matchCase = it })
				CheckboxRow(prefs.text("Regex", "正则"), regex, { regex = it })
			}
			TextField(
				path,
				{ path = it },
				Modifier.fillMaxWidth(),
				placeholder = {
					Text(
						prefs.text(
							"Package / path filter (optional)",
							"包名 / 路径筛选（可选）"
						)
					)
				})
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				DefaultButton(
					submit,
					enabled = model.file != null && !model.busy && query.text.isNotBlank()
				) { Text(prefs.text("Find", "查找")) }
				if (model.searchBusy) OutlinedButton(model::cancelSearch) {
					Text(
						prefs.text(
							"Cancel",
							"取消"
						)
					)
				}
			}
		}
		val progress = model.searchProgress
		Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
			Muted(
				"${progress.hits.size}${if (progress.limited) "+" else ""} " + prefs.text(
					"results",
					"个结果"
				)
			)
			Spacer(Modifier.weight(1f))
			if (progress.total > 0) Muted("${progress.scanned} / ${progress.total}")
		}
		if (progress.limited) Text(
			prefs.text(
				"First 500 results. Narrow the query or path.",
				"仅显示前 500 条，请缩小查询或路径范围。"
			), Modifier.padding(8.dp), color = JewelTheme.globalColors.text.info
		)
		if (progress.skipped > 0) Text(
			prefs.text(
				"${progress.skipped} unreadable/binary items skipped",
				"已跳过 ${progress.skipped} 个不可读或二进制项目"
			), Modifier.padding(8.dp), color = JewelTheme.globalColors.text.info
		)
		model.searchError?.let { InlineErrorBanner(it, Modifier.fillMaxWidth()) }
		Rule()
		LazyColumn(Modifier.fillMaxSize()) {
			itemsIndexed(progress.hits) { _, hit ->
				Column(Modifier.fillMaxWidth().clickable { model.showHit(hit) }
					.padding(horizontal = 12.dp, vertical = 8.dp)) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp)
					) {
						Icon(
							if (hit.entry.kind == EntryKind.CLASS) AllIconsKeys.Nodes.Class else AllIconsKeys.FileTypes.Text,
							null,
							Modifier.size(16.dp)
						)
						Text(hit.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
					}
					Text(
						hit.detail,
						Modifier.padding(start = 22.dp, top = 3.dp),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						color = JewelTheme.globalColors.text.info
					)
				}
			}
		}
	}
}

internal fun searchLabel(scope: SearchScope, prefs: AppPreferences) = when (scope) {
	SearchScope.CLASSES -> prefs.text("Classes", "类")
	SearchScope.METHODS -> prefs.text("Methods", "方法")
	SearchScope.FIELDS -> prefs.text("Fields", "字段")
	SearchScope.JAVA -> prefs.text("Java source", "Java 全文")
	SearchScope.SMALI -> prefs.text("Smali", "Smali 全文")
	SearchScope.RESOURCES -> prefs.text("Resources", "资源名称与内容")
}
