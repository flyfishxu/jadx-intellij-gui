@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package jadx.compose.ui.workspace

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import jadx.compose.preferences.AppPreferences
import jadx.compose.preferences.Language
import jadx.compose.ui.model.WorkspaceModel
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import javax.tools.ToolProvider
import kotlin.test.*
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

class NavigationWorkspaceUiTest {
	@Test
	fun `hover modifiers and real clicks resolve mapping open target and reveal declaration`() = runDesktopComposeUiTest(width = 1440, height = 860) {
		val dir = Files.createTempDirectory("jadx-navigation-ui").toFile()
		val storage = Preferences.userRoot().node("jadx/compose-gui-test/${UUID.randomUUID()}")
		val model = WorkspaceModel()
		try {
			File(dir, "a.java").writeText("public class a {" + (1..80).joinToString("\n") { "public int field$it = $it;" } + "public static int m(int value) { return value + 1; } }")
			File(dir, "b.java").writeText("public class b { public int run(int value) { System.nanoTime(); return a.m(value); } }")
			assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-g", "--release", "11", File(dir, "a.java").path, File(dir, "b.java").path))
			val jar = File(dir, "navigation.jar")
			JarOutputStream(jar.outputStream()).use { out ->
				listOf("a", "b").forEach { out.putNextEntry(JarEntry("$it.class")); out.write(File(dir, "$it.class").readBytes()); out.closeEntry() }
			}
			val mapping = File(dir, "mapping.txt").apply { writeText("sample.Target -> a:\n    int compute(int) -> m\nsample.Caller -> b:\n") }
			val prefs = AppPreferences(storage).apply { language(Language.ENGLISH) }
			setContent { IntUiTheme(isDark = true) { Workspace(model, prefs, {}) } }
			runOnIdle { model.openWithMapping(jar, mapping) }
			waitUntil(timeoutMillis = 30_000) { model.file != null && !model.busy }
			runOnIdle { model.show(model.entries.first { it.id == "class:b" }) }
			waitUntil(timeoutMillis = 30_000) { model.selectedId == "class:b" && !model.busy }
			val caller = model.selected!!
			fun layout(): TextLayoutResult {
				val results = mutableListOf<TextLayoutResult>()
				onNodeWithTag("source-code").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
				return results.single()
			}
			fun underlines() = layout().layoutInput.text.spanStyles.filter { it.item.textDecoration == TextDecoration.Underline }
			val usage = caller.code.indexOf("compute(")
			assertTrue(usage >= 0)
			val point = layout().getBoundingBox(usage + 3).center
			// Keep the tree focused. Modifier changes must be noticed without clicking the editor first.
			onNodeWithText("Find class or resource").performClick()
			onNodeWithTag("source-code").performMouseInput { moveTo(point) }
			assertTrue(underlines().isEmpty())
			onRoot().performKeyInput { keyDown(Key.MetaLeft) }
			waitUntil(timeoutMillis = 10_000) { underlines().any { it.start == usage && it.end == usage + 7 } }
			File("build/reports/ui").mkdirs()
			ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/reports/ui/code-link-hover.png"))
			onRoot().performKeyInput { keyUp(Key.MetaLeft) }
			waitUntil(timeoutMillis = 10_000) { underlines().isEmpty() }
			onRoot().performKeyInput { keyDown(Key.MetaLeft) }
			waitUntil(timeoutMillis = 10_000) { underlines().isNotEmpty() }
			onNodeWithTag("source-code").performMouseInput { click(point) }
			onRoot().performKeyInput { keyUp(Key.MetaLeft) }
			waitUntil(timeoutMillis = 30_000) { model.selectedId == "class:a" && !model.busy }
			assertNull(model.error)
			val destination = model.selected!!.symbols.first { it.label.startsWith("compute(") }.offset
			assertEquals(TextRange(destination), onNodeWithTag("source-code").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			val editor = onNodeWithTag("editor-pane").fetchSemanticsNode().boundsInRoot
			val caretY = onNodeWithTag("source-code").fetchSemanticsNode().positionInRoot.y + layout().getCursorRect(destination).center.y
			assertTrue(caretY in editor.top..editor.bottom, "Destination must be visible after navigation: $caretY in $editor")
			ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/reports/ui/code-link-destination.png"))
			val target = model.selected!!
			val methodLabel = target.symbols.first { it.label.startsWith("compute(") }.label
			val field = target.symbols.first { it.label.startsWith("field80:") }
			onNodeWithText(methodLabel).assertIsSelected()
			val scrolledUsage = target.code.indexOf("return value") + 7
			val codeNode = onNodeWithTag("source-code").fetchSemanticsNode()
			val scrolledPoint = layout().getBoundingBox(scrolledUsage + 2).center + codeNode.positionInRoot - codeNode.boundsInRoot.topLeft
			onNodeWithTag("source-code").performMultiModalInput {
				key { keyDown(Key.CtrlLeft) }; mouse { click(scrolledPoint) }; key { keyUp(Key.CtrlLeft) }
			}
			waitUntil(timeoutMillis = 10_000) { !model.busy && model.jump?.offset == target.code.indexOf("value)") }
			assertEquals(TextRange(target.code.indexOf("value)")), onNodeWithTag("source-code").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			onNodeWithText(field.label).performClick()
			onNodeWithText(field.label).assertIsSelected()
			onNodeWithText(methodLabel).assertIsNotSelected()
			assertEquals(TextRange(field.offset), onNodeWithTag("source-code").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/reports/ui/structure-selection.png"))
			runOnIdle { model.selectTab(caller.entry.id) }
			waitForIdle()
			val local = caller.code.indexOf("value);")
			val localPoint = layout().getBoundingBox(local + 2).center
			onNodeWithTag("source-code").performMultiModalInput {
				key { keyDown(Key.CtrlLeft) }; mouse { click(localPoint) }; key { keyUp(Key.CtrlLeft) }
			}
			waitUntil(timeoutMillis = 10_000) { !model.busy && model.jump?.viewKey == caller.viewKey }
			assertEquals(TextRange(caller.code.indexOf("value)")), onNodeWithTag("source-code").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
			val missing = layout().getBoundingBox(caller.code.indexOf("nanoTime") + 2).center
			onNodeWithTag("source-code").performMultiModalInput { mouse { moveTo(missing) }; key { keyDown(Key.MetaLeft) } }
			waitForIdle()
			assertTrue(underlines().isEmpty(), "External symbols must not look like available links")
			onRoot().performKeyInput { keyUp(Key.MetaLeft) }
		} finally { model.close(); storage.removeNode(); dir.deleteRecursively() }
	}
}
