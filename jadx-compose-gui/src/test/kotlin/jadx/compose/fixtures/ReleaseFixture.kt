package jadx.compose.fixtures

import java.io.File

/** Private artifacts are supplied by the caller and never copied into the repository. */
internal class ReleaseFixture(mapping: File) {
	private val isWearToolbox = System.getProperty("testFixture") == "wear-toolbox"
	val className = if (isWearToolbox) "com.flyfishstudio.adbcore.AdbCoreInitializer" else "com.flyfishstudio.onionstore.adb.AdbInstaller"
	val simpleName = className.substringAfterLast('.')
	val method = if (isWearToolbox) "setDelayedAckBetaEnabled" else "ensureIdentity"
	val field = if (isWearToolbox) "_applicationContext" else "identityReady"
	val rawName = mapping.useLines { lines -> lines.first { it.startsWith("$className -> ") }.substringAfter(" -> ").removeSuffix(":") }
	val rawId = "class:$rawName"
	val descriptor = "L${rawName.replace('.', '/')};"
}
