package com.example.ismapc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import android.app.TimePickerDialog
import android.app.TimePickerDialog.OnTimeSetListener
import android.icu.util.Calendar
import android.util.Log
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class ScreenTimeLimitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val childId = intent.getStringExtra("childId") ?: ""
        val childName = intent.getStringExtra("childName") ?: "Screen Time Limits"
        
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScreenTimeLimitScreen(childId = childId, childName = childName)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTimeLimitScreen(childId: String, childName: String) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    
    // Data structures
    data class AppLimit(
        val appName: String,
        val limitMinutes: Long,
        val packageName: String? = null
    )
    
    data class LockTime(
        val day: String,
        val time: LocalTime
    )
    
    // State variables
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<String?>(null) }
    var selectedTime by remember { mutableStateOf<LocalTime?>(null) }
    var selectedApp by remember { mutableStateOf<AppLimit?>(null) }
    var selectedLimit by remember { mutableStateOf<Long?>(null) }
    
    // Data flows
    val appLimits = remember { MutableStateFlow<List<AppLimit>>(emptyList()) }
    val lockTimes = remember { MutableStateFlow<List<LockTime>>(emptyList()) }
    
    // Collect states
    val appLimitsState = appLimits.collectAsState(initial = emptyList())
    val lockTimesState = lockTimes.collectAsState(initial = listOf(
        LockTime("Weekdays", LocalTime.of(22, 0)),
        LockTime("Weekends", LocalTime.of(23, 0))
    ))

    // Load data when the screen is created
    LaunchedEffect(Unit) {
        try {
            // Load app limits
            val appLimitsDoc = firestore
                .collection("screenTime")
                .document(childId)
                .get()
                .await()
            
            if (appLimitsDoc.exists()) {
                val apps = appLimitsDoc.data?.get("apps") as? Map<String, Any> ?: emptyMap()
                val limits = apps.map { (pkg, data) ->
                    try {
                        AppLimit(
                            appName = (data as Map<String, Any>)["name"] as? String ?: "Unknown",
                            limitMinutes = ((data as Map<String, Any>)["limitMinutes"] as? Number)?.toLong() ?: 0,
                            packageName = pkg
                        )
                    } catch (e: Exception) {
                        AppLimit(
                            appName = "Unknown",
                            limitMinutes = 0,
                            packageName = pkg
                        )
                    }
                }
                appLimits.value = limits
            }
            
            // Load lock times
            val lockTimesDoc = firestore
                .collection("screenTime")
                .document(childId)
                .collection("lockTimes")
                .document("schedule")
                .get()
                .await()
            
            if (lockTimesDoc.exists()) {
                val weekdays = lockTimesDoc.data?.get("weekdays") as? String ?: "22:00"
                val weekends = lockTimesDoc.data?.get("weekends") as? String ?: "23:00"
                
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                try {
                    lockTimes.value = listOf(
                        LockTime("Weekdays", LocalTime.parse(weekdays, formatter)),
                        LockTime("Weekends", LocalTime.parse(weekends, formatter))
                    )
                } catch (e: Exception) {
                    // If time parsing fails, use default times
                    lockTimes.value = listOf(
                        LockTime("Weekdays", LocalTime.of(22, 0)),
                        LockTime("Weekends", LocalTime.of(23, 0))
                    )
                }
            } else {
                // If document doesn't exist, use default times
                lockTimes.value = listOf(
                    LockTime("Weekdays", LocalTime.of(22, 0)),
                    LockTime("Weekends", LocalTime.of(23, 0))
                )
            }
            
            isLoading = false
        } catch (e: Exception) {
            error = e.message ?: "Failed to load screen time data"
            isLoading = false
            // If loading fails, use default values
            appLimits.value = emptyList()
            lockTimes.value = listOf(
                LockTime("Weekdays", LocalTime.of(22, 0)),
                LockTime("Weekends", LocalTime.of(23, 0))
            )
        }
    }

    // Functions to update data
    fun updateAppLimit(appName: String, limitMinutes: Long, packageName: String) {
        scope.launch {
            try {
                val docRef = firestore
                    .collection("screenTime")
                    .document(childId)
                
                // Create or update the apps map
                val appsUpdate = mapOf(
                    "apps.$packageName" to mapOf(
                        "name" to appName,
                        "limitMinutes" to limitMinutes
                    )
                )
                
                docRef.set(appsUpdate, SetOptions.merge())
                    .await()
                
                appLimits.value = appLimits.value.map { limit ->
                    if (limit.packageName == packageName) {
                        limit.copy(limitMinutes = limitMinutes)
                    } else {
                        limit
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to update app limit"
                Log.e("ScreenTimeLimit", "Error updating app limit: ${e.message}")
            }
        }
    }
    
    fun updateLockTime(day: String, time: LocalTime) {
        scope.launch {
            try {
                val docRef = firestore
                    .collection("screenTime")
                    .document(childId)
                    .collection("lockTimes")
                    .document("schedule")
                
                // Create or update the lock time
                val timeUpdate = mapOf(day.lowercase() to time.toString())
                docRef.set(timeUpdate, SetOptions.merge())
                    .await()
                
                lockTimes.value = lockTimes.value.map { lockTime ->
                    if (lockTime.day == day) {
                        lockTime.copy(time = time)
                    } else {
                        lockTime
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to update lock time"
                Log.e("ScreenTimeLimit", "Error updating lock time: ${e.message}")
            }
        }
    }

    // Convert minutes to hours and minutes
    fun Long.toHours(): Int = this.toInt() / 60
    fun Long.toMinutes(): Int = this.toInt() % 60

    // Show time picker dialog
    fun showTimePicker(
        context: android.content.Context,
        initialTime: LocalTime,
        onTimeSelected: (LocalTime) -> Unit
    ) {
        try {
            val calendar = Calendar.getInstance()
            calendar.set(
                Calendar.HOUR_OF_DAY,
                initialTime.hour
            )
            calendar.set(
                Calendar.MINUTE,
                initialTime.minute
            )

            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val newTime = LocalTime.of(hourOfDay, minute)
                    onTimeSelected(newTime)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        } catch (e: Exception) {
            error = "Failed to show time picker: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "$childName's Screen Time")
                },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            (context as? ComponentActivity)?.finish()
                            android.util.Log.d("ScreenTime", "Back button pressed")
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back to previous screen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // TODO: Implement add app limit dialog
                    }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add app limit",
                            tint = Color(0xFFE0852D)
                        )
                    }
                }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Device Lock Times Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Device Lock Times",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFE0852D),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFFE0852D)
                                )
                            }
                        } else if (error != null) {
                            Text(
                                text = error!!,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            val times = lockTimesState.value
                            if (times.isEmpty()) {
                                Text(
                                    text = "No lock times set",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                times.forEach { time ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = time.day,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = time.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        IconButton(
                                            onClick = {
                                                selectedDay = time.day
                                                selectedTime = time.time
                                                showTimePicker(
                                                    context = context,
                                                    initialTime = time.time,
                                                    onTimeSelected = { newTime ->
                                                        scope.launch {
                                                            updateLockTime(time.day, newTime)
                                                            selectedDay = null
                                                            selectedTime = null
                                                        }
                                                    }
                                                )
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit lock time",
                                                tint = Color(0xFFE0852D)
                                            )
                                        }
                                    }
                                    if (time.day != times.last().day) {
                                        Divider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // App Limits Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "App Limits",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFE0852D),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFFE0852D)
                                )
                            }
                        } else if (error != null) {
                            Text(
                                text = error!!,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            val limits = appLimitsState.value
                            if (limits.isEmpty()) {
                                Text(
                                    text = "No app limits set",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(limits) { app ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = app.appName,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = formatUsageTime(app.limitMinutes),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            IconButton(
                                                onClick = {
                                                    selectedApp = app
                                                    selectedLimit = app.limitMinutes
                                                    showTimePicker(
                                                        context = context,
                                                        initialTime = LocalTime.of(
                                                            app.limitMinutes.toInt() / 60,
                                                            app.limitMinutes.toInt() % 60
                                                        ),
                                                        onTimeSelected = { newTime ->
                                                            scope.launch {
                                                                updateAppLimit(
                                                                    app.appName,
                                                                    (newTime.hour * 60 + newTime.minute).toLong(),
                                                                    app.packageName ?: ""
                                                                )
                                                                selectedApp = null
                                                                selectedLimit = null
                                                            }
                                                        }
                                                    )
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit app limit",
                                                    tint = Color(0xFFE0852D)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun formatUsageTime(minutes: Long): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours >= 24 -> "${hours / 24} days ${hours % 24} hours"
        hours > 0 -> "$hours hours $remainingMinutes minutes"
        else -> "$remainingMinutes minutes"
    }
}