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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.data.GeoUtils
import ir.chidari.data.local.ProductEntity
import ir.chidari.data.local.StoreEntity
import ir.chidari.ui.components.DiscountBadge
import ir.chidari.ui.components.DistanceBadge
import ir.chidari.ui.components.EmojiAvatar
import ir.chidari.ui.components.ImageOrEmoji
import ir.chidari.util.Navigation
import ir.chidari.ui.components.EmptyState
import ir.chidari.ui.components.RatingBadge
import ir.chidari.ui.components.Tag
import ir.chidari.util.Fa

/**
 * صفحه فروشگاه — مانند «پیج» فروشنده:
 * سربرگ با نام و بیو و اطلاعات تماس، سپس شبکه محصولات/خدمات دسته‌بندی‌شده.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreDetailScreen(
    store: StoreEntity?,
    products: List<ProductEntity>,
    isFavorite: Boolean,
    isFollowing: Boolean,
    isOwner: Boolean,
    followerCount: Int,
    onToggleFollow: () -> Unit,
    userLat: Double?,
    userLng: Double?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onProductClick: (Long) -> Unit,
    onAddProduct: () -> Unit,
    onEditProduct: (Long) -> Unit,
    onEditStore: () -> Unit,
    onDeleteProduct: (ProductEntity) -> Unit,
    onRate: (Int) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showCallConfirm by remember { mutableStateOf(false) }
    var showRating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ProductEntity?>(null) }
    var selectedCategory by remember { mutableStateOf("") }

    if (store == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("فروشگاه یافت نشد")
        }
        return
    }

    val distance = if (userLat != null && userLng != null)
        GeoUtils.formatDistance(GeoUtils.distanceKm(userLat, userLng, store.lat, store.lng)) else null

    val categories = remember(products) { products.map { it.category }.distinct() }
    val visible = remember(products, selectedCategory) {
        if (selectedCategory.isBlank()) products else products.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(store.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        val text = "${store.name}\n${store.category}\n${store.address}\nتلفن: ${store.phone}\n(از برنامه بازار نزدیک)"
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }, "اشتراک‌گذاری فروشگاه"
                            )
                        )
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "اشتراک‌گذاری")
                    }
                    if (isOwner) {
                        IconButton(onClick = onEditStore) {
                            Icon(Icons.Filled.Edit, contentDescription = "ویرایش فروشگاه")
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
        },
        floatingActionButton = {
            if (isOwner) {
                ExtendedFloatingActionButton(
                    onClick = onAddProduct,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("افزودن محصول") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // سربرگ پیج فروشگاه
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ImageOrEmoji(
                            imageUrl = store.imageUri,
                            emoji = store.coverEmoji,
                            size = 76,
                            background = MaterialTheme.colorScheme.surface
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                store.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                store.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatItem("محصول", Fa.number(products.size.toLong()))
                                StatItem("دنبال‌کننده", Fa.number(followerCount.toLong()))
                                StatItem("امتیاز", Fa.rating(store.rating))
                            }
                        }
                    }

                    if (store.bio.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            store.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        distance?.let { DistanceBadge(it) }
                        if (store.openHours.isNotBlank()) {
                            Tag(
                                "🕒 ${Fa.digits(store.openHours)}",
                                container = MaterialTheme.colorScheme.surface,
                                content = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // دکمه فالو (برای فروشگاه خودِ کاربر نمایش داده نمی‌شود)
                    if (!isOwner) {
                        Spacer(Modifier.height(14.dp))
                        if (isFollowing) {
                            OutlinedButton(
                                onClick = {
                                    if (Fa.hapticFeedback) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    onToggleFollow()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) { Text("✓ دنبال می‌کنید") }
                        } else {
                            Button(
                                onClick = {
                                    if (Fa.hapticFeedback) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    onToggleFollow()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) { Text("+ دنبال کردن", fontWeight = FontWeight.Bold) }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (store.phone.isNotBlank()) {
                            FilledTonalButton(
                                onClick = {
                                    if (Fa.confirmBeforeCall) showCallConfirm = true
                                    else context.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${store.phone}"))
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Call, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("تماس", maxLines = 1)
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                Navigation.openNavigation(context, store.lat, store.lng, store.name)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Map, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مسیریابی", maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = { showRating = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("⭐ امتیاز", maxLines = 1)
                        }
                    }
                }
            }

            // نشانی و تماس
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        InfoRow("📍 نشانی", store.address.ifBlank { "${store.city}، ${store.province}" })
                        if (store.phone.isNotBlank()) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            InfoRow("📞 تلفن", Fa.digits(store.phone))
                        }
                        if (store.instagram.isNotBlank()) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            InfoRow("📷 اینستاگرام", store.instagram)
                        }
                    }
                }
            }

            // فیلتر دسته‌بندی داخل فروشگاه
            if (categories.size > 1) {
                item {
                    ir.chidari.ui.components.ChipRow(
                        items = categories,
                        selected = selectedCategory,
                        onSelect = { selectedCategory = it },
                        allLabel = "همه"
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            item {
                ir.chidari.ui.components.SectionHeader(
                    title = "محصولات و خدمات",
                    actionText = "${Fa.number(visible.size.toLong())} مورد"
                )
            }

            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "📦",
                        title = "هنوز محصولی ثبت نشده",
                        subtitle = if (isOwner)
                            "با دکمه «افزودن محصول» اولین کالا یا خدمت خود را ثبت کنید."
                        else "این فروشگاه هنوز محصولی اضافه نکرده است."
                    )
                }
            } else {
                items(visible, key = { it.id }) { product ->
                    StoreProductRow(
                        product = product,
                        editable = isOwner,
                        onClick = { onProductClick(product.id) },
                        onEdit = { onEditProduct(product.id) },
                        onDelete = { deleteTarget = product }
                    )
                }
            }
        }
    }

    if (showCallConfirm) {
        AlertDialog(
            onDismissRequest = { showCallConfirm = false },
            title = { Text("تماس با فروشگاه") },
            text = { Text("با شماره ${Fa.digits(store.phone)} تماس گرفته شود؟") },
            confirmButton = {
                Button(onClick = {
                    showCallConfirm = false
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${store.phone}")))
                }) { Text("تماس") }
            },
            dismissButton = {
                TextButton(onClick = { showCallConfirm = false }) { Text("انصراف") }
            }
        )
    }

    if (showRating) {
        var chosen by remember { mutableStateOf(5) }
        AlertDialog(
            onDismissRequest = { showRating = false },
            title = { Text("امتیاز شما به این فروشگاه") },
            text = {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    (1..5).forEach { star ->
                        Text(
                            if (star <= chosen) "⭐" else "☆",
                            fontSize = 30.sp,
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable { chosen = star }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onRate(chosen)          // میانگین را سرور حساب می‌کند
                    showRating = false
                }) { Text("ثبت امتیاز") }
            },
            dismissButton = {
                TextButton(onClick = { showRating = false }) { Text("انصراف") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف محصول") },
            text = { Text("«${target.title}» حذف شود؟ این کار قابل بازگشت نیست.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteProduct(target)
                    deleteTarget = null
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("انصراف") }
            }
        )
    }
}


@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreProductRow(
    product: ProductEntity,
    editable: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ImageOrEmoji(
                imageUrl = product.imageUri,
                emoji = product.emoji,
                size = 50,
                background = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    product.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val final = if (product.discountPercent > 0)
                        product.price * (100 - product.discountPercent) / 100 else product.price
                    Text(
                        Fa.price(final),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        " / ${product.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (product.discountPercent > 0) {
                        Spacer(Modifier.width(8.dp))
                        DiscountBadge(product.discountPercent)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(product.category)
                    if (!product.available) {
                        Tag(
                            "ناموجود",
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            if (editable) {
                Column {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "ویرایش", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "حذف",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
