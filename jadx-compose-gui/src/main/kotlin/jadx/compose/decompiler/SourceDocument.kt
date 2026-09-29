package jadx.compose.decompiler

internal enum class CodeMode { JAVA, SMALI }
internal enum class EntryKind { CLASS, RESOURCE }
internal enum class SymbolKind { CLASS, FIELD, METHOD }

internal data class Declaration(val document: SourceDocument, val offset: Int)

internal data class ProjectEntry(
	val id: String,
	val name: String,
	val path: String,
	val group: String,
	val kind: EntryKind,
)

internal data class Symbol(
	val label: String,
	val offset: Int,
	val kind: SymbolKind,
	val key: String = label,
)

internal data class SourceDocument(
	val entry: ProjectEntry,
	val code: String,
	val language: String,
	val symbols: List<Symbol>,
	val errors: Int = 0,
	val warnings: Int = 0,
	val mode: CodeMode = CodeMode.JAVA,
	val references: CodeReferences = CodeReferences(),
) {
	val viewKey get() = "${entry.id}:${mode.name}"
}
