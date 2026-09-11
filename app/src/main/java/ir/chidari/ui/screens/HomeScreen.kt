package ir.chidari.ui.screens

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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.data.Categories
import ir.chidari.data.prefs.UserLocation
import ir.chidari.data.repo.SortMode
import ir.chidari.data.repo.StoreWithDistance
import ir.chidari.data.remote.SyncStatus
import ir.chidari.ui.components.ChipRow
import ir.chidari.ui.components.SyncBanner
import ir.chidari.ui.components.EmptyState
import ir.chidari.ui.components.SectionHeader
import ir.chidari.ui.components.StoreCard
import ir.chidari.ui.vm.FilterState
import ir.chidari.util.Fa

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    location: UserLocation,
    filters: FilterState,
    stores: List<StoreWithDistance>,
    nearestStore: StoreWithDistance?,
    favorites: Set<String>,
    locating: Boolean,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSortChange: (SortMode) -> Unit,
    onOpenLocation: () -> Unit,
    onUseGps: () -> Unit,
    onOpenFilters: () -> Unit,
    onStoreClick: (Long) -> Unit,
    onToggleFavorite: (String) -> Unit,
    syncStatus: SyncStatus = SyncStatus.Idle,
    onRetrySync: () -> Unit = {},
    ownedIds: Set<Long> = emptySet(),
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // سربرگ: موقعیت + جست‌وجو
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenLocation() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                if (location.useGps) "موقعیت فعلی شما" else "موقعیت انتخابی",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                            Text(
                                location.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("▾", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = onUseGps, enabled = !locating) {
                        if (locating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = "یافتن موقعیت با GPS",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = filters.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("جست‌وجوی فروشگاه، خدمات یا محصول…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = onOpenFilters) {
                            Icon(Icons.Filled.FilterList, contentDescription = "فیلترها")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }

        // نوار وضعیت ارتباط با سرور
        item {
            SyncBanner(status = syncStatus, onRetry = onRetrySync)
        }

        // دسته‌بندی فروشگاه‌ها
        item {
            Spacer(Modifier.height(8.dp))
            ChipRow(
                items = Categories.names,
                selected = filters.storeCategory,
                onSelect = onCategoryChange,
                allLabel = "همه دسته‌ها",
                leadingEmoji = { Categories.emojiOf(it) }
            )
        }

        // مرتب‌سازی
        item {
            Spacer(Modifier.height(4.dp))
            ChipRow(
                items = SortMode.entries.filter { it != SortMode.CHEAPEST }.map { it.label },
                selected = filters.sort.label.takeIf { filters.sort != SortMode.CHEAPEST } ?: "",
                onSelect = { label ->
                    SortMode.entries.firstOrNull { it.label == label }?.let(onSortChange)
                },
                allLabel = "مرتب‌سازی:"
            )
            Spacer(Modifier.height(4.dp))
        }

        // کارت نزدیک‌ترین فروشگاه
        if (nearestStore != null && filters.sort == SortMode.NEAREST) {
            item {
                SectionHeader("🎯 نزدیک‌ترین فروشگاه به شما")
                StoreCard(
                    item = nearestStore,
                    isFavorite = favorites.contains("store:${nearestStore.store.id}"),
                    onClick = { onStoreClick(nearestStore.store.id) },
                    onToggleFavorite = { onToggleFavorite("store:${nearestStore.store.id}") },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    highlighted = true,
                    isOwner = ownedIds.contains(nearestStore.store.id)
                )
            }
        } else if (!location.hasCoordinates) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = onUseGps,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📡", fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "موقعیت خود را فعال کنید",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "تا فروشگاه‌ها بر اساس فاصله واقعی از شما مرتب شوند",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // فهرست فروشگاه‌ها
        item {
            SectionHeader(
                title = if (filters.storeCategory.isBlank()) "فروشگاه‌ها و خدمات"
                else "${Categories.emojiOf(filters.storeCategory)} ${filters.storeCategory}",
                actionText = "${Fa.number(stores.size.toLong())} مورد"
            )
        }

        if (stores.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🔍",
                    title = "فروشگاهی پیدا نشد",
                    subtitle = "می‌توانید شهر دیگری انتخاب کنید، فیلترها را پاک کنید یا خودتان اولین فروشگاه این منطقه را ثبت کنید."
                )
            }
        } else {
            items(stores, key = { it.store.id }) { item ->
                val key = "store:${item.store.id}"
                StoreCard(
                    item = item,
                    isFavorite = favorites.contains(key),
                    onClick = { onStoreClick(item.store.id) },
                    onToggleFavorite = { onToggleFavorite(key) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    isOwner = ownedIds.contains(item.store.id)
                )
            }
        }
    }
}
