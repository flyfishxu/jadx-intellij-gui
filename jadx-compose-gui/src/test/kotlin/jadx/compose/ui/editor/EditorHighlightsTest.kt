package jadx.compose.ui.editor

import kotlin.test.*

class EditorHighlightsTest {
	@Test
	fun `search options preserve actual match lengths and reject invalid expressions`() {
		val code = "value Value values value12"
		assertEquals(4, editorFind(code, "value", EditorFindOptions()).matches.size)
		assertEquals(1, editorFind(code, "value", EditorFindOptions(matchCase = true, wholeWord = true)).matches.size)
		assertEquals(listOf("value", "Value"), editorFind(code, "value", EditorFindOptions(wholeWord = true)).matches.map { code.substring(it.min, it.max) })
		assertEquals("value12", editorFind(code, "value\\d+", EditorFindOptions(regex = true)).matches.single().let { code.substring(it.min, it.max) })
		assertNotNull(editorFind(code, "[", EditorFindOptions(regex = true)).error)
	}

	@Test
	fun `matching brackets ignore comments string and character literals`() {
		val code = "call(\"ignored )\", '['); /* { */ { // ]\n next(); }"
		val pairs = bracketPairs(code)
		assertEquals(code.indexOf(");"), pairs[code.indexOf('(')])
		assertFalse(code.indexOf(')') in pairs)
		assertFalse(code.indexOf('[') in pairs)
		assertEquals(code.lastIndexOf('}'), pairs[code.indexOf("{ //")])
	}
}
