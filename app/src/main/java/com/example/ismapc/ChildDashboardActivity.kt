package com.example.ismapc

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.example.ismapc.GeminiContentService.SuggestionCategory
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class ChildDashboardActivity : ComponentActivity() {
    private val TAG = "ChildDashboardActivity"
    private val auth = FirebaseAuth.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val childId = auth.currentUser?.uid ?: ""
        
        // Force generate suggestions if needed
        val forceGenerate = intent.getBooleanExtra("forceGenerateSuggestions", false)
        if (forceGenerate) {
            Log.d(TAG, "Forcing suggestion generation")
            val workRequest = OneTimeWorkRequestBuilder<ContentSuggestionWorker>().build()
            WorkManager.getInstance(this).enqueue(workRequest)
        }
        
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChildDashboardScreen(childId = childId)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDashboardScreen(childId: String) {
    val TAG = "ChildDashboardScreen"
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // State for content suggestions
    var isLoading by remember { mutableStateOf(true) }
    var suggestions by remember { mutableStateOf<List<GeminiContentService.ContentSuggestion>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var childName by remember { mutableStateOf("") }

    // Fetch content suggestions and child name when screen is first displayed
    LaunchedEffect(childId) {
        try {
            // Get child's name
            val profileDoc = withContext(Dispatchers.IO) {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document("child")
                    .collection("profile")
                    .document(childId)
                    .get()
                    .await()
            }
            
            if (profileDoc.exists()) {
                childName = profileDoc.getString("name") ?: ""
                Log.d(TAG, "Loaded child name: $childName")
            } else {
                Log.w(TAG, "No profile document found for child: $childId")
                childName = "Child"
            }

            // Generate suggestions directly
            val geminiService = GeminiContentService(context)
            suggestions = geminiService.generateSuggestions(childId)
            
            // Sort suggestions by category
            suggestions = suggestions.sortedBy { it.category }

            isLoading = false
            errorMessage = null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading content suggestions: ${e.message}", e)
            isLoading = false
            errorMessage = "Error loading suggestions: ${e.message}"
        }
    }

    // Refresh suggestions when pull-to-refresh is triggered
    val refreshState = remember { mutableStateOf(false) }
    
    LaunchedEffect(key1 = refreshState.value) {
        if (refreshState.value) {
            try {
                // Generate new suggestions directly
                val geminiService = GeminiContentService(context)
                suggestions = geminiService.generateSuggestions(childId)
                
                // Sort suggestions by category
                suggestions = suggestions.sortedBy { it.category }

                isLoading = false
                errorMessage = null
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing suggestions: ${e.message}", e)
                isLoading = false
                errorMessage = "Error refreshing suggestions: ${e.message}"
            }
            refreshState.value = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Title with child's name
            Text(
                text = "Hi $childName! Here are your personalized suggestions:",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.primary
                ),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .wrapContentSize(Alignment.Center)
            )

            // Error message if any
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterHorizontally)
                )
                return@Column
            }

            // Content suggestions grouped by category
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Group suggestions by category
                val groupedSuggestions = suggestions.groupBy { it.category }

                // Display each category section
                groupedSuggestions.entries.forEach { (category, categorySuggestions) ->
                    item {
                        // Category header
                        Text(
                            text = category.name.replace('_', ' ').lowercase().capitalize(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    
                    items(categorySuggestions) { suggestion ->
                        SuggestionCard(suggestion = suggestion)
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionCard(suggestion: GeminiContentService.ContentSuggestion) {
    val context = LocalContext.current
    val categoryIcon = when (suggestion.category) {
        SuggestionCategory.EDUCATIONAL -> Icons.Default.MenuBook
        SuggestionCategory.ENTERTAINMENT -> Icons.Default.Gamepad
        SuggestionCategory.PRODUCTIVITY -> Icons.Default.Business
        SuggestionCategory.HEALTH -> Icons.Default.DirectionsBike
        SuggestionCategory.CREATIVE -> Icons.Default.Create
    }
    
    val categoryColor = when (suggestion.category) {
        SuggestionCategory.EDUCATIONAL -> Color(0xFF64B5F6) // Lighter Blue
        SuggestionCategory.ENTERTAINMENT -> Color(0xFFFFAB91) // Lighter Orange
        SuggestionCategory.PRODUCTIVITY -> Color(0xFF81C784) // Lighter Green
        SuggestionCategory.HEALTH -> Color(0xFFF48FB1) // Lighter Pink
        SuggestionCategory.CREATIVE -> Color(0xFFBA68C8) // Lighter Purple
    }
    
    val gradientColors = listOf(
        categoryColor,
        categoryColor.copy(alpha = 0.7f)
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Open link URL if available
                suggestion.linkUrl?.let { url ->
                    if (url.isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = android.net.Uri.parse(url)
                        context.startActivity(intent)
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Category header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(colors = gradientColors),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = suggestion.category.name.lowercase()
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                    
                    // Show link indicator if URL is available
                    suggestion.linkUrl?.let {
                        if (it.isNotEmpty()) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "Open Link",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Display image if available
                suggestion.imageUrl?.let { imageUrl ->
                    if (imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = suggestion.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentScale = ContentScale.Crop
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    ),
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = suggestion.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun generateFallbackSuggestions(): List<GeminiContentService.ContentSuggestion> {
    return listOf(
        GeminiContentService.ContentSuggestion(
            title = "Educational Games Challenge",
            description = "Try playing a new educational game for 30 minutes today to learn while having fun!",
            category = SuggestionCategory.EDUCATIONAL,
            imageUrl = "https://cdn-icons-png.flaticon.com/512/2331/2331860.png",
            linkUrl = "https://www.education.com/games/"
        ),
        GeminiContentService.ContentSuggestion(
            title = "Digital Art Project",
            description = "Create a digital artwork about your favorite subject and share it with your family.",
            category = SuggestionCategory.CREATIVE,
            imageUrl = "https://cdn-icons-png.flaticon.com/512/1547/1547527.png",
            linkUrl = "https://www.autodraw.com/"
        ),
        GeminiContentService.ContentSuggestion(
            title = "Outdoor Adventure",
            description = "Take a break from screens and go on a nature scavenger hunt to find 5 interesting items.",
            category = SuggestionCategory.HEALTH,
            imageUrl = "https://cdn-icons-png.flaticon.com/512/2382/2382461.png",
            linkUrl = "https://www.natureplaysa.org.au/wp-content/uploads/2017/08/Nature-Scavenger-Hunt-2.pdf"
        ),
        GeminiContentService.ContentSuggestion(
            title = "Reading Challenge",
            description = "Try reading a new e-book about a topic you're interested in for 20 minutes.",
            category = SuggestionCategory.EDUCATIONAL,
            imageUrl = "https://cdn-icons-png.flaticon.com/512/2436/2436882.png",
            linkUrl = "https://www.getepic.com/"
        ),
        GeminiContentService.ContentSuggestion(
            title = "Learn to Code",
            description = "Try a beginner-friendly coding exercise on Scratch or Code.org.",
            category = SuggestionCategory.PRODUCTIVITY,
            imageUrl = "https://cdn-icons-png.flaticon.com/512/2721/2721620.png",
            linkUrl = "https://scratch.mit.edu/"
        )
    )
} 