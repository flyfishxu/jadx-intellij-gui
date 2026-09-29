@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package jadx.compose.ui.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextRange
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont
import jadx.compose.decompiler.CodeMode
import jadx.compose.fixtures.ReleaseFixture
import jadx.compose.preferences.AppPreferences
import jadx.compose.preferences.Appearance
import jadx.compose.preferences.Language
import jadx.compose.ui.model.WorkspaceModel
import java.awt.Frame
import java.awt.MenuItem
import java.awt.event.ActionEvent
import java.io.File
import java.util.UUID
import java.util.prefs.Preferences
import javax.imageio.ImageIO
import kotlin.test.*
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Assume.assumeTrue

class RealApkUiTest {
	@Test
	fun `import release mapping search mapped method and switch java smali in the real workspace`() {
		val apkPath = System.getProperty("testApk")
		val mappingPath = System.getProperty("testMapping")
		assumeTrue(apkPath != null && mappingPath != null)
		runDesktopComposeUiTest(width = 1440, height = 900) {
			FlatJetBrainsMonoFont.install()
			val storage = Preferences.userRoot().node("jadx/compose-gui-test/${UUID.randomUUID()}")
			val prefs = AppPreferences(storage).apply { language(Language.ENGLISH); appearance(Appearance.DARK) }
			val model = WorkspaceModel()
			val mapping = File(mappingPath!!)
			val fixture = ReleaseFixture(mapping)
			lateinit var menuWindow: Frame
			runOnIdle { menuWindow = Frame("Jadx menu test") }
			fun fileMenuItem(label: String): MenuItem {
				val menu = menuWindow.menuBar.getMenu(0)
				return (0 until menu.itemCount).map(menu::getItem).first { it.label == label }
			}
			fun invokeFileMenu(label: String) = runOnIdle {
				val item = fileMenuItem(label)
				assertTrue(item.isEnabled)
				item.actionListeners.forEach { it.actionPerformed(ActionEvent(item, ActionEvent.ACTION_PERFORMED, label)) }
			}
			try {
				setContent { IntUiTheme(isDark = prefs.appearance == Appearance.DARK) { Workspace(model, prefs, {}, { model.importMapping(mapping) }, nativeWindow = menuWindow) } }
				runOnIdle { model.open(File(apkPath!!)) }
				waitUntil(timeoutMillis = 60_000) { !model.busy && model.file != null }
				onNodeWithTag("menu-file").assertDoesNotExist()
				runOnIdle {
					assertEquals(listOf("File", "Search", "View", "Window"), (0 until menuWindow.menuBar.menuCount).map { menuWindow.menuBar.getMenu(it).label })
					assertFalse(fileMenuItem("Remove Mapping").isEnabled)
				}
				invokeFileMenu("Import Mapping…")
				waitUntil(timeoutMillis = 60_000) { !model.busy && model.mapping == mapping }
				assertTrue(model.mappingMatches > 500)
				onNodeWithContentDescription("Search").performClick()
				onNodeWithTag("project-search-input").performTextInput(fixture.simpleName)
				onNodeWithText("Find").performClick()
				waitUntil(timeoutMillis = 30_000) { !model.searchBusy && model.searchProgress.hits.isNotEmpty() }
				onNode(hasText(fixture.simpleName) and !hasSetTextAction()).performClick()
				waitUntil(timeoutMillis = 30_000) { !model.busy && model.selected != null }
				val java = model.selected!!
				assertTrue(java.code.contains(fixture.method))
				onNodeWithText(java.code).assertExists()
				val fieldUsage = java.code.lastIndexOf(fixture.field)
				val fieldDefinition = java.symbols.first { it.label.startsWith(fixture.field + ":") }
				assertTrue(fieldUsage > fieldDefinition.offset)
				runOnIdle { model.jumpTo(fieldUsage) }
				waitForIdle()
				fun codeLayout(): TextLayoutResult {
					val layouts = mutableListOf<TextLayoutResult>()
					onNodeWithTag("source-code").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
					return layouts.single()
				}
				val sourceNode = onNodeWithTag("source-code").fetchSemanticsNode()
				val fieldPoint = codeLayout().getBoundingBox(fieldUsage + 2).center + sourceNode.positionInRoot - sourceNode.boundsInRoot.topLeft
				onNodeWithTag("source-code").performMultiModalInput {
					mouse { moveTo(fieldPoint) }; key { keyDown(Key.MetaLeft) }
				}
				waitUntil(timeoutMillis = 10_000) { codeLayout().layoutInput.text.spanStyles.any { it.start == fieldUsage && it.item.textDecoration == TextDecoration.Underline } }
				File("build/reports/ui").mkdirs()
				ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/reports/ui/release-code-link.png"))
				onNodeWithTag("source-code").performMouseInput { click(fieldPoint) }
				onRoot().performKeyInput { keyUp(Key.MetaLeft) }
				waitUntil(timeoutMillis = 10_000) { !model.busy && model.jump?.offset == fieldDefinition.offset }
				assertEquals(TextRange(fieldDefinition.offset), onNodeWithTag("source-code").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
				onNodeWithText(fieldDefinition.label).assertIsSelected()
				val editorBounds = onNodeWithTag("editor-pane").fetchSemanticsNode().boundsInRoot
				val structureBounds = onNodeWithTag("structure-panel").fetchSemanticsNode().boundsInRoot
				assertEquals(editorBounds.top, structureBounds.top, "Structure starts at the top of the workspace")
				assertEquals(editorBounds.bottom, structureBounds.bottom, "Structure occupies the entire workspace height")
				assertTrue(onNodeWithTag("code-mode").fetchSemanticsNode().boundsInRoot.top >= editorBounds.bottom)
				File("build/reports/ui").mkdirs()
				ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/reports/ui/release-java-dark.png"))
				onNodeWithTag("code-mode").performClick()
				onNodeWithText("Smali").performClick()
				waitUntil(timeoutMillis = 30_000) { !model.busy && model.selected?.mode == CodeMode.SMALI }
				val smali = model.selected!!
				assertTrue(smali.code.contains(fixture.descriptor))
				onNodeWithText(smali.code).assertExists()
				ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/reports/ui/release-smali-dark.png"))
				onNodeWithTag("project-search-scope").performClick()
				onNodeWithText("Methods").performClick()
				onNodeWithTag("project-search-input").performTextReplacement(fixture.method)
				onNodeWithText("Find").performClick()
				waitUntil(timeoutMillis = 30_000) { !model.searchBusy && model.searchProgress.hits.any { it.title == "${fixture.method}(…)" } }
				onNodeWithText("${fixture.method}(…)").performClick()
				waitUntil(timeoutMillis = 30_000) { !model.busy && model.selected?.mode == CodeMode.JAVA }
				val restored = model.selected!!
				val method = restored.symbols.first { it.label.startsWith("${fixture.method}(") }
				assertEquals(TextRange(method.offset), onNodeWithText(restored.code).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
				runOnIdle { model.importMapping(File("/missing-mapping-for-test.txt")) }
				waitUntil(timeoutMillis = 10_000) { !model.busy && model.error != null }
				assertEquals(mapping, model.mapping)
				assertEquals(restored, model.selected)
				onNodeWithContentDescription("Project").performClick()
				runOnIdle { model.error = null; prefs.appearance(Appearance.LIGHT) }
				waitForIdle()
				onNodeWithText("Import mapping…").assertDoesNotExist()
				val packageRow = onNodeWithText("adb")
				val treeBounds = onNodeWithTag("project-tree").fetchSemanticsNode().boundsInRoot
				val rowBounds = packageRow.fetchSemanticsNode().boundsInRoot
				assertTrue(treeBounds.right - rowBounds.right <= 20f, "Package selection must reach the tree's trailing edge: tree=$treeBounds, row=$rowBounds")
				packageRow.performMouseInput { click(Offset(width - 4f, height / 2f)) }.assertIsSelected()
				ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/reports/ui/release-project-light.png"))
				invokeFileMenu("Remove Mapping")
				waitUntil(timeoutMillis = 60_000) { !model.busy && model.mapping == null }
				assertFalse(model.selected!!.entry.path.contains(fixture.simpleName))
			} finally { model.close(); storage.removeNode(); runOnIdle { menuWindow.dispose() } }
		}
	}
}
