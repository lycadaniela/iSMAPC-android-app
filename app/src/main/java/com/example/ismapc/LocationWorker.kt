package com.example.ismapc

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class LocationWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val firestore = FirebaseFirestore.getInstance()
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser

            if (currentUser == null) {
                Log.e("LocationWorker", "No user logged in")
                return Result.failure()
            }

            // Check permissions
            val fineLocationGranted = ActivityCompat.checkSelfPermission(
                applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!fineLocationGranted) {
                Log.e("LocationWorker", "No location permission")
                return Result.failure()
            }

            // Get last known location (fast, battery-friendly)
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                val locationData = hashMapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "userId" to currentUser.uid
                )
                firestore.collection("locations")
                    .document(currentUser.uid)
                    .set(locationData)
                Log.d("LocationWorker", "Location saved: $locationData")
            } else {
                Log.e("LocationWorker", "No location available")
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e("LocationWorker", "Error: ${e.message}")
            return Result.retry()
        }
    }
} 