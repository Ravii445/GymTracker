package com.example.gymtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val actual = remember { mutableStateMapOf<Long, Triple<Int, Int, Float>>() }
    var restSeconds by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(routineId) {
        viewModel.getRoutine(routineId) { r ->
            routine = r
            r.exercises.forEach { ex ->
                actual[ex.id] = Triple(ex.sets, ex.reps, ex.weight)
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
                    val t = actual[ex.id] ?: Triple(ex.sets, ex.reps, ex.weight)
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(ex.exerciseName, style = MaterialTheme.typography.titleMedium)
                            Text("Target: ${ex.sets}x${ex.reps} @ ${ex.weight} kg • Rest ${ex.restSeconds}s",
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberField("Sets", t.first.toString(), Modifier.weight(1f)) {
                                    actual[ex.id] = Triple(it.toIntOrNull() ?: 0, t.second, t.third)
                                }
                                NumberField("Reps", t.second.toString(), Modifier.weight(1f)) {
                                    actual[ex.id] = Triple(t.first, it.toIntOrNull() ?: 0, t.third)
                                }
                                NumberField("Weight", t.third.toString(), Modifier.weight(1f)) {
                                    actual[ex.id] = Triple(t.first, t.second, it.toFloatOrNull() ?: 0f)
                                }
                            }
                            Button(
                                onClick = { restSeconds = ex.restSeconds },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Start rest (${ex.restSeconds}s)")
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.logCompletedRoutine(r, actual.toMap())
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Finish workout")
            }
        }
    }
}
