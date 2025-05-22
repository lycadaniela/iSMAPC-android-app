package com.example.ismapc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AppLockService : Service() {
    private val TAG = "AppLockService"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isRunning = false
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastLockedApp: String? = null
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "AppLockServiceChannel"
    private var wakeLock: PowerManager.WakeLock? = null
    private var usageStatsManager: UsageStatsManager? = null
    private var lockedAppsCache: List<String> = emptyList()
    private var lastCacheUpdate = 0L
    private val CACHE_DURATION = 5000L // Cache locked apps for 5 seconds

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AppLockService created")
        try {
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            createNotificationChannel()
            acquireWakeLock()
            startForeground()
            startMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "iSMAPC:AppLockServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10*60*1000L /*10 minutes*/)
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        if (!isRunning) {
            try {
                isRunning = true
                startForeground()
                startMonitoring()
            } catch (e: Exception) {
                Log.e(TAG, "Error in onStartCommand: ${e.message}")
                restartService()
            }
        }
        return START_STICKY
    }

    private fun restartService() {
        try {
            val intent = Intent(applicationContext, AppLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "Service restarted")
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting service: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "App Lock Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps the app lock service running"
                    setShowBadge(false)
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification channel: ${e.message}")
        }
    }

    private fun startForeground() {
        try {
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("iSMAPC Active")
                .setContentText("Monitoring app usage and location")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Service started in foreground")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground: ${e.message}")
        }
    }

    private suspend fun getLockedApps(): List<String> {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCacheUpdate < CACHE_DURATION) {
            return lockedAppsCache
        }

        try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val lockedAppsDoc = firestore.collection("lockedApps")
                    .document(currentUser.uid)
                    .get()
                    .await()

                if (lockedAppsDoc.exists()) {
                    lockedAppsCache = lockedAppsDoc.get("lockedApps") as? List<String> ?: emptyList()
                    lastCacheUpdate = currentTime
                    return lockedAppsCache
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting locked apps: ${e.message}")
        }
        return emptyList()
    }

    private fun getCurrentApp(): String? {
        try {
            val time = System.currentTimeMillis()
            val usageEvents = usageStatsManager?.queryEvents(time - 1000, time)
            if (usageEvents == null) {
                Log.e(TAG, "UsageStatsManager is null")
                return null
            }

            val event = UsageEvents.Event()
            var lastMoveToForeground: UsageEvents.Event? = null

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastMoveToForeground = event
                }
            }

            return lastMoveToForeground?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current app: ${e.message}")
            return null
        }
    }

    private fun showLockScreen() {
        try {
            val intent = Intent(this, AppLockScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or
                       Intent.FLAG_ACTIVITY_NO_ANIMATION
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
            Log.d(TAG, "Lock screen shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing lock screen: ${e.message}")
        }
    }

    private fun startMonitoring() {
        job = scope.launch {
            try {
                while (isRunning) {
                    try {
                        val currentUser = auth.currentUser
                        if (currentUser != null) {
                            // Check if current user is a child
                            val childProfile = firestore.collection("users/child/profile")
                                .document(currentUser.uid)
                                .get()
                                .await()

                            if (childProfile.exists()) {
                                // Get locked apps (using cache)
                                val lockedApps = getLockedApps()
                                
                                // Check current app
                                val currentApp = getCurrentApp()
                                if (currentApp != null && currentApp != lastLockedApp) {
                                    Log.d(TAG, "Current app: $currentApp, Locked apps: $lockedApps")
                                    if (lockedApps.contains(currentApp)) {
                                        // App is locked, show lock screen immediately
                                        Log.d(TAG, "Showing lock screen for app: $currentApp")
                                        lastLockedApp = currentApp
                                        withContext(Dispatchers.Main) {
                                            showLockScreen()
                                        }
                                    } else {
                                        lastLockedApp = null
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in app monitoring: ${e.message}")
                        if (e is kotlinx.coroutines.CancellationException) {
                            throw e
                        }
                    }
                    
                    delay(50) // Check every 50ms for faster response
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Monitoring coroutine cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in monitoring: ${e.message}")
                restartService()
            }
        }
    }

    override fun onDestroy() {
        try {
            Log.d(TAG, "onDestroy called")
            isRunning = false
            job?.cancel()
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            restartService()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        } finally {
            super.onDestroy()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved called")
        restartService()
    }
} 