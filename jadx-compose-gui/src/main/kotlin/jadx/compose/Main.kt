package jadx.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import jadx.compose.ui.window.FileSwitcher
import jadx.compose.ui.window.MacForceClick
import jadx.compose.ui.window.LocalForceClick
import jadx.compose.ui.window.ThemeToggle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont
import jadx.compose.preferences.AppPreferences
import jadx.compose.preferences.Appearance
import jadx.compose.ui.model.WorkspaceModel
import jadx.compose.ui.window.isMacOS
import jadx.compose.ui.workspace.Workspace
import java.awt.Dimension
import java.awt.FileDialog
import java.io.File
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.*
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.*
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.window.*
import org.jetbrains.jewel.window.styling.TitleBarStyle
import org.jetbrains.jewel.window.utils.clientRegion

fun main(args: Array<String>) {
	System.setProperty("apple.awt.application.appearance", "system")
	if (isMacOS) System.setProperty("apple.laf.useScreenMenuBar", "true")
	System.setProperty("apple.awt.application.name", "Jadx Compose")
	System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Jadx Compose")
	FlatJetBrainsMonoFont.install()
	application {
		val prefs = remember { AppPreferences() }
		val model = remember { WorkspaceModel { prefs.autoImportMapping } }
		DisposableEffect(model) { onDispose { model.close() } }
		LaunchedEffect(Unit) {
			args.firstOrNull()?.let { input ->
				val mappingIndex = args.indexOf("--mapping")
				if (mappingIndex >= 0 && mappingIndex + 1 < args.size) model.openWithMapping(File(input), File(args[mappingIndex + 1]), prefs::remember)
				else model.open(File(input), prefs::remember)
			}
		}
		val dark = when (prefs.appearance) {
			Appearance.SYSTEM -> isSystemInDarkTheme()
			Appearance.DARK -> true
			else -> false
		}
		val definition = remember(dark) { if (dark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition() }
		IntUiTheme(definition, ComponentStyling.default().decoratedWindow(
			titleBarStyle = when {
				dark -> TitleBarStyle.dark()
				prefs.appearance == Appearance.LIGHT_HEADER -> TitleBarStyle.lightWithLightHeader()
				else -> TitleBarStyle.light()
			}
		)) {
			DecoratedWindow(
				onCloseRequest = ::exitApplication,
				title = (model.file?.name?.plus(" — ") ?: "") + "Jadx Compose",
				state = rememberWindowState(width = 1440.dp, height = 920.dp),
			) {
				DisposableEffect(window) {
					window.minimumSize = Dimension(1000, 640)
					onDispose { }
				}
				val open: () -> Unit = {
					if (!model.busy) {
						FileDialog(window, prefs.text("Open APK, DEX or JAR", "打开 APK、DEX 或 JAR"), FileDialog.LOAD).apply {
							isMultipleMode = false
							isVisible = true
							files.firstOrNull()?.let { model.open(it, prefs::remember) }
							dispose()
						}
					}
				}
				TitleBar(
					Modifier.newFullscreenControls(),
					gradientStartColor = if (prefs.appearance == Appearance.LIGHT_HEADER) Color(0xFFF5D4C1) else Color(0xFF654B40),
				) {
					FileSwitcher(model, prefs, open, Modifier.align(Alignment.Start).clientRegion("file-switcher"))
					ThemeToggle(prefs, dark, Modifier.align(Alignment.End).clientRegion("switch-theme"))
				}
				val forceClick = remember(window) { MacForceClick(window.rootPane) }
				DisposableEffect(forceClick) { onDispose { forceClick.close() } }
				CompositionLocalProvider(LocalForceClick provides forceClick) {
					Workspace(model, prefs, open, nativeWindow = window, importMapping = {
						if (!model.busy && model.file != null) {
							FileDialog(window, prefs.text("Import R8 / ProGuard mapping", "导入 R8 / ProGuard mapping"), FileDialog.LOAD).apply {
								directory = model.file?.parent
								file = "mapping.txt"
								isVisible = true
								files.firstOrNull()?.let(model::importMapping)
								dispose()
							}
						}
					})
				}
			}
		}
	}
}
