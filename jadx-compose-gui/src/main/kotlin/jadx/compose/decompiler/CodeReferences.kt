package jadx.compose.decompiler

import jadx.api.ICodeInfo
import jadx.api.JadxDecompiler
import jadx.api.JavaClass
import jadx.api.JavaField
import jadx.api.JavaMethod
import jadx.api.JavaNode
import jadx.api.JavaVariable

internal enum class ReferenceKind { CLASS, METHOD, FIELD, VARIABLE }
internal data class CodeReference(val start: Int, val end: Int, val symbol: Int, val kind: ReferenceKind, val name: String)

/** Immutable, document-local semantic index. No live jadx nodes cross into Compose. */
internal class CodeReferences(val references: List<CodeReference> = emptyList()) {
	private val bySymbol = references.groupBy { it.symbol }

	fun at(offset: Int): CodeReference? {
		val found = references.binarySearchBy(offset) { it.start }
		return references.getOrNull(if (found >= 0) found else -found - 2)?.takeIf { offset < it.end }
	}

	fun atCaret(offset: Int): CodeReference? = at(offset) ?: at(offset - 1)?.takeIf { it.end == offset }

	fun occurrences(reference: CodeReference): List<CodeReference> = bySymbol[reference.symbol].orEmpty()
}

internal fun codeReferences(jadx: JadxDecompiler, info: ICodeInfo): CodeReferences {
	val code = info.codeStr
	val symbols = mutableMapOf<JavaNode, Int>()
	val references = info.codeMetadata.asMap.entries.sortedBy { it.key }.mapNotNull { (offset, annotation) ->
		if (offset !in code.indices || !Character.isJavaIdentifierStart(code[offset])) return@mapNotNull null
		val node = jadx.getJavaNodeByCodeAnnotation(info, annotation) ?: return@mapNotNull null
		val kind = when (node) {
			is JavaClass -> ReferenceKind.CLASS
			is JavaMethod -> ReferenceKind.METHOD
			is JavaField -> ReferenceKind.FIELD
			is JavaVariable -> ReferenceKind.VARIABLE
			else -> return@mapNotNull null
		}
		var end = offset + 1
		while (end < code.length && Character.isJavaIdentifierPart(code[end])) end++
		val name = when (node) {
			is JavaMethod -> "${node.declaringClass.fullName}.${node.name}(${node.arguments.joinToString(", ")})"
			is JavaField -> "${node.declaringClass.fullName}.${node.name}"
			is JavaVariable -> "${node.declaringClass.fullName}.${node.mth.name} : ${node.name}"
			else -> node.fullName
		}
		CodeReference(offset, end, symbols.getOrPut(node) { symbols.size }, kind, name)
	}
	return CodeReferences(references)
}
