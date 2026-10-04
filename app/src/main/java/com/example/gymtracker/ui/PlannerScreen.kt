package com.example.gymtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val days = listOf(
    1 to "Monday", 2 to "Tuesday", 3 to "Wednesday",
    4 to "Thursday", 5 to "Friday", 6 to "Saturday", 7 to "Sunday"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(viewModel: RoutineViewModel) {
    val routines by viewModel.routines.collectAsState()
    val schedule by viewModel.schedule.collectAsState()
    var expandedDay by remember { mutableStateOf<Int?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Weekly Planner") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(days.size) { i ->
                val (dayNum, dayName) = days[i]
                val entry = schedule.firstOrNull { it.dayOfWeek == dayNum }

                Card {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(dayName, style = MaterialTheme.typography.titleMedium)
                            Text(entry?.routineName ?: "Rest day",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { expandedDay = dayNum }) {
                                Text(if (entry == null) "Assign" else "Change")
                            }
                            if (entry != null) {
                                OutlinedButton(onClick = { viewModel.clearDay(dayNum) }) {
                                    Text("Clear")
                                }
                            }
                        }

                        if (expandedDay == dayNum) {
                            Spacer(Modifier.height(8.dp))
                            routines.forEach { r ->
                                TextButton(
                                    onClick = {
                                        viewModel.setSchedule(dayNum, r.routine.id)
                                        expandedDay = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(r.routine.name)
                                }
                            }
                            if (routines.isEmpty()) {
                                Text("Create a routine first.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
