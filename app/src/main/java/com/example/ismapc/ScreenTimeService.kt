package com.example.ismapc

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import java.util.*
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat

class ScreenTimeService : Service() {
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isScreenOn = true
    private var lastUpdateTime = 0L
    private var totalScreenTime = 0L
    private var startTime = 0L
    private var isNetworkAvailable = true
    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val CHECK_INTERVAL = 60 * 1000L // Check every 1 minute
    private val TAG = "ScreenTimeService"
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "ScreenTimeServiceChannel"
    private var isRunning = false
    private var isReceiverRegistered = false

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                Log.d(TAG, "Screen turned on")
                isScreenOn = true
            } else if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d(TAG, "Screen turned off")
                isScreenOn = false
                // Save screen time when screen turns off
                saveScreenTimeToFirestore()
            } else if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
                intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                Log.d(TAG, "Device rebooted or package replaced, restarting service")
                // Restart the service if it gets killed
                val restartServiceIntent = Intent(applicationContext, ScreenTimeService::class.java)
                restartServiceIntent.setPackage(packageName)
                startService(restartServiceIntent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        try {
            // Request to ignore battery optimizations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = android.net.Uri.parse("package:$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                }
            }

            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            
            // Check if user is a child before proceeding
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e(TAG, "No user logged in")
                stopSelf()
                return
            }

            // Check for required permissions
            if (!hasRequiredPermissions()) {
                Log.e(TAG, "Required permissions not granted")
                stopSelf()
                return
            }

            // Register screen state receiver
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_BOOT_COMPLETED)
                    addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
                }
                registerReceiver(screenOnReceiver, filter)
                isReceiverRegistered = true
                Log.d(TAG, "Screen state receiver registered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering screen state receiver: ${e.message}")
            }

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            
            setupNetworkCallback()
            startPeriodicUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Time Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks screen time usage"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Screen Time Tracking")
        .setContentText("Monitoring screen time usage")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun setupNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                isNetworkAvailable = true
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                isNetworkAvailable = false
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun calculateScreenTime(): Long {
        val currentTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        Log.d(TAG, "⏰ DAILY RESET CHECK: Calculating screen time from ${Date(startOfDay)} to ${Date(currentTime)}")

        try {
            // CRITICAL FIX: Only query events from today, not all usage stats
            // This ensures we're only counting today's screen time
            val events = usageStatsManager.queryEvents(startOfDay, currentTime)
            
            if (events != null && events.hasNextEvent()) {
                var totalTime = 0L
                val event = UsageEvents.Event()
                val lastEventTime = mutableMapOf<String, Long>()
                val lastEventType = mutableMapOf<String, Int>()
                
                Log.d(TAG, "Processing ONLY today's events for accurate screen time")
                
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    
                    val packageName = event.packageName
                    val eventTime = event.timeStamp
                    val eventType = event.eventType
                    
                    // Skip any events from before today
                    if (eventTime < startOfDay) {
                        continue
                    }
                    
                    // Track foreground/background transitions to calculate actual usage time
                    if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || 
                        eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                        
                        if (lastEventType[packageName] == UsageEvents.Event.MOVE_TO_FOREGROUND && 
                            eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                            
                            val duration = eventTime - (lastEventTime[packageName] ?: eventTime)
                            totalTime += duration
                        }
                        
                        lastEventTime[packageName] = eventTime
                        lastEventType[packageName] = eventType
                    }
                }
                
                // Account for apps still in foreground
                val currentlyInForeground = lastEventType.filter { it.value == UsageEvents.Event.MOVE_TO_FOREGROUND }
                for ((packageName, _) in currentlyInForeground) {
                    val foregroundStart = lastEventTime[packageName] ?: startOfDay
                    if (foregroundStart >= startOfDay) {
                        val duration = currentTime - foregroundStart
                        totalTime += duration
                    }
                }
                
                Log.d(TAG, "Total screen time calculated from events: ${totalTime / (1000 * 60)} minutes")
                return totalTime
            } else {
                Log.d(TAG, "No usage events found for today")
                return 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating screen time", e)
            return 0L
        }
    }

    private fun saveScreenTimeToFirestore() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "No user logged in")
            return
        }

        try {
            val screenTime = calculateScreenTime()
            Log.d(TAG, "Saving today's screen time: ${screenTime / (1000 * 60)} minutes")

            // Get today's start timestamp for accurate tracking
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.timeInMillis
            
            // Use the child's ID as the document ID
            val documentId = currentUser.uid
            
            val screenTimeData = hashMapOf(
                "screenTime" to screenTime,
                "lastUpdated" to System.currentTimeMillis(),
                "userId" to currentUser.uid,
                "dayTimestamp" to todayStart,
                "screenTimeMinutes" to screenTime / (1000 * 60),
                "dayDate" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(todayStart)),
                "timestamp" to FieldValue.serverTimestamp()
            )
            
            Log.d(TAG, "Screen time data to save: ${screenTime / (1000 * 60)} minutes for ${Date(todayStart)}")
            Log.d(TAG, "Document ID: $documentId")

            // Save directly under screentime/childID with retry mechanism
            saveWithRetry(documentId, screenTimeData)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in saveScreenTimeToFirestore", e)
            Log.e(TAG, "Error details: ${e.message}")
        }
    }

    private fun saveWithRetry(documentId: String, screenTimeData: Map<String, Any>, retryCount: Int = 0) {
        if (retryCount >= 3) {
            Log.e(TAG, "Max retry attempts reached for saving screen time")
            return
        }

        try {
            firestore.collection("screenTime")
                .document(documentId)
                .set(screenTimeData)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Screen time saved successfully on attempt ${retryCount + 1}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Error saving screen time on attempt ${retryCount + 1}: ${e.message}")
                    // Wait for 2 seconds before next retry
                    scope.launch {
                        delay(2000)
                        saveWithRetry(documentId, screenTimeData, retryCount + 1)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in saveWithRetry", e)
        }
    }

    private fun startPeriodicUpdates() {
        try {
            updateJob?.cancel()
            updateJob = scope.launch {
                try {
                    while (isActive) {
                        try {
                            if (isNetworkAvailable) {
                                saveScreenTimeToFirestore()
                                Log.d(TAG, "Screen time updated successfully")
                            } else {
                                Log.d(TAG, "Network not available, skipping update")
                            }
                            delay(CHECK_INTERVAL)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                Log.d(TAG, "Update job was cancelled normally")
                                break
                            }
                            Log.e(TAG, "Error in update job", e)
                            // Wait a bit before retrying on error
                            delay(5000)
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.d(TAG, "Update job was cancelled normally")
                    } else {
                        Log.e(TAG, "Fatal error in update job", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting periodic updates", e)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        try {
            // Check usage stats permission
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                Log.e(TAG, "Usage stats permission not granted")
                return false
            }

            // Check foreground service permissions based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // For Android 14 and above
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
                    ) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Foreground service permissions not granted")
                    return false
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10 and above
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.FOREGROUND_SERVICE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Foreground service permission not granted")
                    return false
                }
            }

            // For older devices, check basic permissions
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.INTERNET
                ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_NETWORK_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Basic network permissions not granted")
                return false
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}")
            return false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Cancel the job gracefully
            updateJob?.let { job ->
                if (job.isActive) {
                    job.cancel()
                }
            }
            scope.cancel()
            if (isReceiverRegistered) {
                unregisterReceiver(screenOnReceiver)
                isReceiverRegistered = false
            }
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (!isRunning) {
                isRunning = true
                startPeriodicUpdates()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}")
            // Try to restart the service if it fails
            stopSelf()
            startService(Intent(this, ScreenTimeService::class.java))
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed, restarting service")
        // Restart the service if it gets killed
        val restartServiceIntent = Intent(applicationContext, ScreenTimeService::class.java)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
    }
} 