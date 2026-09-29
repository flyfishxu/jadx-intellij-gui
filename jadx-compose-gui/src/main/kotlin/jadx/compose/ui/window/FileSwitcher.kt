package jadx.compose.ui.window

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jadx.compose.preferences.AppPreferences
import jadx.compose.ui.model.WorkspaceModel
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.io.File

@Composable
internal fun FileSwitcher(
	model: WorkspaceModel,
	prefs: AppPreferences,
	open: () -> Unit,
	modifier: Modifier = Modifier
) {
	var expanded by remember { mutableStateOf(false) }
	Box(modifier.padding(horizontal = 8.dp)) {
		IconButton(
			{ expanded = !expanded },
			Modifier.height(32.dp).widthIn(max = 400.dp).testTag("file-switcher")
		) {
			Row(
				Modifier.padding(horizontal = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Icon(AllIconsKeys.FileTypes.Archive, null, Modifier.size(20.dp))
				Text(
					model.file?.name ?: prefs.text("Open…", "打开…"),
					Modifier.weight(1f, fill = false),
					fontWeight = FontWeight.Medium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Icon(AllIconsKeys.General.ChevronDown, null, Modifier.size(16.dp))
			}
		}
		if (expanded) PopupMenu(
			onDismissRequest = { expanded = false; true },
			horizontalAlignment = Alignment.Start
		) {
			selectableItem(false, onClick = { expanded = false; open() }, enabled = !model.busy) {
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Icon(AllIconsKeys.Actions.MenuOpen, null)
					Text(prefs.text("Open…", "打开…"))
				}
			}
			if (prefs.recent.isNotEmpty()) {
				separator()
				passiveItem {
					Text(
						prefs.text("Recent files", "最近打开的文件"),
						Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
						color = JewelTheme.globalColors.text.info
					)
				}
				prefs.recent.forEach { path ->
					val file = File(path)
					selectableItem(
						file.absolutePath == model.file?.absolutePath,
						enabled = !model.busy,
						onClick = {
							expanded = false
							if (file.absolutePath != model.file?.absolutePath) model.open(
								file,
								prefs::remember
							)
						}) {
						Column(
							Modifier.widthIn(min = 280.dp, max = 520.dp).padding(vertical = 4.dp)
						) {
							Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
							Text(
								(file.parent ?: path).let {
									if (it.startsWith(System.getProperty("user.home") + File.separator)) "~" + it.removePrefix(
										System.getProperty("user.home")
									) else it
								},
								color = LocalContentColor.current.copy(alpha = .75f),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					}
				}
			}
		}
	}
}
