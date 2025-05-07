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
    private val CHECK_INTERVAL = 60 * 60 * 1000L // Check every hour
    private val TAG = "ScreenTimeService"
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "ScreenTimeServiceChannel"

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
                .document("child")
                .collection("profile")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { childDoc ->
                    if (childDoc.exists()) {
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

        Log.d(TAG, "Calculating screen time from $startOfDay to $currentTime")

        try {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                currentTime
            )

            var totalTime = 0L
            stats?.forEach { usageStats ->
                val timeInForeground = usageStats.totalTimeInForeground
                Log.d(TAG, "App ${usageStats.packageName}: $timeInForeground ms")
                totalTime += timeInForeground
            }

            Log.d(TAG, "Total screen time calculated: $totalTime ms")
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
            val screenTime = calculateScreenTime()
            Log.d(TAG, "Saving screen time: $screenTime ms")

            // Use only the user ID as the document ID
            val documentId = currentUser.uid
            Log.d(TAG, "Saving to document: $documentId")

            val screenTimeData = hashMapOf(
                "screenTime" to screenTime,
                "lastUpdated" to FieldValue.serverTimestamp(),
                "userId" to currentUser.uid
            )
            
            Log.d(TAG, "Screen time data to save: $screenTimeData")

            firestore.collection("screenTime")
                .document(documentId)
                .set(screenTimeData)
                .addOnSuccessListener {
                    Log.d(TAG, "Screen time saved successfully to document: $documentId")
                    // Verify the data was saved
                    firestore.collection("screenTime")
                        .document(documentId)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                Log.d(TAG, "Verified saved data: ${doc.data}")
                            } else {
                                Log.e(TAG, "Document does not exist after saving!")
                            }
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving screen time to document: $documentId", e)
                    Log.e(TAG, "Error details: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveScreenTimeToFirestore", e)
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
                    }
                    delay(CHECK_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in update job", e)
                    delay(5000)
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

        // Check foreground service permissions for Android 14 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
} 