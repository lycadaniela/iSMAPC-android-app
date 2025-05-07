package com.example.ismapc

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.util.*

class InstalledAppsService : Service() {
    private lateinit var packageManager: PackageManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val TAG = "InstalledAppsService"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        try {
            packageManager = applicationContext.packageManager
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            
            // Check if user is a child before proceeding
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e(TAG, "No user logged in")
                stopSelf()
                return
            }

            Log.d(TAG, "User is logged in: ${currentUser.uid}")
            // Get and save installed apps
            saveInstalledApps()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    private fun saveInstalledApps() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "No user logged in")
            return
        }

        try {
            Log.d(TAG, "Getting installed apps...")
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            Log.d(TAG, "Found ${installedApps.size} installed apps")
            
            val appsList = installedApps.map { appInfo ->
                hashMapOf(
                    "packageName" to appInfo.packageName,
                    "appName" to packageManager.getApplicationLabel(appInfo).toString(),
                    "isSystemApp" to ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                )
            }

            val appsData = hashMapOf(
                "apps" to appsList,
                "lastUpdated" to FieldValue.serverTimestamp(),
                "userId" to currentUser.uid
            )

            Log.d(TAG, "Preparing to save ${appsList.size} installed apps to Firestore")
            Log.d(TAG, "Document path: installedApps/${currentUser.uid}")
            
            firestore.collection("installedApps")
                .document(currentUser.uid)
                .set(appsData)
                .addOnSuccessListener {
                    Log.d(TAG, "Installed apps saved successfully")
                    // Verify the data was saved
                    firestore.collection("installedApps")
                        .document(currentUser.uid)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                Log.d(TAG, "Verified saved data: ${doc.data}")
                            } else {
                                Log.e(TAG, "Document does not exist after saving!")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error verifying saved data", e)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving installed apps", e)
                    Log.e(TAG, "Error details: ${e.message}")
                    Log.e(TAG, "Error cause: ${e.cause}")
                    Log.e(TAG, "Error stack trace: ${e.stackTraceToString()}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveInstalledApps", e)
            Log.e(TAG, "Error details: ${e.message}")
            Log.e(TAG, "Error cause: ${e.cause}")
            Log.e(TAG, "Error stack trace: ${e.stackTraceToString()}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 