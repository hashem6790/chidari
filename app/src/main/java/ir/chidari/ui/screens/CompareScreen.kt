package ir.chidari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.data.repo.ProductOffer
import ir.chidari.data.repo.SortMode
import ir.chidari.ui.components.ChipRow
import ir.chidari.ui.components.DiscountBadge
import ir.chidari.ui.components.DistanceBadge
import ir.chidari.ui.components.EmptyState
import ir.chidari.ui.components.RatingBadge
import ir.chidari.ui.components.Tag
import ir.chidari.util.Fa

/**
 * صفحه مقایسه: یک عنوان کالا/خدمت را در همه فروشگاه‌ها کنار هم می‌گذارد
 * و اختلاف قیمت نسبت به ارزان‌ترین گزینه را نشان می‌دهد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    title: String,
    offers: List<ProductOffer>,
    onBack: () -> Unit,
    onOfferClick: (Long) -> Unit,
    onStoreClick: (Long) -> Unit
) {
    var sort by remember { mutableStateOf(SortMode.CHEAPEST) }

    val sorted = remember(offers, sort) {
        when (sort) {
            SortMode.CHEAPEST -> offers.sortedBy { it.product.finalPrice }
            SortMode.NEAREST -> offers.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            SortMode.TOP_RATED -> offers.sortedByDescending { it.product.rating }
            SortMode.NEWEST -> offers.sortedByDescending { it.product.id }
        }
    }

    val minPrice = offers.minOfOrNull { it.product.finalPrice }
    val maxPrice = offers.maxOfOrNull { it.product.finalPrice }
    val avgPrice = if (offers.isNotEmpty()) offers.sumOf { it.product.finalPrice } / offers.size else 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مقایسه قیمت", maxLines = 1) },
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
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${Fa.number(offers.size.toLong())} فروشگاه این مورد را ارائه می‌دهند",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            // خلاصه آماری قیمت
            if (offers.isNotEmpty() && minPrice != null && maxPrice != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            PriceStat("کمترین", minPrice, MaterialTheme.colorScheme.primary)
                            PriceStat("میانگین", avgPrice, MaterialTheme.colorScheme.onSurface)
                            PriceStat("بیشترین", maxPrice, MaterialTheme.colorScheme.error)
                        }
                        if (maxPrice > minPrice) {
                            Text(
                                "با انتخاب ارزان‌ترین گزینه تا ${Fa.price(maxPrice - minPrice)} صرفه‌جویی می‌کنید",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            item {
                ChipRow(
                    items = SortMode.entries.map { it.label },
                    selected = sort.label,
                    onSelect = { label ->
                        SortMode.entries.firstOrNull { it.label == label }?.let { sort = it }
                    },
                    allLabel = "مرتب‌سازی:"
                )
                Spacer(Modifier.height(8.dp))
            }

            if (offers.isEmpty()) {
                item {
                    EmptyState("⚖️", "موردی برای مقایسه نیست", "این محصول فقط در یک فروشگاه ثبت شده است.")
                }
            } else {
                itemsIndexed(sorted, key = { _, it -> it.product.id }) { index, offer ->
                    CompareRow(
                        rank = index + 1,
                        offer = offer,
                        minPrice = minPrice ?: 0L,
                        onClick = { onOfferClick(offer.product.id) },
                        onStoreClick = { onStoreClick(offer.product.storeId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceStat(label: String, value: Long, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(
            Fa.priceShort(value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompareRow(
    rank: Int,
    offer: ProductOffer,
    minPrice: Long,
    onClick: () -> Unit,
    onStoreClick: () -> Unit
) {
    val p = offer.product
    val isBest = p.finalPrice == minPrice
    val diff = p.finalPrice - minPrice

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBest) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBest) 3.dp else 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Fa.number(rank.toLong()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(26.dp)
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            p.storeName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isBest) {
                            Spacer(Modifier.width(6.dp))
                            Tag(
                                "🏆 بهترین قیمت",
                                container = MaterialTheme.colorScheme.primary,
                                content = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RatingBadge(p.rating)
                        offer.distanceLabel?.let { DistanceBadge(it) }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Fa.price(p.finalPrice),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isBest) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "/ ${p.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (diff > 0) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "+${Fa.number(diff)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (p.discountPercent > 0) {
                        Spacer(Modifier.height(3.dp))
                        DiscountBadge(p.discountPercent)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📍 ${p.city}، ${p.province}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "مشاهده فروشگاه ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onStoreClick)
                )
            }
            if (!p.available) {
                Spacer(Modifier.height(6.dp))
                Tag(
                    "در حال حاضر ناموجود",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
