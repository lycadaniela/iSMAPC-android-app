package com.example.ismapc

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Service to interact with Gemini AI for generating content suggestions
 * based on a child's app usage patterns.
 */
class GeminiContentService {
    private val TAG = "GeminiContentService"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Replace with your actual API key from Google AI Studio
    private val API_KEY = "YOUR_GEMINI_API_KEY" 
    
    // Categories for suggestions
    enum class SuggestionCategory {
        EDUCATIONAL, ENTERTAINMENT, PRODUCTIVITY, HEALTH, CREATIVE
    }
    
    // Data class for structured suggestions
    data class ContentSuggestion(
        val title: String,
        val description: String,
        val category: SuggestionCategory,
        val ageAppropriate: Boolean = true,
        val imageUrl: String? = null,
        val linkUrl: String? = null,
        val timestamp: Long = Calendar.getInstance().timeInMillis
    )
    
    /**
     * Generate content suggestions based on the child's app usage data.
     * @param childId The ID of the child to generate suggestions for
     * @return List of content suggestions or empty list if generation fails
     */
    suspend fun generateSuggestions(childId: String): List<ContentSuggestion> {
        try {
            Log.d(TAG, "Generating suggestions for child: $childId")
            
            // 1. Retrieve the child's app usage data from Firestore
            val appUsageData = getAppUsageData(childId)
            if (appUsageData.isEmpty()) {
                Log.e(TAG, "No app usage data available for child: $childId")
                return emptyList()
            }
            
            // 2. Get child's profile to know their age
            val childAge = getChildAge(childId)
            
            // 3. Format data for Gemini prompt
            val prompt = createGeminiPrompt(appUsageData, childAge)
            
            // 4. Call Gemini API
            val suggestions = callGeminiAPI(prompt)
            
            // 5. Store suggestions in Firestore
            storeSuggestions(childId, suggestions)
            
            return suggestions
        } catch (e: Exception) {
            Log.e(TAG, "Error generating suggestions: ${e.message}", e)
            return emptyList()
        }
    }
    
    /**
     * Retrieve the child's app usage data from Firestore.
     */
    private suspend fun getAppUsageData(childId: String): Map<String, Long> {
        return try {
            val result = mutableMapOf<String, Long>()
            
            val document = withContext(Dispatchers.IO) {
                firestore.collection("appUsage")
                    .document(childId)
                    .collection("stats")
                    .document("daily")
                    .get()
                    .await()
            }
            
            if (document.exists()) {
                @Suppress("UNCHECKED_CAST")
                val appsData = document.get("apps") as? Map<String, Map<String, Any>> ?: return emptyMap()
                
                for ((appName, appData) in appsData) {
                    val weeklyMinutes = (appData["weeklyMinutes"] as? Number)?.toLong() ?: 0L
                    val packageName = (appData["packageName"] as? String) ?: ""
                    
                    // Skip sample data
                    if (packageName.contains(".SAMPLE") || appName.contains("[SAMPLE]")) {
                        continue
                    }
                    
                    // Store app usage time
                    result[appName] = weeklyMinutes
                }
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving app usage data: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Get child's age from their profile.
     */
    private suspend fun getChildAge(childId: String): Int {
        return try {
            val document = withContext(Dispatchers.IO) {
                firestore.collection("users")
                    .document("child")
                    .collection("profile")
                    .document(childId)
                    .get()
                    .await()
            }
            
            if (document.exists()) {
                val birthDateTimestamp = document.get("birthDate") as? Timestamp
                val birthDate = birthDateTimestamp?.toDate()
                if (birthDate != null) {
                    val calendar = Calendar.getInstance()
                    val currentYear = calendar.get(Calendar.YEAR)
                    calendar.time = birthDate
                    val birthYear = calendar.get(Calendar.YEAR)
                    return currentYear - birthYear
                }
            }
            
            // Default age if not found
            10
        } catch (e: Exception) {
            Log.e(TAG, "Error getting child age: ${e.message}", e)
            10 // Default fallback age
        }
    }
    
    /**
     * Create a structured prompt for Gemini based on the child's app usage and age.
     */
    private fun createGeminiPrompt(appUsage: Map<String, Long>, childAge: Int): String {
        // Sort apps by usage time (descending)
        val sortedApps = appUsage.entries.sortedByDescending { it.value }
            .take(10) // Take top 10 apps
        
        val appListText = sortedApps.joinToString("\n") { (app, minutes) ->
            "- $app: ${minutes} minutes per week"
        }
        
        return """
            You are a helpful assistant providing content suggestions for a child.
            
            Child's age: $childAge years old
            
            The child frequently uses these apps (weekly usage time):
            $appListText
            
            Based on this information, suggest 5 age-appropriate content ideas, activities, or resources that:
            1. Align with the child's interests shown by their app usage
            2. Are educational but engaging
            3. Are appropriate for a $childAge-year-old child
            4. Encourage healthy digital habits
            5. Include a mix of online and offline activities
            
            Format each suggestion as follows:
            Title: [Title]
            Description: [Brief description]
            Category: [One of: EDUCATIONAL, ENTERTAINMENT, PRODUCTIVITY, HEALTH, CREATIVE]
            ImageURL: [Optional URL to a relevant image]
            LinkURL: [Optional URL to a website, video, or resource]
            
            IMPORTANT: 
            - Ensure all suggestions are age-appropriate, safe, and beneficial for a $childAge-year-old child.
            - For online suggestions, include direct URLs to safe, child-appropriate resources.
            - For image suggestions, provide URLs to appropriate educational or informative images.
            - All URLs must be to reputable, safe, and age-appropriate websites.
        """.trimIndent()
    }
    
    /**
     * Call the Gemini API to generate suggestions.
     */
    private suspend fun callGeminiAPI(prompt: String): List<ContentSuggestion> {
        return try {
            Log.d(TAG, "Calling Gemini API with prompt: $prompt")
            
            // Initialize Gemini model
            val model = GenerativeModel(
                modelName = "gemini-pro",
                apiKey = API_KEY
            )
            
            // Generate content
            val response = withContext(Dispatchers.IO) {
                model.generateContent(
                    content {
                        text(prompt)
                    }
                )
            }
            
            val responseText = response.text ?: ""
            Log.d(TAG, "Gemini response received: $responseText")
            
            // Parse the response into structured suggestion objects
            parseGeminiResponse(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API: ${e.message}", e)
            
            // Return fallback suggestions in case of API failure
            generateFallbackSuggestions()
        }
    }
    
    /**
     * Parse the Gemini text response into structured ContentSuggestion objects.
     */
    private fun parseGeminiResponse(response: String): List<ContentSuggestion> {
        val suggestions = mutableListOf<ContentSuggestion>()
        
        try {
            // Split the response by suggestion blocks (each starting with "Title:")
            val suggestionBlocks = response.split("Title:").filter { it.isNotBlank() }
            
            for (block in suggestionBlocks) {
                try {
                    val lines = block.trim().split("\n")
                    
                    val title = lines[0].trim()
                    
                    // Find description line
                    val descriptionLine = lines.find { it.startsWith("Description:") }
                    val description = descriptionLine?.substringAfter("Description:")?.trim() ?: ""
                    
                    // Find category line
                    val categoryLine = lines.find { it.startsWith("Category:") }
                    val categoryStr = categoryLine?.substringAfter("Category:")?.trim() ?: "EDUCATIONAL"
                    
                    // Parse category
                    val category = try {
                        SuggestionCategory.valueOf(categoryStr.uppercase())
                    } catch (e: Exception) {
                        SuggestionCategory.EDUCATIONAL
                    }
                    
                    // Find image URL
                    val imageUrlLine = lines.find { it.startsWith("ImageURL:") }
                    val imageUrl = imageUrlLine?.substringAfter("ImageURL:")?.trim()
                    
                    // Find link URL
                    val linkUrlLine = lines.find { it.startsWith("LinkURL:") }
                    val linkUrl = linkUrlLine?.substringAfter("LinkURL:")?.trim()
                    
                    // Create and add suggestion
                    suggestions.add(
                        ContentSuggestion(
                            title = title,
                            description = description,
                            category = category,
                            imageUrl = imageUrl,
                            linkUrl = linkUrl
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing suggestion block: $block", e)
                    // Continue with next block
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini response: ${e.message}", e)
        }
        
        return if (suggestions.isEmpty()) {
            // If parsing failed, return fallback suggestions
            generateFallbackSuggestions()
        } else {
            suggestions
        }
    }
    
    /**
     * Generate fallback suggestions in case the API call fails.
     */
    private fun generateFallbackSuggestions(): List<ContentSuggestion> {
        return listOf(
            ContentSuggestion(
                title = "Educational Games Challenge",
                description = "Try playing a new educational game for 30 minutes today to learn while having fun!",
                category = SuggestionCategory.EDUCATIONAL,
                imageUrl = "https://cdn-icons-png.flaticon.com/512/2331/2331860.png",
                linkUrl = "https://www.education.com/games/"
            ),
            ContentSuggestion(
                title = "Digital Art Project",
                description = "Create a digital artwork about your favorite subject and share it with your family.",
                category = SuggestionCategory.CREATIVE,
                imageUrl = "https://cdn-icons-png.flaticon.com/512/1547/1547527.png",
                linkUrl = "https://www.autodraw.com/"
            ),
            ContentSuggestion(
                title = "Outdoor Adventure",
                description = "Take a break from screens and go on a nature scavenger hunt to find 5 interesting items.",
                category = SuggestionCategory.HEALTH,
                imageUrl = "https://cdn-icons-png.flaticon.com/512/2382/2382461.png",
                linkUrl = "https://www.natureplaysa.org.au/wp-content/uploads/2017/08/Nature-Scavenger-Hunt-2.pdf"
            ),
            ContentSuggestion(
                title = "Reading Challenge",
                description = "Try reading a new e-book about a topic you're interested in for 20 minutes.",
                category = SuggestionCategory.EDUCATIONAL,
                imageUrl = "https://cdn-icons-png.flaticon.com/512/2436/2436882.png",
                linkUrl = "https://www.getepic.com/"
            ),
            ContentSuggestion(
                title = "Learn to Code",
                description = "Try a beginner-friendly coding exercise on Scratch or Code.org.",
                category = SuggestionCategory.PRODUCTIVITY,
                imageUrl = "https://cdn-icons-png.flaticon.com/512/2721/2721620.png",
                linkUrl = "https://scratch.mit.edu/"
            )
        )
    }
    
    /**
     * Store the generated suggestions in Firestore.
     */
    private suspend fun storeSuggestions(childId: String, suggestions: List<ContentSuggestion>) {
        try {
            // Convert suggestions to map for Firestore
            val suggestionsMap = suggestions.mapIndexed { index, suggestion ->
                "suggestion_$index" to mapOf(
                    "title" to suggestion.title,
                    "description" to suggestion.description,
                    "category" to suggestion.category.name,
                    "ageAppropriate" to suggestion.ageAppropriate,
                    "imageUrl" to (suggestion.imageUrl ?: ""),
                    "linkUrl" to (suggestion.linkUrl ?: ""),
                    "timestamp" to suggestion.timestamp
                )
            }.toMap()
            
            // Add metadata
            val dataToStore = mapOf(
                "suggestions" to suggestionsMap,
                "lastUpdated" to Calendar.getInstance().timeInMillis,
                "count" to suggestions.size
            )
            
            // Store in Firestore
            withContext(Dispatchers.IO) {
                firestore.collection("contentSuggestions")
                    .document(childId)
                    .set(dataToStore)
            }
            
            Log.d(TAG, "Successfully stored ${suggestions.size} suggestions for child: $childId")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing suggestions: ${e.message}", e)
        }
    }
} 