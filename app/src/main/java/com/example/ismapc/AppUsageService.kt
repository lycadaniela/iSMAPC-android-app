package com.example.ismapc

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.TimeUnit

class AppUsageService : Service() {
    private val TAG = "AppUsageService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "AppUsageServiceChannel"
    
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var isRunning = false
    private var uploadJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "AppUsageService created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "AppUsageService started")
        
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "No user logged in, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Show a persistent notification to keep the service running
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Usage Monitoring")
            .setContentText("Tracking app usage for parental controls")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
        
        if (!isRunning) {
            isRunning = true
            startUsageDataCollection()
        }
        
        // If the service is killed, restart it
        return START_STICKY
    }
    
    private fun startUsageDataCollection() {
        // Cancel any existing job
        uploadJob?.cancel()
        
        uploadJob = serviceScope.launch {
            try {
                while (isActive) {
                    try {
                        Log.e(TAG, "Starting scheduled app usage data collection")
                        
                        val currentUser = auth.currentUser
                        if (currentUser == null) {
                            Log.e(TAG, "No user logged in, stopping collection")
                            break
                        }
                        
                        val childId = currentUser.uid
                        
                        // Check for usage stats permission
                        if (!hasUsageStatsPermission()) {
                            Log.e(TAG, "Missing usage stats permission, cannot collect data")
                            delay(30 * 1000L) // Check again in 30 seconds during testing
                            continue
                        }
                        
                        // Collect usage data
                        val appUsage = getAppUsageStats()
                        
                        if (appUsage.isEmpty()) {
                            Log.e(TAG, "No app usage data collected")
                        } else {
                            Log.e(TAG, "Collected usage data for ${appUsage.size} apps")
                            
                            // Create a formatted list of all apps for the log
                            val appsList = appUsage.joinToString("\n") { 
                                "${it.name}: ${it.dailyMinutes} min today, ${it.weeklyMinutes} min this week"
                            }
                            Log.e(TAG, "Apps usage data:\n$appsList")
                            
                            // Upload to Firestore
                            updateFirestoreWithUsageData(childId, appUsage)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in usage data collection cycle", e)
                    }
                    
                    // Wait for the next collection interval (30 seconds during testing)
                    delay(30 * 1000L)
                }
            } catch (e: CancellationException) {
                // Job was cancelled - normal behavior
                Log.e(TAG, "Usage data collection job cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in usage data collection job", e)
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
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    private fun getAppUsageStats(): List<AppUsage> {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val packageManager = packageManager
            val calendar = Calendar.getInstance()
            
            // Get today's start time
            val todayStart = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            // Look back 30 days
            val weekStart = calendar.apply {
                add(Calendar.DAY_OF_YEAR, -30)
            }.timeInMillis
            
            val now = System.currentTimeMillis()
            
            // Try both approaches to maximize data collection
            
            // 1. Try the direct stats query first (works better on some devices)
            val dailyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, todayStart, now)
            val weeklyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, weekStart, now)
            val monthlyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, weekStart, now)
            
            // Combine all stats
            val allStats = (dailyStats + weeklyStats + monthlyStats).distinctBy { it.packageName }
            
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
            
            // Always use the event-based approach as well to catch the most recent app usage
            // that might not have been included in the stats query yet
            val events = usageStatsManager.queryEvents(weekStart, now)
            
            if (events != null && events.hasNextEvent()) {
                val event = UsageEvents.Event()
                val lastEventTime = mutableMapOf<String, Long>()
                val lastEventType = mutableMapOf<String, Int>()
                val recentlyUsedApps = mutableSetOf<String>() // Track recently used apps even without duration
                
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    
                    val packageName = event.packageName
                    val eventTime = event.timeStamp
                    val eventType = event.eventType
                    
                    // Track any app that was brought to foreground as "used" - even without a background event
                    if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && eventTime > (now - 5 * 60 * 1000)) {
                        // If app was used in the last 5 minutes, consider it used even without duration
                        recentlyUsedApps.add(packageName)
                    }
                    
                    if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || 
                        eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                        
                        if (lastEventType[packageName] == UsageEvents.Event.MOVE_TO_FOREGROUND && 
                            eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                            
                            val duration = eventTime - (lastEventTime[packageName] ?: eventTime)
                            
                            weeklyUsage[packageName] = (weeklyUsage[packageName] ?: 0) + duration
                            
                            if (lastEventTime[packageName] ?: 0 >= todayStart) {
                                dailyUsage[packageName] = (dailyUsage[packageName] ?: 0) + duration
                            }
                        }
                        
                        lastEventTime[packageName] = eventTime
                        lastEventType[packageName] = eventType
                    }
                }
                
                // Add recently used apps that don't have duration (just opened)
                for (packageName in recentlyUsedApps) {
                    if (!weeklyUsage.containsKey(packageName)) {
                        // Add with a minimal duration of 1 minute to ensure it shows up
                        weeklyUsage[packageName] = 60 * 1000L // 1 minute in milliseconds
                        dailyUsage[packageName] = 60 * 1000L // 1 minute in milliseconds
                    }
                }
            }
            
            // Convert to AppUsage objects
            val result = mutableListOf<AppUsage>()
            
            for (packageName in weeklyUsage.keys) {
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    
                    val daily = TimeUnit.MILLISECONDS.toMinutes(dailyUsage[packageName] ?: 0)
                    val weekly = TimeUnit.MILLISECONDS.toMinutes(weeklyUsage[packageName] ?: 0)
                    
                    // Include any non-system app with any usage time at all
                    if (!isSystemApp) {
                        result.add(
                            AppUsage(
                                name = appName,
                                packageName = packageName,
                                dailyMinutes = Math.max(daily, 1), // Ensure at least 1 minute
                                weeklyMinutes = Math.max(weekly, 1) // Ensure at least 1 minute
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Skip apps we can't resolve
                }
            }
            
            Log.e(TAG, "Service collected ${result.size} app usage records")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app usage stats in service", e)
            return emptyList()
        }
    }
    
    private fun updateFirestoreWithUsageData(childId: String, usageData: List<AppUsage>) {
        Log.e(TAG, "🔄 Updating Firestore with app usage data for childId: $childId")
        
        // Don't continue if there's no data
        if (usageData.isEmpty()) {
            Log.e(TAG, "⚠️ No usage data to upload")
            return
        }
        
        // Log the data we're about to upload
        Log.e(TAG, "📊 Uploading data for ${usageData.size} apps:")
        usageData.forEachIndexed { index, app ->
            Log.e(TAG, "  $index. ${app.name}: daily=${app.dailyMinutes}m, weekly=${app.weeklyMinutes}m")
        }
        
        try {
            // Create a map of app usage data
            val appsData = usageData.associate { appUsage ->
                appUsage.name to mapOf(
                    "packageName" to appUsage.packageName,
                    "dailyMinutes" to appUsage.dailyMinutes,
                    "weeklyMinutes" to appUsage.weeklyMinutes,
                    "lastUpdated" to System.currentTimeMillis()
                )
            }
            
            // Calculate total usage
            val totalDailyMinutes = usageData.sumOf { it.dailyMinutes }
            val totalWeeklyMinutes = usageData.sumOf { it.weeklyMinutes }
            
            // Create the final data to store
            val dataToStore = hashMapOf(
                "apps" to appsData,
                "totalDailyMinutes" to totalDailyMinutes,
                "totalAppWeeklyUsage" to totalWeeklyMinutes,
                "lastUpdated" to System.currentTimeMillis(),
                "appCount" to usageData.size,
                "dataSource" to "REAL_DEVICE_DATA"
            )
            
            // Store the data to Firestore
            Log.e(TAG, "🔥 Writing to Firestore path: appUsage/$childId/stats/daily")
            Log.e(TAG, "📊 Total stats: daily=${totalDailyMinutes}m, weekly=${totalWeeklyMinutes}m, count=${usageData.size}")
            
            // Use a transaction to ensure data is properly saved
            firestore.runTransaction { transaction ->
                val docRef = firestore.collection("appUsage")
                    .document(childId)
                    .collection("stats")
                    .document("daily")
                
                // Set data in transaction
                transaction.set(docRef, dataToStore)
            }.addOnSuccessListener {
                Log.e(TAG, "✅ Successfully updated app usage data in Firestore")
                
                // Verify the data was written correctly by reading it back
                firestore.collection("appUsage")
                    .document(childId)
                    .collection("stats")
                    .document("daily")
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            @Suppress("UNCHECKED_CAST")
                            val apps = document.get("apps") as? Map<String, Any>
                            val count = apps?.size ?: 0
                            val total = document.getLong("totalAppWeeklyUsage") ?: 0
                            
                            Log.e(TAG, "✅ Verification successful - data was saved: count=$count, totalWeekly=$total")
                        } else {
                            Log.e(TAG, "❌ Verification failed - document doesn't exist after saving")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Verification failed: ${e.message}", e)
                    }
            }.addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to update app usage data: ${e.message}", e)
                if (e.message?.contains("permission_denied") == true) {
                    Log.e(TAG, "❌ Permission denied - check Firestore security rules")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating Firestore with usage data: ${e.message}", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "App Usage Service"
            val descriptionText = "Monitors app usage for parental controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        uploadJob?.cancel()
        serviceScope.cancel()
        Log.e(TAG, "AppUsageService destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 