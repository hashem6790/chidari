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
import androidx.compose.runtime.LaunchedEffect
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
import ir.chidari.ui.crop.CropShape
import ir.chidari.ui.crop.FrameRect
import ir.chidari.ui.crop.Luma

/** چه چیزی زیر انگشت در حال حرکت است. */
private enum class DragMode { RESIZE, MOVE_FRAME }

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
    /** مستطیل برش و اینکه خروجی باید دایره‌ای باشد یا نه. */
    onConfirm: (Rect, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var shape by remember { mutableStateOf(CropShape.SQUARE) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf<FrameRect?>(null) }

    /*
     * محدوده‌ی واقعی تصویر روی صفحه. تصویر با Fit کشیده می‌شود و اطرافش نوار
     * خالی می‌ماند؛ کادر برش نباید وارد آن نوار شود وگرنه ناحیه‌ی خالی هم
     * بریده می‌شود. تصویر ثابت است و فقط کادر حرکت می‌کند — چون برش همیشه
     * از فایل تمام‌کیفیت خوانده می‌شود، بزرگ‌نمایی تصویر هیچ سودی نداشت.
     */
    val imageBounds = remember(boxSize, bitmap) {
        if (bitmap == null || boxSize.width == 0) null
        else CropGeometry.imageBounds(
            boxSize.width.toFloat(), boxSize.height.toFloat(), bitmap.width, bitmap.height
        )
    }

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
        imageBounds?.let { frame = CropGeometry.initialFrame(it, shape.ratio, marginPx) }
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
                    .onSizeChanged { boxSize = it }
                    /*
                     * یک شناساگر دست‌نویس به‌جای مدیفایرهای آماده.
                     *
                     * چرا: اگر تشخیص کشیدن و تشخیص تبدیل را روی دو مدیفایر
                     * جدا بگذاریم، اولی همه‌ی لمس‌ها را می‌بلعد و حرکت دو
                     * انگشتی هرگز اجرا نمی‌شود.
                     *
                     * قرارداد لمس:
                     *   دستگیره + یک انگشت → فقط همان ضلع/گوشه
                     *   یک انگشت (هرجای دیگر) → جابه‌جایی کل کادر
                     *   دو انگشت → بزرگ/کوچک کردن کادر حول مرکز
                     */
                    .pointerInput(boxSize, shape, imageBounds) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val bounds = imageBounds ?: return@awaitEachGesture
                            val f0 = frame ?: return@awaitEachGesture

                            val handle = CropGeometry.hitTest(
                                down.position.x, down.position.y, f0, touchRadius
                            )
                            val mode = if (handle != null) DragMode.RESIZE else DragMode.MOVE_FRAME
                            var pinching = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val active = event.changes.filter { it.pressed }
                                if (active.isEmpty()) break

                                val cur = frame ?: break

                                if (active.size >= 2) {
                                    // دو انگشت: کادر بزرگ و کوچک می‌شود
                                    pinching = true
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1f) {
                                        frame = CropGeometry.scaleAround(cur, zoom, bounds, minFrame)
                                        event.changes.forEach { it.consume() }
                                    }
                                } else if (!pinching) {
                                    val change: PointerInputChange = active.first()
                                    val d = change.positionChange()
                                    if (d != Offset.Zero) {
                                        frame = when (mode) {
                                            DragMode.RESIZE -> CropGeometry.resize(
                                                cur, handle!!, d.x, d.y, bounds,
                                                minFrame, shape.forceSquare
                                            )
                                            DragMode.MOVE_FRAME ->
                                                CropGeometry.move(cur, d.x, d.y, bounds)
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
                    modifier = Modifier.fillMaxSize()
                )

                // کادر اولیه به محض معلوم شدن محدوده‌ی تصویر ساخته می‌شود
                LaunchedEffect(imageBounds, shape) {
                    imageBounds?.let { frame = CropGeometry.initialFrame(it, shape.ratio, marginPx) }
                }

                frame?.let {
                    CropOverlay(
                        frame = it,
                        circular = shape == CropShape.CIRCLE,
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
                    "دستگیره = فقط همان ضلع • یک انگشت = جابه‌جایی کادر • دو انگشت = بزرگ و کوچک کردن کادر",
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
                    CropShape.entries.forEach { sh ->
                        FilterChip(
                            selected = shape == sh,
                            // تغییر شکل، کادر را از نو می‌سازد (LaunchedEffect بالا)
                            onClick = { shape = sh },
                            label = {
                                Text(sh.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            },
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
                            val b = imageBounds ?: return@Button
                            val r = CropGeometry.toSourceRect(f, b, sourceWidth, sourceHeight)
                            onConfirm(Rect(r[0], r[1], r[2], r[3]), shape == CropShape.CIRCLE)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !busy && frame != null && imageBounds != null
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
    circular: Boolean,
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
        val shade = Color(0x99000000)

        if (circular) {
            /*
             * برای دایره نمی‌شود با چهار مستطیل پرده کشید. کل صفحه تیره
             * می‌شود و بعد دایره با BlendMode.Clear پاک می‌شود — به یک لایه‌ی
             * جدا نیاز دارد، وگرنه پاک‌کننده روی تصویر زیرین هم اثر می‌گذارد.
             */
            drawContext.canvas.saveLayer(
                androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                androidx.compose.ui.graphics.Paint()
            )
            drawRect(shade, topLeft = Offset(0f, 0f), size = Size(w, h))
            drawCircle(
                color = Color.Black,
                radius = minOf(fw, fh) / 2f,
                center = Offset(frame.centerX, frame.centerY),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
            )
            drawContext.canvas.restore()
        } else {
            // چهار مستطیل تیره اطراف پنجره
            drawRect(shade, topLeft = Offset(0f, 0f), size = Size(w, t))
            drawRect(shade, topLeft = Offset(0f, t + fh), size = Size(w, (h - t - fh).coerceAtLeast(0f)))
            drawRect(shade, topLeft = Offset(0f, t), size = Size(l, fh))
            drawRect(shade, topLeft = Offset(l + fw, t), size = Size((w - l - fw).coerceAtLeast(0f), fh))
        }

        // ---- قاب: هاله‌ی ضدرنگ زیر خط اصلی ----
        if (circular) {
            val r = minOf(fw, fh) / 2f
            val c = Offset(frame.centerX, frame.centerY)
            drawCircle(haloColor, radius = r, center = c, style = Stroke(handleThicknessPx * 0.9f))
            drawCircle(frameColor, radius = r, center = c, style = Stroke(handleThicknessPx * 0.38f))
        } else {
            drawRect(haloColor, Offset(l, t), Size(fw, fh), style = Stroke(handleThicknessPx * 0.9f))
            drawRect(frameColor, Offset(l, t), Size(fw, fh), style = Stroke(handleThicknessPx * 0.38f))
        }

        // ---- خطوط راهنمای یک‌سوم (فقط برای کادر مستطیلی) ----
        if (!circular) {
            val guide = frameColor.copy(alpha = 0.34f)
            for (i in 1..2) {
                drawLine(
                    guide, Offset(l + fw * i / 3f, t), Offset(l + fw * i / 3f, t + fh),
                    strokeWidth = handleThicknessPx * 0.28f
                )
                drawLine(
                    guide, Offset(l, t + fh * i / 3f), Offset(l + fw, t + fh * i / 3f),
                    strokeWidth = handleThicknessPx * 0.28f
                )
            }
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
        if (!circular) {
            corner(l, t, 1f, 1f)
            corner(l + fw, t, -1f, 1f)
            corner(l, t + fh, 1f, -1f)
            corner(l + fw, t + fh, -1f, -1f)
        }

        // ---- وسط هر ضلع: روی دایره هم همان‌جاست تا لمس یکسان بماند ----
        val midX = frame.centerX
        val midY = frame.centerY
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
