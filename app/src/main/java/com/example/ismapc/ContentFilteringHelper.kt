package com.example.ismapc

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.Timestamp

class ContentFilteringHelper(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun submitContentForFiltering(content: String, childId: String, childName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val contentDoc = hashMapOf(
                    "content" to content,
                    "childId" to childId,
                    "childName" to childName,
                    "timestamp" to Timestamp.now(),
                    "status" to "pending"
                )

                val docRef = firestore.collection("contentToFilter")
                    .add(contentDoc)
                    .await()

                docRef.id
            } catch (e: Exception) {
                Log.e("ContentFilteringHelper", "Error submitting content for filtering", e)
                throw e
            }
        }
    }

    suspend fun getContentFilteringResult(contentId: String): ContentFilteringResult? {
        return withContext(Dispatchers.IO) {
            try {
                val doc = firestore.collection("contentFilteringResults")
                    .document(contentId)
                    .get()
                    .await()

                if (doc.exists()) {
                    val data = doc.data ?: return@withContext null
                    ContentFilteringResult(
                        id = doc.id,
                        childId = data["childId"] as? String ?: return@withContext null,
                        childName = data["childName"] as? String ?: return@withContext null,
                        content = data["content"] as? String ?: return@withContext null,
                        isBlockable = data["isBlockable"] as? Boolean ?: false,
                        reason = data["reason"] as? String ?: "",
                        timestamp = data["timestamp"] as? Timestamp ?: Timestamp.now(),
                        status = data["status"] as? String ?: "pending"
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("ContentFilteringHelper", "Error getting content filtering result", e)
                null
            }
        }
    }

    suspend fun waitForContentFilteringResult(contentId: String, timeoutMillis: Long = 30000): ContentFilteringResult? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            val result = getContentFilteringResult(contentId)
            if (result != null) {
                return result
            }
            kotlinx.coroutines.delay(1000) // Wait 1 second before checking again
        }
        return null
    }
}

data class ContentFilteringResult(
    val id: String,
    val childId: String,
    val childName: String,
    val content: String,
    val isBlockable: Boolean,
    val reason: String,
    val timestamp: Timestamp,
    val status: String // pending, blocked, allowed
) 