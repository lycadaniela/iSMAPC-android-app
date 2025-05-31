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

        // Check for usage stats permission
        if (!hasUsageStatsPermission()) {
            Log.e(TAG, "Missing usage stats permission, stopping service")
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
        uploadJob = serviceScope.launch {
            while (isRunning) {
                try {
                    val usageData = getAppUsageStats()
                    if (usageData.isNotEmpty()) {
                        updateFirestoreWithUsageData(usageData)
                    }
                    delay(1000) // Reduced polling interval to 1 second
                } catch (e: Exception) {
                    Log.e(TAG, "Error in usage data collection", e)
                    delay(5000) // Wait longer on error
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
            
            // Get the start of the current week (Monday)
            calendar.add(Calendar.DAY_OF_WEEK, -calendar.get(Calendar.DAY_OF_WEEK) + Calendar.MONDAY)
            val weekStart = calendar.timeInMillis
            
            val now = System.currentTimeMillis()
            
            Log.e(TAG, "⏰ Getting app usage from ${Date(todayStart)} to ${Date(now)}")
            
            // Initialize maps for storing usage data
            val dailyUsage = mutableMapOf<String, Long>()
            val weeklyUsage = mutableMapOf<String, Long>()
            
            // Process events for exact matching with device settings
            val events = usageStatsManager.queryEvents(weekStart, now)
            
            if (events != null) {
                var eventCount = 0
                val event = UsageEvents.Event()
                val lastEventTime = mutableMapOf<String, Long>()
                val lastEventType = mutableMapOf<String, Int>()
                val recentlyUsedApps = mutableSetOf<String>()
                
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    eventCount++
                    
                    val packageName = event.packageName
                    val eventTime = event.timeStamp
                    val eventType = event.eventType
                    
                    // Track any app that was brought to foreground recently
                    if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && eventTime > (now - 5 * 60 * 1000)) {
                        recentlyUsedApps.add(packageName)
                    }
                    
                    // Track foreground/background transitions
                    if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || 
                        eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                        
                        // Calculate duration when app moves to background after being in foreground
                        if (lastEventType[packageName] == UsageEvents.Event.MOVE_TO_FOREGROUND && 
                            eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                            
                            val duration = eventTime - (lastEventTime[packageName] ?: eventTime)
                            
                            // Add to weekly usage
                            weeklyUsage[packageName] = (weeklyUsage[packageName] ?: 0) + duration
                            
                            // Add to daily usage only if the event was today
                            if (lastEventTime[packageName] ?: 0 >= todayStart) {
                                dailyUsage[packageName] = (dailyUsage[packageName] ?: 0) + duration
                            }
                        }
                        
                        lastEventTime[packageName] = eventTime
                        lastEventType[packageName] = eventType
                    }
                }
                
                // Handle apps still in foreground
                val currentlyInForeground = lastEventType.filter { it.value == UsageEvents.Event.MOVE_TO_FOREGROUND }
                for ((packageName, _) in currentlyInForeground) {
                    val foregroundStart = lastEventTime[packageName] ?: todayStart
                    
                    // Add to weekly usage
                    val weeklyDuration = now - foregroundStart
                    weeklyUsage[packageName] = (weeklyUsage[packageName] ?: 0) + weeklyDuration
                    
                    // Add to daily usage only if started today
                    if (foregroundStart >= todayStart) {
                        val dailyDuration = now - foregroundStart
                        dailyUsage[packageName] = (dailyUsage[packageName] ?: 0) + dailyDuration
                    }
                }
                
                // Add minimal usage for recently used apps
                for (packageName in recentlyUsedApps) {
                    if (!weeklyUsage.containsKey(packageName) || weeklyUsage[packageName] == 0L) {
                        weeklyUsage[packageName] = 60 * 1000L // 1 minute
                        dailyUsage[packageName] = 60 * 1000L // 1 minute
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
                    
                    // Only include non-system apps with any usage
                    if (!isSystemApp && (daily > 0 || weekly > 0)) {
                        result.add(
                            AppUsage(
                                name = appName,
                                packageName = packageName,
                                dailyMinutes = daily,
                                weeklyMinutes = weekly
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing app $packageName", e)
                }
            }
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting app usage stats", e)
            return emptyList()
        }
    }
    
    private fun updateFirestoreWithUsageData(usageData: List<AppUsage>) {
        Log.e(TAG, "🔄 Updating Firestore with app usage data")
        
        // Don't continue if there's no data
        if (usageData.isEmpty()) {
            Log.e(TAG, "⚠️ No usage data to upload")
            return
        }

        // Get the current user's ID
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "❌ No user logged in, cannot update Firestore")
            return
        }
        val childId = currentUser.uid
        
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
                "lastDailyReset" to previousLastDailyReset,
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