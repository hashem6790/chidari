package ir.chidari.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.chidari.data.repo.ProductOffer
import ir.chidari.data.repo.StoreWithDistance
import ir.chidari.ui.components.EmptyState
import ir.chidari.ui.components.ProductCard
import ir.chidari.ui.components.SectionHeader
import ir.chidari.ui.components.StoreCard
import ir.chidari.util.Fa

/** فهرست علاقه‌مندی‌های کاربر: فروشگاه‌ها و محصولات ذخیره‌شده. */
@Composable
fun FavoritesScreen(
    favoriteStores: List<StoreWithDistance>,
    favoriteProducts: List<ProductOffer>,
    favorites: Set<String>,
    isSignedIn: Boolean,
    onSignInClick: () -> Unit,
    onStoreClick: (Long) -> Unit,
    onProductClick: (Long) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(20.dp)
            ) {
                Text(
                    "علاقه‌مندی‌های من",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "فروشگاه‌ها و محصولاتی که ذخیره کرده‌اید",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
            }
        }

        if (!isSignedIn) {
            item {
                Spacer(Modifier.height(40.dp))
                EmptyState(
                    emoji = "🔐",
                    title = "برای ذخیره علاقه‌مندی‌ها وارد شوید",
                    subtitle = "دیدن فروشگاه‌ها و محصولات آزاد است، ولی برای نگه داشتن آن‌ها به یک حساب کاربری نیاز دارید.",
                    action = {
                        Button(onClick = onSignInClick, shape = RoundedCornerShape(14.dp)) {
                            Text("ورود یا ثبت‌نام")
                        }
                    }
                )
            }
        } else if (favoriteStores.isEmpty() && favoriteProducts.isEmpty()) {
            item {
                Spacer(Modifier.height(40.dp))
                EmptyState(
                    emoji = "🤍",
                    title = "هنوز چیزی ذخیره نکرده‌اید",
                    subtitle = "با زدن روی نشان قلب در کارت فروشگاه‌ها و محصولات، آن‌ها را اینجا نگه دارید."
                )
            }
        }

        if (isSignedIn && favoriteStores.isNotEmpty()) {
            item {
                SectionHeader(
                    "فروشگاه‌ها",
                    actionText = "${Fa.number(favoriteStores.size.toLong())} مورد"
                )
            }
            items(favoriteStores, key = { "s${it.store.id}" }) { item ->
                val key = "store:${item.store.id}"
                StoreCard(
                    item = item,
                    isFavorite = favorites.contains(key),
                    onClick = { onStoreClick(item.store.id) },
                    onToggleFavorite = { onToggleFavorite(key) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        if (isSignedIn && favoriteProducts.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    "محصولات و خدمات",
                    actionText = "${Fa.number(favoriteProducts.size.toLong())} مورد"
                )
            }
            items(favoriteProducts, key = { "p${it.product.id}" }) { offer ->
                val key = "product:${offer.product.id}"
                ProductCard(
                    offer = offer,
                    isFavorite = favorites.contains(key),
                    onClick = { onProductClick(offer.product.id) },
                    onToggleFavorite = { onToggleFavorite(key) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                )
            }
        }
    }
}
