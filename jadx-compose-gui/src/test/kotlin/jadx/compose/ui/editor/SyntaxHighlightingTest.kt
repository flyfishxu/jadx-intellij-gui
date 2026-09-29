package jadx.compose.ui.editor

import kotlin.test.*

class SyntaxHighlightingTest {
	@Test
	fun `syntax colors keep source offsets and comments intact`() {
		val code = "// public 12\npublic String text = \"return 7\";"
		val colored = highlight(code, true)
		assertEquals(code, colored.text)
		assertEquals(3, colored.spanStyles.size)
		assertEquals(listOf(0, 3, 9, 13), findMatches("oneONE---one ONE", "one"))
		assertEquals(emptyList(), findMatches(code, ""))
	}
}
