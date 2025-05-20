package com.example.ismapc

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ContentFilterActivity : ComponentActivity() {
    private val TAG = "ContentFilterActivity"
    private val firestore = FirebaseFirestore.getInstance()
    private var childId: String? = null
    private var childName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        childId = intent.getStringExtra("childId")
        childName = intent.getStringExtra("childName")
        
        if (childId == null) {
            Log.e(TAG, "No child ID provided")
            Toast.makeText(this, "Error: No child ID provided", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContentFilterScreen(
                        childId = childId!!,
                        childName = childName ?: "Child",
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentFilterScreen(
    childId: String,
    childName: String,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Blocked Words", "Content History")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Content Filter - $childName") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> BlockedWordsTab(
                childId = childId,
                modifier = Modifier.padding(padding)
            )
            1 -> ContentHistoryTab(
                childId = childId,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedWordsTab(
    childId: String,
    modifier: Modifier = Modifier
) {
    val TAG = "BlockedWordsTab"
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    var blockedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var newWord by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(childId) {
        try {
            val wordsSnapshot = firestore.collection("contentFiltering")
                .document(childId)
                .get()
                .await()
            
            blockedWords = wordsSnapshot.get("blockedWords") as? List<String> ?: emptyList()
            isLoading = false
        } catch (e: Exception) {
            Log.e(TAG, "Error loading data", e)
            Toast.makeText(context, "Error loading data: ${e.message}", Toast.LENGTH_LONG).show()
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, "Add Word")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Blocked Word")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (blockedWords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No blocked words yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(blockedWords) { word ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(word)
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val currentWords = blockedWords.toMutableList()
                                            currentWords.remove(word)
                                            
                                            firestore.collection("contentFiltering")
                                                .document(childId)
                                                .set(mapOf("blockedWords" to currentWords))
                                                .await()
                                            
                                            blockedWords = currentWords
                                            Toast.makeText(context, "Word removed", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("BlockedWordsTab", "Error removing word", e)
                                            Toast.makeText(context, "Error removing word: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, "Remove Word")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Blocked Word") },
            text = {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    label = { Text("Word to block") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedWord = newWord.trim()
                        if (trimmedWord.isNotBlank()) {
                            scope.launch {
                                try {
                                    val currentWords = blockedWords.toMutableList()
                                    if (!currentWords.contains(trimmedWord)) {
                                        currentWords.add(trimmedWord)
                                        
                                        Log.d(TAG, "Saving blocked words: $currentWords")
                                        firestore.collection("contentFiltering")
                                            .document(childId)
                                            .set(mapOf("blockedWords" to currentWords), com.google.firebase.firestore.SetOptions.merge())
                                            .addOnSuccessListener {
                                                Log.d(TAG, "Successfully saved blocked words")
                                                blockedWords = currentWords
                                                Toast.makeText(context, "Word added", Toast.LENGTH_SHORT).show()
                                                showAddDialog = false
                                                newWord = ""
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e(TAG, "Error saving blocked words", e)
                                                Toast.makeText(context, "Error adding word: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                    } else {
                                        Toast.makeText(context, "Word already blocked", Toast.LENGTH_SHORT).show()
                                        showAddDialog = false
                                        newWord = ""
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error adding word", e)
                                    Toast.makeText(context, "Error adding word: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please enter a word to block", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        newWord = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ContentHistoryTab(
    childId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    var blockedContent by remember { mutableStateOf<List<BlockedContent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(childId) {
        try {
            val contentSnapshot = firestore.collection("contentToFilter")
                .document(childId)
                .collection("filteredContent")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            blockedContent = contentSnapshot.documents.mapNotNull { doc ->
                val title = doc.getString("title") ?: return@mapNotNull null
                val url = doc.getString("url") ?: return@mapNotNull null
                val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: return@mapNotNull null
                BlockedContent(title, url, timestamp)
            }
            
            isLoading = false
        } catch (e: Exception) {
            Log.e("ContentHistoryTab", "Error loading data", e)
            Toast.makeText(context, "Error loading data: ${e.message}", Toast.LENGTH_LONG).show()
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (blockedContent.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No blocked content history",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(blockedContent) { content ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                content.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                content.url,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Blocked on: ${SimpleDateFormat("MMM dd, yyyy HH:mm").format(content.timestamp)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

data class BlockedContent(
    val title: String,
    val url: String,
    val timestamp: Long
) 