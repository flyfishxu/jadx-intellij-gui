@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package jadx.compose.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import jadx.compose.decompiler.*
import jadx.compose.preferences.AppPreferences
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.*
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

class CodeNavigationUiTest {
	@Test
	fun `plain click selects while command and control click navigate exact text offset`() = runDesktopComposeUiTest {
		val storage = Preferences.userRoot().node("jadx/compose-gui-test/${UUID.randomUUID()}")
		try {
			val code = "int result = input;"
			val document = SourceDocument(ProjectEntry("class:test", "Test.java", "Test", "", EntryKind.CLASS), code, "Java", emptyList())
			val requests = mutableListOf<Int>()
			setContent { IntUiTheme { CodeEditor(document, AppPreferences(storage), false, {}, null, { requests += it }, { true }, onCaret = { _, _ -> }) } }
			val node = onNodeWithTag("source-code")
			val layouts = mutableListOf<TextLayoutResult>()
			node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
			val index = code.indexOf("input") + 2
			val point = layouts.single().getBoundingBox(index).center
			node.performMouseInput { click(point) }
			assertTrue(requests.isEmpty())
			for (modifier in listOf(Key.MetaLeft, Key.CtrlLeft)) {
				node.performMultiModalInput {
					key { keyDown(modifier) }
					mouse { click(point) }
					key { keyUp(modifier) }
				}
				waitForIdle()
				assertTrue(requests.last() == code.indexOf("input"))
			}
			assertEquals(2, requests.size)
		} finally { storage.removeNode() }
	}
}
