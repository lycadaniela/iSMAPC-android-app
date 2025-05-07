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
import java.util.*
import java.util.concurrent.TimeUnit

class ScreenTimeService : Service() {
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var firestore: FirebaseFirestore
    private var isRunning = false
    private val TAG = "ScreenTimeService"
    private var lastUpdateTime = 0L
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkAvailable = false
    private val UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(15) // 15 minutes
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "ScreenTimeServiceChannel"

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                Log.d(TAG, "Screen turned on, checking if service needs to be restarted")
                if (!isRunning) {
                    Log.d(TAG, "Service not running, restarting...")
                    startScreenTimeTracking()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        try {
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            firestore = FirebaseFirestore.getInstance()
            
            // Create notification channel for Android O and above
            createNotificationChannel()
            
            // Start as foreground service if we have notification permission
            if (hasNotificationPermission()) {
                startForeground(NOTIFICATION_ID, createNotification())
            } else {
                Log.w(TAG, "No notification permission, running as background service")
            }
            
            // Register screen on receiver
            val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
            registerReceiver(screenOnReceiver, filter)

            // Set up network callback
            setupNetworkCallback()
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
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available")
                    isNetworkAvailable = true
                    // Retry saving any pending data
                    if (isRunning) {
                        saveInitialData()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting")
        try {
            if (!isRunning) {
                isRunning = true
                // Save initial data immediately
                saveInitialData()
                // Then start the regular tracking
                startScreenTimeTracking()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }
        return START_STICKY
    }

    private fun saveInitialData() {
        Thread {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    Log.d(TAG, "Saving initial screen time data for user: ${currentUser.uid}")
                    val screenTime = calculateScreenTime()
                    Log.d(TAG, "Initial screen time calculated: $screenTime ms")
                    saveScreenTimeToFirestore(currentUser.uid, screenTime)
                    lastUpdateTime = System.currentTimeMillis()
                } else {
                    Log.w(TAG, "No user logged in for initial save")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving initial screen time data", e)
            }
        }.start()
    }

    private fun startScreenTimeTracking() {
        Thread {
            while (isRunning) {
                try {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser != null) {
                        val currentTime = System.currentTimeMillis()
                        // Only update if it's been at least 15 minutes since last update
                        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
                            Log.d(TAG, "Calculating screen time for user: ${currentUser.uid}")
                            val screenTime = calculateScreenTime()
                            Log.d(TAG, "Screen time calculated: $screenTime ms")
                            if (isNetworkAvailable) {
                                saveScreenTimeToFirestore(currentUser.uid, screenTime)
                                lastUpdateTime = currentTime
                            } else {
                                Log.w(TAG, "Network not available, skipping save")
                            }
                        }
                    } else {
                        Log.w(TAG, "No user logged in")
                    }
                    // Sleep for 1 minute before checking again
                    Thread.sleep(TimeUnit.MINUTES.toMillis(1))
                } catch (e: Exception) {
                    Log.e(TAG, "Error tracking screen time", e)
                    // Sleep for 1 minute before retrying on error
                    Thread.sleep(TimeUnit.MINUTES.toMillis(1))
                }
            }
        }.start()
    }

    private fun calculateScreenTime(): Long {
        try {
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.HOUR, -1) // Look at last hour
            val startTime = calendar.timeInMillis

            Log.d(TAG, "Querying usage stats from $startTime to $endTime")

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            var totalScreenTime = 0L
            stats?.forEach { usageStats ->
                // Log each app's usage
                Log.d(TAG, "App: ${usageStats.packageName}, Time: ${usageStats.totalTimeInForeground}ms")
                // Track all apps, not just our app
                totalScreenTime += usageStats.totalTimeInForeground
            }

            Log.d(TAG, "Total screen time: $totalScreenTime ms")
            return totalScreenTime
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating screen time", e)
            return 0L
        }
    }

    private fun saveScreenTimeToFirestore(userId: String, screenTime: Long) {
        if (!isNetworkAvailable) {
            Log.w(TAG, "Network not available, skipping save")
            return
        }

        try {
            val calendar = Calendar.getInstance()
            val date = calendar.time
            val dateString = getDateString(calendar)

            // Get the current user to verify
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.e(TAG, "No user logged in")
                return
            }

            Log.d(TAG, "Current user ID: ${currentUser.uid}")
            Log.d(TAG, "Provided user ID: $userId")

            val screenTimeData = mapOf(
                "date" to date,
                "screenTime" to screenTime,
                "lastUpdated" to System.currentTimeMillis(),
                "userId" to currentUser.uid,  // Use the current user's ID
                "email" to (currentUser.email ?: "")
            )

            Log.d(TAG, "Attempting to save screen time data:")
            Log.d(TAG, "User ID: ${currentUser.uid}")
            Log.d(TAG, "Date: $dateString")
            Log.d(TAG, "Screen Time: $screenTime ms")
            Log.d(TAG, "Document ID will be: ${currentUser.uid}_$dateString")
            
            // Save directly to a simpler path structure
            firestore.collection("screenTime")
                .document("${currentUser.uid}_$dateString")
                .set(screenTimeData)
                .addOnSuccessListener {
                    Log.d(TAG, "Screen time data saved successfully for date: $dateString")
                    // Verify the data was saved
                    firestore.collection("screenTime")
                        .document("${currentUser.uid}_$dateString")
                        .get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                Log.d(TAG, "Verified data exists: ${doc.data}")
                            } else {
                                Log.e(TAG, "Data verification failed - document does not exist")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error verifying data: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving screen time data", e)
                    Log.e(TAG, "Error details: ${e.message}")
                    // Try to get more details about the error
                    if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                        Log.e(TAG, "Firestore error code: ${e.code}")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveScreenTimeToFirestore", e)
        }
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
        isRunning = false
        
        try {
            unregisterReceiver(screenOnReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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