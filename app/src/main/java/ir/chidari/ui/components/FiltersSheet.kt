package ir.chidari.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.data.repo.AdvancedFilters
import ir.chidari.data.repo.SortMode
import ir.chidari.ui.vm.FilterState
import ir.chidari.util.Fa

/** گزینه‌های آماده‌ی فاصله بر حسب کیلومتر؛ ۰ یعنی بدون محدودیت. */
private val DISTANCE_PRESETS = listOf(1f, 3f, 5f, 10f, 25f, 0f)

/** میان‌برهای محدوده‌ی قیمت: برچسب، کف، سقف. */
private val PRICE_PRESETS = listOf(
    Triple("زیر ۱۰۰ هزار", 0L, 100_000L),
    Triple("۱۰۰ تا ۵۰۰ هزار", 100_000L, 500_000L),
    Triple("بالای ۱ میلیون", 1_000_000L, 0L)
)

/** بیشترین قیمتی که نوار لغزنده پوشش می‌دهد (تومان). */
private const val PRICE_SLIDER_MAX = 5_000_000f

/**
 * برگه پایینی فیلترها.
 *
 * ساختار: سربرگ چسبان، بدنه‌ی پیمایش‌شونده، نوار پایین چسبان. روی صفحه‌های
 * کوچک فهرست فیلترها بلندتر از صفحه می‌شود؛ بدون پیمایش، دکمه‌های پایین
 * دست‌نیافتنی می‌شدند.
 *
 * @param productMode در تب محصولات، قیمت و تخفیف و عکس معنا دارند؛ در تب
 *   فروشگاه‌ها نه. همان پنجره با بخش‌های متفاوت نشان داده می‌شود.
 * @param resultCount شمارش زنده‌ی نتیجه — روی دکمه نوشته می‌شود.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersSheet(
    filters: FilterState,
    locationLabel: String,
    hasCoordinates: Boolean,
    productMode: Boolean,
    categories: List<String>,
    resultCount: Int,
    onSortChange: (SortMode) -> Unit,
    onCategoryChange: (String) -> Unit,
    onMaxDistanceChange: (Float) -> Unit,
    onOnlyAvailableChange: (Boolean) -> Unit,
    onIgnoreCityChange: (Boolean) -> Unit,
    onPriceRangeChange: (Long, Long) -> Unit,
    onOnlyDiscountedChange: (Boolean) -> Unit,
    onOnlyWithImageChange: (Boolean) -> Unit,
    onMinRatingChange: (Float) -> Unit,
    onOnlyWithProductsChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {

            // ---------------- سربرگ ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "فیلترها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (filters.activeCount > 0) {
                        Spacer(Modifier.height(0.dp))
                        Surface(
                            modifier = Modifier.padding(start = 8.dp),
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "${Fa.number(filters.activeCount.toLong())} فعال",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = onReset, enabled = filters.activeCount > 0) {
                    Text("پاک کردن همه", style = MaterialTheme.typography.labelLarge)
                }
            }
            HorizontalDivider()

            // ---------------- بدنه ----------------
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 14.dp)
            ) {
                SectionTitle("↕️", "مرتب‌سازی")
                ChipRow(
                    items = SortMode.entries.map { it.label },
                    selected = filters.sort.label,
                    onSelect = { label ->
                        SortMode.entries.firstOrNull { it.label == label }?.let(onSortChange)
                    },
                    allLabel = "پیش‌فرض",
                    contentPadding = PaddingValues(0.dp)
                )

                if (productMode) {
                    Spacer(Modifier.height(18.dp))
                    PriceSection(
                        minPrice = filters.minPrice,
                        maxPrice = filters.maxPrice,
                        onChange = onPriceRangeChange
                    )
                }

                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    SectionTitle("🏷️", if (productMode) "دسته محصول" else "نوع فروشگاه")
                    ChipRow(
                        items = categories,
                        selected = if (productMode) filters.productCategory else filters.storeCategory,
                        onSelect = onCategoryChange,
                        allLabel = "همه",
                        contentPadding = PaddingValues(0.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))
                DistanceSection(
                    current = filters.maxDistanceKm,
                    enabled = hasCoordinates,
                    onChange = onMaxDistanceChange
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                if (productMode) {
                    ToggleRow(
                        "✅", "فقط موارد موجود", "کالاهای ناموجود نمایش داده نشوند",
                        filters.onlyAvailable, onOnlyAvailableChange
                    )
                    ToggleRow(
                        "🏷️", "فقط تخفیف‌دار", null,
                        filters.onlyDiscounted, onOnlyDiscountedChange
                    )
                    ToggleRow(
                        "🖼️", "فقط دارای عکس", null,
                        filters.onlyWithImage, onOnlyWithImageChange
                    )
                } else {
                    ToggleRow(
                        "📦", "فقط فروشگاه‌های دارای محصول", null,
                        filters.onlyWithProducts, onOnlyWithProductsChange
                    )
                }

                ToggleRow(
                    "⭐",
                    if (productMode) "فروشگاه با امتیاز ۴ به بالا" else "امتیاز ۴ به بالا",
                    null,
                    filters.minRating > 0f,
                    { on -> onMinRatingChange(if (on) AdvancedFilters.GOOD_RATING else 0f) }
                )

                ToggleRow(
                    "🇮🇷", "جست‌وجو در سراسر ایران",
                    "نادیده گرفتن محدودیت «$locationLabel»",
                    filters.ignoreCityFilter, onIgnoreCityChange
                )

                Spacer(Modifier.height(12.dp))
            }

            // ---------------- نوار پایین ----------------
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReset,
                    enabled = filters.activeCount > 0,
                    modifier = Modifier.weight(0.85f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("پاک کردن") }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1.15f),
                    shape = RoundedCornerShape(12.dp),
                    colors = if (resultCount == 0) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) else ButtonDefaults.buttonColors()
                ) {
                    Text(
                        if (resultCount == 0) "نتیجه‌ای نیست — فیلترها را کم کنید"
                        else "نمایش ${Fa.number(resultCount.toLong())} نتیجه",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ---------------------------------------------------------------- بخش قیمت

/**
 * محدوده‌ی قیمت با **هر دو** روش: نوار دوسره برای تنظیم سریع و ورودی عددی
 * برای مبلغ دقیق. هر کدام تغییر کند، دیگری هم به‌روز می‌شود.
 */
@Composable
private fun PriceSection(minPrice: Long, maxPrice: Long, onChange: (Long, Long) -> Unit) {
    SectionTitle("💰", "محدوده قیمت (تومان)")

    // متن ورودی‌ها جدا نگه داشته می‌شود تا هنگام تایپ، عدد ناقص پاک نشود
    var minText by remember(minPrice) { mutableStateOf(if (minPrice > 0) minPrice.toString() else "") }
    var maxText by remember(maxPrice) { mutableStateOf(if (maxPrice > 0) maxPrice.toString() else "") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = minText,
            onValueChange = {
                minText = it.filter(Char::isDigit)
                onChange(minText.toLongOrNull() ?: 0L, maxText.toLongOrNull() ?: 0L)
            },
            label = { Text("از", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        )
        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = maxText,
            onValueChange = {
                maxText = it.filter(Char::isDigit)
                onChange(minText.toLongOrNull() ?: 0L, maxText.toLongOrNull() ?: 0L)
            },
            label = { Text("تا", style = MaterialTheme.typography.labelSmall) },
            placeholder = { Text("بدون سقف", style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        )
    }

    // نوار دوسره؛ سقف باز (۰) یعنی انتهای نوار
    val low = minPrice.toFloat().coerceIn(0f, PRICE_SLIDER_MAX)
    val high = (if (maxPrice > 0L) maxPrice.toFloat() else PRICE_SLIDER_MAX)
        .coerceIn(low, PRICE_SLIDER_MAX)
    RangeSlider(
        value = low..high,
        onValueChange = { r ->
            val lo = r.start.toLong() / 10_000 * 10_000        // گرد کردن به ۱۰ هزار
            val hi = if (r.endInclusive >= PRICE_SLIDER_MAX) 0L
            else r.endInclusive.toLong() / 10_000 * 10_000
            onChange(lo, hi)
        },
        valueRange = 0f..PRICE_SLIDER_MAX
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PRICE_PRESETS.forEach { (label, lo, hi) ->
            FilterChip(
                selected = minPrice == lo && maxPrice == hi,
                onClick = {
                    if (minPrice == lo && maxPrice == hi) onChange(0L, 0L) else onChange(lo, hi)
                },
                label = { Text(label, fontSize = 10.5.sp, maxLines = 1) },
                shape = RoundedCornerShape(9.dp)
            )
        }
    }
}

// ------------------------------------------------------------- بخش فاصله

@Composable
private fun DistanceSection(current: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SectionTitle("📍", "حداکثر فاصله", bottomSpace = false)
        Text(
            if (current <= 0f) "بدون محدودیت"
            else "تا ${Fa.number(current.toLong())} کیلومتر",
            style = MaterialTheme.typography.labelLarge,
            color = if (current > 0f) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DISTANCE_PRESETS.forEach { km ->
            FilterChip(
                selected = current == km,
                enabled = enabled,
                onClick = { onChange(km) },
                label = {
                    Text(
                        if (km <= 0f) "بدون حد" else Fa.number(km.toLong()),
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                },
                shape = RoundedCornerShape(9.dp)
            )
        }
    }
    if (!enabled) {
        Spacer(Modifier.height(6.dp))
        Text(
            "فیلتر فاصله وقتی فعال می‌شود که موقعیت با GPS مشخص شده باشد.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

// -------------------------------------------------------------- اجزای کوچک

@Composable
private fun SectionTitle(emoji: String, title: String, bottomSpace: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 13.sp)
        Spacer(Modifier.padding(horizontal = 3.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
    if (bottomSpace) Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToggleRow(
    emoji: String,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 15.sp)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** نشان شمارنده روی آیکن فیلتر. */
@Composable
fun FilterBadge(count: Int, content: @Composable () -> Unit) {
    Box {
        content()
        if (count > 0) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart),
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.error
            ) {
                Text(
                    Fa.number(count.toLong()),
                    color = MaterialTheme.colorScheme.onError,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}
