@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package jadx.compose.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Column
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import jadx.compose.decompiler.*
import jadx.compose.preferences.AppPreferences
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.*

class EditorScrollUiTest {
	@Test
	fun `plain clicks within scrolled code keep viewport stationary`() = runDesktopComposeUiTest(width = 700, height = 500) {
		val storage = Preferences.userRoot().node("jadx/compose-gui-test/${UUID.randomUUID()}")
		try {
			val code = (1..200).joinToString("\n") { "int variable$it = " + (1..30).joinToString(" + ") { "variable$it" } + ";" }
			val doc = SourceDocument(ProjectEntry("test", "Test", "Test", "", EntryKind.CLASS), code, "Java", emptyList())
			setContent { IntUiTheme { Column { DefaultButton({}) { Text("Other focus") }; CodeEditor(doc, AppPreferences(storage), false, {}, null, { fail("Plain click navigated") }, onCaret = { _, _ -> }) } } }
			val viewport = onNodeWithTag("editor-scroll")
			viewport.performMouseInput { click(center) }
			onNodeWithText("Other focus").performClick()
			viewport.performMouseInput { moveTo(center); scroll(10f, ScrollWheel.Horizontal); scroll(20f) }
			waitForIdle()
			fun position(): Pair<Float, Float> = viewport.fetchSemanticsNode().config.let {
				it[SemanticsProperties.HorizontalScrollAxisRange].value() to it[SemanticsProperties.VerticalScrollAxisRange].value()
			}
			val before = position()
			assertTrue(before.first > 0 && before.second > 0, "Expected both axes to scroll: $before")
			repeat(6) { index ->
				viewport.performMouseInput { click(Offset(90f + index * 30, 80f + index * 30)) }
				waitForIdle()
				assertEquals(before, position(), "Plain click moved the viewport")
			}
			// Keyboard caret navigation still reveals code outside the visible viewport.
			onNodeWithTag("source-code").performKeyInput { repeat(30) { pressKey(Key.DirectionDown) } }
			waitForIdle()
			assertTrue(position().second > before.second, "Keyboard navigation must still scroll to the caret")
		} finally { storage.removeNode() }
	}
}
