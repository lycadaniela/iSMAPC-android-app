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
    val firestore = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(childId) {
        if (childId.isBlank()) {
            screenTimeState = ScreenTimeState.Error("Invalid child ID")
            return@LaunchedEffect
        }

        try {
            Log.d("OverviewTab", "Fetching screen time data for child: $childId")
            val calendar = Calendar.getInstance()
            val dateString = getDateString(calendar)
            Log.d("OverviewTab", "Date: $dateString")

            // Construct the document path
            val documentPath = "${childId}_$dateString"
            Log.d("OverviewTab", "Fetching screen time from document: screenTime/$documentPath")

            // First try to get today's data
            firestore.collection("screenTime")
                .document(documentPath)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        Log.d("OverviewTab", "Document exists: true")
                        val screenTime = document.getLong("screenTime") ?: 0L
                        screenTimeState = ScreenTimeState.Success(screenTime)
                    } else {
                        Log.d("OverviewTab", "Document exists: false")
                        Log.d("OverviewTab", "No data for today, searching for any data")
                        // If no data for today, try to get any data for this child
                        firestore.collection("screenTime")
                            .whereEqualTo("userId", childId)
                            .orderBy("date", Query.Direction.DESCENDING)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { documents ->
                                Log.d("OverviewTab", "Found ${documents.size()} documents")
                                if (documents.isEmpty) {
                                    Log.d("OverviewTab", "No screen time data found")
                                    screenTimeState = ScreenTimeState.Success(0L)
                                } else {
                                    val screenTime = documents.documents[0].getLong("screenTime") ?: 0L
                                    screenTimeState = ScreenTimeState.Success(screenTime)
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("OverviewTab", "Error getting screen time data", e)
                                screenTimeState = ScreenTimeState.Error("Error loading screen time data")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("OverviewTab", "Error getting today's screen time data", e)
                    screenTimeState = ScreenTimeState.Error("Error loading screen time data")
                }
        } catch (e: Exception) {
            Log.e("OverviewTab", "Error in LaunchedEffect", e)
            screenTimeState = ScreenTimeState.Error("Error loading screen time data")
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

        when (screenTimeState) {
            is ScreenTimeState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ScreenTimeState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (screenTimeState as ScreenTimeState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            is ScreenTimeState.Success -> {
                ScreenTimeCard((screenTimeState as ScreenTimeState.Success).screenTime)
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