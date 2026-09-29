package jadx.compose.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import jadx.compose.decompiler.CodeReference
import jadx.compose.decompiler.ReferenceKind

internal data class EditorFindOptions(
	val matchCase: Boolean = false,
	val wholeWord: Boolean = false,
	val regex: Boolean = false
)

internal data class EditorFindResult(
	val matches: List<TextRange> = emptyList(),
	val error: String? = null,
	val limited: Boolean = false
)

internal fun editorFind(code: String, query: String, options: EditorFindOptions): EditorFindResult {
	if (query.isEmpty()) return EditorFindResult()
	return try {
		val pattern = Regex(
			if (options.regex) query else Regex.escape(query),
			if (options.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
		)
		val matches = pattern.findAll(code).filter { match ->
			match.value.isNotEmpty() && (!options.wholeWord ||
					(code.getOrNull(match.range.first - 1)
						?.let(Character::isJavaIdentifierPart) != true &&
							code.getOrNull(match.range.last + 1)
								?.let(Character::isJavaIdentifierPart) != true))
		}.take(10_001).map { TextRange(it.range.first, it.range.last + 1) }.toList()
		EditorFindResult(matches.take(10_000), limited = matches.size > 10_000)
	} catch (failure: IllegalArgumentException) {
		EditorFindResult(error = failure.message)
	}
}

internal fun decorateCode(
	syntax: AnnotatedString, references: List<CodeReference>, occurrences: List<CodeReference>,
	matches: List<TextRange>, brackets: List<Int>, dark: Boolean
): AnnotatedString = AnnotatedString.Builder(syntax).apply {
	for (reference in references) {
		val color = when (reference.kind) {
			ReferenceKind.METHOD -> if (dark) Color(0xFF56A8F5) else Color(0xFF00627A)
			ReferenceKind.FIELD -> if (dark) Color(0xFFC77DBB) else Color(0xFF871094)
			else -> null
		}
		if (color != null) addStyle(SpanStyle(color = color), reference.start, reference.end)
	}
	occurrences.forEach {
		addStyle(
			SpanStyle(background = occurrenceColor(dark)),
			it.start,
			it.end
		)
	}
	matches.forEach {
		addStyle(
			SpanStyle(
				background = if (dark) Color(0xFF5B4D2B) else Color(
					0xFFFFE7A8
				)
			), it.min, it.max
		)
	}
	brackets.forEach {
		addStyle(
			SpanStyle(
				background = if (dark) Color(0xFF3B514D) else Color(
					0xFFD5E8D4
				)
			), it, it + 1
		)
	}
}.toAnnotatedString()

internal fun occurrenceColor(dark: Boolean) = if (dark) Color(0xFF344764) else Color(0xFFDCE8F7)

/** Build once, ignoring bracket characters inside comments and string/character literals. */
internal fun bracketPairs(code: String): Map<Int, Int> {
	val ignored =
		Regex("""//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""").findAll(code)
			.iterator()
	var skip = if (ignored.hasNext()) ignored.next().range else null
	val stack = ArrayDeque<Int>()
	val pairs = mutableMapOf<Int, Int>()
	for (i in code.indices) {
		while (skip != null && i > skip.last) skip =
			if (ignored.hasNext()) ignored.next().range else null
		if (skip != null && i in skip) continue
		val c = code[i]
		if (c in "({[") stack.addLast(i)
		else if (c in ")}]" && stack.isNotEmpty()) {
			val start = stack.last()
			if ("({[".indexOf(code[start]) == ")}]".indexOf(c)) {
				stack.removeLast(); pairs[start] = i; pairs[i] = start
			}
		}
	}
	return pairs
}
