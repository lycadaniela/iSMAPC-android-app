package com.example.ismapc

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth
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
                Log.e(TAG, "Error listening to app usage updates", error)
                errorMessage = "Error loading app usage data"
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                try {
                    // Get last updated timestamp
                    lastUpdated = snapshot.getLong("lastUpdated")
                    
                    // Process apps data
                    @Suppress("UNCHECKED_CAST")
                    val appsData = snapshot.get("apps") as? Map<String, Map<String, Any>>
                    if (appsData != null) {
                        val newAppUsageList = mutableListOf<AppUsage>()
                        
                        for ((appName, appData) in appsData) {
                            val dailyMinutes = (appData["dailyMinutes"] as? Number)?.toLong() ?: 0L
                            val weeklyMinutes = (appData["weeklyMinutes"] as? Number)?.toLong() ?: 0L
                            val packageName = (appData["packageName"] as? String) ?: "unknown.package.$appName"
                            
                            // Include all apps with any usage
                            if (dailyMinutes > 0 || weeklyMinutes > 0) {
                                newAppUsageList.add(
                                    AppUsage(
                                        name = appName,
                                        packageName = packageName,
                                        dailyMinutes = dailyMinutes,
                                        weeklyMinutes = weeklyMinutes
                                    )
                                )
                            }
                        }
                        
                        // Calculate total usage
                        totalAppUsage = newAppUsageList.sumOf { it.weeklyMinutes }
                        
                        // Update the app usage list, sorted alphabetically by name
                        appUsageList = newAppUsageList.sortedBy { it.name }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            (context as? ComponentActivity)?.finish()
                            Log.d("AppUsage", "Back button pressed")
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
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "App Usage",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFE0852D),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track and manage app usage",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF424242),
                    textAlign = TextAlign.Center
                )
            }

            // Total Usage Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                        text = "Total Usage",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE0852D),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatUsageTime(totalAppUsage),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (lastUpdated != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Last updated: ${formatLastUpdated(lastUpdated!!)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // App Usage List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFE0852D)
                    )
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else if (appUsageList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No app usage data available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "App Usage Details",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE0852D),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    appUsageList.forEach { appUsage ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = appUsage.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Daily: ${formatUsageTime(appUsage.dailyMinutes)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Weekly",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF4A4A4A)
                                    )
                                    Text(
                                        text = formatUsageTime(appUsage.weeklyMinutes),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFE0852D)
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

private fun formatLastUpdated(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> "Just now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
        else -> "${diff / (24 * 60 * 60 * 1000)} days ago"
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

private suspend fun getAppUsageStats(context: Context, firestore: FirebaseFirestore, childId: String): List<AppUsage> {
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
        
        // Get the start of the current week (Sunday)
        val weekStart = calendar.apply {
            // Go back to the start of the week (Sunday)
            add(Calendar.DAY_OF_WEEK, -(get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val now = System.currentTimeMillis()
        
        Log.e(TAG, "⏰ Time ranges:")
        Log.e(TAG, "  - Today start: ${Date(todayStart)}")
        Log.e(TAG, "  - Week start: ${Date(weekStart)}")
        Log.e(TAG, "  - Now: ${Date(now)}")
        Log.e(TAG, "  - Current day of week: ${calendar.get(Calendar.DAY_OF_WEEK)}")
        
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
        
        // Get device info for debugging
        val deviceManufacturer = android.os.Build.MANUFACTURER
        val deviceModel = android.os.Build.MODEL
        val androidVersion = android.os.Build.VERSION.RELEASE
        Log.e(TAG, "Device info: $deviceManufacturer $deviceModel, Android $androidVersion")
        
        // Initialize usage maps
        val dailyUsage = mutableMapOf<String, Long>()
        val weeklyUsage = mutableMapOf<String, Long>()
        
        // Get daily stats directly from UsageStatsManager
        val dailyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, todayStart, now)
        Log.e(TAG, "📊 Found ${dailyStats?.size ?: 0} apps with daily stats")
        
        // Process daily stats
        dailyStats?.forEach { stat ->
            if (stat.totalTimeInForeground > 0) {
                dailyUsage[stat.packageName] = stat.totalTimeInForeground
                Log.e(TAG, "📱 Daily usage for ${stat.packageName}: ${stat.totalTimeInForeground/1000}s")
            }
        }
        
        // Get usage events for the entire week
        val events = usageStatsManager.queryEvents(weekStart, now)
        if (events != null) {
            val event = UsageEvents.Event()
            val lastEventTime = mutableMapOf<String, Long>()
            val lastEventType = mutableMapOf<String, Int>()
            val recentlyUsedApps = mutableSetOf<String>()
            
            var eventCount = 0
            var foregroundEvents = 0
            var backgroundEvents = 0
            
            Log.e(TAG, "📊 Processing events from ${Date(weekStart)} to ${Date(now)}")
            
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                eventCount++
                
                val packageName = event.packageName
                val eventTime = event.timeStamp
                val eventType = event.eventType
                
                when (eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        foregroundEvents++
                        // Track recently used apps
                        if (eventTime > (now - 10 * 60 * 1000)) {
                            recentlyUsedApps.add(packageName)
                        }
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> backgroundEvents++
                }
                
                // Calculate usage duration
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
            
            Log.e(TAG, "📊 Processed $eventCount events (Foreground: $foregroundEvents, Background: $backgroundEvents)")
            
            // Add minimal usage for recently used apps
            for (packageName in recentlyUsedApps) {
                if (!weeklyUsage.containsKey(packageName) || weeklyUsage[packageName] == 0L) {
                    weeklyUsage[packageName] = 60 * 1000L // 1 minute
                    dailyUsage[packageName] = 60 * 1000L // 1 minute
                    Log.e(TAG, "📱 Added minimal usage for recently used app: $packageName")
                }
            }
        }
        
        // Convert to AppUsage objects
        val result = mutableListOf<AppUsage>()
        val allPackages = (dailyUsage.keys + weeklyUsage.keys).toSet()
        
        for (packageName in allPackages) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                
                // Get daily usage directly from UsageStatsManager for this specific app
                val appDailyStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    todayStart,
                    now
                )?.find { it.packageName == packageName }
                
                val daily = if (appDailyStats != null) {
                    TimeUnit.MILLISECONDS.toMinutes(appDailyStats.totalTimeInForeground)
                } else {
                    TimeUnit.MILLISECONDS.toMinutes(dailyUsage[packageName] ?: 0)
                }
                
                val weekly = TimeUnit.MILLISECONDS.toMinutes(weeklyUsage[packageName] ?: 0)
                
                // Include all apps with any usage
                if (daily > 0 || weekly > 0) {
                    result.add(
                        AppUsage(
                            name = if (isSystemApp) "$appName (System)" else appName,
                            packageName = packageName,
                            dailyMinutes = daily,
                            weeklyMinutes = weekly
                        )
                    )
                    Log.e(TAG, "📱 Added app: $appName - Daily: $daily min, Weekly: $weekly min")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing app $packageName: ${e.message}")
            }
        }
        
        // Filter out system apps if we have enough user apps
        val userApps = result.filter { !it.name.contains("(System)") }
        val finalResult = if (userApps.size >= 3) userApps else result
        
        Log.e(TAG, "✅ Final result: ${finalResult.size} apps")
        Log.e(TAG, "========== COMPLETED APP USAGE DATA COLLECTION ==========")
        return finalResult
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error getting app usage stats", e)
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