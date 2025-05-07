package com.example.ismapc

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AppLockService : Service() {
    private val TAG = "AppLockService"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isRunning = false
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastLockedApp: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AppLockService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        job = scope.launch {
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
                            // Get locked apps for this child
                            val lockedAppsDoc = firestore.collection("lockedApps")
                                .document(currentUser.uid)
                                .get()
                                .await()

                            if (lockedAppsDoc.exists()) {
                                val lockedApps = lockedAppsDoc.get("lockedApps") as? List<String> ?: emptyList()
                                
                                // Check current app
                                val currentApp = getCurrentApp()
                                if (currentApp != null) {
                                    if (lockedApps.contains(currentApp)) {
                                        // App is locked, show lock screen
                                        if (lastLockedApp != currentApp) {
                                            lastLockedApp = currentApp
                                            showLockScreen()
                                        }
                                    } else {
                                        lastLockedApp = null
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in app monitoring", e)
                }
                kotlinx.coroutines.delay(1000) // Check every second
            }
        }
    }

    private fun getCurrentApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(time - 1000, time)
        val event = UsageEvents.Event()
        var lastEvent: UsageEvents.Event? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastEvent = event
            }
        }

        return lastEvent?.packageName
    }

    private fun showLockScreen() {
        val intent = Intent(this, AppLockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        isRunning = false
        job?.cancel()
        super.onDestroy()
        Log.d(TAG, "AppLockService destroyed")
    }
} 