package jadx.compose.decompiler

internal enum class SearchScope { CLASSES, METHODS, FIELDS, JAVA, SMALI, RESOURCES }
internal data class SearchRequest(
	val query: String,
	val scope: SearchScope = SearchScope.CLASSES,
	val matchCase: Boolean = false,
	val regex: Boolean = false,
	val pathFilter: String = "",
	val limit: Int = 500,
) {
	fun pattern(): Regex = Regex(
		if (regex) query else Regex.escape(query),
		if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
	)
}

internal data class SearchHit(
	val entry: ProjectEntry,
	val title: String,
	val detail: String,
	val mode: CodeMode = CodeMode.JAVA,
	val offset: Int = 0,
	val symbolKey: String? = null,
)

internal data class SearchProgress(
	val scanned: Int = 0,
	val total: Int = 0,
	val hits: List<SearchHit> = emptyList(),
	val skipped: Int = 0,
	val limited: Boolean = false
)

/** No UI dependencies. Called on the session worker, with cooperative cancellation between classes. */
internal fun DecompilerSession.search(
	request: SearchRequest,
	cancelled: () -> Boolean = { false },
	progress: (SearchProgress) -> Unit = {}
): SearchProgress {
	if (request.query.isBlank()) return SearchProgress()
	val pattern = request.pattern()
	val hits = mutableListOf<SearchHit>()
	var scanned = 0
	var skipped = 0
	var limited = false
	val byId = entries.associateBy { it.id }
	val candidates = when (request.scope) {
		SearchScope.CLASSES, SearchScope.METHODS, SearchScope.FIELDS -> allClasses.mapNotNull { cls ->
			byId["class:${cls.topParentClass.rawName}"]?.let { it to cls }
		}.filter { (entry, cls) ->
			entry.path.contains(
				request.pathFilter,
				true
			) || cls.fullName.contains(request.pathFilter, true)
		}

		else -> entries.filter {
			(it.kind == EntryKind.RESOURCE) == (request.scope == SearchScope.RESOURCES) && it.path.contains(
				request.pathFilter,
				true
			)
		}.map { it to classes[it.id] }
	}

	fun snapshot() = SearchProgress(scanned, candidates.size, hits.toList(), skipped, limited)
	fun add(hit: SearchHit) {
		if (hits.size < request.limit) hits.add(hit) else limited = true
	}
	progress(snapshot())
	for ((entry, cls) in candidates) {
		if (cancelled() || limited) break
		try {
			when (request.scope) {
				SearchScope.CLASSES -> if (pattern.containsMatchIn(cls!!.fullName) || pattern.containsMatchIn(
						cls.rawName
					)
				) {
					add(SearchHit(entry, cls.name, cls.fullName, symbolKey = "c:${cls.rawName}"))
				}

				SearchScope.METHODS -> cls!!.classNode.methods.forEach { method ->
					val signature = "${cls.fullName}.${method.alias}${
						method.methodInfo.shortId.substringAfter(method.name)
					}"
					if (pattern.containsMatchIn(signature) || pattern.containsMatchIn(method.methodInfo.shortId)) {
						add(
							SearchHit(
								entry,
								method.alias + "(…)",
								signature,
								symbolKey = "m:${cls.rawName}:${method.methodInfo.shortId}"
							)
						)
					}
				}

				SearchScope.FIELDS -> cls!!.classNode.fields.forEach { field ->
					val signature = "${cls.fullName}.${field.alias}: ${field.type}"
					if (pattern.containsMatchIn(signature) || pattern.containsMatchIn(field.name)) {
						add(
							SearchHit(
								entry,
								field.alias,
								signature,
								symbolKey = "f:${cls.rawName}:${field.name}"
							)
						)
					}
				}

				else -> {
					val mode =
						if (request.scope == SearchScope.SMALI) CodeMode.SMALI else CodeMode.JAVA
					val code = if (request.scope == SearchScope.RESOURCES) {
						if (pattern.containsMatchIn(entry.path)) add(
							SearchHit(
								entry,
								entry.name,
								entry.path
							)
						)
						val resource = resources.getValue(entry.id)
						if (resource.type.name in setOf("IMG", "FONT", "CODE")) {
							scanned++; continue
						}
						readResource(resource)
					} else if (mode == CodeMode.SMALI) cls!!.smali else cls!!.code
					for (match in pattern.findAll(code)) {
						if (cancelled() || limited) break
						val start = code.lastIndexOf('\n', (match.range.first - 1).coerceAtLeast(0))
							.let { if (it < 0) 0 else it + 1 }
						val end = code.indexOf('\n', match.range.first)
							.let { if (it < 0) code.length else it }
						val line = code.substring(0, match.range.first).count { it == '\n' } + 1
						add(
							SearchHit(
								entry,
								"${entry.name}:$line",
								code.substring(start, end).trim().take(240),
								mode,
								match.range.first
							)
						)
					}
				}
			}
		} catch (failure: Exception) {
			skipped++
		}
		scanned++
		if (scanned % 16 == 0) progress(snapshot())
	}
	return snapshot().also(progress)
}
