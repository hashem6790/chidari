package ir.chidari.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.data.repo.ProductOffer
import ir.chidari.ui.components.DiscountBadge
import ir.chidari.ui.components.DistanceBadge
import ir.chidari.ui.components.EmojiAvatar
import ir.chidari.util.Navigation
import ir.chidari.ui.components.RatingBadge
import ir.chidari.ui.components.SectionHeader
import ir.chidari.ui.components.Tag
import ir.chidari.util.Fa

/**
 * جزئیات کامل یک محصول: قیمت، مشخصات، توضیحات، فروشگاه ارائه‌دهنده و
 * فهرست همان محصول در فروشگاه‌های دیگر برای مقایسه سریع.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    offer: ProductOffer?,
    otherOffers: List<ProductOffer>,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStoreClick: (Long) -> Unit,
    onOfferClick: (Long) -> Unit,
    onCompareAll: (String) -> Unit
) {
    val context = LocalContext.current

    if (offer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("محصول یافت نشد") }
        return
    }
    val p = offer.product

    val cheaperCount = otherOffers.count { it.product.finalPrice < p.finalPrice }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "علاقه‌مندی"
                        )
                    }
                    IconButton(onClick = {
                        val text = "${p.title}\nقیمت: ${Fa.price(p.finalPrice)} / ${p.unit}\n" +
                                "فروشگاه: ${p.storeName} - ${p.city}\n(از برنامه بازار نزدیک)"
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }, "اشتراک‌گذاری محصول"
                            )
                        )
                    }) { Icon(Icons.Filled.Share, contentDescription = "اشتراک‌گذاری") }
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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(p.emoji, fontSize = 64.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        p.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Fa.price(p.finalPrice),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            " / ${p.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                    if (p.discountPercent > 0) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Fa.price(p.price),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                textDecoration = TextDecoration.LineThrough
                            )
                            Spacer(Modifier.width(8.dp))
                            DiscountBadge(p.discountPercent)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tag(
                            p.category,
                            container = MaterialTheme.colorScheme.surface,
                            content = MaterialTheme.colorScheme.onSurface
                        )
                        offer.distanceLabel?.let { DistanceBadge(it) }
                        Tag(
                            if (p.available) "موجود" else "ناموجود",
                            container = if (p.available) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.errorContainer,
                            content = if (p.available) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // هشدار قیمت بهتر
            if (cheaperCount > 0) {
                item {
                    val best = otherOffers.minByOrNull { it.product.finalPrice }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        onClick = { onCompareAll(p.title) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("💰", fontSize = 26.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "همین کالا ارزان‌تر هم هست!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                best?.let {
                                    Text(
                                        "${Fa.price(it.product.finalPrice)} در ${it.product.storeName}" +
                                                (it.distanceLabel?.let { d -> " • $d" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text("مقایسه ›", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // توضیحات
            if (p.description.isNotBlank()) {
                item {
                    SectionHeader("توضیحات")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            p.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // مشخصات
            if (p.specs.isNotBlank()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("مشخصات")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            p.specs.lines().filter { it.isNotBlank() }.forEachIndexed { index, line ->
                                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                val parts = line.split(":", "：").map { it.trim() }
                                if (parts.size >= 2) {
                                    Row(Modifier.fillMaxWidth()) {
                                        Text(
                                            parts[0],
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(120.dp)
                                        )
                                        Text(
                                            Fa.digits(parts.drop(1).joinToString(": ")),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    Text("• ${Fa.digits(line)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // فروشگاه ارائه‌دهنده
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("فروشگاه ارائه‌دهنده")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = { onStoreClick(p.storeId) },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EmojiAvatar(
                                ir.chidari.data.Categories.emojiOf(p.storeCategory),
                                52
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.storeName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${p.city}، ${p.province}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                RatingBadge(p.rating)
                            }
                            Icon(Icons.Filled.Store, contentDescription = null)
                        }
                        if (p.address.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "📍 ${p.address}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (p.phone.isNotBlank()) {
                                FilledTonalButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.phone}"))
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Call, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("تماس")
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    Navigation.openNavigation(context, p.lat, p.lng, p.storeName)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Map, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("مسیر")
                            }
                        }
                    }
                }
            }

            // سایر فروشگاه‌ها
            if (otherOffers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader(
                        title = "همین کالا در فروشگاه‌های دیگر",
                        actionText = "مقایسه کامل",
                        onAction = { onCompareAll(p.title) }
                    )
                }
                items(otherOffers.take(6), key = { it.product.id }) { other ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                        onClick = { onOfferClick(other.product.id) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    other.product.storeName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    other.product.city + (other.distanceLabel?.let { " • $it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    Fa.price(other.product.finalPrice),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (other.product.finalPrice < p.finalPrice)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                val diff = other.product.finalPrice - p.finalPrice
                                if (diff != 0L) {
                                    Text(
                                        if (diff < 0) "${Fa.number(-diff)} ارزان‌تر"
                                        else "${Fa.number(diff)} گران‌تر",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (diff < 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
