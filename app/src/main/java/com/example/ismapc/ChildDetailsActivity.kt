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
    val context = LocalContext.current
    var screenTimeData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(childId) {
        try {
            val db = FirebaseFirestore.getInstance()
            val calendar = Calendar.getInstance()
            val dateString = getDateString(calendar)

            Log.d("OverviewTab", "Fetching screen time data for child: $childId, date: $dateString")

            // Fetch screen time data directly using the child ID
            val screenTimeDocRef = db.collection("screenTime")
                .document("${childId}_$dateString")
            
            Log.d("OverviewTab", "Fetching screen time from document: ${screenTimeDocRef.path}")
            
            screenTimeDocRef.get()
                .addOnSuccessListener { document ->
                    Log.d("OverviewTab", "Document exists: ${document.exists()}")
                    if (document.exists()) {
                        screenTimeData = document.data
                        Log.d("OverviewTab", "Screen time data: $screenTimeData")
                    } else {
                        // If no data for today, try to get any data for this user
                        Log.d("OverviewTab", "No data for today, searching for any data")
                        db.collection("screenTime")
                            .whereEqualTo("userId", childId)
                            .get()
                            .addOnSuccessListener { docs ->
                                Log.d("OverviewTab", "Found ${docs.size()} documents")
                                if (!docs.isEmpty) {
                                    // Get the most recent document manually
                                    val mostRecent = docs.documents.maxByOrNull { 
                                        (it.get("lastUpdated") as? Long) ?: 0L 
                                    }
                                    if (mostRecent != null) {
                                        screenTimeData = mostRecent.data
                                        Log.d("OverviewTab", "Found data: $screenTimeData")
                                    } else {
                                        Log.d("OverviewTab", "No screen time data found")
                                    }
                                } else {
                                    Log.d("OverviewTab", "No screen time data found")
                                }
                                isLoading = false
                            }
                            .addOnFailureListener { e ->
                                Log.e("OverviewTab", "Error fetching data", e)
                                error = "Error loading screen time data: ${e.message}"
                                isLoading = false
                            }
                    }
                    isLoading = false
                }
                .addOnFailureListener { e ->
                    Log.e("OverviewTab", "Error fetching screen time data", e)
                    error = "Error loading screen time data: ${e.message}"
                    isLoading = false
                }
        } catch (e: Exception) {
            Log.e("OverviewTab", "Unexpected error", e)
            error = "Unexpected error: ${e.message}"
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Screen Time Overview",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (screenTimeData != null) {
            ScreenTimeCard(screenTimeData)
        } else {
            Text(
                text = "No screen time data available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScreenTimeCard(screenTimeData: Map<String, Any>?) {
    val screenTime = screenTimeData?.get("screenTime") as? Long ?: 0L
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