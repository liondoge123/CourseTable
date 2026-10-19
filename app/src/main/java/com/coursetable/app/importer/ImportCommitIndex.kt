package com.coursetable.app.importer

import com.coursetable.app.data.Course
import java.util.TreeMap

/** Transaction-local view. Session fields stay local; parent metadata is hydrated on lookup. */
internal class ImportCommitIndex(initial: List<Course>) {
    private data class Key(val parent: Long, val session: Long) : Comparable<Key> {
        override fun compareTo(other: Key): Int = compareValuesBy(this, other, Key::parent, Key::session)
    }
    private val sessions = mutableMapOf<Long, Course>()
    private val parents = mutableMapOf<Long, Course>()
    private val parentSessions = mutableMapOf<Long, MutableSet<Long>>()
    private val names = mutableMapOf<String, TreeMap<Long, Course>>()
    private val fingerprints = mutableMapOf<String, TreeMap<Key, Long>>()
    private val fingerprintById = mutableMapOf<Long, String>()

    init { initial.forEach(::save) }

    fun parent(id: Long): Course? = parents[id]
    fun named(name: String): List<Course> = names[normalizedName(name)]?.values?.toList().orEmpty()
    fun matchingParent(name: String, teacher: String): Course? =
        uniqueTeacherMatch(names[normalizedName(name)]?.values.orEmpty(), teacher)
    fun duplicate(incoming: Course, target: Long?, groupParent: Long?, grouped: Boolean): Course? =
        fingerprints[scheduleFingerprint(incoming)]?.values?.asSequence()?.mapNotNull { sessions[it] }
            ?.firstOrNull { (target == null || it.courseId == target) && (!grouped || it.courseId == groupParent) }
            ?.let(::hydrate)

    private fun hydrate(row: Course): Course = parents[row.courseId]?.let { row.copy(name = it.name, teacher = it.teacher, color = it.color) } ?: row
    private fun index(row: Course) {
        val key = scheduleFingerprint(row)
        fingerprintById[row.id] = key
        fingerprints.getOrPut(key) { TreeMap() }[Key(row.courseId, row.id)] = row.id
    }

    fun save(row: Course) {
        val previousParent = parents[row.courseId]
        val previous = sessions[row.id]
        previous?.let { old -> fingerprintById.remove(old.id)?.let { fingerprints[it]?.remove(Key(old.courseId, old.id)) } }
        sessions[row.id] = row
        parentSessions.getOrPut(row.courseId) { linkedSetOf() }.add(row.id)
        parents[row.courseId] = row
        if (previousParent != null && normalizedName(previousParent.name) != normalizedName(row.name)) {
            names[normalizedName(previousParent.name)]?.remove(row.courseId)
            parentSessions[row.courseId].orEmpty().forEach { id ->
                val session = sessions.getValue(id)
                fingerprintById.remove(id)?.let { fingerprints[it]?.remove(Key(session.courseId, id)) }
                index(hydrate(session))
            }
        }
        names.getOrPut(normalizedName(row.name)) { TreeMap() }[row.courseId] = row
        index(row)
    }
}
