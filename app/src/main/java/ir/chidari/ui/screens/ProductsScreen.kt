package ir.chidari.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.chidari.data.Categories
import ir.chidari.data.local.TitleStats
import ir.chidari.data.repo.ProductOffer
import ir.chidari.data.repo.SortMode
import ir.chidari.ui.components.ChipRow
import ir.chidari.ui.components.EmptyState
import ir.chidari.ui.components.ProductCard
import ir.chidari.ui.components.SectionHeader
import ir.chidari.ui.vm.FilterState
import ir.chidari.util.Fa

/**
 * فهرست همه محصولات و خدمات منطقه انتخابی، با امکان مرتب‌سازی بر اساس
 * ارزان‌ترین / نزدیک‌ترین و پرش به صفحه مقایسه هر محصول.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    locationLabel: String,
    filters: FilterState,
    products: List<ProductOffer>,
    offerStats: Map<String, TitleStats>,
    favorites: Set<String>,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSortChange: (SortMode) -> Unit,
    onOnlyAvailableChange: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
    onProductClick: (Long) -> Unit,
    onCompareClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    "مقایسه محصولات و خدمات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "📍 $locationLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = filters.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("نام محصول یا خدمت را بنویسید…") },
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

        item {
            Spacer(Modifier.height(8.dp))
            ChipRow(
                items = Categories.allProductCategories,
                selected = filters.productCategory,
                onSelect = onCategoryChange,
                allLabel = "همه دسته‌ها"
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            ChipRow(
                items = SortMode.entries.map { it.label },
                selected = filters.sort.label,
                onSelect = { label ->
                    SortMode.entries.firstOrNull { it.label == label }?.let(onSortChange)
                },
                allLabel = "مرتب‌سازی:"
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("فقط کالاهای موجود", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = filters.onlyAvailable, onCheckedChange = onOnlyAvailableChange)
            }
        }

        item {
            SectionHeader(
                title = "نتایج",
                actionText = "${Fa.number(products.size.toLong())} مورد"
            )
        }

        if (products.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🧾",
                    title = "محصولی مطابق جست‌وجوی شما نیست",
                    subtitle = "عبارت دیگری امتحان کنید، دسته‌بندی را عوض کنید یا محدوده جغرافیایی را بازتر کنید."
                )
            }
        } else {
            items(products, key = { it.product.id }) { offer ->
                val key = "product:${offer.product.id}"
                // آمار از پیش توسط پایگاه داده محاسبه شده: جست‌وجوی O(1)
                val stats = offerStats[offer.product.title]
                Column {
                    ProductCard(
                        offer = offer,
                        isFavorite = favorites.contains(key),
                        onClick = { onProductClick(offer.product.id) },
                        onToggleFavorite = { onToggleFavorite(key) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                        cheapestBadge = stats != null && stats.minPrice == offer.product.finalPrice
                    )
                    if (stats != null) {
                        Text(
                            "⚖️ مقایسه قیمت در ${Fa.number(stats.offerCount.toLong())} فروشگاه",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = 30.dp, vertical = 2.dp)
                                .clickable { onCompareClick(offer.product.title) }
                        )
                    }
                }
            }
        }
    }
}

