package com.example.ismapc

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Divider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import android.content.SharedPreferences
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.auth.FirebaseAuthSettings
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import com.example.ismapc.ui.theme.Orange
import com.example.ismapc.ui.theme.DarkOrange

class LoginActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var authStateListener: AuthStateListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        // Firebase Auth persistence is enabled by default, no need to set it explicitly
        sharedPreferences = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

        // Add auth state listener
        authStateListener = AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // User is signed in, check if they exist in our database
                val db = FirebaseFirestore.getInstance()
                db.collection("users")
                    .document("parents")
                    .collection(user.uid)
                    .document("profile")
                    .get()
                    .addOnSuccessListener { parentDoc ->
                        if (parentDoc.exists()) {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        } else {
                            db.collection("users")
                                .document("child")
                                .collection("profile")
                                .document(user.uid)
                                .get()
                                .addOnSuccessListener { childDoc ->
                                    if (childDoc.exists()) {
                                        startActivity(Intent(this, MainActivity::class.java))
                                        finish()
                                    }
                                }
                        }
                    }
            }
        }

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
                    val context = LocalContext.current
                    // Load saved credentials
                    val savedEmail = sharedPreferences.getString("email", "") ?: ""
                    val savedPassword = sharedPreferences.getString("password", "") ?: ""
                    val savedRememberMe = sharedPreferences.getBoolean("rememberMe", false)
                    var initialLoad by remember { mutableStateOf(true) }
                    var usernameState by remember { mutableStateOf(savedEmail) }
                    var passwordState by remember { mutableStateOf(savedPassword) }
                    var rememberMeState by remember { mutableStateOf(savedRememberMe) }
                    LoginScreen(
                        username = usernameState,
                        password = passwordState,
                        rememberMe = rememberMeState,
                        onUsernameChange = { usernameState = it },
                        onPasswordChange = { passwordState = it },
                        onRememberMeChange = { rememberMeState = it },
                        onLoginSuccess = { email, password ->
                            if (!isNetworkAvailable(context)) {
                                Toast.makeText(context, "No internet connection. Please check your network settings.", Toast.LENGTH_LONG).show()
                                return@LoginScreen
                            }
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        if (rememberMeState) {
                                            sharedPreferences.edit()
                                                .putString("email", email)
                                                .putString("password", password)
                                                .putBoolean("rememberMe", true)
                                                .apply()
                                        } else {
                                            sharedPreferences.edit().clear().apply()
                                        }
                                        startActivity(Intent(context, MainActivity::class.java))
                                        finish()
                                    } else {
                                        Toast.makeText(context, "Incorrect email or password", Toast.LENGTH_LONG).show()
                                    }
                                }
                        },
                        onForgotPassword = {
                            startActivity(Intent(context, ForgotPasswordActivity::class.java))
                        },
                        onGoogleSignIn = {
                            startGoogleSignIn()
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authStateListener)
    }

    private fun startGoogleSignIn() {
        // Sign out from Firebase and Google to ensure a fresh sign-in
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener(this) {
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
                if (account.idToken != null) {
                    firebaseAuthWithGoogle(account.idToken!!)
                } else {
                    Log.e("LoginActivity", "Google sign in failed: No ID token received")
                    Toast.makeText(this, "Google sign in failed: No ID token received", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                val errorMessage = when (e.statusCode) {
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign in was cancelled"
                    GoogleSignInStatusCodes.SIGN_IN_FAILED -> "Sign in failed. Please try again"
                    GoogleSignInStatusCodes.NETWORK_ERROR -> "Network error. Please check your connection"
                    else -> "Google sign in failed: ${e.message}"
                }
                Log.e("LoginActivity", errorMessage, e)
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        Log.d("LoginActivity", "Starting Google sign in with token")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        Log.d("LoginActivity", "Google sign in successful. User ID: ${user.uid}, Email: ${user.email}")
                        // Check if user exists in either parent or child collection
                        val db = FirebaseFirestore.getInstance()
                        
                        // First check parents collection
                        Log.d("LoginActivity", "Checking parent collection")
                        db.collection("users")
                            .document("parents")
                            .collection(user.uid)
                            .document("profile")
                            .get()
                            .addOnSuccessListener { parentDoc ->
                                Log.d("LoginActivity", "Parent doc exists: ${parentDoc.exists()}")
                                if (parentDoc.exists()) {
                                    // User is a parent, proceed to MainActivity
                                    Log.d("LoginActivity", "User is a parent, proceeding to MainActivity")
                                    startActivity(Intent(this, MainActivity::class.java))
                                    finish()
                                } else {
                                    // If not found in parents, check child collection
                                    Log.d("LoginActivity", "Checking child collection")
                                    db.collection("users")
                                        .document("child")
                                        .collection("profile")
                                        .document(user.uid)
                                        .get()
                                        .addOnSuccessListener { childDoc ->
                                            Log.d("LoginActivity", "Child doc exists: ${childDoc.exists()}")
                                            if (childDoc.exists()) {
                                                // User is a child, proceed to MainActivity
                                                Log.d("LoginActivity", "User is a child, proceeding to MainActivity")
                                                startActivity(Intent(this, MainActivity::class.java))
                                                finish()
                                            } else {
                                                // User not found in either collection
                                                Log.e("LoginActivity", "User not found in either collection")
                                                // Delete the Firebase Auth account since it's not registered
                                                user.delete().addOnCompleteListener { deleteTask ->
                                                    if (deleteTask.isSuccessful) {
                                                        Log.d("LoginActivity", "Deleted unregistered Firebase Auth account")
                                                    } else {
                                                        Log.e("LoginActivity", "Failed to delete unregistered account", deleteTask.exception)
                                                    }
                                                    // Sign out from both Firebase and Google
                                                    auth.signOut()
                                                    googleSignInClient.signOut()
                                                    Toast.makeText(
                                                        this,
                                                        "This Google account is not registered. Please sign up first.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("LoginActivity", "Error checking child collection", e)
                                            // Delete the Firebase Auth account on error
                                            user.delete().addOnCompleteListener { deleteTask ->
                                                if (deleteTask.isSuccessful) {
                                                    Log.d("LoginActivity", "Deleted Firebase Auth account after error")
                                                } else {
                                                    Log.e("LoginActivity", "Failed to delete account after error", deleteTask.exception)
                                                }
                                                auth.signOut()
                                                googleSignInClient.signOut()
                                                Toast.makeText(
                                                    this,
                                                    "Error checking account: ${e.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("LoginActivity", "Error checking parent collection", e)
                                // Delete the Firebase Auth account on error
                                user.delete().addOnCompleteListener { deleteTask ->
                                    if (deleteTask.isSuccessful) {
                                        Log.d("LoginActivity", "Deleted Firebase Auth account after error")
                                    } else {
                                        Log.e("LoginActivity", "Failed to delete account after error", deleteTask.exception)
                                    }
                                    auth.signOut()
                                    googleSignInClient.signOut()
                                    Toast.makeText(
                                        this,
                                        "Error checking account: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    }
                } else {
                    Log.e("LoginActivity", "Google sign in failed", task.exception)
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    companion object {
        private const val RC_SIGN_IN = 9001
    }
}

@Composable
fun LoginScreen(
    username: String,
    password: String,
    rememberMe: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberMeChange: (Boolean) -> Unit,
    onLoginSuccess: (String, String) -> Unit,
    onForgotPassword: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Info Icon in top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp)
                .width(64.dp)
                .height(48.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Orange,      // Primary theme color
                            DarkOrange   // Tertiary theme color
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                )
                .clickable {
                    // TODO: Add info dialog or navigation
                    Toast.makeText(context, "Info about the app", Toast.LENGTH_SHORT).show()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Information",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // App Logo
            Image(
                painter = painterResource(id = R.drawable.splash_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 24.dp),
                contentScale = ContentScale.Fit
            )

            // Welcome Text
            Text(
                text = "Welcome Back!",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Sign in to continue",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Input Fields
            OutlinedTextField(
                value = username,
                onValueChange = { onUsernameChange(it) },
                label = { Text("Email") },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Email,
                        contentDescription = "Email",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isError) 4.dp else 16.dp),
                shape = RoundedCornerShape(12.dp),
                isError = isError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            if (isError && username.isEmpty()) {
                Text(
                    text = "Please enter your email",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, bottom = 16.dp)
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = { onPasswordChange(it) },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "Password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isError) 4.dp else 8.dp),
                shape = RoundedCornerShape(12.dp),
                isError = isError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            if (isError && password.isEmpty()) {
                Text(
                    text = "Please enter your password",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, bottom = 16.dp)
                )
            }

            // Remember Me and Forgot Password row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { onRememberMeChange(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        "Remember me",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                TextButton(onClick = { onForgotPassword() }) {
                    Text(
                        text = "Forgot Password?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Login Button
            Button(
                onClick = {
                    if (username.isEmpty() || password.isEmpty()) {
                        isError = true
                        errorMessage = if (username.isEmpty() && password.isEmpty()) {
                            "Please enter your email and password"
                        } else if (username.isEmpty()) {
                            "Please enter your email"
                        } else {
                            "Please enter your password"
                        }
                    } else {
                        isError = false
                        onLoginSuccess(username, password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "Sign In",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (isError) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Or divider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
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

            // Google Sign-In Button
            OutlinedButton(
                onClick = onGoogleSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
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
                    Text(
                        "Continue with Google",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Add this after the Google Sign-In Button in LoginScreen composable
            Spacer(modifier = Modifier.height(24.dp))

            // Sign Up Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        context.startActivity(Intent(context, SignUpOptionActivity::class.java))
                    }
                ) {
                    Text("Sign Up")
                }
            }
        }
    }
} 