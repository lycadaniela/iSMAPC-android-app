package com.example.ismapc

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import com.google.firebase.Timestamp

class BrowserContentMonitorService : AccessibilityService() {
    private val TAG = "BrowserContentMonitor"
    private val firestore = FirebaseFirestore.getInstance()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var currentChildId: String? = null
    private var isServiceConnected = false
    private var lastProcessedContent: String? = null
    private var lastProcessedTime: Long = 0
    private val CONTENT_DEBOUNCE_TIME = 2000L // 2 seconds

    override fun onServiceConnected() {
        try {
            val info = AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                             AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                             AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 100
            info.packageNames = arrayOf(
                "com.android.chrome",
                "org.mozilla.firefox",
                "com.microsoft.emmx",
                "com.opera.browser",
                "com.UCMobile.intl"
            )
            serviceInfo = info
            isServiceConnected = true
            Log.d(TAG, "Service connected successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected", e)
            isServiceConnected = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isServiceConnected) {
            Log.w(TAG, "Service not connected")
            return
        }

        try {
            val packageName = event.packageName?.toString() ?: return
            if (currentChildId == null) {
                Log.w(TAG, "No child ID set")
                return
            }

            // Check if it's a browser event
            if (isBrowserPackage(packageName)) {
                val content = event.text?.joinToString(" ") ?: return
                if (content.isNotBlank()) {
                    // Debounce content to avoid duplicate processing
                    val currentTime = System.currentTimeMillis()
                    if (content != lastProcessedContent || 
                        (currentTime - lastProcessedTime) > CONTENT_DEBOUNCE_TIME) {
                        
                        lastProcessedContent = content
                        lastProcessedTime = currentTime
                        
                        serviceScope.launch {
                            try {
                                Log.d(TAG, "Processing new content: ${content.take(50)}...")
                                
                                // Add content to Firestore under the user's document
                                val contentData = mapOf(
                                    "content" to content,
                                    "source" to "browser",
                                    "url" to (event.className ?: "unknown"),
                                    "timestamp" to Timestamp.now(),
                                    "status" to "pending"
                                )

                                Log.d(TAG, "Saving content to Firestore under user: contentToFilter/$currentChildId")
                                firestore.collection("contentToFilter")
                                    .document(currentChildId!!)
                                    .collection("content")
                                    .add(contentData)
                                    .addOnSuccessListener { docRef ->
                                        Log.d(TAG, "Content saved successfully with ID: ${docRef.id}")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Error saving content", e)
                                    }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in content processing", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent", e)
        }
    }

    private fun isBrowserPackage(packageName: String): Boolean {
        return packageName in listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.UCMobile.intl"
        )
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            currentChildId = intent?.getStringExtra("childId")
            if (currentChildId == null) {
                Log.e(TAG, "No child ID provided in intent")
                stopSelf()
                return START_NOT_STICKY
            } else {
                Log.d(TAG, "Service started with child ID: $currentChildId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            isServiceConnected = false
            serviceScope.cancel()
            Log.d(TAG, "Service destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        } finally {
            super.onDestroy()
        }
    }
} 