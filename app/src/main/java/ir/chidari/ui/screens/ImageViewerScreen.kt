package ir.chidari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ir.chidari.ui.vm.DraftImage
import ir.chidari.ui.vm.UploadState
import ir.chidari.util.Fa

/**
 * نمایش تمام‌صفحه‌ی تصویر محصول با ابزارهای ویرایش — همان کاری که دیوار
 * موقع لمس عکس انجام می‌دهد.
 *
 * بزرگ‌نمایی با دو انگشت، و نوار پایین: برش دوباره، جایگزینی، عکس اصلی،
 * جابه‌جایی و حذف. حذف حتماً پرسش تأیید دارد چون برگشت‌پذیر نیست.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    image: DraftImage?,
    index: Int,
    total: Int,
    isCover: Boolean,
    onRecrop: () -> Unit,
    onReplace: () -> Unit,
    onMakeCover: () -> Unit,
    onMove: (Int) -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (total > 1) "تصویر ${Fa.number((index + 1).toLong())} از ${Fa.number(total.toLong())}"
                        else "تصویر محصول",
                        fontSize = 15.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xCC000000),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (image == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("تصویر پیدا نشد", color = Color.White)
                }
                return@Column
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale <= 1f) Offset.Zero else offset + pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = image.displayModel,
                    contentDescription = "تصویر محصول",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )

                // وضعیت بارگذاری
                val status = when (image.state) {
                    UploadState.UPLOADING ->
                        "در حال بارگذاری… " + Fa.digits("${(image.progress * 100).toInt()}٪") to Color(0xCC004D40)
                    UploadState.DONE -> "✓ ذخیره روی سرور" to Color(0xCC1B5E20)
                    UploadState.FAILED -> "⚠️ ${image.error}" to Color(0xCCB71C1C)
                    UploadState.LOCAL_ONLY -> "فقط روی گوشی — با ذخیره محصول ارسال می‌شود" to Color(0xCC37474F)
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(status.second)
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(status.first, color = Color.White, fontSize = 11.sp)
                }

                if (isCover) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
                            .padding(horizontal = 11.dp, vertical = 4.dp)
                    ) {
                        Text("عکس اصلی", color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            // ---------- جابه‌جایی ----------
            if (total > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161616))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MoveButton("→ جلوتر", enabled = index > 0) { onMove(-1) }
                    MoveButton("عقب‌تر ←", enabled = index < total - 1) { onMove(1) }
                    if (!isCover) MoveButton("⭐ عکس اصلی", enabled = true) { onMakeCover() }
                }
            }

            // ---------- نوار ابزار ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1C1C))
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (image.state == UploadState.FAILED) {
                    ToolButton("🔁", "تلاش دوباره", onRetry)
                }
                ToolButton("✂️", "برش دوباره", onRecrop, enabled = image.canRecrop)
                ToolButton("🔄", "جایگزینی", onReplace)
                ToolButton("🗑️", "حذف", { confirmDelete = true }, danger = true)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف تصویر محصول؟") },
            text = {
                Text(
                    if (image?.state == UploadState.DONE)
                        "تصویر از این محصول و از سرور حذف می‌شود. این کار برگشت‌پذیر نیست."
                    else "تصویر از این محصول حذف می‌شود."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun MoveButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) Color(0xFF2E2E2E) else Color(0xFF212121))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Color(0xFF616161),
            fontSize = 11.5.sp
        )
    }
}

@Composable
private fun ToolButton(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val tint = when {
        !enabled -> Color(0xFF5A5A5A)
        danger -> Color(0xFFEF5350)
        else -> Color.White
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 19.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = tint, fontSize = 10.5.sp, textAlign = TextAlign.Center)
    }
}
