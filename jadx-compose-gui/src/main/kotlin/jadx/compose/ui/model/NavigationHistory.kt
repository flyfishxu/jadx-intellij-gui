package jadx.compose.ui.model

import androidx.compose.runtime.mutableStateListOf
import jadx.compose.decompiler.CodeMode
import jadx.compose.decompiler.ProjectEntry

internal data class CodeLocation(val entry: ProjectEntry, val mode: CodeMode, val offset: Int)

/** Keep explicit code navigation separate from caret movement and text selection. */
internal class NavigationHistory {
	private val past = mutableStateListOf<CodeLocation>()
	private val future = mutableStateListOf<CodeLocation>()
	val canGoBack get() = past.isNotEmpty()
	val canGoForward get() = future.isNotEmpty()

	fun record(origin: CodeLocation?, destination: CodeLocation) {
		if (origin == null || origin == destination) return
		push(past, origin)
		future.clear()
	}

	fun back(current: CodeLocation?): CodeLocation? = move(past, future, current)
	fun forward(current: CodeLocation?): CodeLocation? = move(future, past, current)
	fun clear() {
		past.clear(); future.clear()
	}

	private fun move(
		from: MutableList<CodeLocation>,
		to: MutableList<CodeLocation>,
		current: CodeLocation?
	): CodeLocation? {
		if (from.isEmpty()) return null
		current?.let { push(to, it) }
		return from.removeAt(from.lastIndex)
	}

	private fun push(stack: MutableList<CodeLocation>, location: CodeLocation) {
		if (stack.lastOrNull() != location) stack.add(location)
		if (stack.size > 100) stack.removeAt(0)
	}
}
