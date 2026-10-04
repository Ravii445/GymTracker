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
                                bestWorkout.sets.maxByOrNull { it.weight }!!
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
