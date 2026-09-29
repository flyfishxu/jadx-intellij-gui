package jadx.compose.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jadx.compose.preferences.AppPreferences
import jadx.compose.preferences.Appearance
import jadx.compose.preferences.appearanceLabel
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun ThemeToggle(prefs: AppPreferences, dark: Boolean, modifier: Modifier = Modifier) {
	val next = when (prefs.appearance) {
		Appearance.LIGHT -> Appearance.LIGHT_HEADER
		Appearance.LIGHT_HEADER -> Appearance.DARK
		Appearance.DARK, Appearance.SYSTEM -> Appearance.LIGHT
	}
	val label = appearanceLabel(prefs.appearance, prefs)
	Box(modifier) {
		Tooltip({ Text(label) }) {
			IconButton({ prefs.appearance(next) }, Modifier.size(40.dp).padding(5.dp)) {
				Icon(
					if (dark) AllIconsKeys.MeetNewUi.DarkTheme else AllIconsKeys.MeetNewUi.LightTheme,
					label,
					Modifier.size(20.dp),
				)
			}
		}
	}
}
