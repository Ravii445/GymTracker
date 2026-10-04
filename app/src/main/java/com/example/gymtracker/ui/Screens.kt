package com.example.gymtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gymtracker.data.Workout
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
    val totalVolume = workouts.sumOf { it.sets * it.reps * it.weight.toDouble() }

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
            items(workouts.take(5)) { workout -> WorkoutCard(workout) }
        }
    }
}

@Composable
fun AddWorkoutScreen(
    viewModel: WorkoutViewModel,
    onDone: () -> Unit
) {
    var exercise by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf("3") }
    var reps by remember { mutableStateOf("10") }
    var weight by remember { mutableStateOf("20") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Log Workout", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = exercise,
            onValueChange = { exercise = it },
            label = { Text("Exercise") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = sets,
            onValueChange = { sets = it },
            label = { Text("Sets") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = reps,
            onValueChange = { reps = it },
            label = { Text("Reps") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it },
            label = { Text("Weight (kg)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Button(
            onClick = {
                val s = sets.toIntOrNull() ?: 0
                val r = reps.toIntOrNull() ?: 0
                val w = weight.toFloatOrNull() ?: 0f
                if (exercise.isNotBlank() && s > 0 && r > 0) {
                    viewModel.addWorkout(exercise.trim(), s, r, w)
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

@Composable
fun HistoryScreen(viewModel: WorkoutViewModel) {
    val workouts by viewModel.workouts.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("History", style = MaterialTheme.typography.headlineMedium)
        }

        items(workouts) { workout ->
            WorkoutCard(workout, onDelete = { viewModel.deleteWorkout(workout) })
        }
    }
}

@Composable
fun WorkoutCard(
    workout: Workout,
    onDelete: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(workout.exercise, style = MaterialTheme.typography.titleMedium)
                Text("${workout.sets} sets x ${workout.reps} reps @ ${workout.weight} kg")
                Text(
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(workout.date)),
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
