@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package jadx.compose.ui.workspace

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import jadx.compose.ui.window.FileSwitcher
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextRange
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont
import jadx.compose.decompiler.EntryKind
import jadx.compose.preferences.AppPreferences
import jadx.compose.preferences.Appearance
import jadx.compose.preferences.Language
import jadx.compose.ui.model.WorkspaceModel
import java.io.File
import java.util.UUID
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import kotlin.test.*
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

class WorkspaceUiTest {
	@Test
	fun `open dex navigate code find switch settings and retain project on failed open`() = runDesktopComposeUiTest(width = 1440, height = 860) {
		FlatJetBrainsMonoFont.install()
		val storage = Preferences.userRoot().node("jadx/compose-gui-test/${UUID.randomUUID()}")
		val prefs = AppPreferences(storage).apply { language(Language.ENGLISH); appearance(Appearance.DARK) }
		val model = WorkspaceModel()
		try {
			setContent { IntUiTheme(isDark = prefs.appearance == Appearance.DARK) { Column { FileSwitcher(model, prefs, {}); Workspace(model, prefs, {}) } } }
			runOnIdle { model.open(File("../jadx-core/src/test/resources/test-samples/hello.dex"), prefs::remember) }
			waitUntil(timeoutMillis = 30_000) { model.file != null && !model.busy }
			val entry = model.entries.first { it.kind == EntryKind.CLASS }
			onNodeWithText(entry.name).performClick()
			waitForIdle()
			assertNull(model.selected, "Selecting a tree item must not open or decompile it")
			onNodeWithText(entry.name).performMouseInput { doubleClick() }
			waitUntil(timeoutMillis = 30_000) { model.selected != null && !model.busy }
			// Reopening the same row must continue to work across repeated double-clicks.
			repeat(3) {
				runOnIdle { model.closeTab(entry.id) }
				onNodeWithText(entry.name).performMouseInput { advanceEventTime(250); doubleClick() }
				waitUntil(timeoutMillis = 10_000) { model.selected != null && !model.busy }
			}
			val code = model.selected!!.code
			onNodeWithText(code).assertExists()
			File("build/reports/ui").mkdirs()
			ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/reports/ui/workspace-dark.png"))
			onNodeWithText(code).performClick().performKeyInput { keyDown(Key.CtrlLeft); pressKey(Key.F); keyUp(Key.CtrlLeft) }
			onNodeWithText("Find in file").performTextInput("class")
			onNodeWithContentDescription("Next match").performClick()
			waitForIdle()
			val matchOffset = code.indexOf("class", ignoreCase = true)
			assertEquals(TextRange(matchOffset, matchOffset + 5), onNodeWithText(code).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			val method = model.selected!!.symbols.first { it.label.startsWith("main(") }
			onNodeWithText(method.label).performClick()
			onNodeWithText(method.label).assertIsSelected()
			waitForIdle()
			assertEquals(TextRange(method.offset), onNodeWithText(code).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			runOnIdle { model.open(File("/nonexistent-jadx-compose-test.apk")) }
			waitUntil(timeoutMillis = 10_000) { model.error != null && !model.busy }
			assertNotNull(model.selected)
			assertEquals(entry.id, model.selectedId)
			runOnIdle { model.error = null }
			onNodeWithContentDescription("Settings").performClick()
			onNodeWithTag("settings-tab", useUnmergedTree = true).assertExists()
			onNode(hasText(entry.name) and hasAnyAncestor(hasTestTag("editor-tabs"))).assertExists()
			onNodeWithText(code).assertDoesNotExist()
			onNodeWithText("简体中文").performClick()
			onNodeWithText("外观与行为").assertExists()
			onNodeWithText("浅色顶栏").performClick()
			waitForIdle()
			ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/reports/ui/settings-light-zh.png"))
			assertEquals(Language.CHINESE, AppPreferences(storage).language)
			assertEquals(Appearance.LIGHT_HEADER, AppPreferences(storage).appearance)
			onNode(hasText(entry.name) and hasAnyAncestor(hasTestTag("editor-tabs"))).performClick()
			onNodeWithText(code).assertExists()
			assertEquals(TextRange(method.offset), onNodeWithText(code).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			onNodeWithTag("settings-tab", useUnmergedTree = true).performClick()
			onNodeWithText("外观与行为").assertExists()
			onAllNodesWithContentDescription("Close tab").onLast().performClick()
			onNodeWithTag("settings-tab", useUnmergedTree = true).assertDoesNotExist()
			onNodeWithText(code).assertExists()
			runOnIdle { model.closeTab(entry.id) }
			onNodeWithText(code).assertDoesNotExist()
			runOnIdle { model.show(entry) }
			waitUntil(timeoutMillis = 10_000) { model.selected != null && !model.busy }
			onNodeWithText(code).assertExists()
			val recentCopy = File.createTempFile("jadx-recent-", ".dex")
			try {
				model.file!!.copyTo(recentCopy, overwrite = true)
				runOnIdle { prefs.remember(recentCopy) }
				onNodeWithTag("file-switcher").performClick()
				onNodeWithText("最近打开的文件").assertExists()
				onNodeWithText(recentCopy.name).performClick()
				waitUntil(timeoutMillis = 10_000) { model.file == recentCopy && !model.busy }
				assertTrue(model.tabs.isEmpty())
				assertEquals(recentCopy.absolutePath, AppPreferences(storage).recent.first())
			} finally { recentCopy.delete() }
		} finally { model.close(); storage.removeNode() }
	}
}
