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
                            Log.e(TAG, "No usage stats permission, cannot collect data")
                        } else {
                            // Collect and upload data
                            val appUsageData = getAppUsageStats()
                            
                            if (appUsageData.isNotEmpty()) {
                                Log.e(TAG, "Collected ${appUsageData.size} app usage records, uploading to Firestore")
                                updateFirestoreWithUsageData(childId, appUsageData)
                            } else {
                                Log.e(TAG, "No app usage data collected, skipping upload")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in app usage data collection: ${e.message}", e)
                    }
                    
                    // Wait for the next collection interval (15 minutes)
                    delay(15 * 60 * 1000L)
                }
            } catch (e: CancellationException) {
                Log.e(TAG, "App usage data collection job cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in app usage data collection loop: ${e.message}", e)
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
            } else {
                // 2. Try the event-based approach if stats query didn't work
                val events = usageStatsManager.queryEvents(weekStart, now)
                
                if (events != null && events.hasNextEvent()) {
                    val event = UsageEvents.Event()
                    val lastEventTime = mutableMapOf<String, Long>()
                    val lastEventType = mutableMapOf<String, Int>()
                    
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        
                        val packageName = event.packageName
                        val eventTime = event.timeStamp
                        val eventType = event.eventType
                        
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
                    
                    // Only include non-system apps with usage greater than 1 minute
                    if (!isSystemApp && weekly > 1) {
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
        try {
            val appsData = usageData.associate { appUsage ->
                appUsage.name to mapOf(
                    "dailyMinutes" to appUsage.dailyMinutes,
                    "weeklyMinutes" to appUsage.weeklyMinutes,
                    "lastUpdated" to Calendar.getInstance().timeInMillis,
                    "packageName" to appUsage.packageName,
                    "isSampleData" to false
                )
            }
            
            val dataToStore = mapOf(
                "apps" to appsData,
                "lastUpdated" to Calendar.getInstance().timeInMillis,
                "dataSource" to "REAL_DEVICE_DATA",
                "totalDailyMinutes" to usageData.sumOf { it.dailyMinutes },
                "totalWeeklyMinutes" to usageData.sumOf { it.weeklyMinutes },
                "appCount" to usageData.size
            )
            
            firestore.collection("appUsage")
                .document(childId)
                .collection("stats")
                .document("daily")
                .set(dataToStore)
                .addOnSuccessListener {
                    Log.e(TAG, "Service successfully updated app usage data in Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Service failed to update app usage data in Firestore", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in service updating Firestore with usage data", e)
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