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
    private val CHECK_INTERVAL = 5 * 60 * 1000L // Check every 5 minutes
    private val TAG = "ScreenTimeService"
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "ScreenTimeServiceChannel"
    private var isRunning = false

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
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        try {
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

            // Verify user is a child
            firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { userDoc ->
                    if (userDoc.exists()) {
                        val userType = userDoc.getString("type")
                        if (userType == "child") {
                            // User is a child, proceed with service
                            startTime = System.currentTimeMillis()
                            lastUpdateTime = startTime
                            
                            // Register screen state receiver
                            val filter = IntentFilter().apply {
                                addAction(Intent.ACTION_SCREEN_ON)
                                addAction(Intent.ACTION_SCREEN_OFF)
                            }
                            registerReceiver(screenOnReceiver, filter)
                            
                            createNotificationChannel()
                            startForeground(NOTIFICATION_ID, createNotification())
                            
                            setupNetworkCallback()
                            startPeriodicUpdates()
                        } else {
                            Log.e(TAG, "User is not a child")
                            stopSelf()
                        }
                    } else {
                        Log.e(TAG, "User document not found")
                        stopSelf()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking user type", e)
                    stopSelf()
                }
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
            Log.d(TAG, "Screen time data: $screenTimeData")

            // Save directly under screentime/childID
            firestore.collection("screenTime")
                .document(documentId)
                .set(screenTimeData)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Screen time saved successfully: ${screenTime / (1000 * 60)} minutes")
                    // Verify the data was saved
                    firestore.collection("screenTime")
                        .document(documentId)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                val savedScreenTime = doc.getLong("screenTime") ?: 0L
                                val savedDayTimestamp = doc.getLong("dayTimestamp") ?: 0L
                                val savedScreenTimeMinutes = doc.getLong("screenTimeMinutes") ?: 0L
                                val savedTimestamp = doc.getTimestamp("timestamp")?.toDate()
                                
                                // Verify all critical fields match
                                if (savedScreenTime == screenTime && savedDayTimestamp == todayStart && 
                                    savedScreenTimeMinutes == screenTime / (1000 * 60)) {
                                    Log.d(TAG, "✅ Verified saved data matches: ${savedScreenTimeMinutes} minutes for ${Date(savedDayTimestamp)}")
                                    Log.d(TAG, "✅ Timestamp verified: ${savedTimestamp}")
                                } else {
                                    Log.e(TAG, "❌ Data verification failed: Saved data doesn't match original values")
                                }
                            } else {
                                Log.e(TAG, "❌ Document does not exist after saving!")
                            }
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Error saving screen time to document: $documentId", e)
                    Log.e(TAG, "Error details: ${e.message}")
                    Log.e(TAG, "Error cause: ${e.cause}")
                    Log.e(TAG, "Error stack trace: ${e.stackTraceToString()}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in saveScreenTimeToFirestore", e)
            Log.e(TAG, "Error details: ${e.message}")
        }
    }

    private fun startPeriodicUpdates() {
        updateJob?.cancel()
        updateJob = scope.launch {
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
                    Log.e(TAG, "Error in update job", e)
                    delay(5000) // Wait 5 seconds before retrying
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
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
            // For Android 14 and above, check specific foreground service permissions
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
        } else {
            // For older Android versions, only check basic foreground service permission
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.FOREGROUND_SERVICE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Foreground service permission not granted")
                return false
            }
        }

        return true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            updateJob?.cancel()
            scope.cancel()
            unregisterReceiver(screenOnReceiver)
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
        }
        return START_STICKY
    }
} 