@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package jadx.compose.ui.workspace

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import jadx.compose.fixtures.EditorFixture
import jadx.compose.preferences.AppPreferences
import jadx.compose.preferences.Language
import jadx.compose.ui.editor.occurrenceColor
import jadx.compose.ui.model.WorkspaceModel
import java.io.File
import java.util.UUID
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import kotlin.test.*
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

class EditorFeaturesUiTest {
	@Test
	fun `semantic highlight context usages navigation history and search work in the editor`() = runDesktopComposeUiTest(width = 1440, height = 920) {
		val fixture = EditorFixture()
		val storage = Preferences.userRoot().node("jadx/compose-gui-test/${UUID.randomUUID()}")
		val prefs = AppPreferences(storage).apply { language(Language.ENGLISH) }
		val model = WorkspaceModel()
		try {
			var dark by mutableStateOf(true)
			setContent { IntUiTheme(isDark = dark) { Workspace(model, prefs, {}) } }
			runOnIdle { model.openWithMapping(fixture.jar, fixture.mapping) }
			waitUntil(timeoutMillis = 30_000) { model.file != null && !model.busy }
			runOnIdle { model.show(model.entries.first { it.id == "class:b" }) }
			waitUntil(timeoutMillis = 30_000) { model.selected != null && !model.busy }
			val caller = model.selected!!
			fun layout(): TextLayoutResult {
				val results = mutableListOf<TextLayoutResult>()
				onNodeWithTag("source-code").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
				return results.single()
			}
			fun select(offset: Int, end: Int = offset) = onNodeWithTag("source-code").performTextInputSelection(TextRange(offset, end))
			fun highlights() = layout().layoutInput.text.spanStyles.filter { it.item.background == occurrenceColor(dark) }.map { TextRange(it.start, it.end) }
			fun screenshot(name: String) {
				File("build/reports/ui").mkdirs()
				ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/reports/ui/$name.png"))
			}
			val call = caller.code.indexOf("compute(value)")
			val local = call + "compute(".length
			select(local, local + 5)
			waitUntil(timeoutMillis = 10_000) { highlights().size == 3 }
			assertTrue(highlights().all { it.start < caller.code.indexOf("other(") })
			screenshot("editor-occurrences-dark")
			onNodeWithContentDescription("Structure").performClick()
			assertEquals(TextRange(local, local + 5), onNodeWithTag("source-code").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			onNodeWithContentDescription("Structure").performClick()
			assertEquals(TextRange(local, local + 5), onNodeWithTag("source-code").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			onNodeWithTag("source-code").performTextInputSelection(TextRange(local, local + 5))
			runOnIdle { dark = false }
			waitUntil(timeoutMillis = 10_000) { highlights().size == 3 }
			screenshot("editor-occurrences-light")
			runOnIdle { dark = true }
			onNodeWithTag("source-code").performKeyInput { pressKey(Key.Escape) }
			waitUntil(timeoutMillis = 10_000) { highlights().isEmpty() }
				select(0)
				select(local, local + 5)
				waitUntil(timeoutMillis = 10_000) { highlights().size == 3 }
			// Right-click selects the clicked symbol and extends the standard Jewel text menu.
			val node = onNodeWithTag("source-code").fetchSemanticsNode()
			val point = layout().getBoundingBox(call + 3).center + node.positionInRoot - node.boundsInRoot.topLeft
			onNodeWithTag("source-code").performMouseInput { rightClick(point) }
			onNodeWithText("Find Usages").assertExists().performClick()
			waitUntil(timeoutMillis = 30_000) { !model.usagesBusy && model.usagesProgress.hits.isNotEmpty() }
			assertEquals(1, model.usagesProgress.hits.size)
			onNodeWithTag("usages-panel").assertExists()
			screenshot("editor-usages-dark")
			val hit = model.usagesProgress.hits.single()
			val row = onNodeWithTag("usage:${hit.entry.id}:${hit.offset}")
			row.performClick().assertIsSelected()
			row.performMouseInput { doubleClick() }
			waitUntil(timeoutMillis = 10_000) { model.jump?.offset == hit.offset }
			assertEquals(TextRange(hit.offset), onNodeWithTag("source-code").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			onNodeWithContentDescription("Close usages").performClick()
			select(call)
			onNodeWithTag("source-code").performKeyInput { keyDown(Key.MetaLeft); pressKey(Key.B); keyUp(Key.MetaLeft) }
			waitUntil(timeoutMillis = 30_000) { model.selectedId == "class:a" && !model.busy }
			val declaration = model.caretOffset
			assertTrue(model.selected!!.code.substring(declaration).startsWith("compute(int"))
			onRoot().performKeyInput { keyDown(Key.MetaLeft); pressKey(Key.LeftBracket); keyUp(Key.MetaLeft) }
			waitUntil(timeoutMillis = 10_000) { model.selectedId == "class:b" && !model.busy }
			assertEquals(TextRange(call), onNodeWithTag("source-code").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			onRoot().performKeyInput { keyDown(Key.AltLeft); pressKey(Key.DirectionRight); keyUp(Key.AltLeft) }
			waitUntil(timeoutMillis = 10_000) { model.selectedId == "class:a" && !model.busy }
			assertEquals(TextRange(declaration), onNodeWithTag("source-code").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			onNodeWithTag("source-code").performKeyInput { keyDown(Key.AltLeft); pressKey(Key.F7); keyUp(Key.AltLeft) }
			waitUntil(timeoutMillis = 30_000) { !model.usagesBusy && model.usagesVisible }
			assertEquals(1, model.usagesProgress.hits.size)
			onNodeWithContentDescription("Close usages").performClick()
			onNodeWithTag("source-code").performKeyInput { keyDown(Key.MetaLeft); pressKey(Key.F); keyUp(Key.MetaLeft) }
			onNodeWithText("Find in file").performTextInput("value")
			waitUntil(timeoutMillis = 10_000) { !onNodeWithContentDescription("Next match").fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
			onNodeWithContentDescription("Whole words").performClick()
			onNodeWithContentDescription("Next match").performClick()
			waitForIdle()
			screenshot("editor-find-dark")
			onNodeWithContentDescription("Regex").performClick()
			onNodeWithText("value", substring = false).performTextReplacement("[")
			onNodeWithText("Invalid regular expression").assertExists()
			runOnIdle { model.importMapping(null) }
			waitUntil(timeoutMillis = 30_000) { !model.busy && model.mapping == null }
			assertFalse(model.history.canGoBack)
			assertFalse(model.history.canGoForward)
			assertFalse(model.usagesVisible)
		} finally { model.close(); fixture.close(); storage.removeNode() }
	}
}
