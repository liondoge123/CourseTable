package com.coursetable.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultPeriodSchemeTest {
    private class MemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    @Test fun defaultSurvivesEditsDeletionRestartAndTableSwitches() { runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val preferences = MemoryPreferences()
        val repository = SettingsRepository(context, database.timetableDao(), preferences)
        val original = listOf(PeriodTime(LocalTime.of(8, 10), LocalTime.of(9, 0)))
        try {
            database.timetableDao().upsert(Timetable(id = 101, name = "秋季",
                periodsCsv = Timetable.serializePeriods(original), periodDurationMinutes = 50))
            database.timetableDao().upsert(Timetable(id = 102, name = "春季"))
            repository.setActiveTimetable(101)
            val default = repository.periodTimeSchemes.first().single()
            assertTrue(default.isDefault)
            assertEquals(original, default.periods)
            val edited = default.copy(periods = listOf(original.single().copy(end = LocalTime.of(9, 5))))
            repository.saveCurrentPeriodTimeScheme(101, edited)
            assertEquals(edited.periods, database.timetableDao().byId(101)!!.periods())
            val custom = PeriodTimeScheme("winter", "冬季", listOf(PeriodTime(LocalTime.of(9, 0), LocalTime.of(9, 45))), 45)
            repository.savePeriodTimeScheme(custom)
            repository.applyPeriodTimeScheme(101, custom)
            assertEquals(edited, repository.periodTimeSchemes.first().first())
            assertTrue(runCatching { repository.deletePeriodTimeScheme(default.id, 101) }.isFailure)
            repository.deletePeriodTimeScheme(custom.id, 101)
            assertEquals(listOf(edited), repository.periodTimeSchemes.first())
            assertEquals(edited.periods, database.timetableDao().byId(101)!!.periods())
            repository.setActiveTimetable(102)
            assertEquals(PeriodTimeScheme.defaultId(102), repository.periodTimeSchemes.first().single().id)
            repository.setActiveTimetable(101)
            val restarted = SettingsRepository(context, database.timetableDao(), preferences)
            assertEquals(listOf(edited), restarted.periodTimeSchemes.first())
        } finally { database.close() }
    } }
}
