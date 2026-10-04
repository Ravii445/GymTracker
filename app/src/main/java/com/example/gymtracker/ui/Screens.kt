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
                    .joinToString("  \u2022  ") { "${it.reps} x ${formatWeight(it.weight)} kg" }
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
