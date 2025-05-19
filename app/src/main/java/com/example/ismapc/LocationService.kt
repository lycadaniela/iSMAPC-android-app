package com.example.ismapc

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.util.*

class LocationService : Service() {
    private lateinit var locationManager: LocationManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val TAG = "LocationService"
    private val NOTIFICATION_ID = 2
    private val CHANNEL_ID = "LocationServiceChannel"
    private val UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutes
    private val MIN_DISTANCE = 10f // 10 meters
    private var isRunning = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var isInitialized = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            saveLocationToFirestore(location)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (!isInitialized) {
                initializeService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}")
            stopSelf()
        }
        return START_STICKY
    }

    private fun initializeService() {
        try {
            // Initialize basic components
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()

            // Create notification channel and start foreground immediately
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())

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
                        isInitialized = true
                        isRunning = true
                        acquireWakeLock()
                        startLocationUpdates()
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
            Log.e(TAG, "Error in initializeService", e)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
    }

    private fun hasRequiredPermissions(): Boolean {
        // Check basic location permissions
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        // Check foreground service location permission for Android 14 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks location in background"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Location Tracking")
        .setContentText("Tracking location in background")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun startLocationUpdates() {
        try {
            var hasProvider = false
            Log.d(TAG, "Starting location updates")

            // Try to get location from GPS first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Log.d(TAG, "GPS provider is enabled")
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    UPDATE_INTERVAL,
                    MIN_DISTANCE,
                    locationListener
                )
                hasProvider = true
            } else {
                Log.d(TAG, "GPS provider is disabled")
            }

            // Also try network provider as a fallback
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Log.d(TAG, "Network provider is enabled")
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    UPDATE_INTERVAL,
                    MIN_DISTANCE,
                    locationListener
                )
                hasProvider = true
            } else {
                Log.d(TAG, "Network provider is disabled")
            }

            if (!hasProvider) {
                Log.e(TAG, "No location providers available")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
            stopSelf()
        }
    }

    private fun saveLocationToFirestore(location: Location) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "No user logged in")
            return
        }

        try {
            Log.d(TAG, "Saving location: lat=${location.latitude}, lon=${location.longitude}")
            val locationData = hashMapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "timestamp" to FieldValue.serverTimestamp(),
                "userId" to currentUser.uid
            )

            // Use only the user ID as the document ID
            val documentId = currentUser.uid
            Log.d(TAG, "Saving to document: $documentId")
            Log.d(TAG, "Location data to save: $locationData")

            firestore.collection("locations")
                .document(documentId)
                .set(locationData)
                .addOnSuccessListener {
                    Log.d(TAG, "Location data saved successfully to document: $documentId")
                    // Verify the data was saved
                    firestore.collection("locations")
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
                    Log.e(TAG, "Error saving location data to document: $documentId", e)
                    Log.e(TAG, "Error details: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveLocationToFirestore", e)
            Log.e(TAG, "Error details: ${e.message}")
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "iSMAPC:LocationServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10*60*1000L /*10 minutes*/)
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            isRunning = false
            isInitialized = false
            locationManager.removeUpdates(locationListener)
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            Log.d(TAG, "Service destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
} 