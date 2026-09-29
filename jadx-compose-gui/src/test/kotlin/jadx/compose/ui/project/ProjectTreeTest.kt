package jadx.compose.ui.project

import jadx.compose.decompiler.EntryKind
import jadx.compose.decompiler.ProjectEntry
import kotlin.test.*

class ProjectTreeTest {
	@Test
	fun `packages and resources have separate ancestors and unique stable keys`() {
		val cls = ProjectEntry("class:a", "Sample.java", "com.example.ui.Sample", "com.example.ui", EntryKind.CLASS)
		val sibling = ProjectEntry("class:b", "Other.java", "com.example.Other", "com.example", EntryKind.CLASS)
		val resource = ProjectEntry("resource:1", "main.xml", "res/layout/main.xml", "res/layout", EntryKind.RESOURCE)
		val elements = projectTree(listOf(cls, sibling, resource), "Sources", "Resources").walkDepthFirst().toList()
		val leaf = elements.single { it.id == cls.id }
		assertEquals(listOf("Sources", "com", "example", "ui", "Sample.java"), leaf.path().map { it.data.title })
		assertEquals(cls.ancestorKeys(), leaf.path().dropLast(1).map { it.id })
		assertEquals(listOf("Resources", "res", "layout", "main.xml"), elements.single { it.id == resource.id }.path().map { it.data.title })
		assertEquals(elements.size, elements.map { it.id }.toSet().size)
		val filtered = projectTree(listOf(cls), "Sources", "Resources").walkDepthFirst().last()
		assertEquals(leaf.path().map { it.id }, filtered.path().map { it.id })
	}
}
