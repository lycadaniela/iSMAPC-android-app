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
            
            // If we're already running, just return
            if (isRunning) {
                return START_STICKY
            }
            
            // Start location updates if we have permissions and are initialized
            if (isInitialized && hasRequiredPermissions()) {
                startLocationUpdates()
                isRunning = true
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

            // Verify user is a child
            firestore.collection("users")
                .document("child")
                .collection("profile")
                .document(currentUser.uid)
                .get()
                .addOnSuccessListener { childDoc ->
                    if (childDoc.exists()) {
                        // User is a child
                        Log.d(TAG, "User verified as child")
                        
                        // Check for location providers
                        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                        
                        if (!isGpsEnabled && !isNetworkEnabled) {
                            Log.e(TAG, "Location providers are disabled")
                            
                            // Show a notification to prompt user to enable location
                            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                                .setContentTitle("Location Services Disabled")
                                .setContentText("Please enable location services in device settings")
                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setAutoCancel(true)
                                .build()
                            startForeground(NOTIFICATION_ID, notification)
                            stopSelf()
                            return@addOnSuccessListener
                        }

                        // Check for required permissions
                        if (!hasRequiredPermissions()) {
                            Log.e(TAG, "Required permissions not granted")
                            stopSelf()
                            return@addOnSuccessListener
                        }

                        // Initialize service
                        isInitialized = true
                        acquireWakeLock()
                        
                        // Start location updates if we have permissions
                        if (hasRequiredPermissions()) {
                            startLocationUpdates()
                            isRunning = true
                        }
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
        val fineLocationGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocationGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val foregroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // For older Android versions
        }
        
        val foregroundServiceGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.FOREGROUND_SERVICE
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!fineLocationGranted || !coarseLocationGranted || !foregroundLocationGranted || !foregroundServiceGranted) {
            Log.e(TAG, "Missing permissions: FineLocation=$fineLocationGranted, CoarseLocation=$coarseLocationGranted, ForegroundLocation=$foregroundLocationGranted, ForegroundService=$foregroundServiceGranted")
            
            // Show a notification to prompt user to grant permissions
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Permissions Required")
                .setContentText("Please grant location permissions in app settings")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
            
            return false
        }
        
        // Check if location is enabled
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            Log.e(TAG, "Location providers are disabled")
            
            // Show a notification to prompt user to enable location
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Services Disabled")
                .setContentText("Please enable location services in device settings")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
            
            return false
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

            // Save directly to locations/childId document
            firestore.collection("locations")
                .document(currentUser.uid)
                .set(locationData)
                .addOnSuccessListener {
                    Log.d(TAG, "Location saved successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving location", e)
                }

            firestore.collection("locations")
                .document(currentUser.uid)
                .collection("history")
                .add(locationData)
                .addOnSuccessListener { documentReference ->
                    Log.d(TAG, "Location data saved successfully to document: ${documentReference.id}")
                    // Verify the data was saved
                    documentReference.get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                Log.d(TAG, "Verified saved data: ${doc.data}")
                            } else {
                                Log.e(TAG, "Document does not exist after saving!")
                            }
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving location data", e)
                    Log.e(TAG, "Error details: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveLocationToFirestore", e)
            Log.e(TAG, "Error details: ${e.message}")
            
            // Show error notification
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Error Saving Location")
                .setContentText("Unexpected error occurred: ${e.message}")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            startForeground(NOTIFICATION_ID, notification)
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