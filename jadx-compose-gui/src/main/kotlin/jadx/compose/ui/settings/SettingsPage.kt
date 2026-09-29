package jadx.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jadx.compose.preferences.AppPreferences
import jadx.compose.preferences.Appearance
import jadx.compose.preferences.Language
import jadx.compose.preferences.appearanceLabel
import jadx.compose.ui.components.Muted
import jadx.compose.ui.components.Rule
import jadx.compose.ui.components.ToolAction
import jadx.compose.ui.components.editorBackground
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.RadioButtonChip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Composable
internal fun SettingsPage(prefs: AppPreferences) {
	Column(
		Modifier.fillMaxSize().background(editorBackground).verticalScroll(rememberScrollState())
	) {
		Column(
			Modifier.padding(32.dp).widthIn(max = 740.dp),
			verticalArrangement = Arrangement.spacedBy(22.dp)
		) {
			Text(
				prefs.text("Appearance & Behavior", "外观与行为"),
				fontSize = 22.sp,
				fontWeight = FontWeight.SemiBold
			)
			Text(prefs.text("Theme", "主题"), fontWeight = FontWeight.Medium)
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Appearance.entries.forEach { mode ->
					RadioButtonChip(mode == prefs.appearance, { prefs.appearance(mode) }) {
						Text(
							appearanceLabel(mode, prefs)
						)
					}
				}
			}
			Rule()
			Text(prefs.text("Interface language", "界面语言"), fontWeight = FontWeight.Medium)
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Language.entries.forEach { language ->
					RadioButtonChip(language == prefs.language, { prefs.language(language) }) {
						Text(
							when (language) {
								Language.SYSTEM -> prefs.text("System", "跟随系统")
								Language.ENGLISH -> "English"
								Language.CHINESE -> "简体中文"
							}
						)
					}
				}
			}
			Rule()
			Text(prefs.text("Decompilation", "反编译"), fontWeight = FontWeight.Medium)
			CheckboxRow(
				prefs.text(
					"Automatically import mapping.txt beside the APK",
					"打开 APK 时自动导入同目录的 mapping.txt"
				),
				prefs.autoImportMapping,
				prefs::autoImportMapping,
			)
			Rule()
			Text(prefs.text("Editor", "编辑器"), fontWeight = FontWeight.Medium)
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Text("JetBrains Mono")
				ToolAction(
					prefs.text("Decrease font size", "缩小字号"),
					AllIconsKeys.General.Remove,
					prefs.fontSize > 11
				) { prefs.fontSize(prefs.fontSize - 1) }
				Text("${prefs.fontSize} px")
				ToolAction(
					prefs.text("Increase font size", "增大字号"),
					AllIconsKeys.General.Add,
					prefs.fontSize < 24
				) { prefs.fontSize(prefs.fontSize + 1) }
			}
			Text(
				"public String decompile() {\n    return \"Hello, jadx\";\n}",
				fontFamily = androidx.compose.ui.text.font.FontFamily("JetBrains Mono"),
				fontSize = prefs.fontSize.sp, lineHeight = (prefs.fontSize * 1.6).sp,
				color = if (JewelTheme.isDark) androidx.compose.ui.graphics.Color(0xFFCF8E6D) else androidx.compose.ui.graphics.Color(
					0xFF0033B3
				)
			)
			Muted(prefs.text("Changes are applied immediately.", "设置即时生效。"))
		}
	}
}
