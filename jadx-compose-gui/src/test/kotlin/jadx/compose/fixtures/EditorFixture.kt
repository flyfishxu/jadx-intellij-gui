package jadx.compose.fixtures

import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.assertEquals

internal class EditorFixture : Closeable {
	private val directory = Files.createTempDirectory("jadx-editor-").toFile()
	val jar = File(directory, "editor.jar")
	val mapping = File(directory, "mapping.txt")
	init {
		File(directory, "a.java").writeText("""
			public class a {
			    public int f = 3;
			    public int m(int value) { return this.f + value + value; }
			    public String m(String value) { return value.trim(); }
			}
		""".trimIndent())
		File(directory, "b.java").writeText("""
			public class b {
			    public int run(int value) {
			        a target = new a();
			        target.f = value;
			        return target.m(value) + target.f;
			    }
			    public int other(int value) { return value + 1; }
			    public String text() { return "value (ignored)"; }
			    public String overload() { return new a().m("hello"); }
			}
		""".trimIndent())
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-g", "--release", "11", File(directory, "a.java").path, File(directory, "b.java").path))
		JarOutputStream(jar.outputStream()).use { out ->
			listOf("a", "b").forEach { name -> out.putNextEntry(JarEntry("$name.class")); out.write(File(directory, "$name.class").readBytes()); out.closeEntry() }
		}
		mapping.writeText("sample.Target -> a:\n    int count -> f\n    int compute(int) -> m\n    java.lang.String compute(java.lang.String) -> m\nsample.Caller -> b:\n")
	}
	override fun close() { directory.deleteRecursively() }
}
