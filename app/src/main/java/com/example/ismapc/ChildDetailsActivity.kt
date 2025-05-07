package com.example.ismapc

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import java.util.concurrent.TimeUnit
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat

class ChildDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get the child ID from the intent
        val childId = intent.getStringExtra("childId")
        val childName = intent.getStringExtra("childName")
        
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChildDetailsScreen(
                        childId = childId ?: "",
                        childName = childName ?: "Child Details"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDetailsScreen(childId: String, childName: String) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Activity", "Settings")
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(childName) },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // Tab Content
            when (selectedTabIndex) {
                0 -> OverviewTab(childId)
                1 -> ActivityTab(childId)
                2 -> SettingsTab(childId)
            }
        }
    }
}

@Composable
fun OverviewTab(childId: String) {
    var screenTimeState by remember { mutableStateOf<ScreenTimeState>(ScreenTimeState.Loading) }
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()

    LaunchedEffect(childId) {
        if (childId.isBlank()) {
            screenTimeState = ScreenTimeState.Error("Invalid child ID")
            return@LaunchedEffect
        }

        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateString = dateFormat.format(Date())
            val document = "${childId}_$dateString"

            Log.d("ChildDetailsActivity", "Fetching screen time data for document: $document")

            firestore.collection("screenTime")
                .document(document)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val screenTime = documentSnapshot.getLong("screenTime") ?: 0L
                        screenTimeState = ScreenTimeState.Success(screenTime)
                        Log.d("ChildDetailsActivity", "Screen time data found: $screenTime")
                    } else {
                        // Try to get any available data for this child
                        firestore.collection("screenTime")
                            .whereEqualTo("userId", childId)
                            .orderBy("date", Query.Direction.DESCENDING)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                if (!querySnapshot.isEmpty) {
                                    val latestDoc = querySnapshot.documents[0]
                                    val screenTime = latestDoc.getLong("screenTime") ?: 0L
                                    screenTimeState = ScreenTimeState.Success(screenTime)
                                    Log.d("ChildDetailsActivity", "Found previous screen time data: $screenTime")
                                } else {
                                    screenTimeState = ScreenTimeState.Success(0L)
                                    Log.d("ChildDetailsActivity", "No screen time data found")
                                }
                            }
                            .addOnFailureListener { e ->
                                screenTimeState = ScreenTimeState.Success(0L)
                                Log.e("ChildDetailsActivity", "Error querying screen time data", e)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    screenTimeState = ScreenTimeState.Success(0L)
                    Log.e("ChildDetailsActivity", "Error fetching screen time data", e)
                }
        } catch (e: Exception) {
            screenTimeState = ScreenTimeState.Success(0L)
            Log.e("ChildDetailsActivity", "Error in screen time data fetch", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (screenTimeState) {
            is ScreenTimeState.Loading -> {
                CircularProgressIndicator()
            }
            is ScreenTimeState.Success -> {
                val screenTime = (screenTimeState as ScreenTimeState.Success).screenTime
                if (screenTime > 0) {
                    Text(
                        text = "Today's Screen Time",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = formatScreenTime(screenTime),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "No data",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is ScreenTimeState.Error -> {
                Text(
                    text = (screenTimeState as ScreenTimeState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

sealed class ScreenTimeState {
    object Loading : ScreenTimeState()
    data class Success(val screenTime: Long) : ScreenTimeState()
    data class Error(val message: String) : ScreenTimeState()
}

@Composable
private fun ScreenTimeCard(screenTime: Long) {
    val hours = TimeUnit.MILLISECONDS.toHours(screenTime)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(screenTime) % 60

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Today's Screen Time",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$hours hours $minutes minutes",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ActivityTab(childId: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Activity Tab Content")
    }
}

@Composable
fun SettingsTab(childId: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text("Settings Tab Content")
    }
}

private fun getDateString(calendar: Calendar): String {
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private fun formatScreenTime(screenTime: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(screenTime)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(screenTime) % 60
    return "$hours hours $minutes minutes"
} 