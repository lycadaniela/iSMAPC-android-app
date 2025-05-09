package com.example.ismapc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.tasks.await

class ContentFilteringService : Service() {
    private val TAG = "ContentFilteringService"
    private val firestore = FirebaseFirestore.getInstance()
    private val contentFilteringManager = ContentFilteringManager(this)
    private var currentChildId: String? = null
    private var currentChildName: String? = null
    private var currentParentId: String? = null
    private var isServiceRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ContentFilteringServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            Log.d(TAG, "Service created")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent == null) {
                Log.e(TAG, "Intent is null")
                stopSelf()
                return START_NOT_STICKY
            }

            currentChildId = intent.getStringExtra("childId")
            currentChildName = intent.getStringExtra("childName")
            currentParentId = intent.getStringExtra("parentId")

            if (currentChildId == null || currentChildName == null || currentParentId == null) {
                Log.e(TAG, "Missing required parameters")
                stopSelf()
                return START_NOT_STICKY
            }

            Log.d(TAG, "Service started with child: $currentChildName")

            if (!isServiceRunning) {
                startForeground(NOTIFICATION_ID, createNotification())
                isServiceRunning = true
                startContentFiltering()
            }

            return START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun startContentFiltering() {
        try {
            Log.d(TAG, "Starting content filtering service for child: $currentChildId")
            
            // Listen for pending content in the current child's content subcollection
            firestore.collection("contentToFilter")
                .document(currentChildId!!)
                .collection("content")
                .whereEqualTo("status", "pending")
                .addSnapshotListener { contentSnapshot, contentError ->
                    if (contentError != null) {
                        Log.e(TAG, "Error listening for content", contentError)
                        return@addSnapshotListener
                    }

                    Log.d(TAG, "Received ${contentSnapshot?.documents?.size ?: 0} pending documents")
                    
                    contentSnapshot?.documents?.forEach { doc ->
                        val content = doc.getString("content")
                        
                        Log.d(TAG, "Processing document ${doc.id}: content=$content")
                        
                        if (content == null) {
                            Log.e(TAG, "Missing content field in document ${doc.id}")
                            return@forEach
                        }

                        // Process content in a coroutine
                        serviceScope.launch {
                            try {
                                Log.d(TAG, "Starting content analysis for document ${doc.id}")
                                val result = contentFilteringManager.analyzeContent(content)
                                Log.d(TAG, "Content analysis result for ${doc.id}: isBlockable=${result.isBlockable}, reason=${result.reason}")
                                
                                // First, mark the original document as processed
                                doc.reference.update("status", "processed")
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Marked original content as processed")
                                        
                                        // Save the filtered content to the user's filteredContent subcollection
                                        val filteredContentData = mapOf(
                                            "content" to content,
                                            "isBlockable" to result.isBlockable,
                                            "reason" to result.reason,
                                            "timestamp" to com.google.firebase.Timestamp.now(),
                                            "status" to "processed"
                                        )
                                        
                                        Log.d(TAG, "Saving filtered content to user's filteredContent subcollection: contentToFilter/$currentChildId/filteredContent/${doc.id}")
                                        
                                        // Save the filtered content
                                        firestore.collection("contentToFilter")
                                            .document(currentChildId!!)
                                            .collection("filteredContent")
                                            .document(doc.id)  // Use the same document ID as the original content
                                            .set(filteredContentData)
                                            .addOnSuccessListener {
                                                Log.d(TAG, "Filtered content saved successfully with ID: ${doc.id}")
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e(TAG, "Error saving filtered content", e)
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Error marking original content as processed", e)
                                    }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing content", e)
                                // Mark as failed if there's an error
                                doc.reference.update("status", "failed", "error", e.message)
                                    .addOnFailureListener { updateError ->
                                        Log.e(TAG, "Error marking document as failed", updateError)
                                    }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting content filtering", e)
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Content Filtering Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifications for content filtering service"
                }
                notificationManager.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification channel", e)
        }
    }

    private fun createNotification(): Notification {
        return try {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Content Filtering Active")
                .setContentText("Monitoring content for ${currentChildName}")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification", e)
            // Return a basic notification if the custom one fails
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Content Filtering Active")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            isServiceRunning = false
            serviceScope.cancel()
            Log.d(TAG, "Service destroyed")
            } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        } finally {
            super.onDestroy()
        }
    }
}

data class ContentAnalysisResult(
    val isBlockable: Boolean,
    val reason: String
) 