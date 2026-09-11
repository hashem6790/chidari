package ir.chidari.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.data.auth.AuthUser
import ir.chidari.data.prefs.AppSettings
import ir.chidari.data.prefs.BackBehavior
import ir.chidari.data.prefs.DefaultSort
import ir.chidari.data.prefs.ListDensity
import ir.chidari.data.prefs.DistanceUnit
import ir.chidari.data.prefs.FontScale
import ir.chidari.data.prefs.SearchRadius
import ir.chidari.data.prefs.StartTab
import ir.chidari.data.prefs.ThemeMode
import ir.chidari.util.Fa

/**
 * منوی کشویی (همبرگری) برنامه.
 *
 * همه تنظیمات و «خروج از حساب» اینجا جمع شده‌اند تا صفحه «حساب من»
 * فقط به کار اصلی‌اش یعنی فروشگاه‌ها و دنبال‌شده‌ها بپردازد.
 */
@Composable
fun AppDrawer(
    user: AuthUser?,
    settings: AppSettings,
    locationLabel: String,
    appVersion: String,
    onChangeCity: () -> Unit,
    onUseGps: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onFontScaleChange: (FontScale) -> Unit,
    onCompactPricesChange: (Boolean) -> Unit,
    onPersianDigitsChange: (Boolean) -> Unit,
    onDiscountBadgesChange: (Boolean) -> Unit,
    onListDensityChange: (ListDensity) -> Unit,
    onShowDistanceChange: (Boolean) -> Unit,
    onShowRatingsChange: (Boolean) -> Unit,
    onUnitChange: (DistanceUnit) -> Unit,
    onDefaultSortChange: (DefaultSort) -> Unit,
    onSearchRadiusChange: (SearchRadius) -> Unit,
    onStartTabChange: (StartTab) -> Unit,
    onOnlyAvailableChange: (Boolean) -> Unit,
    onSearchHistoryChange: (Boolean) -> Unit,
    onHideEmptyStoresChange: (Boolean) -> Unit,
    onAutoDetectLocationChange: (Boolean) -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onLoadImagesChange: (Boolean) -> Unit,
    onConfirmDeleteChange: (Boolean) -> Unit,
    onHapticChange: (Boolean) -> Unit,
    onConfirmCallChange: (Boolean) -> Unit,
    onBackBehaviorChange: (BackBehavior) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onNotifyProductsChange: (Boolean) -> Unit,
    onNotifyDiscountsChange: (Boolean) -> Unit,
    onNotifyNewStoresChange: (Boolean) -> Unit,
    onLocationHistoryChange: (Boolean) -> Unit,
    onPublicProfileChange: (Boolean) -> Unit,
    onRefreshData: () -> Unit,
    onClearCache: () -> Unit,
    onResetSettings: () -> Unit,
    onShareApp: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {

            // ---------- سربرگ ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiAvatar(
                        emoji = if (user != null) "👤" else "🏪",
                        size = 46,
                        background = MaterialTheme.colorScheme.surface
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            user?.label ?: "چی داری؟",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            user?.email ?: "وارد نشده‌اید",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // هم‌زمان فقط یک بخش باز است
            var openSection by remember { mutableStateOf("") }
            fun toggle(k: String) { openSection = if (openSection == k) "" else k }

            DrawerSection("📍", "موقعیت", openSection == "loc", { toggle("loc") }) {
                DrawerItem("📍", "تغییر شهر", value = locationLabel, onClick = onChangeCity)
                DrawerItem("🎯", "یافتن موقعیت با GPS", onClick = onUseGps)
                DrawerSwitch(
                    emoji = "🧭",
                    title = "تشخیص خودکار موقعیت",
                    subtitle = "هنگام باز شدن برنامه، موقعیت را خودش پیدا کند",
                    checked = settings.autoDetectLocation,
                    onChange = onAutoDetectLocationChange
                )
            }

            DrawerSection("🎨", "نمایش", openSection == "disp", { toggle("disp") }) {
                DrawerPicker(
                    emoji = "🎨",
                    title = "پوسته برنامه",
                    current = settings.themeMode.label,
                    options = ThemeMode.entries.map { it.label },
                    onSelect = { l -> ThemeMode.entries.firstOrNull { it.label == l }?.let(onThemeChange) }
                )
                DrawerPicker(
                    emoji = "🔠",
                    title = "اندازه قلم",
                    current = settings.fontScale.label,
                    options = FontScale.entries.map { it.label },
                    onSelect = { l -> FontScale.entries.firstOrNull { it.label == l }?.let(onFontScaleChange) }
                )
                DrawerSwitch(
                    emoji = "۱۲",
                    title = "ارقام فارسی",
                    subtitle = "خاموش یعنی نمایش 123 به‌جای ۱۲۳",
                    checked = settings.persianDigits,
                    onChange = onPersianDigitsChange
                )
                DrawerSwitch(
                    emoji = "💰",
                    title = "نمایش خلاصه قیمت",
                    subtitle = "«۲٫۴ میلیون» به‌جای عدد کامل",
                    checked = settings.compactPrices,
                    onChange = onCompactPricesChange
                )
                DrawerSwitch(
                    emoji = "🏷️",
                    title = "نشان تخفیف",
                    subtitle = "برچسب درصد تخفیف روی کارت‌ها",
                    checked = settings.showDiscountBadges,
                    onChange = onDiscountBadgesChange
                )
                DrawerPicker(
                    emoji = "📐",
                    title = "چگالی فهرست",
                    current = settings.listDensity.label,
                    options = ListDensity.entries.map { it.label },
                    onSelect = { l -> ListDensity.entries.firstOrNull { it.label == l }?.let(onListDensityChange) }
                )
                DrawerSwitch(
                    emoji = "📍",
                    title = "نمایش فاصله روی کارت‌ها",
                    checked = settings.showDistanceOnCards,
                    onChange = onShowDistanceChange
                )
                DrawerSwitch(
                    emoji = "⭐",
                    title = "نمایش امتیاز",
                    checked = settings.showRatings,
                    onChange = onShowRatingsChange
                )
            }

            DrawerSection("🔎", "جست‌وجو و فهرست‌ها", openSection == "srch", { toggle("srch") }) {
                DrawerPicker(
                    emoji = "📏",
                    title = "واحد فاصله",
                    current = settings.distanceUnit.label,
                    options = DistanceUnit.entries.map { it.label },
                    onSelect = { l -> DistanceUnit.entries.firstOrNull { it.label == l }?.let(onUnitChange) }
                )
                DrawerPicker(
                    emoji = "🔀",
                    title = "ترتیب پیش‌فرض",
                    current = settings.defaultSort.label,
                    options = DefaultSort.entries.map { it.label },
                    onSelect = { l -> DefaultSort.entries.firstOrNull { it.label == l }?.let(onDefaultSortChange) }
                )
                DrawerPicker(
                    emoji = "🎯",
                    title = "شعاع جست‌وجو",
                    current = settings.searchRadius.label,
                    options = SearchRadius.entries.map { it.label },
                    onSelect = { l -> SearchRadius.entries.firstOrNull { it.label == l }?.let(onSearchRadiusChange) }
                )
                DrawerPicker(
                    emoji = "🚪",
                    title = "صفحه شروع",
                    current = settings.startTab.label,
                    options = StartTab.entries.map { it.label },
                    onSelect = { l -> StartTab.entries.firstOrNull { it.label == l }?.let(onStartTabChange) }
                )
                DrawerSwitch(
                    emoji = "📦",
                    title = "فقط کالاهای موجود",
                    subtitle = "کالاهای ناموجود پنهان شوند",
                    checked = settings.onlyAvailableByDefault,
                    onChange = onOnlyAvailableChange
                )
                DrawerSwitch(
                    emoji = "🕘",
                    title = "ذخیره جست‌وجوهای اخیر",
                    checked = settings.saveSearchHistory,
                    onChange = onSearchHistoryChange
                )
                DrawerSwitch(
                    emoji = "🚫",
                    title = "پنهان کردن فروشگاه‌های خالی",
                    subtitle = "فروشگاه‌هایی که هنوز محصولی ندارند",
                    checked = settings.hideEmptyStores,
                    onChange = onHideEmptyStoresChange
                )
            }

            DrawerSection("📶", "داده و شبکه", openSection == "data", { toggle("data") }) {
                DrawerItem("🔄", "به‌روزرسانی از سرور", onClick = onRefreshData)
                DrawerSwitch(
                    emoji = "📶",
                    title = "همگام‌سازی خودکار",
                    subtitle = "هنگام باز شدن برنامه و تغییر شهر",
                    checked = settings.autoSync,
                    onChange = onAutoSyncChange
                )
                DrawerSwitch(
                    emoji = "📡",
                    title = "فقط با وای‌فای",
                    subtitle = "برای صرفه‌جویی در حجم اینترنت",
                    checked = settings.syncOnWifiOnly,
                    onChange = onWifiOnlyChange
                )
                DrawerSwitch(
                    emoji = "🖼️",
                    title = "بارگذاری تصاویر",
                    subtitle = "خاموش کردن، مصرف اینترنت را کم می‌کند",
                    checked = settings.loadImages,
                    onChange = onLoadImagesChange
                )
                DrawerItem("🗑️", "پاک کردن حافظه پنهان", onClick = { confirmClearCache = true })
            }

            DrawerSection("🔔", "اعلان‌ها", openSection == "notif", { toggle("notif") }) {
                DrawerSwitch(
                    emoji = "🆕",
                    title = "محصول تازه",
                    subtitle = "در فروشگاه‌هایی که دنبال می‌کنید",
                    checked = settings.notifyNewProducts,
                    onChange = onNotifyProductsChange
                )
                DrawerSwitch(
                    emoji = "🔥",
                    title = "تخفیف‌ها",
                    subtitle = "خبر تخفیف فروشگاه‌های دنبال‌شده",
                    checked = settings.notifyDiscounts,
                    onChange = onNotifyDiscountsChange
                )
                DrawerSwitch(
                    emoji = "🏪",
                    title = "فروشگاه تازه در شهر شما",
                    checked = settings.notifyNewStores,
                    onChange = onNotifyNewStoresChange
                )
            }

            DrawerSection("⚠️", "رفتار و ایمنی", openSection == "behav", { toggle("behav") }) {
                DrawerSwitch(
                    emoji = "⚠️",
                    title = "تأیید پیش از حذف",
                    subtitle = "هنگام حذف فروشگاه یا محصول بپرسد",
                    checked = settings.confirmBeforeDelete,
                    onChange = onConfirmDeleteChange
                )
                DrawerSwitch(
                    emoji = "📞",
                    title = "تأیید پیش از تماس",
                    subtitle = "پیش از شماره‌گیری بپرسد",
                    checked = settings.confirmBeforeCall,
                    onChange = onConfirmCallChange
                )
                DrawerSwitch(
                    emoji = "📳",
                    title = "بازخورد لرزشی",
                    subtitle = "لرزش کوتاه هنگام فالو و علاقه‌مندی",
                    checked = settings.hapticFeedback,
                    onChange = onHapticChange
                )
                DrawerPicker(
                    emoji = "🔙",
                    title = "دکمه بازگشت",
                    current = settings.backBehavior.label,
                    options = BackBehavior.entries.map { it.label },
                    onSelect = { l -> BackBehavior.entries.firstOrNull { it.label == l }?.let(onBackBehaviorChange) }
                )
                DrawerSwitch(
                    emoji = "💡",
                    title = "روشن ماندن صفحه",
                    subtitle = "هنگام مشاهده فروشگاه، صفحه خاموش نشود",
                    checked = settings.keepScreenOn,
                    onChange = onKeepScreenOnChange
                )
            }

            DrawerSection("🔒", "حریم خصوصی", openSection == "priv", { toggle("priv") }) {
                DrawerSwitch(
                    emoji = "🗺️",
                    title = "ذخیره موقعیت روی دستگاه",
                    subtitle = "برای نمایش سریع‌تر فروشگاه‌های نزدیک",
                    checked = settings.storeLocationHistory,
                    onChange = onLocationHistoryChange
                )
                DrawerSwitch(
                    emoji = "👁️",
                    title = "نمایش عمومی فروشگاه‌های من",
                    subtitle = "خاموش کردن، فروشگاه‌ها را از دید دیگران پنهان می‌کند",
                    checked = settings.publicProfile,
                    onChange = onPublicProfileChange
                )
            }

            DrawerSection("ℹ️", "درباره", openSection == "about", { toggle("about") }) {
                DrawerItem("❓", "راهنمای استفاده", onClick = onHelp)
                DrawerItem("📤", "معرفی به دوستان", onClick = onShareApp)
                DrawerItem("ℹ️", "درباره برنامه", value = "نسخه ${Fa.digits(appVersion)}", onClick = onAbout)
                DrawerItem("♻️", "بازگرداندن تنظیمات پیش‌فرض", onClick = { confirmReset = true })
            }

            // ---------- حساب (همیشه دیده می‌شود) ----------
            if (user != null) {
                DrawerItem(
                    emoji = "🚪",
                    title = "خروج از حساب",
                    danger = true,
                    onClick = { confirmSignOut = true }
                )
            } else {
                DrawerItem("🔑", "ورود یا ثبت‌نام", onClick = { onSignIn() })
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "چی داری؟ — نسخه ${Fa.digits(appVersion)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }

    // ---------- پنجره‌های تأیید ----------
    if (confirmSignOut && user != null) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("خروج از حساب") },
            text = { Text("از حساب «${user.email}» خارج می‌شوید؟ فروشگاه‌های شما روی سرور باقی می‌مانند.") },
            confirmButton = {
                Button(onClick = {
                    confirmSignOut = false
                    onSignOut()
                }) { Text("خروج") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("انصراف") }
            }
        )
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text("پاک کردن حافظه پنهان") },
            text = {
                Text(
                    "فروشگاه‌های ذخیره‌شده روی گوشی پاک و دوباره از سرور دریافت می‌شوند. " +
                            "فروشگاه‌های خودتان و علاقه‌مندی‌ها حذف نمی‌شوند."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmClearCache = false
                    onClearCache()
                }) { Text("پاک کن") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) { Text("انصراف") }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("بازگرداندن تنظیمات") },
            text = { Text("همه تنظیمات به حالت پیش‌فرض برمی‌گردند. ادامه می‌دهید؟") },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    onResetSettings()
                }) { Text("بازگردان") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("انصراف") }
            }
        )
    }
}

// ---------------- اجزای کوچک منو ----------------

/**
 * بخش جمع‌شونده منو.
 *
 * همه بخش‌ها به‌صورت پیش‌فرض بسته‌اند تا منو کوتاه و قابل مرور بماند؛
 * با لمس سربرگ، محتوای آن بخش باز می‌شود. هم‌زمان فقط یک بخش باز است
 * تا کاربر مجبور به پیمایش طولانی نشود.
 */
@Composable
private fun DrawerSection(
    emoji: String,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron"
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 17.sp, modifier = Modifier.width(28.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (expanded) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "بستن" else "باز کردن",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(vertical = 4.dp)
            ) { content() }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun DrawerDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
private fun DrawerItem(
    emoji: String,
    title: String,
    value: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 17.sp, modifier = Modifier.width(28.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (danger) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerSwitch(
    emoji: String,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 17.sp, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** ردیفی که با زدن روی آن، فهرست گزینه‌ها باز می‌شود. */
@Composable
private fun DrawerPicker(
    emoji: String,
    title: String,
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 17.sp, modifier = Modifier.width(28.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                current,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (opt == current) "✓ $opt" else opt,
                            fontWeight = if (opt == current) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}
