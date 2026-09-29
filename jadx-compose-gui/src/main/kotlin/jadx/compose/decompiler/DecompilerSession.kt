package jadx.compose.decompiler

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.JavaClass
import jadx.api.ResourceFile
import jadx.api.ResourcesLoader
import jadx.api.args.UserRenamesMappingsMode
import jadx.core.xmlgen.ResContainer
import jadx.plugins.mappings.RenameMappingsData
import java.io.Closeable
import java.io.File

/** The only adapter to jadx. All calls, including close, run on the session worker. */
internal class DecompilerSession private constructor(
	private val jadx: JadxDecompiler,
	private val preparedMapping: R8MappingFile?
) : Closeable {
	internal val classes = jadx.classes.associateBy { "class:${it.rawName}" }
	internal val resources =
		jadx.resources.withIndex().associate { "resource:${it.index}" to it.value }
	val entries: List<ProjectEntry> = classes.map { (id, cls) ->
		ProjectEntry(id, cls.name + ".java", cls.fullName, cls.`package`, EntryKind.CLASS)
	}.sortedBy { it.path } + resources.map { (id, resource) ->
		val path = resource.deobfName.let { if (File(it).isAbsolute) File(it).name else it }
		ProjectEntry(
			id,
			path.substringAfterLast('/'),
			path,
			path.substringBeforeLast('/', ""),
			EntryKind.RESOURCE
		)
	}.sortedBy { it.path }

	val allClasses: List<JavaClass> by lazy { jadx.classesWithInners }
	val mappingMatches: Int
		get() {
			val tree = RenameMappingsData.getTree(jadx.root) ?: return 0
			return allClasses.count { tree.getClass(it.rawName.replace('.', '/')) != null }
		}

	internal fun nodeAt(document: SourceDocument, offset: Int): jadx.api.JavaNode? {
		if (document.mode != CodeMode.JAVA) return null
		val cls = classes[document.entry.id] ?: return null
		val reference = document.references.atCaret(offset) ?: return null
		return jadx.getJavaNodeAtPosition(cls.codeInfo, reference.start)
	}

	fun definition(document: SourceDocument, offset: Int): Declaration? {
		val node = nodeAt(document, offset) ?: return null
		val parent = node.topParentClass ?: return null
		val target = entries.firstOrNull { it.id == "class:${parent.rawName}" } ?: return null
		val targetDocument = read(target)
		val position = node.defPos
		if (position !in targetDocument.code.indices || position == 0) return null
		return Declaration(targetDocument, position)
	}

	fun read(entry: ProjectEntry, mode: CodeMode = CodeMode.JAVA): SourceDocument {
		val cls = classes[entry.id]
		if (cls != null) {
			if (mode == CodeMode.SMALI) {
				val code = cls.smali
				return SourceDocument(entry, code, "Smali", smaliSymbols(code), mode = mode)
			}
			val info = cls.codeInfo
			val code = info.codeStr
			val symbols = buildList {
				fun collect(current: JavaClass) {
					add(
						Symbol(
							current.name,
							current.defPos,
							SymbolKind.CLASS,
							"c:${current.rawName}"
						)
					)
					current.fields.forEach {
						add(
							Symbol(
								"${it.name}: ${it.type}",
								it.defPos,
								SymbolKind.FIELD,
								"f:${current.rawName}:${it.rawName}"
							)
						)
					}
					current.methods.forEach {
						add(
							Symbol(
								(if (it.isConstructor) current.name else it.name) + "(" + it.arguments.joinToString(
									", "
								) + ")",
								it.defPos,
								SymbolKind.METHOD,
								"m:${current.rawName}:${it.methodNode.methodInfo.shortId}"
							)
						)
					}
					current.innerClasses.forEach(::collect)
				}
				collect(cls)
			}
			return SourceDocument(
				entry,
				code,
				"Java",
				symbols,
				jadx.errorsCount,
				jadx.warnsCount,
				references = codeReferences(jadx, info)
			)
		}
		val resource = requireNotNull(resources[entry.id]) { "Resource no longer available" }
		return SourceDocument(
			entry,
			readResource(resource),
			resource.type.name,
			emptyList(),
			jadx.errorsCount,
			jadx.warnsCount
		)
	}

	internal fun readResource(resource: ResourceFile): String {
		val content = resource.loadContent()
		return when (content.dataType) {
			ResContainer.DataType.TEXT -> content.text.codeStr
			ResContainer.DataType.RES_TABLE -> buildString {
				append(content.text.codeStr)
				content.subFiles.forEach { append("\n\n// ${it.name}\n"); append(it.text.codeStr) }
			}

			else -> ResourcesLoader.decodeStream(resource) { _, stream ->
				val bytes = stream.readNBytes(MAX_RESOURCE_BYTES + 1)
				require(bytes.size <= MAX_RESOURCE_BYTES) { "Resource exceeds the 2 MiB text preview limit" }
				require(bytes.none { it == 0.toByte() }) { "Binary resource: text preview is unavailable" }
				bytes.toString(Charsets.UTF_8)
			}
		}
	}

	override fun close() {
		try {
			jadx.close()
		} finally {
			preparedMapping?.close()
		}
	}

	companion object {
		private const val MAX_RESOURCE_BYTES = 2 * 1024 * 1024

		fun open(file: File, mapping: File? = null): DecompilerSession {
			mapping?.let { require(it.isFile && it.canRead()) { "Mapping file is not readable: ${it.absolutePath}" } }
			require(file.isFile) { "File does not exist: ${file.absolutePath}" }
			val preparedMapping = mapping?.let(R8MappingFile::prepare)
			val jadx = JadxDecompiler(JadxArgs().apply {
				setInputFile(file)
				isShowInconsistentCode = true
				if (mapping != null) {
					userRenamesMappingsPath = requireNotNull(preparedMapping).file.toPath()
					userRenamesMappingsMode = UserRenamesMappingsMode.READ
					pluginOptions = mapOf(
						"rename-mappings.format" to "PROGUARD_FILE",
						"rename-mappings.invert" to "yes"
					)
				}
			})
			try {
				jadx.load()
				if (mapping != null) check(RenameMappingsData.getTree(jadx.root) != null) { "Failed to load the R8 / ProGuard mapping" }
				val session = DecompilerSession(jadx, preparedMapping)
				if (mapping != null) require(session.mappingMatches > 0) { "Mapping does not match any class in this input" }
				require(session.entries.isNotEmpty()) { "No classes or resources found in ${file.name}" }
				return session
			} catch (error: Throwable) {
				try {
					jadx.close()
				} finally {
					preparedMapping?.close()
				}
				throw error
			}
		}
	}
}

internal fun smaliSymbols(code: String): List<Symbol> =
	Regex("(?m)^\\.(class|method|field)\\s+([^\\n]+)").findAll(code).map {
		Symbol(
			it.groupValues[2], it.range.first, when (it.groupValues[1]) {
				"class" -> SymbolKind.CLASS
				"field" -> SymbolKind.FIELD
				else -> SymbolKind.METHOD
			}
		)
	}.toList()
