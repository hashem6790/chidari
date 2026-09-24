package ir.chidari.ui.screens

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/** نسبت‌های برش که کاربر می‌تواند انتخاب کند. */
private enum class CropRatio(val label: String, val value: Float?) {
    SQUARE("مربع", 1f),
    PORTRAIT("۳:۴", 3f / 4f),
    LANDSCAPE("۴:۳", 4f / 3f),
    FREE("آزاد", null)
}

/**
 * صفحه برش تصویر محصول.
 *
 * تصویر با انگشت جابه‌جا و بزرگ/کوچک می‌شود و کادر ثابت وسط، ناحیه‌ی
 * نهایی را مشخص می‌کند. هنگام تأیید، مختصات کادر روی تصویر **اصلی**
 * محاسبه و برگردانده می‌شود تا برش با بالاترین کیفیت ممکن انجام شود
 * (نه از روی پیش‌نمایش کم‌حجم).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropScreen(
    bitmap: Bitmap?,
    /** ابعاد تصویر اصلی روی دیسک — برای تبدیل مختصات. */
    sourceWidth: Int,
    sourceHeight: Int,
    busy: Boolean,
    /** اگر باز کردن تصویر شکست خورده باشد، متن خطا؛ وگرنه null. */
    error: String? = null,
    onConfirm: (Rect) -> Unit,
    onBack: () -> Unit
) {
    var ratio by remember { mutableStateOf(CropRatio.SQUARE) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    // با تغییر نسبت، تبدیل‌ها از نو شروع شوند
    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("برش تصویر") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { reset() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "بازنشانی")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1B1B1B))
        ) {
            // ---------- خطا ----------
            if (error != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️", fontSize = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "تصویر باز نشد",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error,
                            color = Color(0xFFDDDDDD),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("بازگشت") }
                    }
                }
                return@Column
            }

            // ---------- در حال آماده‌سازی ----------
            if (bitmap == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "در حال باز کردن تصویر…",
                            color = Color(0xFFCCCCCC),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                return@Column
            }

            // ---------- ناحیه برش ----------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .onSizeChanged { boxSize = it }
                    .pointerInput(ratio) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset += pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
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

                // پوشش تیره با پنجره شفاف وسط
                CropOverlay(ratio.value)
            }

            // ---------- کنترل‌ها ----------
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp)
            ) {
                Text(
                    "با دو انگشت بزرگ‌نمایی و با کشیدن جابه‌جا کنید",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CropRatio.entries.forEach { r ->
                        FilterChip(
                            selected = ratio == r,
                            onClick = { ratio = r; reset() },
                            label = { Text(r.label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !busy
                    ) { Text("انصراف") }

                    Button(
                        onClick = {
                            val rect = computeSourceRect(
                                bitmapW = bitmap.width,
                                bitmapH = bitmap.height,
                                sourceW = sourceWidth,
                                sourceH = sourceHeight,
                                boxSize = boxSize,
                                scale = scale,
                                offset = offset,
                                ratio = ratio.value
                            )
                            onConfirm(rect)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !busy && boxSize.width > 0
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else Text("تأیید برش")
                    }
                }
            }
        }
    }
}

/** پوشش تیره بیرون کادر برش. */
@Composable
private fun CropOverlay(aspect: Float?) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val margin = 0.06f * minOf(w, h)

        // اندازه کادر بر اساس نسبت انتخابی
        var fw = w - margin * 2
        var fh = h - margin * 2
        if (aspect != null) {
            if (fw / fh > aspect) fw = fh * aspect else fh = fw / aspect
        }
        val left = (w - fw) / 2
        val top = (h - fh) / 2

        // چهار مستطیل تیره اطراف پنجره
        val shade = Color(0x99000000)
        drawRect(shade, topLeft = Offset(0f, 0f), size = Size(w, top))
        drawRect(shade, topLeft = Offset(0f, top + fh), size = Size(w, h - top - fh))
        drawRect(shade, topLeft = Offset(0f, top), size = Size(left, fh))
        drawRect(shade, topLeft = Offset(left + fw, top), size = Size(w - left - fw, fh))

        // کادر سفید
        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(fw, fh),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )

        // خطوط راهنمای یک‌سوم
        val guide = Color(0x55FFFFFF)
        for (i in 1..2) {
            drawLine(
                guide,
                Offset(left + fw * i / 3f, top),
                Offset(left + fw * i / 3f, top + fh),
                strokeWidth = 1.5f
            )
            drawLine(
                guide,
                Offset(left, top + fh * i / 3f),
                Offset(left + fw, top + fh * i / 3f),
                strokeWidth = 1.5f
            )
        }
    }
}

/**
 * تبدیل کادر روی صفحه به مختصات تصویر **اصلی**.
 *
 * چون پیش‌نمایش کوچک‌شده است، نسبت بین پیش‌نمایش و فایل اصلی اعمال
 * می‌شود تا برش از روی تصویر تمام‌کیفیت انجام شود.
 */
private fun computeSourceRect(
    bitmapW: Int,
    bitmapH: Int,
    sourceW: Int,
    sourceH: Int,
    boxSize: IntSize,
    scale: Float,
    offset: Offset,
    ratio: Float?
): Rect {
    if (boxSize.width == 0 || boxSize.height == 0) {
        return Rect(0, 0, sourceW, sourceH)
    }

    val bw = boxSize.width.toFloat()
    val bh = boxSize.height.toFloat()

    // اندازه‌ای که تصویر با ContentScale.Fit روی صفحه اشغال می‌کند
    val fitScale = minOf(bw / bitmapW, bh / bitmapH)
    val drawnW = bitmapW * fitScale * scale
    val drawnH = bitmapH * fitScale * scale
    val imgLeft = (bw - drawnW) / 2f + offset.x
    val imgTop = (bh - drawnH) / 2f + offset.y

    // کادر برش روی صفحه (همان محاسبه CropOverlay)
    val margin = 0.06f * minOf(bw, bh)
    var fw = bw - margin * 2
    var fh = bh - margin * 2
    if (ratio != null) {
        if (fw / fh > ratio) fw = fh * ratio else fh = fw / ratio
    }
    val fLeft = (bw - fw) / 2f
    val fTop = (bh - fh) / 2f

    // نگاشت به مختصات بیت‌مپ پیش‌نمایش
    val px = ((fLeft - imgLeft) / (fitScale * scale))
    val py = ((fTop - imgTop) / (fitScale * scale))
    val pw = fw / (fitScale * scale)
    val ph = fh / (fitScale * scale)

    // نگاشت از پیش‌نمایش به تصویر اصلی
    val k = sourceW.toFloat() / bitmapW

    var l = (px * k).roundToInt()
    var t = (py * k).roundToInt()
    var r = ((px + pw) * k).roundToInt()
    var b = ((py + ph) * k).roundToInt()

    // محدود کردن به مرزهای تصویر
    l = l.coerceIn(0, sourceW - 1)
    t = t.coerceIn(0, sourceH - 1)
    r = r.coerceIn(l + 1, sourceW)
    b = b.coerceIn(t + 1, sourceH)

    return Rect(l, t, r, b)
}
