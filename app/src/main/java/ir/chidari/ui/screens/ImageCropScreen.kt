package ir.chidari.ui.screens

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.ui.crop.CropGeometry
import ir.chidari.ui.crop.CropHandle
import ir.chidari.ui.crop.FrameRect
import ir.chidari.ui.crop.Luma

/** نسبت‌های برش که کاربر می‌تواند انتخاب کند. */
private enum class CropRatio(val label: String, val value: Float?) {
    SQUARE("مربع", 1f),
    PORTRAIT("۳:۴", 3f / 4f),
    LANDSCAPE("۴:۳", 4f / 3f),
    FREE("آزاد", null)
}

/** چه چیزی زیر انگشت در حال حرکت است. */
private enum class DragMode { RESIZE, MOVE_FRAME, PAN_IMAGE }

/**
 * صفحه برش تصویر محصول.
 *
 * کادر برش **خودش** قابل تغییر اندازه است: هشت دستگیره (چهار گوشه و وسط
 * چهار ضلع). کشیدن داخل کادر آن را جابه‌جا می‌کند، کشیدن بیرون کادر تصویر
 * را، و دو انگشت بزرگ‌نمایی می‌کند.
 *
 * رنگ کادر بر اساس روشنایی خود تصویر انتخاب می‌شود تا روی عکس‌های سفید
 * (که کادر سفید در آن‌ها گم می‌شد) هم دیده شود.
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
    var frame by remember { mutableStateOf<FrameRect?>(null) }

    val density = LocalDensity.current
    val touchRadius = with(density) { 26.dp.toPx() }
    val minFrame = with(density) { 64.dp.toPx() }
    val marginPx = with(density) { 22.dp.toPx() }

    /**
     * روشنایی متوسط تصویر — یک بار برای هر بیت‌مپ محاسبه می‌شود.
     * نمونه‌برداری روی یک نسخه‌ی ۲۴×۲۴ انجام می‌شود تا هزینه‌اش ناچیز بماند.
     */
    val bright = remember(bitmap) {
        bitmap?.let { Luma.isBright(averageLuminanceOf(it)) } ?: false
    }
    val frameColor = if (bright) Color(0xFF1B1B1B) else Color.White
    // هاله‌ی ضدرنگ: تضمین می‌کند کادر روی عکس‌های نصفه‌تیره/نصفه‌روشن هم دیده شود
    val haloColor = if (bright) Color(0x73FFFFFF) else Color(0x73000000)

    fun resetFrame() {
        scale = 1f
        offset = Offset.Zero
        if (boxSize.width > 0) {
            frame = CropGeometry.initialFrame(
                boxSize.width.toFloat(), boxSize.height.toFloat(), ratio.value, marginPx
            )
        }
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
                    IconButton(onClick = { resetFrame() }) {
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
                        Button(onClick = onBack, shape = RoundedCornerShape(12.dp)) {
                            Text("بازگشت")
                        }
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
                    .onSizeChanged { size ->
                        boxSize = size
                        if (frame == null && size.width > 0) {
                            frame = CropGeometry.initialFrame(
                                size.width.toFloat(), size.height.toFloat(),
                                ratio.value, marginPx
                            )
                        }
                    }
                    /*
                     * یک شناساگر دست‌نویس به‌جای چند pointerInput جداگانه.
                     *
                     * چرا: اگر تشخیص کشیدن (کادر) و تشخیص تبدیل (بزرگ‌نمایی) را
                     * روی دو مدیفایر بگذاریم، اولی همه‌ی لمس‌ها را می‌بلعد و
                     * بزرگ‌نمایی از کار می‌افتد. اینجا خودمان تصمیم می‌گیریم:
                     * دو انگشت = تصویر، یک انگشت = بسته به محل شروع لمس.
                     */
                    .pointerInput(boxSize, ratio) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val f0 = frame ?: return@awaitEachGesture

                            var handle = CropGeometry.hitTest(
                                down.position.x, down.position.y, f0, touchRadius
                            )
                            var mode = when {
                                handle != null -> DragMode.RESIZE
                                f0.contains(down.position.x, down.position.y) -> DragMode.MOVE_FRAME
                                else -> DragMode.PAN_IMAGE
                            }
                            var multiTouch = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val active = event.changes.filter { it.pressed }
                                if (active.isEmpty()) break

                                if (active.size >= 2) {
                                    // دو انگشت: همیشه بزرگ‌نمایی/جابه‌جایی تصویر
                                    multiTouch = true
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    if (zoom != 1f || pan != Offset.Zero) {
                                        scale = (scale * zoom).coerceIn(1f, 6f)
                                        offset += pan
                                        event.changes.forEach { it.consume() }
                                    }
                                } else if (!multiTouch) {
                                    val change: PointerInputChange = active.first()
                                    val d = change.positionChange()
                                    if (d != Offset.Zero) {
                                        val bw = boxSize.width.toFloat()
                                        val bh = boxSize.height.toFloat()
                                        val cur = frame
                                        if (cur != null) {
                                            frame = when (mode) {
                                                DragMode.RESIZE -> CropGeometry.resize(
                                                    cur, handle!!, d.x, d.y, bw, bh,
                                                    ratio.value, minFrame
                                                )
                                                DragMode.MOVE_FRAME ->
                                                    CropGeometry.move(cur, d.x, d.y, bw, bh)
                                                DragMode.PAN_IMAGE -> {
                                                    offset += d
                                                    cur
                                                }
                                            }
                                        }
                                        change.consume()
                                    }
                                }
                            }
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

                frame?.let {
                    CropOverlay(
                        frame = it,
                        frameColor = frameColor,
                        haloColor = haloColor,
                        handleLengthPx = with(density) { 26.dp.toPx() },
                        handleThicknessPx = with(density) { 4.dp.toPx() }
                    )
                }
            }

            // ---------- کنترل‌ها ----------
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp)
            ) {
                Text(
                    "دستگیره‌ها را بکشید تا کادر تغییر کند • داخل کادر = جابه‌جایی • دو انگشت = بزرگ‌نمایی",
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
                            onClick = {
                                ratio = r
                                // کادر با نسبت تازه از نو ساخته می‌شود؛ تصویر دست نمی‌خورد
                                if (boxSize.width > 0) {
                                    frame = CropGeometry.initialFrame(
                                        boxSize.width.toFloat(), boxSize.height.toFloat(),
                                        r.value, marginPx
                                    )
                                }
                            },
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
                            val f = frame ?: return@Button
                            val r = CropGeometry.toSourceRect(
                                frame = f,
                                bitmapW = bitmap.width,
                                bitmapH = bitmap.height,
                                sourceW = sourceWidth,
                                sourceH = sourceHeight,
                                boxW = boxSize.width.toFloat(),
                                boxH = boxSize.height.toFloat(),
                                scale = scale,
                                offsetX = offset.x,
                                offsetY = offset.y
                            )
                            onConfirm(Rect(r[0], r[1], r[2], r[3]))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !busy && boxSize.width > 0 && frame != null
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

/** پوشش تیره بیرون کادر، به‌همراه قاب و دستگیره‌ها. */
@Composable
private fun CropOverlay(
    frame: FrameRect,
    frameColor: Color,
    haloColor: Color,
    handleLengthPx: Float,
    handleThicknessPx: Float
) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val l = frame.left
        val t = frame.top
        val fw = frame.width
        val fh = frame.height

        // چهار مستطیل تیره اطراف پنجره
        val shade = Color(0x99000000)
        drawRect(shade, topLeft = Offset(0f, 0f), size = Size(w, t))
        drawRect(shade, topLeft = Offset(0f, t + fh), size = Size(w, (h - t - fh).coerceAtLeast(0f)))
        drawRect(shade, topLeft = Offset(0f, t), size = Size(l, fh))
        drawRect(
            shade,
            topLeft = Offset(l + fw, t),
            size = Size((w - l - fw).coerceAtLeast(0f), fh)
        )

        // هاله‌ی ضدرنگ زیر قاب اصلی
        drawRect(
            color = haloColor,
            topLeft = Offset(l, t),
            size = Size(fw, fh),
            style = Stroke(width = handleThicknessPx * 0.9f)
        )
        drawRect(
            color = frameColor,
            topLeft = Offset(l, t),
            size = Size(fw, fh),
            style = Stroke(width = handleThicknessPx * 0.38f)
        )

        // خطوط راهنمای یک‌سوم
        val guide = frameColor.copy(alpha = 0.34f)
        for (i in 1..2) {
            drawLine(
                guide,
                Offset(l + fw * i / 3f, t),
                Offset(l + fw * i / 3f, t + fh),
                strokeWidth = handleThicknessPx * 0.28f
            )
            drawLine(
                guide,
                Offset(l, t + fh * i / 3f),
                Offset(l + fw, t + fh * i / 3f),
                strokeWidth = handleThicknessPx * 0.28f
            )
        }

        // طول دستگیره هرگز از نصف ضلع بیشتر نشود (کادر خیلی کوچک)
        val armX = minOf(handleLengthPx, fw / 2.4f)
        val armY = minOf(handleLengthPx, fh / 2.4f)
        val th = handleThicknessPx

        // ---- گوشه‌ها: براکت L ----
        fun corner(cx: Float, cy: Float, dirX: Float, dirY: Float) {
            drawHandleLine(cx, cy, cx + armX * dirX, cy, th, haloColor, frameColor)
            drawHandleLine(cx, cy, cx, cy + armY * dirY, th, haloColor, frameColor)
        }
        corner(l, t, 1f, 1f)
        corner(l + fw, t, -1f, 1f)
        corner(l, t + fh, 1f, -1f)
        corner(l + fw, t + fh, -1f, -1f)

        // ---- وسط هر ضلع ----
        val midX = l + fw / 2f
        val midY = t + fh / 2f
        drawHandleLine(midX - armX / 2f, t, midX + armX / 2f, t, th, haloColor, frameColor)
        drawHandleLine(midX - armX / 2f, t + fh, midX + armX / 2f, t + fh, th, haloColor, frameColor)
        drawHandleLine(l, midY - armY / 2f, l, midY + armY / 2f, th, haloColor, frameColor)
        drawHandleLine(l + fw, midY - armY / 2f, l + fw, midY + armY / 2f, th, haloColor, frameColor)
    }
}

/** خط دستگیره با هاله‌ی ضدرنگ زیرش. */
private fun DrawScope.drawHandleLine(
    x1: Float, y1: Float, x2: Float, y2: Float,
    thickness: Float, halo: Color, color: Color
) {
    drawLine(halo, Offset(x1, y1), Offset(x2, y2), strokeWidth = thickness * 1.75f)
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = thickness)
}

/**
 * میانگین روشنایی تصویر با نمونه‌برداری روی یک نسخه‌ی کوچک.
 *
 * خواندن همه‌ی پیکسل‌های یک عکس ۱۲۰۰ پیکسلی یعنی ۱٫۴ میلیون عملیات در هر
 * بار ترکیب‌بندی؛ نسخه‌ی ۲۴×۲۴ همان جواب را با ۵۷۶ عملیات می‌دهد.
 */
private fun averageLuminanceOf(bitmap: Bitmap): Float = runCatching {
    val n = 24
    val small = Bitmap.createScaledBitmap(bitmap, n, n, true)
    val pixels = IntArray(n * n)
    small.getPixels(pixels, 0, n, 0, 0, n, n)
    if (small != bitmap) small.recycle()
    Luma.average(pixels)
}.getOrDefault(0f)
