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
            
            // Get today's start time (midnight)
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.timeInMillis
            
            // Get 30 days ago for weekly stats
            val weekStart = calendar.apply {
                add(Calendar.DAY_OF_YEAR, -30)
            }.timeInMillis
            
            val now = System.currentTimeMillis()
            
            Log.e(TAG, "⏰ EXACT MATCH FIX: Today starts at ${Date(todayStart)}, now is ${Date(now)}")
            
            // Initialize maps for storing usage data
            val dailyUsage = mutableMapOf<String, Long>()
            val weeklyUsage = mutableMapOf<String, Long>()
            
            // 1. ONLY process events for exact matching with device settings
            // This is the most accurate method and matches what device settings shows
            val events = usageStatsManager.queryEvents(todayStart, now)
            
            if (events != null && events.hasNextEvent()) {
                var eventCount = 0
                val event = UsageEvents.Event()
                val lastEventTime = mutableMapOf<String, Long>()
                val lastEventType = mutableMapOf<String, Int>()
                
                Log.e(TAG, "🔄 Processing ONLY today's events for exact time tracking")
                
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    eventCount++
                    
                    val packageName = event.packageName
                    val eventTime = event.timeStamp
                    val eventType = event.eventType
                    
                    // Skip any events from before today
                    if (eventTime < todayStart) {
                        continue
                    }
                    
                    // Only track FOREGROUND and BACKGROUND events
                    if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || 
                        eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                        
                        // Calculate duration when app moves to background after being in foreground
                        if (lastEventType[packageName] == UsageEvents.Event.MOVE_TO_FOREGROUND && 
                            eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                            
                            val duration = eventTime - (lastEventTime[packageName] ?: eventTime)
                            
                            // Add to both weekly and daily usage
                            weeklyUsage[packageName] = (weeklyUsage[packageName] ?: 0) + duration
                            dailyUsage[packageName] = (dailyUsage[packageName] ?: 0) + duration
                            
                            Log.e(TAG, "📱 App ${packageName}: Used for ${TimeUnit.MILLISECONDS.toMinutes(duration)}m")
                        }
                        
                        lastEventTime[packageName] = eventTime
                        lastEventType[packageName] = eventType
                    }
                }
                
                Log.e(TAG, "✅ Processed $eventCount events from today")
                
                // Handle apps still in foreground
                val currentlyInForeground = lastEventType.filter { it.value == UsageEvents.Event.MOVE_TO_FOREGROUND }
                for ((packageName, _) in currentlyInForeground) {
                    val foregroundStart = lastEventTime[packageName] ?: todayStart
                    if (foregroundStart >= todayStart) {
                        val duration = now - foregroundStart
                        
                        weeklyUsage[packageName] = (weeklyUsage[packageName] ?: 0) + duration
                        dailyUsage[packageName] = (dailyUsage[packageName] ?: 0) + duration
                        
                        Log.e(TAG, "📱 App ${packageName}: Still in foreground for ${TimeUnit.MILLISECONDS.toMinutes(duration)}m")
                    }
                }
            } else {
                Log.e(TAG, "⚠️ No usage events found for today")
            }
            
            // Get additional weekly data (for apps not used today)
            val weeklyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, weekStart, now)
            weeklyStats.forEach { stat ->
                val packageName = stat.packageName
                val weeklyTimeMs = stat.totalTimeInForeground
                
                // Only update weekly usage if it's larger than what we already calculated
                if (weeklyTimeMs > 0 && (weeklyTimeMs > (weeklyUsage[packageName] ?: 0))) {
                    weeklyUsage[packageName] = weeklyTimeMs
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
                    
                    // Include non-system apps with at least 5 minutes weekly usage
                    if (!isSystemApp && weekly >= 5) {
                        result.add(
                            AppUsage(
                                name = appName,
                                packageName = packageName,
                                dailyMinutes = daily,
                                weeklyMinutes = weekly
                            )
                        )
                        
                        Log.e(TAG, "📊 Final data: ${appName} - Daily: ${daily}m, Weekly: ${weekly}m")
                    }
                } catch (e: Exception) {
                    // Skip apps we can't resolve
                }
            }
            
            Log.e(TAG, "🏁 Finished collecting ${result.size} app usage records")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting app usage stats", e)
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
        
        // Check if we need to reset daily usage
        val needsDailyReset = needsDailyReset(childId)
        if (needsDailyReset) {
            Log.e(TAG, "🔄 Daily reset needed - resetting daily usage counts")
        }
        
        // Log the data we're about to upload
        Log.e(TAG, "📊 Uploading data for ${usageData.size} apps:")
        usageData.forEachIndexed { index, app ->
            Log.e(TAG, "  $index. ${app.name}: daily=${app.dailyMinutes}m, weekly=${app.weeklyMinutes}m")
        }
        
        try {
            // Create a map of app usage data
            val appsData = usageData.associate { appUsage ->
                val dailyMinutes = if (needsDailyReset) {
                    // If we need a daily reset, only include usage from today
                    appUsage.dailyMinutes
                } else {
                    // Otherwise keep daily minutes, but service will verify they're from today
                    // The service will run frequently, so daily updates will be accurate
                    appUsage.dailyMinutes
                }
                
                appUsage.name to mapOf(
                    "packageName" to appUsage.packageName,
                    "dailyMinutes" to dailyMinutes,
                    "weeklyMinutes" to appUsage.weeklyMinutes,
                    "lastUpdated" to System.currentTimeMillis()
                )
            }
            
            // Calculate total usage
            val totalDailyMinutes = usageData.sumOf { it.dailyMinutes }
            val totalWeeklyMinutes = usageData.sumOf { it.weeklyMinutes }
            
            // Get the current document to retrieve existing values
            val docRef = firestore.collection("appUsage")
                .document(childId)
                .collection("stats")
                .document("daily")
            
            var previousLastDailyReset: Long = 0
            try {
                val currentDoc = docRef.get().result
                if (currentDoc != null && currentDoc.exists()) {
                    previousLastDailyReset = currentDoc.getLong("lastDailyReset") ?: 0L
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving current document: ${e.message}", e)
                // Continue with default value if we can't get the current document
            }
            
            // Create the final data to store
            val dataToStore = hashMapOf(
                "apps" to appsData,
                "totalDailyMinutes" to totalDailyMinutes,
                "totalAppWeeklyUsage" to totalWeeklyMinutes,
                "lastUpdated" to System.currentTimeMillis(),
                "lastDailyReset" to if (needsDailyReset) System.currentTimeMillis() else previousLastDailyReset,
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
    
    /**
     * Check if we need to reset daily usage stats because it's a new day
     */
    private fun needsDailyReset(childId: String): Boolean {
        try {
            // Get current calendar for today's date
            val currentCalendar = Calendar.getInstance()
            val currentDayOfYear = currentCalendar.get(Calendar.DAY_OF_YEAR)
            val currentYear = currentCalendar.get(Calendar.YEAR)
            
            // Get the data synchronously - this blocks the thread but is needed for accurate resets
            try {
                val docRef = firestore.collection("appUsage")
                    .document(childId)
                    .collection("stats")
                    .document("daily")
                
                val document = docRef.get().result
                
                if (document != null && document.exists()) {
                    val lastUpdated = document.getLong("lastUpdated") ?: 0L
                    
                    if (lastUpdated > 0) {
                        val lastCalendar = Calendar.getInstance()
                        lastCalendar.timeInMillis = lastUpdated
                        
                        val lastDayOfYear = lastCalendar.get(Calendar.DAY_OF_YEAR)
                        val lastYear = lastCalendar.get(Calendar.YEAR)
                        
                        // Check if we've crossed to a new day
                        val isDifferentDay = lastDayOfYear != currentDayOfYear || lastYear != currentYear
                        if (isDifferentDay) {
                            Log.e(TAG, "Day changed from ${lastDayOfYear}/${lastYear} to ${currentDayOfYear}/${currentYear} - daily reset needed")
                            return true
                        }
                    }
                } else {
                    // If the document doesn't exist yet, no need to reset
                    Log.e(TAG, "No previous app usage data found, no reset needed")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting document for reset check: ${e.message}", e)
                // If we can't determine, don't reset to avoid data loss
                return false
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if daily reset is needed: ${e.message}", e)
            return false
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