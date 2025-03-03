package com.example.ismapc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import java.util.Date
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

class ChildSignUpActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private var showParentEmailDialog by mutableStateOf(false)
    private var pendingGoogleToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showParentEmailDialog) {
                        ParentEmailDialog(
                            onDismiss = { showParentEmailDialog = false },
                            onConfirm = { parentEmail ->
                                showParentEmailDialog = false
                                pendingGoogleToken?.let { token ->
                                    firebaseAuthWithGoogle(token, parentEmail)
                                }
                            }
                        )
                    }
                    
                    ChildSignUpScreen(
                        onSignUp = { email, password, fullName, parentEmail ->
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        // Save additional user data to Firestore
                                        val user = auth.currentUser
                                        if (user != null) {
                                            val userData = hashMapOf(
                                                "fullName" to fullName,
                                                "email" to email,
                                                "parentEmail" to parentEmail,
                                                "userType" to "child",
                                                "createdAt" to Timestamp(Date())
                                            )
                                            
                                            firestore.collection("users")
                                                .document("child")
                                                .collection(user.uid)
                                                .document("profile")
                                                .set(userData)
                                                .addOnSuccessListener {
                                                    Toast.makeText(this, "Account created successfully!", Toast.LENGTH_LONG).show()
                                                    startActivity(Intent(this, MainActivity::class.java))
                                                    finish()
                                                }
                                                .addOnFailureListener { e ->
                                                    if (e.message?.contains("permission-denied") == true) {
                                                        user.delete().addOnCompleteListener { deleteTask ->
                                                            if (deleteTask.isSuccessful) {
                                                                Toast.makeText(this, "Failed to create account: Permission denied. Please try again.", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    } else {
                                                        Toast.makeText(this, "Failed to save user data: ${e.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                        } else {
                                            Toast.makeText(this, "Authentication error: User is null", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(this, "Sign up failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                        },
                        onGoogleSignUp = {
                            startGoogleSignIn()
                        }
                    )
                }
            }
        }
    }

    private fun startGoogleSignIn() {
        // Sign out from Firebase
        auth.signOut()
        
        // Sign out from Google
        googleSignInClient.signOut().addOnCompleteListener(this) {
            // After signing out, start the Google Sign-In flow
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                pendingGoogleToken = account.idToken
                showParentEmailDialog = true
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String, parentEmail: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Send verification email
                        user.sendEmailVerification()
                            .addOnCompleteListener { verificationTask ->
                                if (verificationTask.isSuccessful) {
                                    val userData = hashMapOf(
                                        "fullName" to user.displayName,
                                        "email" to user.email,
                                        "parentEmail" to parentEmail,
                                        "userType" to "child",
                                        "createdAt" to Timestamp(Date()),
                                        "isEmailVerified" to false
                                    )

                                    firestore.collection("users")
                                        .document("child")
                                        .collection(user.uid)
                                        .document("profile")
                                        .set(userData)
                                        .addOnSuccessListener {
                                            // Redirect to email verification screen
                                            val intent = Intent(this, EmailVerificationActivity::class.java)
                                            intent.putExtra("email", user.email)
                                            intent.putExtra("password", idToken)
                                            startActivity(intent)
                                            finish()
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(this, "Failed to save user data: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                } else {
                                    Toast.makeText(
                                        this,
                                        "Failed to send verification email: ${verificationTask.exception?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    }
                } else {
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}

@Composable
fun ParentEmailDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var parentEmail by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Parent's Email Required",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = parentEmail,
                    onValueChange = { 
                        parentEmail = it
                        isError = false
                    },
                    label = { Text("Parent's Email Address") },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isError) 4.dp else 16.dp)
                )

                if (isError) {
                    Text(
                        text = "Please enter a valid email address",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (android.util.Patterns.EMAIL_ADDRESS.matcher(parentEmail).matches()) {
                                onConfirm(parentEmail)
                            } else {
                                isError = true
                            }
                        }
                    ) {
                        Text("Continue")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildSignUpScreen(
    onSignUp: (email: String, password: String, fullName: String, parentEmail: String) -> Unit,
    onGoogleSignUp: () -> Unit
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var parentEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Error states
    var fullNameError by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    var parentEmailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var confirmPasswordError by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = { (context as? ComponentActivity)?.finish() }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Title
        Text(
            text = "Create Child Account",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Profile Image Picker
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { imagePicker.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null) {
                Image(
                    painter = painterResource(id = R.drawable.profile_placeholder),
                    contentDescription = "Selected profile image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add profile picture",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Text(
            text = "Add Profile Picture",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Form fields
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = parentEmail,
            onValueChange = { parentEmail = it },
            label = { Text("Parent's Email") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = password,
            onValueChange = { 
                password = it
                passwordError = false
                if (confirmPassword.isNotEmpty()) {
                    confirmPasswordError = password != confirmPassword
                }
            },
            label = { Text("Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (passwordError) 4.dp else 16.dp),
            singleLine = true,
            isError = passwordError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            )
        )

        if (passwordError) {
            Text(
                text = "Password must be at least 6 characters",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 16.dp)
            )
        }

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { 
                confirmPassword = it
                confirmPasswordError = password != it
            },
            label = { Text("Confirm Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (confirmPasswordError) 4.dp else 32.dp),
            singleLine = true,
            isError = confirmPasswordError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

        if (confirmPasswordError) {
            Text(
                text = "Passwords do not match",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 16.dp)
            )
        }

        Button(
            onClick = {
                // Validate all fields
                fullNameError = fullName.isBlank()
                emailError = !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                parentEmailError = !android.util.Patterns.EMAIL_ADDRESS.matcher(parentEmail).matches()
                passwordError = password.length < 6
                confirmPasswordError = password != confirmPassword

                if (!fullNameError && !emailError && !parentEmailError && 
                    !passwordError && !confirmPasswordError) {
                    onSignUp(email, password, fullName, parentEmail)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Sign Up")
        }

        // Or divider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Divider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                "OR",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Divider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        // Google Sign Up Button
        OutlinedButton(
            onClick = onGoogleSignUp,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google_logo),
                    contentDescription = "Google logo",
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign up with Google")
            }
        }
    }
} 