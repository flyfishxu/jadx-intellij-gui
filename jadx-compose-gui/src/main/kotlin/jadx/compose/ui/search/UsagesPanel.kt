package jadx.compose.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.components.Muted
import jadx.compose.ui.components.Rule
import jadx.compose.ui.components.ToolAction
import jadx.compose.ui.model.WorkspaceModel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.treeStyle

@Composable
internal fun UsagesPanel(model: WorkspaceModel, prefs: AppPreferences) {
	val progress = model.usagesProgress
	var selection by remember(model.usagesTitle) { mutableStateOf<Pair<String, Int>?>(null) }
	val state = rememberLazyListState()
	Column(
		Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)
			.testTag("usages-panel")
	) {
		Row(
			Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Icon(AllIconsKeys.Actions.Find, null, Modifier.size(16.dp))
			Text(prefs.text("Usages", "用法"), fontWeight = FontWeight.SemiBold)
			Text(
				model.usagesTitle,
				Modifier.weight(1f),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Muted("${progress.hits.size}${if (progress.limited) "+" else ""}")
			if (model.usagesBusy) {
				CircularProgressIndicator(Modifier.size(14.dp))
				ToolAction(
					prefs.text("Stop finding usages", "停止查找用法"),
					AllIconsKeys.Actions.Suspend,
					onClick = model::cancelUsages
				)
			}
			ToolAction(
				prefs.text("Close usages", "关闭用法"),
				AllIconsKeys.Actions.Close,
				onClick = model::closeUsages
			)
		}
		Rule()
		model.usagesError?.let { InlineErrorBanner(it, Modifier.fillMaxWidth()) }
		if (model.usagesStopped) Muted(
			prefs.text(
				"Search stopped — partial results",
				"查找已停止，当前为部分结果"
			), Modifier.padding(8.dp)
		)
		if (progress.skipped > 0) Muted(
			prefs.text(
				"${progress.skipped} classes could not be read",
				"${progress.skipped} 个类无法读取"
			), Modifier.padding(8.dp)
		)
		if (progress.limited) Muted(
			prefs.text(
				"Showing the first 1,000 usages",
				"显示前 1,000 处用法"
			), Modifier.padding(8.dp)
		)
		if (!model.usagesBusy && !model.usagesStopped && progress.hits.isEmpty() && model.usagesError == null) {
			Muted(prefs.text("No usages found", "未找到用法"), Modifier.padding(12.dp))
		}
		Box(Modifier.weight(1f)) {
			LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
				progress.hits.groupBy { it.entry }.forEach { (entry, hits) ->
					item(key = entry.id) {
						Row(
							Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp)
						) {
							Icon(AllIconsKeys.Nodes.Class, null, Modifier.size(16.dp))
							Text(
								entry.path,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								fontWeight = FontWeight.Medium
							)
							Muted("(${hits.size})")
						}
					}
					hits.forEach { hit ->
						val key = entry.id to hit.offset
						item(key = "${entry.id}:${hit.offset}") {
							val active = selection == key
							val focus = remember { FocusRequester() }
							Row(
								Modifier.fillMaxWidth().testTag("usage:${entry.id}:${hit.offset}")
								.background(if (active) JewelTheme.treeStyle.colors.backgroundSelectedActive else androidx.compose.ui.graphics.Color.Transparent)
								.semantics { selected = active }
								.focusRequester(focus)
								.pointerInput(key) {
									awaitPointerEventScope {
										while (true) {
											val event = awaitPointerEvent(PointerEventPass.Initial)
											if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
												selection = key; focus.requestFocus()
											}
										}
									}
								}
								.onPreviewKeyEvent {
									if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
										model.showHit(hit); true
									} else false
								}
								.combinedClickable(
									onClick = { selection = key },
									onDoubleClick = { selection = key; model.showHit(hit) })
								.padding(start = 34.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
								horizontalArrangement = Arrangement.spacedBy(14.dp)
							) {
								Muted(hit.title.substringAfterLast(':'), Modifier.width(38.dp))
								Text(
									hit.detail,
									fontFamily = FontFamily("JetBrains Mono"),
									maxLines = 1,
									overflow = TextOverflow.Ellipsis
								)
							}
						}
					}
				}
			}
			VerticalScrollbar(state, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
		}
	}
}
