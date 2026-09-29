package jadx.compose.decompiler

import jadx.compose.fixtures.EditorFixture
import kotlin.test.*

class EditorSemanticsTest {
	@Test
	fun `occurrences follow symbol identity and usages distinguish scopes overloads and mapped fields`() = EditorFixture().use { fixture ->
		DecompilerSession.open(fixture.jar, fixture.mapping).use { session ->
			val caller = session.read(session.entries.first { it.id == "class:b" })
			val usage = caller.code.indexOf("compute(value)")
			assertTrue(usage >= 0, caller.code)
			val variable = assertNotNull(caller.references.at(usage + "compute(".length))
			assertEquals(ReferenceKind.VARIABLE, variable.kind)
			val occurrences = caller.references.occurrences(variable)
			assertEquals(3, occurrences.size, caller.code)
			assertTrue(occurrences.all { caller.code.substring(it.start, it.end) == "value" && it.start < caller.code.indexOf("other(") })
			assertNull(caller.references.at(caller.code.indexOf("value (ignored)")))
			val localUsages = session.usages(caller, variable.start, { false }, {})
			assertEquals(2, localUsages.hits.size)
			assertTrue(localUsages.hits.all { it.entry == caller.entry && it.offset < caller.code.indexOf("other(") })
			val target = assertNotNull(session.definition(caller, usage))
			val methodUsages = session.usages(target.document, target.offset, { false }, {})
			assertEquals(1, methodUsages.hits.size, methodUsages.toString())
			assertEquals(usage, methodUsages.hits.single().offset)
			val field = assertNotNull(caller.references.at(caller.code.indexOf("count")))
			assertEquals(2, caller.references.occurrences(field).size)
			val fields = session.usages(caller, field.start, { false }, {})
			assertTrue(fields.hits.any { it.entry.id == "class:a" })
			assertEquals(2, fields.hits.count { it.entry.id == "class:b" })
			assertTrue(session.usages(caller, field.start, { true }, {}).hits.isEmpty())
		}
	}
}
