package com.example.ismapc

import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * BroadcastReceiver that runs when the device boots up.
 * It checks if the current user is a child account and starts
 * the app usage monitoring service if needed.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    private val TAG = "BootCompletedReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking user account...")
            
            // Get the current user
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            
            if (currentUser != null) {
                Log.d(TAG, "User is logged in: ${currentUser.uid}")
                
                // Check if this is a child account
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("users")
                    .document("child")
                    .collection("profile")
                    .document(currentUser.uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            Log.d(TAG, "This is a child account, starting services")
                            
                            // Check if we have usage stats permission
                            if (hasUsageStatsPermission(context)) {
                                startAppUsageService(context)
                            } else {
                                Log.e(TAG, "Missing usage stats permission, cannot start app usage service")
                            }
                        } else {
                            Log.d(TAG, "This is not a child account, not starting services")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error checking user account type", e)
                    }
            } else {
                Log.d(TAG, "No user logged in, not starting services")
            }
        }
    }
    
    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    private fun startAppUsageService(context: Context) {
        try {
            Log.d(TAG, "Starting AppUsageService after boot")
            val serviceIntent = Intent(context, AppUsageService::class.java)
            
            // Start as a foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "AppUsageService started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AppUsageService", e)
        }
    }
} 