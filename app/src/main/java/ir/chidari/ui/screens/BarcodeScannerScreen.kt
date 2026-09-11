package ir.chidari.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import ir.chidari.util.Fa
import java.util.concurrent.Executors

/**
 * اسکن بارکد محصول با دوربین.
 *
 * تشخیص کاملاً **روی خود دستگاه** انجام می‌شود (ML Kit)، پس نیازی به
 * اینترنت ندارد و کد محصول جایی فرستاده نمی‌شود.
 *
 * پس از خواندن موفق، کد به فرم افزودن محصول برگردانده می‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun BarcodeScannerScreen(
    onResult: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchOn by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf<String?>(null) }
    /**
     * مدل تشخیص از Google Play می‌آید و ممکن است بار اول آماده نباشد.
     * تا وقتی آماده شود، به کاربر پیام می‌دهیم به‌جای اینکه صفحه بی‌صدا کار نکند.
     */
    var moduleError by remember { mutableStateOf<String?>(null) }
    var scannedOnce by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // اجراکننده جداگانه برای پردازش فریم‌ها تا رابط کاربری کند نشود
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اسکن بارکد") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    if (hasPermission) {
                        IconButton(onClick = {
                            torchOn = !torchOn
                            camera?.cameraControl?.enableTorch(torchOn)
                        }) {
                            Icon(
                                if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = "چراغ"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->

        if (!hasPermission) {
            // ---------- بدون اجازه دوربین ----------
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("📷", fontSize = 54.sp)
                Spacer(Modifier.height(14.dp))
                Text(
                    "اجازه دسترسی به دوربین لازم است",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "برای خواندن بارکد محصول، برنامه باید به دوربین دسترسی داشته باشد. " +
                        "هیچ تصویری ذخیره یا ارسال نمی‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("اجازه می‌دهم") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("وارد کردن دستی کد")
                }
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ---------- پیش‌نمایش دوربین ----------
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)

                    providerFuture.addListener({
                        val provider = providerFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val scanner = BarcodeScanning.getClient()

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        analysis.setAnalyzer(executor) { proxy ->
                            val media = proxy.image
                            if (media == null || detected != null) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(
                                media, proxy.imageInfo.rotationDegrees
                            )
                            scanner.process(image)
                                .addOnSuccessListener { codes ->
                                    scannedOnce = true
                                    moduleError = null
                                    val code = codes.firstNotNullOfOrNull { bc ->
                                        bc.rawValue?.takeIf { it.isNotBlank() }
                                    }
                                    if (code != null && detected == null) {
                                        detected = code
                                    }
                                }
                                .addOnFailureListener { e ->
                                    // معمولاً یعنی مدل هنوز از Play دانلود نشده است
                                    val m = e.message.orEmpty().lowercase()
                                    moduleError = when {
                                        m.contains("wait") || m.contains("download") ||
                                            m.contains("unavailable") || m.contains("module") ->
                                            "در حال آماده‌سازی بارکدخوان… بار اول کمی طول می‌کشد."
                                        m.contains("google play") ->
                                            "برای اسکن بارکد، سرویس‌های Google Play لازم است."
                                        else -> "اسکن ممکن نشد: " + e.message.orEmpty()
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        }

                        runCatching {
                            provider.unbindAll()
                            camera = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            // ---------- کادر هدف ----------
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.8f)
                    .height(180.dp)
                    .border(3.dp, Color.White, RoundedCornerShape(16.dp))
            )

            // ---------- راهنما ----------
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "بارکد را داخل کادر بگیرید",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // ---------- وضعیت آماده‌سازی مدل ----------
            if (detected == null && moduleError != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            moduleError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // ---------- نتیجه ----------
            detected?.let { code ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "✅ بارکد خوانده شد",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            Fa.digits(code),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onResult(code) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("استفاده از این کد") }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { detected = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("اسکن دوباره") }
                    }
                }
            }
        }
    }
}
