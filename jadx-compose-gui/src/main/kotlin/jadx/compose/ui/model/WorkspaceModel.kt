package jadx.compose.ui.model

import androidx.compose.runtime.*
import jadx.compose.decompiler.CodeMode
import jadx.compose.decompiler.DecompilerSession
import jadx.compose.decompiler.ProjectEntry
import jadx.compose.decompiler.SearchHit
import jadx.compose.decompiler.SearchProgress
import jadx.compose.decompiler.SearchRequest
import jadx.compose.decompiler.SourceDocument
import jadx.compose.decompiler.search
import jadx.compose.decompiler.usages
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

internal class WorkspaceModel(private val autoImportMapping: () -> Boolean = { true }) : Closeable {
	private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "jadx-session").apply { isDaemon = true } }
	private val worker = executor.asCoroutineDispatcher()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private var session: DecompilerSession? = null // worker confined
	private var requested: OpenRequest? = null
	private var searchCancellation = AtomicBoolean(false)
	private var searchJob: Job? = null
	private var searchGeneration = 0
	private var usageCancellation = AtomicBoolean(false)
	private var usageJob: Job? = null
	private var usageGeneration = 0
	private val caretPositions = mutableMapOf<String, Int>()
	val history = NavigationHistory()
	var editorFocusRequest by mutableIntStateOf(0)
		private set
	var usagesVisible by mutableStateOf(false)
		private set
	var usagesBusy by mutableStateOf(false)
		private set
	var usagesStopped by mutableStateOf(false)
		private set
	var usagesTitle by mutableStateOf("")
		private set
	var usagesProgress by mutableStateOf(SearchProgress())
		private set
	var usagesError by mutableStateOf<String?>(null)
		private set
	var file by mutableStateOf<File?>(null)
		private set
	var mapping by mutableStateOf<File?>(null)
		private set
	var mappingMatches by mutableIntStateOf(0)
		private set
	var projectVersion by mutableIntStateOf(0)
		private set
	var entries by mutableStateOf(emptyList<ProjectEntry>())
		private set
	val tabs = mutableStateListOf<SourceDocument>()
	var settingsOpen by mutableStateOf(false)
		private set
	var settingsSelected by mutableStateOf(false)
		private set
	var selectedId by mutableStateOf<String?>(null)
		private set
	var busy by mutableStateOf(false)
		private set
	var searchBusy by mutableStateOf(false)
		private set
	var lastSearchRequest by mutableStateOf(SearchRequest(""))
		private set
	var searchProgress by mutableStateOf(SearchProgress())
		private set
	var searchError by mutableStateOf<String?>(null)
		private set
	var error by mutableStateOf<String?>(null)
	var caret by mutableStateOf("1:1")
	var caretOffset by mutableIntStateOf(0)
	var jump by mutableStateOf<NavigationTarget?>(null)
	val selected get() = if (settingsSelected) null else tabs.toList().firstOrNull { it.entry.id == selectedId }

	fun openSettings() {
		requested = null
		settingsOpen = true
		settingsSelected = true
	}

	fun closeSettings() {
		settingsOpen = false
		settingsSelected = false
	}

	fun closeActiveTab() {
		if (settingsSelected) closeSettings() else selectedId?.let(::closeTab)
	}

	fun open(input: File, onSuccess: (File) -> Unit = {}) = reload(input, null, false, onSuccess, discoverMapping = autoImportMapping())
	fun openWithMapping(input: File, mapping: File, onSuccess: (File) -> Unit = {}) = reload(input, mapping, false, onSuccess)
	fun importMapping(mapping: File?) { file?.let { reload(it, mapping, true) } }

	private fun reload(input: File, mappingFile: File?, preserveTabs: Boolean, onSuccess: (File) -> Unit = {}, discoverMapping: Boolean = false) {
		if (busy) return
		cancelSearch()
		cancelUsages()
		busy = true
		error = null
		requested = null
		val oldTabs = if (preserveTabs) tabs.toList() else emptyList()
		val oldSelection = selectedId
		scope.launch {
			try {
				val loaded = withContext(worker) {
					var selectedMapping = mappingFile ?: if (discoverMapping && input.extension.equals("apk", ignoreCase = true)) {
						File(input.absoluteFile.parentFile, "mapping.txt").takeIf { it.isFile }
					} else null
					var mappingError: String? = null
					val next = try {
						DecompilerSession.open(input, selectedMapping)
					} catch (cancel: CancellationException) { throw cancel }
					catch (failure: Exception) {
						// An unrelated/broken adjacent mapping must not prevent opening the APK.
						if (!discoverMapping || selectedMapping == null) throw failure
						mappingError = "Could not automatically import ${selectedMapping.absolutePath}: ${failure.message}. Opened APK without mapping."
						selectedMapping = null
						DecompilerSession.open(input)
					}
					try {
						val byId = next.entries.associateBy { it.id }
						val restored = oldTabs.mapNotNull { doc -> byId[doc.entry.id]?.let { next.read(it, doc.mode) } }
						val result = LoadedProject(next.entries, restored, next.mappingMatches, selectedMapping, mappingError)
						session?.close()
						session = next
						result
					} catch (failure: Throwable) { next.close(); throw failure }
				}
				entries = loaded.entries
				requested = null
				tabs.clear()
				tabs.addAll(loaded.tabs)
				selectedId = tabs.firstOrNull { it.entry.id == oldSelection }?.entry?.id ?: tabs.firstOrNull()?.entry?.id
				file = input
				mapping = loaded.mapping
				mappingMatches = loaded.mappingMatches
				error = loaded.mappingError
				history.clear()
				caretPositions.clear()
				usagesVisible = false
				usagesProgress = SearchProgress()
				projectVersion++
				jump = null
				searchProgress = SearchProgress()
				onSuccess(input)
			} catch (cancel: CancellationException) {
				throw cancel
			} catch (failure: Exception) {
				error = failure.message ?: failure.javaClass.simpleName
			} finally { busy = false }
		}
	}

	fun show(entry: ProjectEntry) = openDocument(OpenRequest(entry, tabs.firstOrNull { it.entry.id == entry.id }?.mode ?: CodeMode.JAVA, origin = currentLocation()))
	fun showHit(hit: SearchHit) = openDocument(OpenRequest(hit.entry, hit.mode, hit, origin = currentLocation()))
	fun setMode(mode: CodeMode) { selected?.let { openDocument(OpenRequest(it.entry, mode)) } }
	fun goToDefinition(document: SourceDocument, offset: Int) =
		openDocument(OpenRequest(document.entry, document.mode, definition = document to offset, origin = CodeLocation(document.entry, document.mode, offset)))

	suspend fun canNavigate(document: SourceDocument, offset: Int): Boolean {
		if (selected != document) return false
		val version = projectVersion
		return try {
			val available = withContext(worker) { session?.definition(document, offset) != null }
			available && version == projectVersion && selected == document
		} catch (cancel: CancellationException) { throw cancel }
		catch (_: Exception) { false }
	}

	private fun openDocument(request: OpenRequest) {
		requested = request
		val existing = tabs.firstOrNull { it.entry.id == request.entry.id && it.mode == request.mode }
		if (existing != null && request.definition == null) { selectDocument(existing, request); return }
		if (busy) return
		cancelSearch()
		cancelUsages()
		busy = true
		error = null
		scope.launch {
			try {
				val result = withContext(worker) {
					val activeSession = requireNotNull(session)
					if (request.definition != null) {
						activeSession.definition(request.definition.first, request.definition.second)
					} else jadx.compose.decompiler.Declaration(activeSession.read(request.entry, request.mode), -1)
				}
				if (result == null) {
					if (requested == request) error = "No declaration is available in this input for the selected symbol."
					return@launch
				}
				val document = result.document
				val index = tabs.indexOfFirst { it.entry.id == document.entry.id }
				if (index < 0) tabs.add(document) else tabs[index] = document
				if (requested == request) {
					selectDocument(document, request, result.offset.takeIf { it >= 0 })
				}
			} catch (cancel: CancellationException) {
				throw cancel
			} catch (failure: Exception) {
				error = "${request.entry.name}: ${failure.message ?: failure.javaClass.simpleName}"
			} finally {
				busy = false
				if (scope.isActive) requested?.takeIf { it != request }?.let(::openDocument)
			}
		}
	}

	private fun selectDocument(document: SourceDocument, request: OpenRequest, definitionOffset: Int? = null) {
		val hit = request.hit
		val offset = definitionOffset ?: request.location?.offset ?: hit?.let {
			if (it.symbolKey == null) it.offset else document.symbols.firstOrNull { symbol -> symbol.key == it.symbolKey }?.offset
		}
		val destination = offset ?: caretPositions[document.viewKey] ?: 0
		if (request.recordHistory) history.record(request.origin, CodeLocation(document.entry, document.mode, destination))
		settingsSelected = false
		selectedId = document.entry.id
		caretOffset = destination
		if (hit != null && offset == null) error = "The symbol has no standalone declaration in the decompiled source (it may have been inlined)."
		if (offset != null) applyJump(offset)
	}

	fun updateCaret(document: SourceDocument, label: String, offset: Int) {
		caretPositions[document.viewKey] = offset
		if (selected?.viewKey == document.viewKey) { caret = label; caretOffset = offset }
	}

	private fun currentLocation() = selected?.let { CodeLocation(it.entry, it.mode, caretOffset) }
	fun goBack() { if (!busy) history.back(currentLocation())?.let(::restoreLocation) }
	fun goForward() { if (!busy) history.forward(currentLocation())?.let(::restoreLocation) }
	private fun restoreLocation(location: CodeLocation) = openDocument(OpenRequest(location.entry, location.mode, location = location, recordHistory = false))

	fun findUsages(document: SourceDocument, offset: Int) {
		if (busy || selected != document) return
		val reference = document.references.atCaret(offset) ?: return
		cancelSearch()
		cancelUsages()
		usagesVisible = true
		usagesBusy = true
		usagesStopped = false
		usagesTitle = reference.name
		usagesError = null
		usagesProgress = SearchProgress()
		val generation = usageGeneration
		val cancellation = AtomicBoolean(false).also { usageCancellation = it }
		usageJob = scope.launch {
			try {
				withContext(worker) {
					requireNotNull(session).usages(document, reference.start, cancellation::get) { update ->
						scope.launch { if (generation == usageGeneration) usagesProgress = update }
					}
				}
			} catch (cancel: CancellationException) { throw cancel }
			catch (failure: Exception) { if (generation == usageGeneration) usagesError = failure.message }
			finally { if (generation == usageGeneration) usagesBusy = false }
		}
	}

	fun cancelUsages() {
		if (usagesBusy) usagesStopped = true
		usageCancellation.set(true)
		usageJob?.cancel()
		usageGeneration++
		usagesBusy = false
	}
	fun closeUsages() { cancelUsages(); usagesVisible = false; editorFocusRequest++ }

	fun search(request: SearchRequest) {
		if (file == null || busy) return
		cancelSearch()
		cancelUsages()
		searchError = null
		lastSearchRequest = request
		searchProgress = SearchProgress()
		if (request.query.isBlank()) return
		try { request.pattern() } catch (failure: Exception) { searchError = failure.message; return }
		val generation = searchGeneration
		val cancellation = AtomicBoolean(false).also { searchCancellation = it }
		searchBusy = true
		searchJob = scope.launch {
			try {
				withContext(worker) {
					requireNotNull(session).search(request, cancellation::get) { update ->
						scope.launch { if (generation == searchGeneration) searchProgress = update }
					}
				}
			} catch (cancel: CancellationException) { throw cancel }
			catch (failure: Exception) { if (generation == searchGeneration) searchError = failure.message }
			finally { if (generation == searchGeneration) searchBusy = false }
		}
	}

	fun cancelSearch() {
		searchCancellation.set(true)
		searchJob?.cancel()
		searchGeneration++
		searchBusy = false
	}

	fun selectTab(id: String) { tabs.firstOrNull { it.entry.id == id }?.let { show(it.entry) } }
	fun closeTab(id: String) {
		val index = tabs.indexOfFirst { it.entry.id == id }
		if (index < 0) return
		tabs.removeAt(index)
		if (selectedId == id) {
			selectedId = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.entry?.id
			if (selectedId == null && settingsOpen) settingsSelected = true
		}
	}
	fun jumpTo(offset: Int) {
		selected?.let { history.record(currentLocation(), CodeLocation(it.entry, it.mode, offset)); applyJump(offset) }
	}
	private fun applyJump(offset: Int) {
		selected?.let { caretOffset = offset; caretPositions[it.viewKey] = offset; jump = NavigationTarget(it.viewKey, offset, (jump?.serial ?: 0) + 1) }
	}

	override fun close() {
		cancelSearch()
		cancelUsages()
		scope.cancel()
		// Queue disposal behind blocking jadx work; never close a live session on the EDT.
		executor.execute { session?.close(); session = null }
		worker.close()
	}
}

private data class OpenRequest(val entry: ProjectEntry, val mode: CodeMode, val hit: SearchHit? = null,
	val definition: Pair<SourceDocument, Int>? = null, val origin: CodeLocation? = null,
	val location: CodeLocation? = null, val recordHistory: Boolean = true)
internal data class NavigationTarget(val viewKey: String, val offset: Int, val serial: Int)

private data class LoadedProject(
	val entries: List<ProjectEntry>,
	val tabs: List<SourceDocument>,
	val mappingMatches: Int,
	val mapping: File?,
	val mappingError: String?,
)
