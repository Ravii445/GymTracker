#!/usr/bin/env python3
"""
add_per_set_reps.py
Adds per-set reps & weight to GymTracker by replacing the affected files.
Run from your project root:   python add_per_set_reps.py
Backup is written next to each replaced file with a .bak-<timestamp> suffix.
"""

from __future__ import annotations
import sys
import time
from pathlib import Path

PACKAGE = "app/src/main/java/com/example/gymtracker"
DATA = f"{PACKAGE}/data"
UI = f"{PACKAGE}/ui"

FILES: dict[str, str] = {}

# ---------------------------------------------------------------------------
# data/Workout.kt
# ---------------------------------------------------------------------------
FILES[f"{DATA}/Workout.kt"] = """\
package com.example.gymtracker.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exercise: String,
    val date: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId")]
)
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val setIndex: Int,
    val reps: Int,
    val weight: Float
)

data class WorkoutWithSets(
    @Embedded val workout: Workout,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val sets: List<SetEntry>
) {
    val totalSets: Int get() = sets.size
    val totalReps: Int get() = sets.sumOf { it.reps }
    val totalVolume: Float get() = sets.sumOf { (it.reps * it.weight).toDouble() }.toFloat()
    val topWeight: Float get() = sets.maxOfOrNull { it.weight } ?: 0f
    val estimated1RM: Float get() =
        sets.maxOfOrNull { it.weight * (1 + it.reps / 30f) } ?: 0f
}
"""

# ---------------------------------------------------------------------------
# data/WorkoutDao.kt
# ---------------------------------------------------------------------------
FILES[f"{DATA}/WorkoutDao.kt"] = """\
package com.example.gymtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun getAllWithSets(): Flow<List<WorkoutWithSets>>

    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Insert
    suspend fun insertSets(sets: List<SetEntry>)

    @Transaction
    suspend fun insertWorkoutWithSets(exercise: String, sets: List<SetEntry>): Long {
        val id = insertWorkout(Workout(exercise = exercise))
        insertSets(sets.mapIndexed { i, s -> s.copy(id = 0, workoutId = id, setIndex = i) })
        return id
    }

    @Delete
    suspend fun deleteWorkout(workout: Workout)
}
"""

# ---------------------------------------------------------------------------
# data/AppDatabase.kt
# ---------------------------------------------------------------------------
FILES[f"{DATA}/AppDatabase.kt"] = """\
package com.example.gymtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Workout::class,
        SetEntry::class,
        Routine::class,
        RoutineExercise::class,
        ScheduledRoutine::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun routineDao(): RoutineDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gym_tracker_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
"""

# ---------------------------------------------------------------------------
# ui/WorkoutViewModel.kt
# ---------------------------------------------------------------------------
FILES[f"{UI}/WorkoutViewModel.kt"] = """\
package com.example.gymtracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtracker.data.AppDatabase
import com.example.gymtracker.data.SetEntry
import com.example.gymtracker.data.Workout
import com.example.gymtracker.data.WorkoutWithSets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).workoutDao()

    val workouts: StateFlow<List<WorkoutWithSets>> =
        dao.getAllWithSets()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addWorkout(exercise: String, sets: List<SetEntry>) {
        viewModelScope.launch {
            dao.insertWorkoutWithSets(exercise, sets)
        }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch { dao.deleteWorkout(workout) }
    }
}
"""

# ---------------------------------------------------------------------------
# ui/RoutineViewModel.kt
# ---------------------------------------------------------------------------
FILES[f"{UI}/RoutineViewModel.kt"] = """\
package com.example.gymtracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtracker.data.AppDatabase
import com.example.gymtracker.data.Routine
import com.example.gymtracker.data.RoutineExercise
import com.example.gymtracker.data.RoutineWithExercises
import com.example.gymtracker.data.ScheduleEntry
import com.example.gymtracker.data.ScheduledRoutine
import com.example.gymtracker.data.SetEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RunSetDraft(val reps: Int, val weight: Float)

class RoutineViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val routineDao = db.routineDao()
    private val scheduleDao = db.scheduleDao()
    private val workoutDao = db.workoutDao()

    val routines: StateFlow<List<RoutineWithExercises>> =
        routineDao.getRoutinesWithExercises()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val schedule: StateFlow<List<ScheduleEntry>> =
        scheduleDao.getScheduleWithNames()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveRoutine(
        existingId: Long?,
        name: String,
        description: String,
        exercises: List<RoutineExercise>
    ) {
        viewModelScope.launch {
            if (existingId == null) {
                val newId = routineDao.insertRoutine(Routine(name = name, description = description))
                routineDao.insertExercises(exercises.mapIndexed { i, e ->
                    e.copy(routineId = newId, position = i)
                })
            } else {
                routineDao.updateRoutine(
                    Routine(id = existingId, name = name, description = description)
                )
                routineDao.clearExercises(existingId)
                routineDao.insertExercises(exercises.mapIndexed { i, e ->
                    e.copy(id = 0, routineId = existingId, position = i)
                })
            }
        }
    }

    fun deleteRoutine(routine: Routine) {
        viewModelScope.launch { routineDao.deleteRoutine(routine) }
    }

    fun getRoutine(id: Long, onLoaded: (RoutineWithExercises) -> Unit) {
        viewModelScope.launch { routineDao.getRoutineWithExercises(id)?.let(onLoaded) }
    }

    fun setSchedule(day: Int, routineId: Long) {
        viewModelScope.launch {
            scheduleDao.clearDay(day)
            scheduleDao.insertSchedule(ScheduledRoutine(dayOfWeek = day, routineId = routineId))
        }
    }

    fun clearDay(day: Int) {
        viewModelScope.launch { scheduleDao.clearDay(day) }
    }

    fun logCompletedRoutine(
        routine: RoutineWithExercises,
        actual: Map<Long, List<RunSetDraft>>
    ) {
        viewModelScope.launch {
            routine.exercises.forEach { ex ->
                val drafts = actual[ex.id] ?: return@forEach
                if (drafts.isEmpty()) return@forEach
                val sets = drafts.mapIndexed { i, d ->
                    SetEntry(workoutId = 0, setIndex = i, reps = d.reps, weight = d.weight)
                }
                workoutDao.insertWorkoutWithSets(ex.exerciseName, sets)
            }
        }
    }
}
"""

# ---------------------------------------------------------------------------
# ui/Screens.kt
# ---------------------------------------------------------------------------
FILES[f"{UI}/Screens.kt"] = """\
package com.example.gymtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gymtracker.data.SetEntry
import com.example.gymtracker.data.WorkoutWithSets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: WorkoutViewModel,
    onStartWorkout: () -> Unit,
    onOpenRoutines: () -> Unit
) {
    val workouts by viewModel.workouts.collectAsState()
    val totalWorkouts = workouts.size
    val totalVolume = workouts.sumOf { it.totalVolume.toDouble() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Gym Tracker", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Total workouts: $totalWorkouts")
                Text("Total volume: ${"%.1f".format(totalVolume)} kg")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStartWorkout, modifier = Modifier.weight(1f)) {
                Text("Log Workout")
            }
            OutlinedButton(onClick = onOpenRoutines, modifier = Modifier.weight(1f)) {
                Text("Routines")
            }
        }

        Text("Recent", style = MaterialTheme.typography.titleLarge)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(workouts.take(5)) { item -> WorkoutCard(item) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkoutScreen(
    viewModel: WorkoutViewModel,
    onDone: () -> Unit
) {
    var exercise by remember { mutableStateOf("") }
    val setDrafts = remember { mutableStateListOf(SetDraft(reps = 10, weight = 20f)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Log Workout", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = exercise,
            onValueChange = { exercise = it },
            label = { Text("Exercise") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Sets", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(setDrafts.size) { index ->
                SetDraftRow(
                    index = index + 1,
                    draft = setDrafts[index],
                    onChange = { setDrafts[index] = it },
                    onDelete = if (setDrafts.size > 1) {
                        { setDrafts.removeAt(index) }
                    } else null
                )
            }
        }

        OutlinedButton(
            onClick = {
                val last = setDrafts.lastOrNull() ?: SetDraft(10, 20f)
                setDrafts.add(last.copy())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add set")
        }

        Button(
            onClick = {
                if (exercise.isNotBlank() && setDrafts.isNotEmpty()) {
                    val sets = setDrafts.mapIndexed { i, d ->
                        SetEntry(workoutId = 0, setIndex = i, reps = d.reps, weight = d.weight)
                    }
                    viewModel.addWorkout(exercise.trim(), sets)
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

data class SetDraft(val reps: Int, val weight: Float)

@Composable
private fun SetDraftRow(
    index: Int,
    draft: SetDraft,
    onChange: (SetDraft) -> Unit,
    onDelete: (() -> Unit)?
) {
    Card {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("$index", modifier = Modifier.width(24.dp))

            OutlinedTextField(
                value = draft.reps.toString(),
                onValueChange = { onChange(draft.copy(reps = it.toIntOrNull() ?: 0)) },
                label = { Text("Reps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = draft.weight.toString(),
                onValueChange = { onChange(draft.copy(weight = it.toFloatOrNull() ?: 0f)) },
                label = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove set")
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: WorkoutViewModel) {
    val workouts by viewModel.workouts.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("History", style = MaterialTheme.typography.headlineMedium)
        }

        items(workouts) { item ->
            WorkoutCard(item, onDelete = { viewModel.deleteWorkout(item.workout) })
        }
    }
}

@Composable
fun WorkoutCard(
    item: WorkoutWithSets,
    onDelete: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.workout.exercise, style = MaterialTheme.typography.titleMedium)

                val setsLine = item.sets.sortedBy { it.setIndex }
                    .joinToString("  \\u2022  ") { "${it.reps} x ${formatWeight(it.weight)} kg" }
                Text(setsLine, style = MaterialTheme.typography.bodyMedium)

                Text(
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(item.workout.date)),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

private fun formatWeight(w: Float): String =
    if (w % 1f == 0f) w.toInt().toString() else "%.1f".format(w)
"""

# ---------------------------------------------------------------------------
# ui/ProgressScreen.kt
# ---------------------------------------------------------------------------
FILES[f"{UI}/ProgressScreen.kt"] = """\
package com.example.gymtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: WorkoutViewModel) {
    val workouts by viewModel.workouts.collectAsState()

    val volumeByDay = remember(workouts) {
        workouts.groupBy { dayStart(it.workout.date) }
            .map { (day, list) ->
                ChartPoint(
                    x = day,
                    y = list.sumOf { it.totalVolume.toDouble() }.toFloat()
                )
            }
            .sortedBy { it.x }
    }

    val exercises = remember(workouts) {
        workouts.map { it.workout.exercise }.distinct().sorted()
    }
    var selectedExercise by remember { mutableStateOf(exercises.firstOrNull() ?: "") }
    LaunchedEffect(exercises) {
        if (selectedExercise !in exercises) selectedExercise = exercises.firstOrNull() ?: ""
    }

    val e1rm = remember(workouts, selectedExercise) {
        workouts.filter { it.workout.exercise == selectedExercise }
            .sortedBy { it.workout.date }
            .map { w -> ChartPoint(x = w.workout.date, y = w.estimated1RM) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Progress") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Total volume per day (kg)",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LineChart(
                            points = volumeByDay,
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Estimated 1RM progression",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        if (exercises.isEmpty()) {
                            Text("Log a workout to see progress.")
                        } else {
                            Text("Exercise: $selectedExercise",
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            FlowRowSimple(
                                items = exercises,
                                onSelect = { selectedExercise = it }
                            )
                            Spacer(Modifier.height(8.dp))
                            LineChart(
                                points = e1rm,
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            )
                        }
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Personal records",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val prs = workouts.groupBy { it.workout.exercise }
                            .mapValues { (_, list) ->
                                val bestWorkout = list.maxByOrNull { it.topWeight }!!
                                val topSet = bestWorkout.sets.maxByOrNull { it.weight }!!
                                bestWorkout.workout.exercise to topSet
                            }
                        if (prs.isEmpty()) {
                            Text("No records yet.")
                        } else {
                            prs.forEach { (name, set) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(name)
                                    Text("${set.weight} kg x ${set.reps}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowRowSimple(items: List<String>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item ->
                    AssistChip(
                        onClick = { onSelect(item) },
                        label = { Text(item, maxLines = 1) }
                    )
                }
            }
        }
    }
}

private fun dayStart(millis: Long): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}
"""

# ---------------------------------------------------------------------------
# ui/RoutineScreens.kt
# ---------------------------------------------------------------------------
FILES[f"{UI}/RoutineScreens.kt"] = """\
package com.example.gymtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gymtracker.data.RoutineExercise
import com.example.gymtracker.data.RoutineWithExercises

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    viewModel: RoutineViewModel,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onRun: (Long) -> Unit
) {
    val routines by viewModel.routines.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Routines") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "New routine")
            }
        }
    ) { padding ->
        if (routines.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No routines yet. Tap + to create one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(routines, key = { it.routine.id }) { r ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(r.routine.name, style = MaterialTheme.typography.titleLarge)
                            if (r.routine.description.isNotBlank()) {
                                Text(r.routine.description, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("${r.exercises.size} exercises",
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onRun(r.routine.id) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Start")
                                }
                                OutlinedButton(onClick = { onEdit(r.routine.id) }) { Text("Edit") }
                                IconButton(onClick = { viewModel.deleteRoutine(r.routine) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    viewModel: RoutineViewModel,
    routineId: Long?,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val exercises = remember { mutableStateListOf<RoutineExercise>() }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(routineId) {
        if (routineId != null && !loaded) {
            viewModel.getRoutine(routineId) { r ->
                name = r.routine.name
                description = r.routine.description
                exercises.clear()
                exercises.addAll(r.exercises)
                loaded = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (routineId == null) "New Routine" else "Edit Routine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Routine name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Exercises", style = MaterialTheme.typography.titleMedium)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(exercises.size) { index ->
                    val ex = exercises[index]
                    RoutineExerciseEditor(
                        exercise = ex,
                        onChange = { exercises[index] = it },
                        onDelete = { exercises.removeAt(index) }
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    exercises.add(
                        RoutineExercise(
                            routineId = routineId ?: 0,
                            exerciseName = "",
                            sets = 3,
                            reps = 10,
                            weight = 20f,
                            restSeconds = 90
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add exercise")
            }

            Button(
                onClick = {
                    if (name.isNotBlank() && exercises.all { it.exerciseName.isNotBlank() }) {
                        viewModel.saveRoutine(routineId, name.trim(), description.trim(), exercises.toList())
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun RoutineExerciseEditor(
    exercise: RoutineExercise,
    onChange: (RoutineExercise) -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = exercise.exerciseName,
                    onValueChange = { onChange(exercise.copy(exerciseName = it)) },
                    label = { Text("Exercise") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Sets", exercise.sets.toString(),
                    Modifier.weight(1f)) { onChange(exercise.copy(sets = it.toIntOrNull() ?: 0)) }
                NumberField("Reps", exercise.reps.toString(),
                    Modifier.weight(1f)) { onChange(exercise.copy(reps = it.toIntOrNull() ?: 0)) }
                NumberField("Weight", exercise.weight.toString(),
                    Modifier.weight(1f)) { onChange(exercise.copy(weight = it.toFloatOrNull() ?: 0f)) }
            }
            NumberField("Rest (s)", exercise.restSeconds.toString(), Modifier.fillMaxWidth()) {
                onChange(exercise.copy(restSeconds = it.toIntOrNull() ?: 0))
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunRoutineScreen(
    viewModel: RoutineViewModel,
    routineId: Long,
    onBack: () -> Unit
) {
    var routine by remember { mutableStateOf<RoutineWithExercises?>(null) }
    val actual = remember { mutableStateMapOf<Long, MutableList<RunSetDraft>>() }
    var restSeconds by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(routineId) {
        viewModel.getRoutine(routineId) { r ->
            routine = r
            r.exercises.forEach { ex ->
                actual[ex.id] = MutableList(ex.sets) {
                    RunSetDraft(reps = ex.reps, weight = ex.weight)
                }
            }
        }
    }

    if (restSeconds != null) {
        RestTimerDialog(totalSeconds = restSeconds!!, onDismiss = { restSeconds = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routine?.routine?.name ?: "Workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val r = routine
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(r.exercises, key = { it.id }) { ex ->
                    val drafts = actual[ex.id] ?: mutableListOf()
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(ex.exerciseName, style = MaterialTheme.typography.titleMedium)
                            Text("Rest ${ex.restSeconds}s",
                                style = MaterialTheme.typography.bodySmall)

                            drafts.forEachIndexed { idx, draft ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("${idx + 1}", modifier = Modifier.width(20.dp))
                                    OutlinedTextField(
                                        value = draft.reps.toString(),
                                        onValueChange = {
                                            drafts[idx] = draft.copy(reps = it.toIntOrNull() ?: 0)
                                        },
                                        label = { Text("Reps") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = draft.weight.toString(),
                                        onValueChange = {
                                            drafts[idx] = draft.copy(weight = it.toFloatOrNull() ?: 0f)
                                        },
                                        label = { Text("kg") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val last = drafts.lastOrNull()
                                            ?: RunSetDraft(ex.reps, ex.weight)
                                        drafts.add(last.copy())
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("+ Set")
                                }
                                Button(
                                    onClick = { restSeconds = ex.restSeconds },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Rest ${ex.restSeconds}s")
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.logCompletedRoutine(r, actual.mapValues { it.value.toList() })
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Finish workout")
            }
        }
    }
}
"""

# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------
def find_project_root(start: Path) -> Path | None:
    cur = start.resolve()
    for _ in range(8):
        if (cur / "settings.gradle.kts").exists() or (cur / "settings.gradle").exists():
            return cur
        if (cur / "app" / "build.gradle.kts").exists() or (cur / "app" / "build.gradle").exists():
            return cur
        if cur.parent == cur:
            break
        cur = cur.parent
    return None


def main() -> int:
    here = Path.cwd()
    root = find_project_root(here)
    if root is None:
        print("ERROR: Could not find project root (no settings.gradle.kts).")
        print(f"       Run from your GymTracker directory. Current dir: {here}")
        return 1

    print(f"Project root: {root}")
    print()

    stamp = time.strftime("%Y%m%d-%H%M%S")
    written = 0
    backed_up = 0
    for rel, content in FILES.items():
        target = root / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            backup = target.with_suffix(target.suffix + f".bak-{stamp}")
            backup.write_bytes(target.read_bytes())
            backed_up += 1
            print(f"  backup  {rel} -> {backup.name}")
        target.write_text(content, encoding="utf-8")
        written += 1
        print(f"  write   {rel}")

    print()
    print(f"Done. {written} files written, {backed_up} backups created.")
    print()
    print("Next steps:")
    print("  git add .")
    print('  git commit -m "Add per-set reps and weight"')
    print("  git push")
    return 0


if __name__ == "__main__":
    sys.exit(main())
