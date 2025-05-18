package com.example.ismapc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth

class AppUninstallReceiver : BroadcastReceiver() {
    private val TAG = "AppUninstallReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED ||
            intent.action == Intent.ACTION_PACKAGE_REMOVED) {
            
            val packageName = intent.data?.schemeSpecificPart
            if (packageName == context.packageName) {
                Log.d(TAG, "App is being uninstalled")
                
                try {
                    // Sign out from Firebase Auth
                    FirebaseAuth.getInstance().signOut()
                    
                    // Sign out from Google Sign-In
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                    GoogleSignIn.getClient(context, gso).signOut()
                    
                    Log.d(TAG, "Successfully signed out user during uninstallation")
                } catch (e: Exception) {
                    Log.e(TAG, "Error signing out during uninstallation", e)
                }
            }
        }
    }
} 