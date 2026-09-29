package jadx.compose.ui.project

import jadx.compose.decompiler.EntryKind
import jadx.compose.decompiler.ProjectEntry
import org.jetbrains.jewel.foundation.lazy.tree.Tree
import org.jetbrains.jewel.foundation.lazy.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.buildTree

internal data class TreeEntry(val title: String, val entry: ProjectEntry? = null, val kind: EntryKind? = null)

private class PackageBranch(val name: String = "", val path: String = "") {
	val children = sortedMapOf<String, PackageBranch>()
	val entries = mutableListOf<ProjectEntry>()
}

internal fun ProjectEntry.ancestorKeys(): List<String> {
	val separator = if (kind == EntryKind.CLASS) '.' else '/'
	val parts = group.split(separator).filter { it.isNotEmpty() }
	return listOf(kind.name) + parts.indices.map { "${kind.name}:${parts.take(it + 1).joinToString(separator.toString())}" }
}

/** Separate package segments and resource directories; stable IDs survive filtering and mapping reloads. */
internal fun projectTree(entries: List<ProjectEntry>, sourcesLabel: String, resourcesLabel: String): Tree<TreeEntry> = buildTree {
	for (kind in EntryKind.entries) {
		val root = PackageBranch()
		val separator = if (kind == EntryKind.CLASS) '.' else '/'
		entries.filter { it.kind == kind }.forEach { entry ->
			var branch = root
			entry.group.split(separator).filter { it.isNotEmpty() }.forEach { part ->
				val path = if (branch.path.isEmpty()) part else "${branch.path}$separator$part"
				branch = branch.children.getOrPut(part) { PackageBranch(part, path) }
			}
			branch.entries.add(entry)
		}
		if (root.children.isNotEmpty() || root.entries.isNotEmpty()) {
			addNode(TreeEntry(if (kind == EntryKind.CLASS) sourcesLabel else resourcesLabel), kind.name) { appendBranch(root, kind) }
		}
	}
}

private fun TreeGeneratorScope<TreeEntry>.appendBranch(branch: PackageBranch, kind: EntryKind) {
	branch.children.values.forEach { child ->
		addNode(TreeEntry(child.name, kind = kind), "${kind.name}:${child.path}") { appendBranch(child, kind) }
	}
	branch.entries.sortedBy { it.name }.forEach { addLeaf(TreeEntry(it.name, it), it.id) }
}
