package ir.chidari.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.data.auth.AuthUser
import ir.chidari.data.local.StoreEntity
import ir.chidari.ui.components.EmojiAvatar
import ir.chidari.ui.components.EmptyState
import ir.chidari.ui.components.RatingBadge
import ir.chidari.util.Fa

/** کدام فهرست در «حساب من» نمایش داده می‌شود. */
private enum class ProfileTab { MINE, FOLLOWED }

/**
 * تب «حساب من».
 *
 * دو دکمه‌ی بالای صفحه بین «فروشگاه‌های من» و «دنبال‌شده‌ها» جابه‌جا می‌کنند.
 * خروج از حساب اینجا نیست — به منوی همبرگری منتقل شده است.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: AuthUser?,
    myStores: List<StoreEntity>,
    followedStores: List<StoreEntity>,
    onSignInClick: () -> Unit,
    onCreateStore: () -> Unit,
    onOpenStore: (Long) -> Unit,
    onEditStore: (Long) -> Unit,
    onAddProduct: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf(ProfileTab.MINE) }

    // ---------- حالت مهمان ----------
    if (user == null) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("👤", fontSize = 52.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "وارد نشده‌اید",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "گشت‌وگذار در فروشگاه‌ها آزاد است. برای امکانات شخصی وارد شوید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onSignInClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("ورود یا ثبت‌نام با ایمیل", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "با ساختن حساب می‌توانید:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        listOf(
                            "❤️" to "فروشگاه‌ها و محصولات را در علاقه‌مندی ذخیره کنید",
                            "🔖" to "فروشگاه‌های محبوبتان را دنبال کنید",
                            "🏪" to "فروشگاه خودتان را بسازید و محصول اضافه کنید",
                            "⭐" to "به فروشگاه‌ها امتیاز بدهید"
                        ).forEach { (emoji, text) ->
                            Row(
                                Modifier.padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(emoji, fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(text, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // ---------- کاربر واردشده ----------
    val list = if (tab == ProfileTab.MINE) myStores else followedStores

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiAvatar(
                        emoji = "👤",
                        size = 58,
                        background = MaterialTheme.colorScheme.surface
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            user.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            user.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }

                // ----- دو دکمه‌ی جدید -----
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TabButton(
                        emoji = "🏪",
                        count = myStores.size,
                        label = "فروشگاه‌های من",
                        selected = tab == ProfileTab.MINE,
                        onClick = { tab = ProfileTab.MINE },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        emoji = "🔖",
                        count = followedStores.size,
                        label = "دنبال‌شده‌ها",
                        selected = tab == ProfileTab.FOLLOWED,
                        onClick = { tab = ProfileTab.FOLLOWED },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCreateStore,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ثبت فروشگاه جدید", fontWeight = FontWeight.Bold)
                }
            }
        }

        // ----- فهرست -----
        if (list.isEmpty()) {
            item {
                if (tab == ProfileTab.MINE) {
                    EmptyState(
                        emoji = "🏗️",
                        title = "هنوز فروشگاهی نساخته‌اید",
                        subtitle = "نام، دسته‌بندی و موقعیت فروشگاه را وارد کنید و شروع به افزودن محصول کنید."
                    )
                } else {
                    EmptyState(
                        emoji = "🔖",
                        title = "هنوز فروشگاهی را دنبال نکرده‌اید",
                        subtitle = "در صفحه هر فروشگاه، دکمه «دنبال کردن» را بزنید تا اینجا ذخیره شود."
                    )
                }
            }
        } else {
            items(list, key = { "${tab.name}-${it.id}" }) { store ->
                if (tab == ProfileTab.MINE) {
                    MyStoreCard(
                        store = store,
                        onOpen = { onOpenStore(store.id) },
                        onEdit = { onEditStore(store.id) },
                        onAddProduct = { onAddProduct(store.id) }
                    )
                } else {
                    FollowedStoreCard(store = store, onOpen = { onOpenStore(store.id) })
                }
            }
        }
    }
}

/** دکمه‌ی بزرگ شمارنده‌دار بالای صفحه. */
@Composable
private fun TabButton(
    emoji: String,
    count: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.surface,
        label = "tabBg"
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondary
        else MaterialTheme.colorScheme.primary,
        label = "tabFg"
    )

    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = container,
        shadowElevation = if (selected) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                Fa.number(count.toLong()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = content
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.85f),
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyStoreCard(
    store: StoreEntity,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onAddProduct: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiAvatar(store.coverEmoji, 54)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        store.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        store.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RatingBadge(store.rating, store.ratingCount)
                        Text(
                            "👥 ${Fa.number(store.followerCount.toLong())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            store.city,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAddProduct,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("محصول", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ویرایش", maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowedStoreCard(store: StoreEntity, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiAvatar(store.coverEmoji, 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    store.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${store.category} • ${store.city}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                RatingBadge(store.rating)
                Spacer(Modifier.height(3.dp))
                Text(
                    "✓ دنبال می‌کنید",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
