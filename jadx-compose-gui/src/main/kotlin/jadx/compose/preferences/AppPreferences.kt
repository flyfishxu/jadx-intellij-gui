package jadx.compose.preferences

import androidx.compose.runtime.*
import java.io.File
import java.util.Locale
import java.util.prefs.Preferences

internal enum class Appearance { SYSTEM, LIGHT, LIGHT_HEADER, DARK }
internal enum class Language { SYSTEM, ENGLISH, CHINESE }

internal class AppPreferences(private val storage: Preferences = Preferences.userRoot().node("jadx/compose-gui")) {
	var appearance by mutableStateOf(enumValue(storage.get("appearance", ""), Appearance.SYSTEM))
		private set
	var language by mutableStateOf(enumValue(storage.get("language", ""), Language.SYSTEM))
		private set
	var fontSize by mutableIntStateOf(storage.getInt("fontSize", 14).coerceIn(11, 24))
		private set
	var recent by mutableStateOf((0 until 8).mapNotNull { storage.get("recent.$it", null) }.distinct())
		private set
	val chinese get() = language == Language.CHINESE || (language == Language.SYSTEM && Locale.getDefault().language == "zh")
	fun text(en: String, zh: String) = if (chinese) zh else en
	fun appearance(value: Appearance) { appearance = value; storage.put("appearance", value.name) }
	fun language(value: Language) { language = value; storage.put("language", value.name) }
	fun fontSize(value: Int) { fontSize = value.coerceIn(11, 24); storage.putInt("fontSize", fontSize) }
	fun remember(file: File) {
		recent = (listOf(file.absolutePath) + recent).distinct().take(8)
		(0 until 8).forEach { if (it < recent.size) storage.put("recent.$it", recent[it]) else storage.remove("recent.$it") }
	}
	private inline fun <reified T : Enum<T>> enumValue(raw: String, fallback: T): T = enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}

internal fun appearanceLabel(mode: Appearance, prefs: AppPreferences) = when (mode) {
	Appearance.SYSTEM -> prefs.text("System", "跟随系统")
	Appearance.LIGHT -> prefs.text("Light", "浅色")
	Appearance.LIGHT_HEADER -> prefs.text("Light header", "浅色顶栏")
	Appearance.DARK -> prefs.text("Dark", "深色")
}
