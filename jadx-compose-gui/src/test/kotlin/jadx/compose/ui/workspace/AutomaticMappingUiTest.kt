@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package jadx.compose.ui.workspace

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import jadx.compose.decompiler.DecompilerSession
import jadx.compose.decompiler.EntryKind
import jadx.compose.preferences.AppPreferences
import jadx.compose.preferences.Language
import jadx.compose.ui.model.WorkspaceModel
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

class AutomaticMappingUiTest {
	@Test
	fun `opening apk discovers mapping and respects persisted settings explicit mapping and removal`() = runDesktopComposeUiTest(width = 1440, height = 900) {
		val directory = Files.createTempDirectory("jadx-auto-mapping-").toFile()
		val storage = Preferences.userRoot().node("jadx/compose-gui-test/${UUID.randomUUID()}")
		val prefs = AppPreferences(storage).apply { language(Language.ENGLISH) }
		val model = WorkspaceModel { prefs.autoImportMapping }
		try {
			val dex = File("../jadx-core/src/test/resources/test-samples/hello.dex")
			val (rawName, originalPath) = DecompilerSession.open(dex).use { session ->
				session.classes.values.first().rawName to session.entries.first { it.kind == EntryKind.CLASS }.path
			}
			val apk = File(directory, "sample.APK")
			ZipOutputStream(apk.outputStream()).use { output ->
				output.putNextEntry(ZipEntry("classes.dex"))
				dex.inputStream().use { it.copyTo(output) }
				output.closeEntry()
			}
			val mapping = File(directory, "mapping.txt")
			val checkbox = "Automatically import mapping.txt beside the APK"
			mapping.writeText("sample.Restored -> $rawName:\n")
			prefs.remember(apk)
			setContent { IntUiTheme(isDark = true) { Workspace(model, prefs, {}) } }
			// Open via the actual welcome-page history action, not just WorkspaceModel.open.
			onNodeWithText(apk.name).performClick()
			waitUntil(timeoutMillis = 30_000) { model.file == apk && !model.busy }
			assertEquals(mapping, model.mapping)
			assertTrue(model.entries.any { it.path == "sample.Restored" })
			assertNull(model.error)
			mapping.delete()
			runOnIdle { model.openSettings() }
			onNodeWithText(checkbox).assertIsOn()
			fun load(action: () -> Unit) {
				runOnIdle(action)
				waitUntil(timeoutMillis = 30_000) { !model.busy }
				assertEquals(apk, model.file)
			}
			fun assertMapped(name: String, file: File) {
				assertNull(model.error)
				assertEquals(file, model.mapping)
				assertTrue(model.mappingMatches > 0)
				assertTrue(model.entries.any { it.path == name })
				assertNull(model.error)
			}
			// Missing mapping is a normal open, without a warning.
			load { model.open(apk) }
			assertNull(model.mapping)
			assertNull(model.error)
			mapping.writeText("sample.Restored -> $rawName:\n")
			load { model.open(apk) }
			assertMapped("sample.Restored", mapping)
			// Removing a mapping must not trigger automatic discovery again.
			load { model.importMapping(null) }
			assertNull(model.mapping)
			assertTrue(model.entries.any { it.path == originalPath })
			onNodeWithText(checkbox).performClick().assertIsOff()
			assertFalse(AppPreferences(storage).autoImportMapping)
			load { model.open(apk) }
			assertNull(model.mapping)
			assertTrue(model.entries.any { it.path == originalPath })
			val explicit = File(directory, "explicit.txt").apply { writeText("sample.Explicit -> $rawName:\n") }
			load { model.openWithMapping(apk, explicit) }
			assertMapped("sample.Explicit", explicit)
			onNodeWithText(checkbox).performClick().assertIsOn()
			assertTrue(AppPreferences(storage).autoImportMapping)
			load { model.openWithMapping(apk, explicit) }
			assertMapped("sample.Explicit", explicit)
			load { model.open(apk) }
			assertMapped("sample.Restored", mapping)
			// Discovery is limited to APKs, even when a valid mapping is adjacent.
			val standaloneDex = dex.copyTo(File(directory, "sample.dex"))
			runOnIdle { model.open(standaloneDex) }
			waitUntil(timeoutMillis = 30_000) { !model.busy }
			assertEquals(standaloneDex, model.file)
			assertNull(model.mapping)
			mapping.writeText("sample.Unrelated -> missing.Class:\n")
			load { model.open(apk) }
			assertNull(model.mapping)
			assertTrue(model.entries.any { it.path == originalPath })
			assertTrue(model.error.orEmpty().contains("Opened APK without mapping"))
		} finally {
			model.close()
			storage.removeNode()
			directory.deleteRecursively()
		}
	}
}
