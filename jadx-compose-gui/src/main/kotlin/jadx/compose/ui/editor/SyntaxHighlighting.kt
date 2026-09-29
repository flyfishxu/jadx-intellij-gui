package jadx.compose.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import jadx.compose.decompiler.CodeMode

/** Lexical coloring only; jadx owns decompilation and source positions. */
internal fun highlight(
	code: String,
	dark: Boolean,
	mode: CodeMode = CodeMode.JAVA
): AnnotatedString {
	val keyword = if (dark) Color(0xFFCF8E6D) else Color(0xFF0033B3)
	val string = if (dark) Color(0xFF6AAB73) else Color(0xFF067D17)
	val comment = if (dark) Color(0xFF7A7E85) else Color(0xFF8C8C8C)
	val number = if (dark) Color(0xFF2AACB8) else Color(0xFF1750EB)
	val annotation = if (dark) Color(0xFFB3AE60) else Color(0xFF9E880D)
	return buildAnnotatedString {
		append(code)
		(if (mode == CodeMode.SMALI) smaliTokens else tokens).findAll(code).forEach { match ->
			val token = match.value
			val color = when {
				token.startsWith("#") || token.startsWith("//") || token.startsWith("/*") || token.startsWith(
					"<!--"
				) -> comment

				token.startsWith('"') || token.startsWith('\'') -> string
				token.startsWith('@') -> annotation
				token.first().isDigit() -> number
				else -> keyword
			}
			addStyle(SpanStyle(color), match.range.first, match.range.last + 1)
		}
	}
}

private val tokens = Regex(
	"""//[^\n]*|/\*[\s\S]*?\*/|<!--[\s\S]*?-->|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|@[\w.]+|\b(?:0x[\da-fA-F]+|\d+(?:\.\d+)?[fFdDlL]?)\b|\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false|null|record|sealed|permits|var|yield)\b"""
)

private val smaliTokens = Regex(
	"""\#[^\n]*|"(?:\\.|[^"\\])*"|\.[a-zA-Z][\w-]*|\b(?:[vp]\d+|0x[\da-fA-F]+|\d+)\b|\b(?:public|private|protected|static|final|abstract|synthetic|constructor|return[\w-]*|invoke[\w/-]*|const[\w/-]*|move[\w/-]*|if[\w-]*|goto[\w/-]*|new[\w-]*|iget[\w-]*|iput[\w-]*|sget[\w-]*|sput[\w-]*)\b"""
)
