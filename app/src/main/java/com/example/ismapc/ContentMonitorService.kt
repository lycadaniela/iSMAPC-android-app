package com.example.ismapc

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class ContentMonitorService : AccessibilityService() {
    private val TAG = "ContentMonitorService"
    private val firestore = FirebaseFirestore.getInstance()
    private var currentChildId: String? = null
    private var currentChildName: String? = null
    private var currentParentId: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val packageName = event.packageName?.toString()
            Log.d(TAG, "Event from package: $packageName")

            if (packageName == null) {
                return
            }

            // Check if it's a browser package
            val isBrowser = isBrowserPackage(packageName)
            Log.d(TAG, "Package $packageName is browser: $isBrowser")

            if (!isBrowser) {
                Log.d(TAG, "Not a browser package: $packageName")
                return
            }

            // Get content details
            val title = event.text.joinToString(" ")
            val url = event.contentDescription?.toString() ?: ""
            Log.d(TAG, "Checking content - Title: $title, URL: $url")

            if (currentChildId == null) {
                Log.d(TAG, "No child ID set, skipping content check")
                return
            }

            // Save content to Firestore for filtering
            val contentData = hashMapOf(
                "content" to "$title\n$url",
                "timestamp" to Timestamp.now(),
                "status" to "pending",
                "childId" to currentChildId,
                "childName" to currentChildName,
                "parentId" to currentParentId
            )

            firestore.collection("contentToFilter")
                .document(currentChildId!!)
                .collection("content")
                .add(contentData)
                .addOnSuccessListener {
                    Log.d(TAG, "Content saved for filtering")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving content", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    private fun isBrowserPackage(packageName: String): Boolean {
        return packageName.contains("browser", ignoreCase = true) ||
               packageName.contains("chrome", ignoreCase = true) ||
               packageName.contains("firefox", ignoreCase = true) ||
               packageName.contains("opera", ignoreCase = true) ||
               packageName.contains("edge", ignoreCase = true) ||
               packageName.contains("samsung", ignoreCase = true) ||
               packageName.contains("sbrowser", ignoreCase = true)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
} 