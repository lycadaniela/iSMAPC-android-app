package com.example.ismapc

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
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
    private var checkJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutes instead of 15
    private val CHECK_INTERVAL = 30 * 1000L // 30 seconds instead of 1 minute
    private val TAG = "ScreenTimeService"
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "ScreenTimeServiceChannel"

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                Log.d(TAG, "Screen turned on")
                isScreenOn = true
                // Force an immediate update when screen turns on
                saveScreenTimeToFirestore()
                startPeriodicUpdates()
            } else if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d(TAG, "Screen turned off")
                isScreenOn = false
                // Force an immediate update when screen turns off
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
            // Force an immediate update on service creation
            saveScreenTimeToFirestore()
            startPeriodicUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotificationChannel() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification channel", e)
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
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available")
                    isNetworkAvailable = true
                    // Retry saving any pending data
                    if (isScreenOn) {
                        saveScreenTimeToFirestore()
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "Network lost")
                    isNetworkAvailable = false
                }

                override fun onUnavailable() {
                    Log.d(TAG, "Network unavailable")
                    isNetworkAvailable = false
                }
            }

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up network callback", e)
        }
    }

    private fun calculateScreenTime(): Long {
        val currentTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        try {
            Log.d(TAG, "Querying usage stats from $startOfDay to $currentTime")
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                currentTime
            )

            var totalTime = 0L
            stats?.forEach { usageStats ->
                Log.d(TAG, "App: ${usageStats.packageName}, Time: ${usageStats.totalTimeInForeground}ms")
                totalTime += usageStats.totalTimeInForeground
            }

            Log.d(TAG, "Total calculated screen time: $totalTime ms")
            return totalTime
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
            Log.d(TAG, "Current user: ${currentUser.uid}, email: ${currentUser.email}")
            val screenTime = calculateScreenTime()
            if (screenTime <= 0) {
                Log.d(TAG, "No screen time to save")
                return
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateString = dateFormat.format(Date())
            val document = "${currentUser.uid}_$dateString"

            val screenTimeData = hashMapOf(
                "date" to dateString,
                "screenTime" to screenTime,
                "lastUpdated" to FieldValue.serverTimestamp(),
                "userId" to currentUser.uid,
                "email" to (currentUser.email ?: "")
            )

            Log.d(TAG, "Attempting to save screen time data:")
            Log.d(TAG, "Document: $document")
            Log.d(TAG, "Data: $screenTimeData")

            firestore.collection("screenTime")
                .document(document)
                .set(screenTimeData)
                .addOnSuccessListener {
                    Log.d(TAG, "Screen time data saved successfully")
                    totalScreenTime = screenTime
                    lastUpdateTime = System.currentTimeMillis()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving screen time data", e)
                    Log.e(TAG, "Error details: ${e.message}")
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                        Log.e(TAG, "Firestore error code: ${e.code}")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveScreenTimeToFirestore", e)
            Log.e(TAG, "Error details: ${e.message}")
        }
    }

    private fun startPeriodicUpdates() {
        try {
            Log.d(TAG, "Starting periodic updates")
            updateJob?.cancel()
            updateJob = scope.launch {
                while (isActive) {
                    try {
                        if (isNetworkAvailable) {
                            Log.d(TAG, "Running periodic update")
                            saveScreenTimeToFirestore()
                        } else {
                            Log.d(TAG, "Network not available, skipping update")
                        }
                        delay(UPDATE_INTERVAL)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in update job", e)
                        delay(5000) // Wait 5 seconds before retrying
                    }
                }
            }

            checkJob?.cancel()
            checkJob = scope.launch {
                while (isActive) {
                    try {
                        if (isNetworkAvailable) {
                            val currentTime = System.currentTimeMillis()
                            // Check if it's been more than 5 minutes since last update
                            if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
                                Log.d(TAG, "Running check update - time since last update: ${currentTime - lastUpdateTime}ms")
                                saveScreenTimeToFirestore()
                            }
                        }
                        delay(CHECK_INTERVAL)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in check job", e)
                        delay(5000)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting periodic updates", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting")
        try {
            // Force an immediate update when service starts
            saveScreenTimeToFirestore()
            startPeriodicUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }
        return START_STICKY
    }

    private fun getDateString(calendar: Calendar): String {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service being destroyed")
        
        try {
            updateJob?.cancel()
            checkJob?.cancel()
            scope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling jobs", e)
        }
        
        try {
            unregisterReceiver(screenOnReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
        try {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }

        // Restart the service
        try {
            val intent = Intent(applicationContext, ScreenTimeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting service", e)
        }
    }
} 