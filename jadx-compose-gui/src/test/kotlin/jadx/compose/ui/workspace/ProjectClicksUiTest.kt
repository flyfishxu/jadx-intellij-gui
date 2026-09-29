@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package jadx.compose.ui.workspace

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import jadx.compose.fixtures.EditorFixture
import jadx.compose.preferences.AppPreferences
import jadx.compose.preferences.Language
import jadx.compose.ui.model.WorkspaceModel
import jadx.compose.ui.project.ProjectPanel
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.*

class ProjectClicksUiTest {
	@Test
	fun `rapid consecutive double clicks expand nested packages and open file once`() = runDesktopComposeUiTest(width = 400, height = 800) {
		val storage = Preferences.userRoot().node("jadx/compose-gui-test/${UUID.randomUUID()}")
		val prefs = AppPreferences(storage).apply { language(Language.ENGLISH) }
		val model = WorkspaceModel()
		EditorFixture().use { fixture ->
			try {
				fixture.mapping.writeText("one.two.three.four.Target -> a:\none.two.three.four.Caller -> b:\n")
				setContent { IntUiTheme { ProjectPanel(model, prefs, "", {}, FocusRequester()) } }
				runOnIdle { model.openWithMapping(fixture.jar, fixture.mapping) }
				waitUntil(timeoutMillis = 30_000) { model.file != null && !model.busy }
				repeat(6) { round ->
					onNodeWithContentDescription("Collapse all").performClick()
					for (name in listOf("Sources", "one", "two", "three", "four")) {
						onNodeWithText(name, useUnmergedTree = true).performMouseInput {
							if (round % 2 == 0) {
								click(Offset(center.x, -1f)); advanceEventTime(80); click(Offset(center.x, 2f))
							} else { click(); advanceEventTime(80); click() }
						}
					}
					val entry = model.entries.first { it.name == "Target" || it.name == "Target.java" }
					onNodeWithText(entry.name, useUnmergedTree = true).performMouseInput {
						if (round % 2 == 0) {
							click(Offset(center.x, -1f)); advanceEventTime(80); click(Offset(center.x, 2f))
						} else { click(); advanceEventTime(80); click() }
					}
					waitUntil(timeoutMillis = 10_000) { model.selected?.entry?.id == entry.id && !model.busy }
					runOnIdle { model.closeTab(entry.id) }
				}
				// Selecting a file remains independent from opening it; Enter is an explicit open action.
				val entry = model.entries.first { it.name.startsWith("Target") }
				onNodeWithText(entry.name).performMouseInput { click() }
				onNodeWithText(entry.name).assertIsSelected()
				assertNull(model.selected)
				onNodeWithTag("project-tree").performKeyInput { pressKey(Key.Enter) }
				waitUntil(timeoutMillis = 10_000) { model.selected?.entry?.id == entry.id && !model.busy }
				// The disclosure arrow still expands with one click, and does not open a file.
				onNodeWithContentDescription("Collapse all").performClick()
				onNodeWithTag("project-tree").performMouseInput { click(Offset(24f, 11f)) }
				onNodeWithText("one").assertExists()
				onNodeWithTag("project-tree").performKeyInput { pressKey(Key.DirectionLeft) }
				onNodeWithText("one").assertDoesNotExist()
			} finally { model.close(); storage.removeNode() }
		}
	}
}
