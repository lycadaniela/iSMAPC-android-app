package com.example.ismapc

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentFilteringManager(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private var geminiModel: GenerativeModel? = null

    init {
        try {
            val apiKey = context.getString(R.string.gemini_api_key)
            if (apiKey.isNotBlank()) {
                geminiModel = GenerativeModel(
                    modelName = "gemini-pro",
                    apiKey = apiKey
                )
            } else {
                Log.e("ContentFilteringManager", "Gemini API key is empty")
            }
        } catch (e: Exception) {
            Log.e("ContentFilteringManager", "Error initializing Gemini model", e)
        }
    }

    suspend fun analyzeContent(content: String): ContentAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val model = geminiModel
            if (model == null) {
                Log.e("ContentFilteringManager", "Gemini model not initialized")
                return@withContext ContentAnalysisResult(
                    isBlockable = false,
                    reason = "Content filtering service not properly configured"
                )
            }

            val prompt = """
                Analyze the following content and determine if it should be blocked for children.
                Consider factors like violence, explicit content, hate speech, and other inappropriate material.
                Respond in JSON format with two fields:
                - isBlockable: boolean indicating if the content should be blocked
                - reason: string explaining why the content should or should not be blocked
                
                Content to analyze: $content
            """.trimIndent()

            val response = model.generateContent(prompt)
            val result = parseGeminiResponse(response)
            
            Log.d("ContentFilteringManager", "Content analysis result: $result")
            result
        } catch (e: Exception) {
            Log.e("ContentFilteringManager", "Error analyzing content", e)
            ContentAnalysisResult(
                isBlockable = false,
                reason = "Error analyzing content: ${e.message}"
            )
        }
    }

    private fun parseGeminiResponse(response: GenerateContentResponse): ContentAnalysisResult {
        return try {
            val text = response.text ?: return ContentAnalysisResult(
                isBlockable = false,
                reason = "Empty response from Gemini"
            )
            
            // Parse the JSON response from Gemini
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = text.substring(jsonStart, jsonEnd)
                val json = org.json.JSONObject(jsonStr)
                
                ContentAnalysisResult(
                    isBlockable = json.getBoolean("isBlockable"),
                    reason = json.getString("reason")
                )
            } else {
                ContentAnalysisResult(
                    isBlockable = false,
                    reason = "Could not parse Gemini response"
                )
            }
        } catch (e: Exception) {
            Log.e("ContentFilteringManager", "Error parsing Gemini response", e)
            ContentAnalysisResult(
                isBlockable = false,
                reason = "Error parsing response: ${e.message}"
            )
        }
    }

    suspend fun updateContentFilteringResult(resultId: String, status: String) {
        try {
            firestore.collection("contentFilteringResults")
                .document(resultId)
                .update("status", status)
        } catch (e: Exception) {
            Log.e("ContentFilteringManager", "Error updating content filtering result", e)
        }
    }
} 