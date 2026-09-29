package jadx.compose.decompiler

import jadx.api.JavaClass
import jadx.api.JavaField
import jadx.api.JavaMethod
import jadx.api.JavaVariable
import jadx.core.dex.visitors.prepare.CollectConstValues
import java.util.concurrent.CancellationException

/** Same semantic usage query as jadx-gui, including constructors and related overrides. */
internal fun DecompilerSession.usages(document: SourceDocument, offset: Int, cancelled: () -> Boolean,
	progress: (SearchProgress) -> Unit): SearchProgress {
	val node = nodeAt(document, offset) ?: return SearchProgress().also(progress)
	val targets = when (node) {
		is JavaMethod -> node.overrideRelatedMethods.ifEmpty { listOf(node) }
		is JavaClass -> listOf(node) + node.methods.filter { it.isConstructor }
		else -> listOf(node)
	}
	val byId = entries.associateBy { it.id }
	val jobs = targets.flatMap { target ->
		val useClasses = when {
			target is JavaVariable -> listOf(target.topParentClass)
			target is JavaField && !target.fieldNode.accessFlags.isPrivate && CollectConstValues.getFieldConstValue(target.fieldNode) != null -> classes.values.toList()
			else -> target.useIn.map { it.topParentClass }
		}
		(useClasses + target.topParentClass).distinct().map { target to it }
	}
	val hits = linkedMapOf<Pair<String, Int>, SearchHit>()
	var scanned = 0
	var skipped = 0
	var limited = false
	fun snapshot() = SearchProgress(scanned, jobs.size, hits.values.toList(), skipped, limited)
	progress(snapshot())
	for ((target, cls) in jobs) {
		if (cancelled() || limited) break
		val entry = byId["class:${cls.rawName}"] ?: continue
		try {
			val info = cls.codeInfo
			val code = info.codeStr
			val lineStarts = buildList { add(0); code.forEachIndexed { i, c -> if (c == '\n') add(i + 1) } }
			for (position in cls.getUsePlacesFor(info, target)) {
				if (cancelled()) break
				val line = lineStarts.binarySearch(position).let { if (it >= 0) it else -it - 2 }
				val text = code.substring(lineStarts[line], code.indexOf('\n', position).let { if (it < 0) code.length else it }).trim()
				if (text.startsWith("import ")) continue
				val key = entry.id to position
				if (hits.size >= 1000 && key !in hits) { limited = true; break }
				hits[key] = SearchHit(entry, "${entry.name}:${line + 1}", text.take(240), offset = position)
			}
		} catch (cancel: CancellationException) { throw cancel }
		catch (_: Exception) { skipped++ }
		scanned++
		if (scanned % 8 == 0) progress(snapshot())
	}
	return snapshot().also(progress)
}
