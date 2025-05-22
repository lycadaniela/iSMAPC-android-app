package com.example.ismapc

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

// Data structure for app usage
data class AppUsage(
    val name: String,
    val packageName: String,
    val dailyMinutes: Long,
    val weeklyMinutes: Long
)

class AppUsageActivity : ComponentActivity() {
    private val TAG = "AppUsageActivity"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // Add a permission request code
    private val USAGE_STATS_PERMISSION_REQUEST = 100
    private var permissionJustGranted = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val childId = intent.getStringExtra("childId") ?: ""
        val childName = intent.getStringExtra("childName") ?: "App Usage"
        
        Log.e(TAG, "Starting AppUsageActivity for child: $childName ($childId)")
        
        // Determine if we're running on a child or parent device
        val currentUser = auth.currentUser
        val isChildDevice = currentUser?.uid == childId
        
        Log.e(TAG, "Current user: ${currentUser?.uid}, Child ID: $childId, Is child device: $isChildDevice")
        
        // Only check for usage stats permission if this is the child's device
        if (isChildDevice) {
            Log.e(TAG, "Child device - checking for usage stats permission")
            val hasPermission = hasUsageStatsPermission()
            Log.e(TAG, "Usage stats permission check: ${if (hasPermission) "GRANTED" else "DENIED"}")
            
            if (!hasPermission) {
                Log.e(TAG, "Child device missing usage stats permission. Requesting now.")
                Toast.makeText(this, "Usage stats permission required to track app usage", Toast.LENGTH_LONG).show()
                
                // Attempt to open the Usage Access settings
                try {
                    val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    Toast.makeText(this, "Please grant usage access for this app", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open usage access settings", e)
                    Toast.makeText(this, "Please enable usage access in Settings > Apps > Special access", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Log.e(TAG, "Parent device - no need to check for usage stats permission")
        }
        
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppUsageScreen(childId = childId, childName = childName, isChildDevice = isChildDevice)
                }
            }
        }
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        val granted = mode == AppOpsManager.MODE_ALLOWED
        Log.e(TAG, "Usage stats permission check: mode=$mode, granted=$granted")
        return granted
    }
    
    // FIX: Add forced usage refresh with safeguard against infinite loop
    override fun onResume() {
        super.onResume()
        
        // Check permission again in case it was just granted
        val hasPermission = hasUsageStatsPermission()
        Log.e(TAG, "onResume: Usage stats permission = $hasPermission, permissionJustGranted = $permissionJustGranted")
        
        if (hasPermission && !permissionJustGranted) {
            // Set flag to prevent infinite loop
            permissionJustGranted = true
            
            // Force a refresh of the content
            Toast.makeText(this, "Permission granted! Refreshing app usage data...", Toast.LENGTH_SHORT).show()
            
            // Restart the activity to refresh the data
            val intent = intent
            intent.putExtra("forceRefresh", true)  // Add a flag to indicate a forced refresh
            finish()
            startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(childId: String, childName: String, isChildDevice: Boolean) {
    val context = LocalContext.current
    val TAG = "AppUsageScreen"
    val firestore = FirebaseFirestore.getInstance()
    
    var appUsageList by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSampleData by remember { mutableStateOf(false) }
    var totalAppUsage by remember { mutableStateOf<Long>(0) }
    var lastUpdated by remember { mutableStateOf<Long?>(null) }
    
    // Set up a snapshot listener for real-time updates
    DisposableEffect(childId) {
        val docRef = firestore.collection("appUsage")
            .document(childId)
            .collection("stats")
            .document("daily")
            
        // Register the snapshot listener
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening for app usage updates", error)
                errorMessage = "Error getting real-time updates: ${error.message}"
                return@addSnapshotListener
            }
            
            if (snapshot != null && snapshot.exists()) {
                Log.e(TAG, "Real-time update received for app usage data")
                
                // Get last updated timestamp
                lastUpdated = snapshot.getLong("lastUpdated")
                
                // Get data source to check if it's sample data
                val dataSource = snapshot.getString("dataSource")
                isSampleData = dataSource == "SAMPLE_DATA"
                
                // Get today's start timestamp (midnight)
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val todayStart = calendar.timeInMillis
                
                Log.e(TAG, "Today starts at: ${Date(todayStart)}")
                
                // Only use official total if it's from today, otherwise recalculate
                val docLastUpdated = snapshot.getLong("lastUpdated") ?: 0
                val useTotalFromDoc = docLastUpdated >= todayStart
                
                if (useTotalFromDoc) {
                    // Get total app usage from document if it's recent
                    totalAppUsage = snapshot.getLong("totalAppWeeklyUsage") ?: 0
                } else {
                    Log.e(TAG, "Document was last updated at ${Date(docLastUpdated)}, which is before today. Will recalculate totals.")
                }
                
                try {
                    // Process apps data
                    @Suppress("UNCHECKED_CAST")
                    val appsData = snapshot.get("apps") as? Map<String, Map<String, Any>>
                    if (appsData != null) {
                        val newAppUsageList = mutableListOf<AppUsage>()
                        
                        for ((appName, appData) in appsData) {
                            val dailyMinutes = (appData["dailyMinutes"] as? Number)?.toLong() ?: 0L
                            val weeklyMinutes = (appData["weeklyMinutes"] as? Number)?.toLong() ?: 0L
                            val packageName = (appData["packageName"] as? String) ?: "unknown.package.$appName"
                            
                            // Add a sample data indicator to the name if needed
                            val displayName = if (isSampleData && !appName.contains("[SAMPLE]")) {
                                "$appName [SAMPLE]"
                            } else {
                                appName
                            }
                            
                            newAppUsageList.add(
                                AppUsage(
                                    name = displayName,
                                    packageName = packageName,
                                    dailyMinutes = dailyMinutes,
                                    weeklyMinutes = weeklyMinutes
                                )
                            )
                        }
                        
                        // If we couldn't use the total from the document, calculate it from app list
                        if (!useTotalFromDoc) {
                            totalAppUsage = newAppUsageList.sumOf { it.weeklyMinutes }
                        }
                        
                        // Update the app usage list
                        appUsageList = newAppUsageList
                        Log.e(TAG, "Updated app usage list with ${newAppUsageList.size} apps")
                        
                        // Clear loading and error states
                        isLoading = false
                        if (newAppUsageList.isNotEmpty()) {
                            errorMessage = null
                        }
                    } else {
                        Log.e(TAG, "No apps data found in snapshot")
                        errorMessage = "No app usage data available"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing app data from snapshot", e)
                    errorMessage = "Error processing app data: ${e.message}"
                }
            } else {
                Log.e(TAG, "No app usage data document exists")
                errorMessage = "No app usage data available"
            }
        }
        
        // Return the cleanup function
        onDispose {
            listener.remove()
            Log.e(TAG, "Removed app usage snapshot listener")
        }
    }
    
    // Handle initial loading for child devices
    if (isChildDevice) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                try {
                    val localData = getAppUsageStats(context)
                    
                    if (localData.isNotEmpty()) {
                        Log.e(TAG, "Initial load: Retrieved ${localData.size} app usage records from device")
                        
                        // Calculate total screen time
                        totalAppUsage = localData.sumOf { it.weeklyMinutes }
                        
                        // Store the data to Firestore
                        updateFirestoreWithUsageData(firestore, childId, localData, false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting initial app usage data", e)
                } finally {
                    // Snapshot listener will handle the rest
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$childName's App Usage") },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading app usage data...", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else if (appUsageList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "No app usage data available",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isSampleData) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "⚠️ SHOWING SAMPLE DATA",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                errorMessage ?: if (isChildDevice) {
                                    "The app was unable to collect real usage data from this device. " +
                                    "Please make sure usage stats permission is granted in device settings."
                                } else {
                                    "No real usage data available for this child. " +
                                    "The child's device may need to be set up properly."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else {
                    // Show weekly total screen time
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "📱 Weekly App Usage",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                formatUsageTime(totalAppUsage),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            
                            // Show last updated time if available
                            lastUpdated?.let { timestamp ->
                                Spacer(modifier = Modifier.height(8.dp))
                                val date = Date(timestamp)
                                val dateFormat = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                                Text(
                                    "Last updated: ${dateFormat.format(date)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                
                Text(
                    "App Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                )
                
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                    items(appUsageList.sortedByDescending { it.dailyMinutes }) { appUsage ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = appUsage.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatUsageTime(appUsage.dailyMinutes),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "This Week",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatUsageTime(appUsage.weeklyMinutes),
                                    style = MaterialTheme.typography.bodyMedium
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

private suspend fun getAppUsageFromFirestore(firestore: FirebaseFirestore, childId: String): List<AppUsage> {
    val TAG = "FirestoreAppUsage"
    Log.d(TAG, "Fetching app usage data from Firestore for child: $childId")
    
    try {
        val result = mutableListOf<AppUsage>()
        var isSampleData = false
        
        // Get the app usage data from Firestore
        try {
            val document = firestore.collection("appUsage")
                .document(childId)
                .collection("stats")
                .document("daily")
                .get()
                .await()
            
            if (!document.exists()) {
                Log.e(TAG, "No app usage data found in Firestore for child: $childId")
                return emptyList()
            }
            
            // Check if this is sample data
            val dataSource = document.getString("dataSource")
            if (dataSource == "SAMPLE_DATA") {
                Log.w(TAG, "⚠️ Firestore contains SAMPLE data for this child")
                isSampleData = true
            } else {
                Log.d(TAG, "✅ Firestore contains REAL device data for this child")
            }
            
            @Suppress("UNCHECKED_CAST")
            val appsData = document.get("apps") as? Map<String, Map<String, Any>> 
                ?: return emptyList()
            
            Log.d(TAG, "Found ${appsData.size} app records in Firestore")
            
            for ((appName, appData) in appsData) {
                val dailyMinutes = (appData["dailyMinutes"] as? Number)?.toLong() ?: 0L
                val weeklyMinutes = (appData["weeklyMinutes"] as? Number)?.toLong() ?: 0L
                val packageName = (appData["packageName"] as? String) ?: "unknown.package.$appName"
                val appIsSampleData = (appData["isSampleData"] as? Boolean) ?: isSampleData
                
                // Skip apps with zero usage time
                if (weeklyMinutes <= 0) {
                    Log.d(TAG, "Skipping app with zero usage: $appName")
                    continue
                }
                
                // Add a sample data indicator to the name if needed
                val displayName = if (appIsSampleData && !appName.contains("[SAMPLE]")) {
                    "$appName [SAMPLE]"
                } else {
                    appName
                }
                
                result.add(
                    AppUsage(
                        name = displayName,
                        packageName = packageName,
                        dailyMinutes = dailyMinutes,
                        weeklyMinutes = weeklyMinutes
                    )
                )
                
                Log.d(TAG, "App from Firestore: $displayName - Daily: $dailyMinutes min, Weekly: $weeklyMinutes min")
            }
            
            Log.d(TAG, "Successfully fetched ${result.size} app usage records with non-zero usage from Firestore")
            return result
        } catch (e: Exception) {
            if (e.message?.contains("permission_denied") == true) {
                Log.e(TAG, "Firestore permission denied. Make sure the security rules have been updated.", e)
                throw Exception("Firestore permission denied: You need to update the Firestore security rules for the appUsage collection. Please wait a few minutes for the rules to propagate or contact the app developer.")
            } else {
                throw e
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching app usage data from Firestore", e)
        throw e
    }
}

private fun updateFirestoreWithUsageData(firestore: FirebaseFirestore, childId: String, usageData: List<AppUsage>, isSampleData: Boolean = false) {
    val TAG = "FirestoreUpdate"
    
    if (isSampleData) {
        Log.w(TAG, "⚠️ CAUTION: Updating Firestore with SAMPLE data for child: $childId")
    } else {
        Log.d(TAG, "✅ Updating Firestore with REAL device data for child: $childId")
        
        // Log each app we're storing for debugging
        Log.d(TAG, "Apps being stored to Firestore:")
        usageData.forEachIndexed { index, app ->
            Log.d(TAG, "$index: ${app.name}, Daily: ${app.dailyMinutes}m, Weekly: ${app.weeklyMinutes}m")
        }
    }
    
    Log.d(TAG, "Updating Firestore with ${usageData.size} app usage records for child: $childId")
    
    try {
        if (childId.isEmpty()) {
            Log.e(TAG, "Cannot update Firestore: childId is empty")
            return
        }

        val appsData = usageData.associate { appUsage ->
            appUsage.name to mapOf(
                "dailyMinutes" to appUsage.dailyMinutes,
                "weeklyMinutes" to appUsage.weeklyMinutes,
                "lastUpdated" to Calendar.getInstance().timeInMillis,
                "packageName" to appUsage.packageName,
                "isSampleData" to isSampleData
            )
        }
        
        val dataToStore = mapOf(
            "apps" to appsData,
            "lastUpdated" to Calendar.getInstance().timeInMillis,
            "dataSource" to if (isSampleData) "SAMPLE_DATA" else "REAL_DEVICE_DATA",
            "totalDailyMinutes" to usageData.sumOf { it.dailyMinutes },
            "totalAppWeeklyUsage" to usageData.sumOf { it.weeklyMinutes },
            "appCount" to usageData.size
        )
        
        Log.d(TAG, "Storing app usage data to Firestore with metadata: totalDaily=${dataToStore["totalDailyMinutes"]}, totalAppWeekly=${dataToStore["totalAppWeeklyUsage"]}, count=${dataToStore["appCount"]}")
        
        // Full Firestore path for debugging
        val firestorePath = "appUsage/$childId/stats/daily"
        Log.d(TAG, "Writing to Firestore path: $firestorePath")
        
        // Update app usage data
        firestore.collection("appUsage")
            .document(childId)
            .collection("stats")
            .document("daily")
            .set(dataToStore)
            .addOnSuccessListener {
                Log.d(TAG, "App usage data successfully updated in Firestore")
                
                // Also update the screen time data
                val totalDailyMinutes = usageData.sumOf { it.dailyMinutes }
                val screenTimeMs = totalDailyMinutes * 60 * 1000 // Convert minutes to milliseconds
                
                firestore.collection("screenTime")
                    .document(childId)
                    .set(mapOf(
                        "screenTime" to screenTimeMs,
                        "lastUpdated" to Calendar.getInstance().timeInMillis
                    ))
                    .addOnSuccessListener {
                        Log.d(TAG, "Screen time data successfully updated in Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error updating screen time data", e)
                    }
                
                // Verify data was written by reading it back
                firestore.collection("appUsage")
                    .document(childId)
                    .collection("stats")
                    .document("daily")
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val appsMap = document.get("apps") as? Map<*, *>
                            val appCount = appsMap?.size ?: 0
                            val totalWeekly = document.getLong("totalAppWeeklyUsage") ?: 0
                            
                            Log.d(TAG, "Verified data was written: ${appCount} apps, ${totalWeekly}m total app weekly usage")
                            
                            // Check if we wrote sample or real data
                            val dataSource = document.getString("dataSource") ?: "UNKNOWN"
                            if (dataSource == "SAMPLE_DATA") {
                                Log.w(TAG, "⚠️ VERIFICATION: Sample data was written to Firestore")
                            } else {
                                Log.d(TAG, "✅ VERIFICATION: Real device data was written to Firestore")
                            }
                        } else {
                            Log.e(TAG, "Verification failed: Document does not exist after writing!")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error verifying written data: ${e.message}", e)
                    }
            }
            .addOnFailureListener { e ->
                if (e.message?.contains("permission_denied") == true) {
                    Log.e(TAG, "Firestore permission denied when updating app usage data. Security rules may need updating.", e)
                } else {
                    Log.e(TAG, "Error updating app usage data in Firestore", e)
                }
            }
    } catch (e: Exception) {
        Log.e(TAG, "Error updating Firestore with usage data", e)
    }
}

private fun getFallbackData(): List<AppUsage> {
    val TAG = "AppUsageFallback"
    Log.w(TAG, "⚠️ Creating FALLBACK/SAMPLE app usage data instead of real data")
    return listOf(
        AppUsage("Facebook [SAMPLE]", "com.facebook.katana.SAMPLE", 120L, 840L),
        AppUsage("Instagram [SAMPLE]", "com.instagram.android.SAMPLE", 90L, 630L),
        AppUsage("Twitter [SAMPLE]", "com.twitter.android.SAMPLE", 60L, 420L),
        AppUsage("WhatsApp [SAMPLE]", "com.whatsapp.SAMPLE", 45L, 315L),
        AppUsage("YouTube [SAMPLE]", "com.google.android.youtube.SAMPLE", 30L, 210L),
        AppUsage("Spotify [SAMPLE]", "com.spotify.music.SAMPLE", 20L, 140L),
        AppUsage("Snapchat [SAMPLE]", "com.snapchat.android.SAMPLE", 15L, 105L),
        AppUsage("TikTok [SAMPLE]", "com.zhiliaoapp.musically.SAMPLE", 10L, 70L)
    )
}

private fun getAppUsageStats(context: Context): List<AppUsage> {
    val TAG = "AppUsageStats"
    Log.e(TAG, "========== STARTING APP USAGE DATA COLLECTION ==========")
    Log.e(TAG, "Getting app usage stats from device")
    
    try {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager
        val calendar = Calendar.getInstance()
        
        // Get today's start time
        val todayStart = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        // Get week's start time - use a much longer period to ensure we catch data
        val weekStart = calendar.apply {
            add(Calendar.DAY_OF_YEAR, -30)  // Look back 30 days instead of just 7
        }.timeInMillis
        
        val now = System.currentTimeMillis()
        
        Log.e(TAG, "Querying usage events from ${Date(weekStart)} to ${Date(now)}")
        
        // Make sure we have the right permission
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        if (mode != AppOpsManager.MODE_ALLOWED) {
            Log.e(TAG, "❌ DO NOT HAVE USAGE STATS PERMISSION! This is likely why we're not getting real data.")
            Log.e(TAG, "Please ensure the user has granted usage stats permission in device settings.")
            return emptyList()
        } else {
            Log.e(TAG, "✅ Have usage stats permission")
        }
        
        // Get some device info for debugging
        val deviceManufacturer = android.os.Build.MANUFACTURER
        val deviceModel = android.os.Build.MODEL
        val androidVersion = android.os.Build.VERSION.RELEASE
        Log.e(TAG, "Device info: $deviceManufacturer $deviceModel, Android $androidVersion")
        
        // First get usage stats from direct query
        val dailyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, todayStart, now)
        val weeklyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, weekStart, now)
        val monthlyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, weekStart, now)
        
        // Combine all stats
        val allStats = (dailyStats + weeklyStats + monthlyStats).distinctBy { it.packageName }
        Log.e(TAG, "Found ${allStats.size} apps via direct stats query")
        
        // Process stats
        val dailyUsage = mutableMapOf<String, Long>()
        val weeklyUsage = mutableMapOf<String, Long>()
        
        if (allStats.isNotEmpty()) {
            for (stat in allStats) {
                val packageName = stat.packageName
                val totalTimeMs = stat.totalTimeInForeground
                
                if (totalTimeMs > 0) {
                    weeklyUsage[packageName] = totalTimeMs
                    
                    // Estimate daily usage as 1/7 of weekly or use actual if available
                    if (dailyStats.any { it.packageName == packageName }) {
                        val dailyStat = dailyStats.first { it.packageName == packageName }
                        dailyUsage[packageName] = dailyStat.totalTimeInForeground
                    } else {
                        dailyUsage[packageName] = totalTimeMs / 7
                    }
                }
            }
        }
        
        // Always use the event-based approach to catch recent activity
        val events = usageStatsManager.queryEvents(weekStart, now)
        
        if (events == null) {
            Log.e(TAG, "Failed to get usage events - null returned")
        } else if (!events.hasNextEvent()) {
            Log.e(TAG, "No usage events found despite having permission. The device may not track usage stats.")
        } else {
            val event = UsageEvents.Event()
            
            // Track app usage
            val lastEventTime = mutableMapOf<String, Long>()
            val lastEventType = mutableMapOf<String, Int>()
            val recentlyUsedApps = mutableSetOf<String>() // Track recently used apps even without duration
            
            var eventCount = 0
            var foregroundEvents = 0
            var backgroundEvents = 0
            var otherEvents = 0
            
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                eventCount++
                
                val packageName = event.packageName
                val eventTime = event.timeStamp
                val eventType = event.eventType
                
                // Track event types for debugging
                when (eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> foregroundEvents++
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> backgroundEvents++
                    else -> otherEvents++
                }
                
                // Track any app that was brought to foreground recently as "used"
                if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && eventTime > (now - 10 * 60 * 1000)) {
                    // If app was used in the last 10 minutes, consider it used even without duration
                    recentlyUsedApps.add(packageName)
                    Log.e(TAG, "Recently used app detected: $packageName")
                }
                
                // Handle MOVE_TO_FOREGROUND and MOVE_TO_BACKGROUND events
                if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || 
                    eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    
                    if (lastEventType[packageName] == UsageEvents.Event.MOVE_TO_FOREGROUND && 
                        eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                        
                        val duration = eventTime - (lastEventTime[packageName] ?: eventTime)
                        
                        // Update weekly usage
                        weeklyUsage[packageName] = (weeklyUsage[packageName] ?: 0) + duration
                        
                        // Update daily usage if the event was today
                        if (lastEventTime[packageName] ?: 0 >= todayStart) {
                            dailyUsage[packageName] = (dailyUsage[packageName] ?: 0) + duration
                        }
                    }
                    
                    lastEventTime[packageName] = eventTime
                    lastEventType[packageName] = eventType
                }
            }
            
            // Add recently used apps that don't have duration yet
            for (packageName in recentlyUsedApps) {
                if (!weeklyUsage.containsKey(packageName) || weeklyUsage[packageName] == 0L) {
                    // Add with a minimal duration of 1 minute to ensure it shows up
                    weeklyUsage[packageName] = 60 * 1000L // 1 minute in milliseconds
                    dailyUsage[packageName] = 60 * 1000L // 1 minute in milliseconds
                    Log.e(TAG, "Added minimal usage for recently used app: $packageName")
                }
            }
            
            Log.e(TAG, "Processed $eventCount events (Foreground: $foregroundEvents, Background: $backgroundEvents, Other: $otherEvents)")
        }
        
        Log.e(TAG, "Found ${weeklyUsage.size} apps with usage data")
        
        // Convert to AppUsage objects and don't filter system apps yet
        val result = mutableListOf<AppUsage>()
        
        weeklyUsage.keys.forEach { packageName ->
            try {
                // Try to get app info
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                
                val daily = TimeUnit.MILLISECONDS.toMinutes(dailyUsage[packageName] ?: 0)
                val weekly = TimeUnit.MILLISECONDS.toMinutes(weeklyUsage[packageName] ?: 0)
                
                // Only include apps with at least 5 minutes of weekly usage
                if (weekly >= 5) {
                    result.add(
                        AppUsage(
                            name = if (isSystemApp) "$appName (System)" else appName,
                            packageName = packageName,
                            dailyMinutes = Math.max(daily, 1), // Ensure at least 1 minute
                            weeklyMinutes = weekly
                        )
                    )
                    
                    Log.e(TAG, "Added app: ${appName}, isSystem: $isSystemApp, Daily: $daily min, Weekly: $weekly min")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing app $packageName: ${e.message}")
            }
        }
        
        // Filter out system apps for the final result, but only if we have enough user apps
        val userApps = result.filter { !it.name.contains("(System)") }
        val finalResult = if (userApps.size >= 3) userApps else result
        
        Log.e(TAG, "Final result: ${finalResult.size} apps")
        
        Log.e(TAG, "========== COMPLETED APP USAGE DATA COLLECTION ==========")
        return finalResult
    } catch (e: Exception) {
        Log.e(TAG, "Error getting app usage stats", e)
        Log.e(TAG, "Exception details:", e)
        throw e
    }
}

private fun formatUsageTime(minutes: Long): String {
    val hours = TimeUnit.MINUTES.toHours(minutes)
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 -> "$hours h $remainingMinutes m"
        else -> "$remainingMinutes m"
    }
} 