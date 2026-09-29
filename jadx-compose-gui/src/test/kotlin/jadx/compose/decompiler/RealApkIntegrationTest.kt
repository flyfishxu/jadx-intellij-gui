package jadx.compose.decompiler

import jadx.compose.fixtures.ReleaseFixture
import java.io.File
import java.security.MessageDigest
import kotlin.test.*
import org.junit.Assume.assumeTrue

/** Opt-in: private release artifacts stay outside the repository. */
class RealApkIntegrationTest {
	@Test
	fun `release apk mapping restores names and supports every search scope plus original smali`() {
		val apkPath = System.getProperty("testApk")
		val mappingPath = System.getProperty("testMapping")
		assumeTrue("Pass -PtestApk and -PtestMapping for the real release integration check", apkPath != null && mappingPath != null)
		val apk = File(apkPath!!)
		val mapping = File(mappingPath!!)
		val before = listOf(apk, mapping).map(::sha256)
		val fixture = ReleaseFixture(mapping)
		val rawId = fixture.rawId
		DecompilerSession.open(apk).use { original ->
			val entry = original.entries.first { it.id == rawId }
			assertFalse(entry.path.contains(fixture.simpleName))
			assertTrue(original.read(entry, CodeMode.SMALI).code.contains(fixture.descriptor))
		}
		val started = System.nanoTime()
		DecompilerSession.open(apk, mapping).use { session ->
			val entry = session.entries.first { it.id == rawId }
			assertEquals(fixture.className, entry.path)
			assertTrue(session.mappingMatches > 500)
			val java = session.read(entry)
			assertTrue(java.code.contains(fixture.simpleName))
			assertTrue(java.code.contains(fixture.field))
			val smali = session.read(entry, CodeMode.SMALI)
			assertTrue(smali.code.contains(".class"))
			assertTrue(smali.code.contains(fixture.descriptor))
			assertTrue(smali.symbols.any { it.kind == SymbolKind.METHOD })
			val queries = mapOf(
				SearchScope.CLASSES to fixture.simpleName,
				SearchScope.METHODS to fixture.method,
				SearchScope.FIELDS to fixture.field,
				SearchScope.JAVA to fixture.field,
				SearchScope.SMALI to fixture.descriptor,
				SearchScope.RESOURCES to "manifest",
			)
			for ((scope, query) in queries) {
				val filter = if (scope == SearchScope.RESOURCES) "AndroidManifest.xml" else entry.path
				val results = session.search(SearchRequest(query, scope, pathFilter = filter))
				assertTrue(results.hits.isNotEmpty(), "$scope did not find $query")
				if (scope == SearchScope.METHODS || scope == SearchScope.FIELDS) {
					val hit = results.hits.first()
					val symbol = java.symbols.first { it.key == hit.symbolKey }
					assertTrue(symbol.offset in 0 until java.code.length)
					assertTrue(java.code.substring(symbol.offset).startsWith(query))
				}
				println("$scope: ${results.hits.size} hits, ${results.scanned}/${results.total} scanned")
			}
			val capped = session.search(SearchRequest("public", SearchScope.JAVA, limit = 20))
			assertEquals(20, capped.hits.size)
			assertTrue(capped.limited)
			var stop = false
			val interrupted = session.search(SearchRequest("no_such_class_123", SearchScope.CLASSES), cancelled = { stop }) { if (it.scanned >= 16) stop = true }
			assertEquals(16, interrupted.scanned)
			val cancelled = session.search(SearchRequest("public", SearchScope.JAVA), cancelled = { true })
			assertEquals(0, cancelled.scanned)
			println("Release APK: ${session.entries.count { it.kind == EntryKind.CLASS }} top-level classes; ${session.mappingMatches} mapping matches; ${(System.nanoTime() - started) / 1_000_000} ms mapped checks")
		}
		assertEquals(before, listOf(apk, mapping).map(::sha256), "Input APK/mapping must remain unchanged")
	}

	private fun sha256(file: File): String {
		val digest = MessageDigest.getInstance("SHA-256")
		file.inputStream().buffered().use { stream ->
			val buffer = ByteArray(65536)
			while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
		}
		return digest.digest().joinToString("") { "%02x".format(it) }
	}
}
