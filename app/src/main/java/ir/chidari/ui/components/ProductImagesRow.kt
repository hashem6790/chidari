package ir.chidari.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ir.chidari.data.local.ProductImages
import ir.chidari.ui.vm.DraftImage
import ir.chidari.ui.vm.UploadState
import ir.chidari.util.Fa

/**
 * بخش تصاویر محصول در فرم — رفتار آشنای دیوار.
 *
 * هر بندانگشتی وضعیت بارگذاری خودش را نشان می‌دهد: نوار پیشرفت هنگام ارسال،
 * تیک سبز پس از موفقیت، و پوشش قرمز با «تلاش دوباره» در صورت شکست. تصویر
 * اول نشان «عکس اصلی» می‌گیرد چون همان است که روی کارت محصول دیده می‌شود.
 */
@Composable
fun ProductImagesSection(
    images: List<DraftImage>,
    onAdd: () -> Unit,
    onOpen: (DraftImage) -> Unit,
    onRemove: (DraftImage) -> Unit,
    onRetry: (DraftImage) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "تصاویر محصول",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${Fa.number(images.size.toLong())} از ${Fa.number(ProductImages.MAX.toLong())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            if (images.isEmpty()) {
                // حالت خالی: یک هدف بزرگ و واضح برای اولین عکس
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .clickable(onClick = onAdd),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📷", fontSize = 38.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "افزودن تصویر محصول",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "گالری یا دوربین • برش و بهینه‌سازی خودکار",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images, key = { it.id }) { img ->
                        Thumbnail(
                            image = img,
                            isCover = images.firstOrNull()?.id == img.id,
                            onClick = {
                                if (img.state == UploadState.FAILED) onRetry(img) else onOpen(img)
                            },
                            onRemove = { onRemove(img) }
                        )
                    }
                    if (images.size < ProductImages.MAX) {
                        item {
                            Column(
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                    .clickable(onClick = onAdd),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("＋", fontSize = 26.sp)
                                Text(
                                    "افزودن",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "برای دیدن، برش دوباره یا حذف، روی تصویر بزنید. اولین تصویر روی کارت محصول نشان داده می‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** یک بندانگشتی با پوشش وضعیت. */
@Composable
private fun Thumbnail(
    image: DraftImage,
    isCover: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        Modifier
            .size(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = image.displayModel,
            contentDescription = "تصویر محصول",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        when (image.state) {
            UploadState.UPLOADING -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x8C000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            Modifier.size(26.dp),
                            strokeWidth = 2.5.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            Fa.digits("${(image.progress * 100).toInt()}٪"),
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { image.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0x55FFFFFF)
                )
            }

            UploadState.FAILED -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x8CB71C1C)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️", fontSize = 17.sp)
                    Text(
                        "تلاش دوباره",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            UploadState.DONE -> Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xE61B5E20))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) { Text("✓", color = Color.White, fontSize = 10.sp) }

            UploadState.LOCAL_ONLY -> Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xE6455A64))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) { Text("روی گوشی", color = Color.White, fontSize = 8.5.sp) }
        }

        if (isCover) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
            ) {
                Text(
                    "عکس اصلی",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
        }

        // دکمه حذف سریع
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xA6000000))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) { Text("✕", color = Color.White, fontSize = 12.sp) }
    }
}
