package com.example.ismapc

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class ContentFilteringActivity : ComponentActivity() {
    private val TAG = "ContentFilteringActivity"
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val childId = intent.getStringExtra("childId") ?: run {
            Log.e(TAG, "No childId provided")
            finish()
            return
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContentFilteringScreen(childId)
                }
            }
        }
    }
}

@Composable
fun ContentFilteringScreen(childId: String) {
    var contentList by remember { mutableStateOf<List<FilteredContent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val firestore = FirebaseFirestore.getInstance()

    LaunchedEffect(childId) {
        try {
            // Query the subcollection under the user's ID
            firestore.collection("contentToFilter")
                .document(childId)
                .collection("filteredContent")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        error = "Error loading content: ${e.message}"
                        isLoading = false
                        return@addSnapshotListener
                    }

                    contentList = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            FilteredContent(
                                id = doc.id,
                                content = doc.getString("content") ?: "",
                                isBlockable = doc.getBoolean("isBlockable") ?: false,
                                reason = doc.getString("reason") ?: "",
                                timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now(),
                                status = doc.getString("status") ?: "pending"
                            )
                        } catch (e: Exception) {
                            Log.e("ContentFilteringScreen", "Error parsing document ${doc.id}", e)
                            null
                        }
                    } ?: emptyList()
                    
                    isLoading = false
                }
        } catch (e: Exception) {
            error = "Error setting up listener: ${e.message}"
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Content Filtering History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            contentList.isEmpty() -> {
                Text(
                    text = "No content filtering history found",
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contentList) { content ->
                        ContentItem(content)
                    }
                }
            }
        }
    }
}

@Composable
fun ContentItem(content: FilteredContent) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (content.isBlockable) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = content.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Status: ${content.status}",
                style = MaterialTheme.typography.bodySmall
            )
            
            if (content.isBlockable) {
                Text(
                    text = "Reason: ${content.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Text(
                text = dateFormat.format(content.timestamp.toDate()),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

data class FilteredContent(
    val id: String,
    val content: String,
    val isBlockable: Boolean,
    val reason: String,
    val timestamp: Timestamp,
    val status: String
) 