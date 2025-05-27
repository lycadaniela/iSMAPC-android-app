package com.example.ismapc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DeviceLockService : Service() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val TAG = "DeviceLockService"
    private val NOTIFICATION_ID = 3
    private val CHANNEL_ID = "DeviceLockServiceChannel"
    private var isLockScreenActive = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        try {
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            
            // Check if user is a child before proceeding
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e(TAG, "No user logged in")
                stopSelf()
                return
            }

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            
            // Start monitoring device lock state
            monitorDeviceLockState(currentUser.uid)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Device Lock Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitors device lock state"
                setShowBadge(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Device Lock Active")
        .setContentText("Monitoring device lock state")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setOngoing(true)
        .build()

    private fun monitorDeviceLockState(userId: String) {
        firestore.collection("deviceLocks")
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to device lock state", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val isLocked = snapshot.getBoolean("isLocked") ?: false
                    
                    Log.d(TAG, "DeviceLockService - Current state:")
                    Log.d(TAG, "isLocked: $isLocked")
                    Log.d(TAG, "isLockScreenActive: $isLockScreenActive")
                    
                    if (isLocked && !isLockScreenActive) {
                        // Launch the lock screen activity
                        val intent = Intent(this, DeviceLockScreenActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        isLockScreenActive = true
                    } else if (!isLocked && isLockScreenActive) {
                        // Device is unlocked, update state
                        isLockScreenActive = false
                    }
                } else {
                    // No lock document exists, update state
                    isLockScreenActive = false
                }
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        // Return START_STICKY to ensure the service restarts if it's killed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        // Don't clear the lock state when the service is destroyed
        // This ensures the lock persists even if the service is killed
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Service onTaskRemoved")
        // Restart the service if it's killed
        val restartServiceIntent = Intent(applicationContext, DeviceLockService::class.java)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
    }
} 