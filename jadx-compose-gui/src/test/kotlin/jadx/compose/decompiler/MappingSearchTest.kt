package jadx.compose.decompiler

import java.io.File
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.*

class MappingSearchTest {
	@Test
	fun `reverse proguard import restores class field and method and rejects mismatched mapping`() {
		val dir = Files.createTempDirectory("jadx-mapping-test").toFile()
		try {
			val source = File(dir, "a.java").apply { writeText("public class a { public int b = 3; public int c(int n) { return b + n; } }") }
			assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "11", source.path))
			val mapping = File(dir, "mapping.txt").apply { writeText("sample.Original -> a:\n    int count -> b\n    1:1:int inlinedHelper(int):12:12 -> c\n    1:1:int compute(int):20 -> c\n    2:3:int compute(int):21:22 -> c\n") }
			DecompilerSession.open(File(dir, "a.class"), mapping).use { session ->
				val entry = session.entries.first { it.kind == EntryKind.CLASS }
				assertEquals("sample.Original", entry.path)
				val java = session.read(entry)
				assertTrue(java.code.contains("count"))
				assertTrue(java.code.contains("compute"))
				val hit = session.search(SearchRequest("compute", SearchScope.METHODS)).hits.single()
				val symbol = java.symbols.first { it.key == hit.symbolKey }
				assertTrue(java.code.substring(symbol.offset).startsWith("compute"))
				assertEquals(1, session.search(SearchRequest("count", SearchScope.FIELDS)).hits.size)
				assertTrue(session.search(SearchRequest("ORIGINAL", SearchScope.CLASSES, matchCase = true)).hits.isEmpty())
				assertTrue(session.search(SearchRequest("sample\\.Ori.*", SearchScope.CLASSES, regex = true)).hits.isNotEmpty())
				assertTrue(session.search(SearchRequest("public", SearchScope.JAVA, pathFilter = "unrelated")).hits.isEmpty())
				assertTrue(session.search(SearchRequest("public", SearchScope.JAVA, limit = 1)).limited)
				assertFails { session.search(SearchRequest("[", regex = true)) }
			}
			mapping.writeText("unrelated.Original -> absent:\n    int count -> b\n")
			assertFailsWith<IllegalArgumentException> { DecompilerSession.open(File(dir, "a.class"), mapping) }
		} finally { dir.deleteRecursively() }
	}
}
