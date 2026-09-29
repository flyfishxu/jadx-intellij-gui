package jadx.compose.decompiler

import java.io.Closeable
import java.io.File
import java.nio.file.Files

/**
 * mapping-io's ProGuard reader skips methods with original-line suffixes as inline frames.
 * R8 also emits those suffixes on ordinary methods. Feed jadx a temporary, position-free
 * mapping: for a shared obfuscated line range the last record is the outermost frame.
 * Qualified foreign frames are intentionally not guessed into declarations of this class.
 * This is a rename mapping, not a replacement for R8 Retrace or its line-number metadata.
 */
internal class R8MappingFile private constructor(val file: File) : Closeable {
	override fun close() { Files.deleteIfExists(file.toPath()) }

	companion object {
		private val methodLine = Regex("^\\s+(?:(\\d+):(\\d+):)?(\\S+)\\s+([^\\s(]+)\\(([^)]*)\\)(?::\\d+(?::\\d+)?)?\\s+->\\s+(\\S+)\\s*$")

		fun prepare(source: File): R8MappingFile {
			val target = Files.createTempFile("jadx-compose-mapping-", ".txt").toFile()
			try {
				target.bufferedWriter().use { out ->
					var group: String? = null
					var candidate: String? = null
					val emitted = hashSetOf<String>()
					fun emit(line: String) { if (emitted.add(line)) { out.write(line); out.newLine() } }
					fun flush() { candidate?.let(::emit); candidate = null; group = null }
					source.bufferedReader().useLines { lines ->
						lines.forEach { line ->
							if (line.isBlank() || line.trimStart().startsWith('#')) return@forEach
							val match = methodLine.matchEntire(line)
							if (match == null) {
								flush()
								if (!line.first().isWhitespace()) emitted.clear()
								emit(line)
							} else {
								val (start, end, type, name, arguments, obfuscated) = match.destructured
								val normalized = if ('.' in name) null else "    $type $name($arguments) -> $obfuscated"
								if (start.isEmpty()) { flush(); normalized?.let(::emit) }
								else {
									val key = "$obfuscated:$start:$end"
									if (key != group) flush()
									group = key
									candidate = normalized
								}
							}
						}
					}
					flush()
				}
				return R8MappingFile(target)
			} catch (failure: Throwable) { target.delete(); throw failure }
		}
	}
}
