package com.example.gymtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gymtracker.data.Workout
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: WorkoutViewModel) {
    val workouts by viewModel.workouts.collectAsState()

    // Volume per day
    val volumeByDay = remember(workouts) {
        workouts.groupBy { dayStart(it.date) }
            .map { (day, list) ->
                ChartPoint(
                    x = day,
                    y = list.sumOf { it.sets * it.reps * it.weight.toDouble() }.toFloat()
                )
            }
            .sortedBy { it.x }
    }

    // Estimated 1RM progression per exercise
    val exercises = remember(workouts) { workouts.map { it.exercise }.distinct().sorted() }
    var selectedExercise by remember { mutableStateOf(exercises.firstOrNull() ?: "") }
    LaunchedEffect(exercises) {
        if (selectedExercise !in exercises) selectedExercise = exercises.firstOrNull() ?: ""
    }

    val e1rm = remember(workouts, selectedExercise) {
        workouts.filter { it.exercise == selectedExercise }
            .sortedBy { it.date }
            .map { w ->
                val orm = w.weight * (1 + w.reps / 30f) // Epley
                ChartPoint(x = w.date, y = orm)
            }
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
                        val prs = workouts.groupBy { it.exercise }
                            .mapValues { (_, list) -> list.maxByOrNull { it.weight }!! }
                        if (prs.isEmpty()) {
                            Text("No records yet.")
                        } else {
                            prs.forEach { (name, w) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(name)
                                    Text("${w.weight} kg × ${w.reps}")
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
    // Simple wrap using a Column of Rows in chunks of 3
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
