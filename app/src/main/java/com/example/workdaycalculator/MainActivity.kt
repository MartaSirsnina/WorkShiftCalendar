package com.example.workdaycalculator

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.*

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            WorkDayCalculatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WorkDayCalculatorScreen()
                }
            }
        }
    }
}

val PastelGreen = Color(0xFF77DD77)
val PastelRed = Color(0xFFFF6961)

@Composable
fun WorkDayCalculatorTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WorkDayCalculatorScreen() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    var onDaysInput by remember { mutableStateOf("") }
    var offDaysInput by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(listOf<Pair<LocalDate, Boolean>>()) }

    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDate = Instant
                                .ofEpochMilli(it)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            Text(
                text = "Work / Off Day Calculator",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(16.dp))

            Text("Start date", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedDate?.toString() ?: "Select start date")
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = onDaysInput,
                onValueChange = { onDaysInput = it },
                label = { Text("On days count") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = offDaysInput,
                onValueChange = { offDaysInput = it },
                label = { Text("Off days count") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    selectedDate?.let {
                        result = calculateSchedule(it, onDaysInput, offDaysInput)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedDate != null
            ) {
                Text("Calculate next 30 days")
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val onDaysList = result.filter { it.second }
                    onDaysList.forEach { (date, _) ->
                        insertEventToCalendar(context, date)
                    }

                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "${onDaysList.size} ON days exported to calendar ✅"
                        )
                    }

                },
                modifier = Modifier.fillMaxWidth(),
                enabled = result.isNotEmpty()
            ) {
                Text("Export ON days to Calendar")
            }

            Spacer(Modifier.height(16.dp))

            val schedulesByMonth = result.groupBy { it.first.monthValue to it.first.year }
            schedulesByMonth.forEach { (monthYear, monthSchedule) ->
                val (month, year) = monthYear
                CalendarView(month = month, year = year, schedule = monthSchedule)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun calculateSchedule(
    startDate: LocalDate,
    onDaysStr: String,
    offDaysStr: String
): List<Pair<LocalDate, Boolean>> {
    return try {
        val onDays = onDaysStr.toInt()
        val offDays = offDaysStr.toInt()
        val cycleLength = onDays + offDays

        (0 until 30).map { dayOffset ->
            val date = startDate.plusDays(dayOffset.toLong())
            val cycleDay = dayOffset % cycleLength
            val isOn = cycleDay < onDays
            date to isOn
        }
    } catch (e: Exception) {
        emptyList()
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun insertEventToCalendar(context: Context, date: LocalDate) {
    val startMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val endMillis = startMillis + 8 * 60 * 60 * 1000

    val values = ContentValues().apply {
        put(CalendarContract.Events.DTSTART, startMillis)
        put(CalendarContract.Events.DTEND, endMillis)
        put(CalendarContract.Events.TITLE, "Work Day")
        put(CalendarContract.Events.DESCRIPTION, "ON Day")
        put(CalendarContract.Events.CALENDAR_ID, 1)
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
    }

    context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CalendarView(
    month: Int,
    year: Int,
    schedule: List<Pair<LocalDate, Boolean>>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault()) + " $year",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(4.dp))

        if (schedule.isEmpty()) return

        val firstOnDay = schedule.first { it.second }.first
        val lastOnDay = schedule.last { it.second }.first

        val firstDayOfMonth = LocalDate.of(year, month, 1)
        val lastDayOfMonth = firstDayOfMonth.withDayOfMonth(firstDayOfMonth.lengthOfMonth())

        val displayStart = firstOnDay.with(DayOfWeek.MONDAY)
        val displayEnd = lastOnDay.with(DayOfWeek.SUNDAY)

        val weeks = mutableListOf<MutableList<Pair<LocalDate?, Boolean>?>>()
        var week = MutableList<Pair<LocalDate?, Boolean>?>(7) { null }

        var current = displayStart
        while (!current.isAfter(displayEnd)) {
            val isInMonth = !current.isBefore(firstDayOfMonth) && !current.isAfter(lastDayOfMonth)
            val isOn = schedule.find { it.first == current }?.second ?: false

            val dayPair = if (isInMonth) current to isOn else null
            val dayOfWeekIndex = (current.dayOfWeek.value + 6) % 7

            week[dayOfWeekIndex] = dayPair

            if (dayOfWeekIndex == 6) {
                weeks.add(week)
                week = MutableList(7) { null }
            }

            current = current.plusDays(1)
        }
        if (week.any { it != null }) weeks.add(week)

        weeks.forEach { w ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                w.forEach { datePair ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                when {
                                    datePair == null -> Color.Transparent
                                    datePair.second -> PastelGreen
                                    else -> PastelRed
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = datePair?.first?.dayOfMonth?.toString() ?: "",
                            color = if (datePair?.first != null) Color.White else Color.Transparent
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
