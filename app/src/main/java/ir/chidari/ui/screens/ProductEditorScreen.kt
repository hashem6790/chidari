package ir.chidari.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.data.Categories
import ir.chidari.data.Units
import ir.chidari.util.Fa
import ir.chidari.data.local.ProductEntity

private val productEmojis = listOf(
    "📦", "🥛", "🍞", "🍎", "🥩", "🍗", "🐟", "🍚", "🛢️", "🥚", "🥤", "☕",
    "🍕", "🍔", "🍢", "🍲", "🧴", "💊", "👕", "👟", "🧥", "📱", "💻", "🎧",
    "🔋", "📺", "🧺", "🔧", "🚗", "🛞", "🎨", "💇", "🌸", "⚽", "🧸", "📚", "🛠️", "🏠"
)

/** فرم افزودن/ویرایش محصول یا خدمت. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditorScreen(
    existing: ProductEntity?,
    storeId: Long,
    storeCategory: String,
    /** بارکدی که از صفحه اسکن برگشته (خالی یعنی اسکنی انجام نشده). */
    scannedBarcode: String = "",
    onScanBarcode: () -> Unit,
    /** مسیر تصویری که از صفحه برش برگشته (خالی = تغییری نکرده). */
    processedImagePath: String = "",
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSave: (ProductEntity) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    val suggestedCats = remember(storeCategory) {
        (Categories.productCategoriesOf(storeCategory) + Categories.allProductCategories).distinct()
    }
    var category by remember { mutableStateOf(existing?.category ?: suggestedCats.first()) }
    var priceText by remember { mutableStateOf(existing?.price?.toString() ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: Units.all.first()) }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var specs by remember { mutableStateOf(existing?.specs ?: "") }
    var available by remember { mutableStateOf(existing?.available ?: true) }
    var discountText by remember { mutableStateOf(existing?.discountPercent?.takeIf { it > 0 }?.toString() ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "📦") }
    var barcode by remember { mutableStateOf(existing?.barcode ?: "") }
    var imagePath by remember { mutableStateOf(existing?.imageUri ?: "") }

    // تصویر تازه‌ی آماده‌شده از صفحه برش
    LaunchedEffect(processedImagePath) {
        if (processedImagePath.isNotBlank()) imagePath = processedImagePath
    }

    // وقتی از صفحه اسکن برمی‌گردیم، کد را در فرم می‌گذاریم
    LaunchedEffect(scannedBarcode) {
        if (scannedBarcode.isNotBlank()) barcode = scannedBarcode
    }
    var attempted by remember { mutableStateOf(false) }

    val price = priceText.toLongOrNull() ?: 0L
    val discount = discountText.toIntOrNull()?.coerceIn(0, 90) ?: 0
    val titleError = attempted && title.isBlank()
    val priceError = attempted && price <= 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "افزودن محصول / خدمت" else "ویرایش محصول") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان محصول یا خدمت *") },
                    placeholder = { Text("مثلاً شیر پرچرب ۱ لیتری") },
                    isError = titleError,
                    supportingText = if (titleError) { { Text("عنوان الزامی است") } }
                    else { { Text("برای مقایسه بهتر، از نام‌های استاندارد و رایج استفاده کنید") } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                // ---------- تصویر محصول ----------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    onClick = onPickImage
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (imagePath.isNotBlank()) {
                            AsyncImage(
                                model = java.io.File(imagePath),
                                contentDescription = "تصویر محصول",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onPickImage,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("تغییر تصویر") }
                                OutlinedButton(
                                    onClick = {
                                        onRemoveImage(imagePath)
                                        imagePath = ""
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("حذف") }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("📷", fontSize = 40.sp)
                                Spacer(Modifier.height(8.dp))
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
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔎", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "بارکد کالا",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (barcode.isBlank()) "برای ثبت سریع‌تر، بارکد را اسکن کنید"
                                    else Fa.digits(barcode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (barcode.isNotBlank()) {
                                TextButton(onClick = { barcode = "" }) { Text("پاک") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onScanBarcode,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (barcode.isBlank()) "📷 اسکن بارکد" else "📷 اسکن دوباره")
                        }
                    }
                }
            }

            item {
                DropdownField(
                    label = "دسته‌بندی *",
                    value = category,
                    options = suggestedCats,
                    onSelect = { category = it }
                )
            }

            item {
                Column {
                    Text("آیکون", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(productEmojis) { e ->
                            Card(
                                onClick = { emoji = e },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (emoji == e) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(e, fontSize = 22.sp, modifier = Modifier.padding(9.dp))
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = Fa.toLatinDigits(it).filter { c -> c.isDigit() }.take(12) },
                        label = { Text("قیمت (تومان) *") },
                        isError = priceError,
                        supportingText = {
                            if (priceError) Text("قیمت را وارد کنید")
                            else if (price > 0) Text(Fa.price(price))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        DropdownField(
                            label = "واحد",
                            value = unit,
                            options = Units.all,
                            onSelect = { unit = it }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = Fa.toLatinDigits(it).filter { c -> c.isDigit() }.take(2) },
                    label = { Text("درصد تخفیف (اختیاری)") },
                    supportingText = {
                        if (discount > 0 && price > 0)
                            Text("قیمت نهایی: ${Fa.price(price * (100 - discount) / 100)}")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("توضیحات") },
                    placeholder = { Text("ویژگی‌ها، شرایط فروش، گارانتی و…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = specs,
                    onValueChange = { specs = it },
                    label = { Text("مشخصات فنی") },
                    placeholder = { Text("هر خط یک مشخصه:\nوزن: ۱ کیلوگرم\nجنس: نخ پنبه") },
                    supportingText = { Text("هر خط به شکل «عنوان: مقدار» بنویسید تا جدول‌بندی شود") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("موجود است", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "اگر خاموش باشد، به کاربران «ناموجود» نمایش داده می‌شود",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = available, onCheckedChange = { available = it })
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        attempted = true
                        if (title.isBlank() || price <= 0) return@Button
                        onSave(
                            (existing ?: ProductEntity(
                                storeId = storeId, title = "", category = "", price = 0
                            )).copy(
                                storeId = storeId,
                                title = title.trim(),
                                category = category,
                                price = price,
                                unit = unit,
                                description = description.trim(),
                                specs = specs.trim(),
                                available = available,
                                discountPercent = discount,
                                emoji = emoji,
                                barcode = barcode.trim(),
                                imageUri = imagePath,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (existing == null) "افزودن به فروشگاه" else "ذخیره تغییرات",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
