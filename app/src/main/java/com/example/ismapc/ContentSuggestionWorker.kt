package com.example.ismapc

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker to periodically generate content suggestions for children
 * based on their app usage patterns.
 */
class ContentSuggestionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "ContentSuggestionWorker"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val geminiService = GeminiContentService(context)

    override suspend fun doWork(): Result {
        try {
            Log.d(TAG, "Starting content suggestion worker")
            
            // Get the current user
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e(TAG, "No user logged in, cannot generate suggestions")
                return Result.failure()
            }
            
            // Check if this is a child account (we only generate suggestions for child accounts)
            val isChild = isChildAccount(currentUser.uid)
            
            if (isChild) {
                // Generate suggestions for self
                Log.d(TAG, "Generating suggestions for child: ${currentUser.uid}")
                val suggestions = geminiService.generateSuggestions(currentUser.uid)
                
                if (suggestions.isNotEmpty()) {
                    Log.d(TAG, "Successfully generated ${suggestions.size} suggestions")
                    return Result.success()
                } else {
                    Log.e(TAG, "Failed to generate suggestions")
                    return Result.retry()
                }
            } else {
                // For parent accounts, generate suggestions for all their children
                val childIds = getChildrenIds(currentUser.email)
                
                if (childIds.isEmpty()) {
                    Log.d(TAG, "No children found for parent: ${currentUser.email}")
                    return Result.success()
                }
                
                // Generate suggestions for each child
                var allSuccessful = true
                for (childId in childIds) {
                    try {
                        Log.d(TAG, "Generating suggestions for child: $childId")
                        val suggestions = geminiService.generateSuggestions(childId)
                        
                        if (suggestions.isEmpty()) {
                            Log.e(TAG, "Failed to generate suggestions for child: $childId")
                            allSuccessful = false
                        } else {
                            Log.d(TAG, "Successfully generated ${suggestions.size} suggestions for child: $childId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error generating suggestions for child $childId: ${e.message}", e)
                        allSuccessful = false
                    }
                }
                
                return if (allSuccessful) Result.success() else Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in content suggestion worker: ${e.message}", e)
            return Result.retry()
        }
    }
    
    /**
     * Check if the given user ID is a child account.
     */
    private suspend fun isChildAccount(userId: String): Boolean {
        return try {
            val document = withContext(Dispatchers.IO) {
                firestore.collection("users")
                    .document("child")
                    .collection("profile")
                    .document(userId)
                    .get()
                    .await()
            }
            
            document.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if account is child: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get the IDs of all children associated with the given parent email.
     */
    private suspend fun getChildrenIds(parentEmail: String?): List<String> {
        if (parentEmail.isNullOrEmpty()) return emptyList()
        
        return try {
            val result = mutableListOf<String>()
            
            val documents = withContext(Dispatchers.IO) {
                firestore.collection("users")
                    .document("child")
                    .collection("profile")
                    .whereEqualTo("parentEmail", parentEmail)
                    .get()
                    .await()
            }
            
            documents.documents.forEach { document ->
                result.add(document.id)
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting children IDs: ${e.message}", e)
            emptyList()
        }
    }
    
    companion object {
        // Schedule intervals
        val REFRESH_INTERVAL_HOURS = 6L // Generate new suggestions every 6 hours
        
        // Worker tag for identification
        const val WORKER_TAG = "content_suggestion_worker"
    }
} 