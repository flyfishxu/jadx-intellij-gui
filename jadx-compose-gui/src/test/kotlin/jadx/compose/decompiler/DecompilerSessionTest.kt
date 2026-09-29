package jadx.compose.decompiler

import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.*

class DecompilerSessionTest {
	@Test
	fun `checkout dex plugin loads real dex and produces navigable source`() {
		DecompilerSession.open(File("../jadx-core/src/test/resources/test-samples/hello.dex")).use { session ->
			val entry = session.entries.first { it.kind == EntryKind.CLASS }
			val document = session.read(entry)
			assertTrue(document.code.contains("class "))
			assertTrue(document.symbols.any { it.kind == SymbolKind.METHOD })
			assertTrue(document.symbols.all { it.offset in 0..document.code.length })
		}
	}

	@Test
	fun `java plugin reads jar classes and text resources and rejects binary preview`() {
		val dir = Files.createTempDirectory("jadx-compose-test").toFile()
		try {
			val java = File(dir, "Example.java").apply {
				writeText("public class Example { private int count = 4; public String greet(String name) { return name + count; } }")
			}
			assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "11", java.path))
			val jar = File(dir, "example.jar")
			JarOutputStream(jar.outputStream()).use { output ->
				listOf("Example.class" to File(dir, "Example.class").readBytes(), "assets/info.txt" to "hello resource".toByteArray(), "assets/binary.bin" to byteArrayOf(0, 1, 2)).forEach { (name, bytes) ->
					output.putNextEntry(JarEntry(name)); output.write(bytes); output.closeEntry()
				}
			}
			DecompilerSession.open(jar).use { session ->
				val document = session.read(session.entries.first { it.kind == EntryKind.CLASS })
				assertTrue(document.code.contains("greet"))
				val method = document.symbols.first { it.label.startsWith("greet(") }
				assertTrue(document.code.substring(method.offset).startsWith("greet"))
				val resource = session.entries.first { it.path == "assets/info.txt" }
				assertEquals("hello resource", session.read(resource).code)
				val failure = assertFails { session.read(session.entries.first { it.path == "assets/binary.bin" }) }
				assertTrue(generateSequence(failure) { it.cause }.any { it.message?.contains("Binary resource") == true })
			}
			assertFailsWith<IllegalArgumentException> { DecompilerSession.open(File(dir, "missing.apk")) }
		} finally { dir.deleteRecursively() }
	}

}
