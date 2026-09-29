package jadx.compose.decompiler

import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.*

class DefinitionTest {
	@Test
	fun `metadata resolves mapped cross class overload field and local variable`() {
		val dir = Files.createTempDirectory("jadx-definition").toFile()
		try {
			File(dir, "a.java").writeText("public class a { public static int f; public static int m(int n) { return n + f; } public static String m(String s) { return s; } }")
			File(dir, "b.java").writeText("public class b { public int run(int value) { return a.m(value) + a.f; } public String text() { return a.m(\"hello\"); } }")
			assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-g", "--release", "11", File(dir, "a.java").path, File(dir, "b.java").path))
			val jar = File(dir, "sample.jar")
			JarOutputStream(jar.outputStream()).use { output ->
				listOf("a.class", "b.class").forEach { name ->
					output.putNextEntry(JarEntry(name)); output.write(File(dir, name).readBytes()); output.closeEntry()
				}
			}
			val mapping = File(dir, "mapping.txt").apply { writeText("sample.Target -> a:\n    int count -> f\n    int compute(int) -> m\n    java.lang.String compute(java.lang.String) -> m\nsample.Caller -> b:\n") }
			DecompilerSession.open(jar, mapping).use { session ->
				val caller = session.read(session.entries.first { it.id == "class:b" })
				fun resolve(text: String, shift: Int = 0): Declaration {
					val index = caller.code.indexOf(text)
					assertTrue(index >= 0, caller.code)
					return assertNotNull(session.definition(caller, index + shift), text)
				}
				val intMethod = resolve("compute(value)", 3)
				assertEquals("class:a", intMethod.document.entry.id)
				assertTrue(intMethod.document.code.substring(intMethod.offset).startsWith("compute(int"))
				val stringMethod = resolve("compute(\"hello\")")
				assertTrue(stringMethod.document.code.substring(stringMethod.offset).startsWith("compute(String"))
				val field = resolve("count")
				assertTrue(field.document.code.substring(field.offset).startsWith("count"))
				val variable = resolve("value)", 2)
				assertEquals("class:b", variable.document.entry.id)
				assertTrue(variable.offset < caller.code.indexOf("return"))
				assertNull(session.definition(caller, caller.code.indexOf(';')))
				assertNull(session.definition(caller, -1))
			}
		} finally { dir.deleteRecursively() }
	}
}
