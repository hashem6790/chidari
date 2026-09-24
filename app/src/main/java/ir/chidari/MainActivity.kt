package ir.chidari

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.chidari.data.local.ProductEntity
import ir.chidari.data.local.StoreEntity
import ir.chidari.data.repo.ProductOffer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import ir.chidari.data.prefs.ThemeMode
import ir.chidari.ui.components.AppDrawer
import ir.chidari.ui.components.UpdateDialog
import ir.chidari.ui.components.FiltersSheet
import ir.chidari.ui.nav.Routes
import ir.chidari.ui.nav.Tab
import ir.chidari.ui.screens.CompareScreen
import ir.chidari.ui.screens.FavoritesScreen
import ir.chidari.ui.screens.HomeScreen
import ir.chidari.ui.screens.LocationScreen
import ir.chidari.ui.screens.ProductDetailScreen
import ir.chidari.ui.screens.ProductEditorScreen
import ir.chidari.ui.screens.ProductsScreen
import ir.chidari.ui.screens.StoreDetailScreen
import ir.chidari.ui.screens.StoreEditorScreen
import ir.chidari.ui.theme.ChiDariTheme
import ir.chidari.util.Fa
import ir.chidari.data.auth.AuthState
import ir.chidari.ui.screens.AuthScreen
import ir.chidari.ui.components.ImagePickerSheet
import ir.chidari.ui.screens.BarcodeScannerScreen
import ir.chidari.ui.screens.ImageCropScreen
import ir.chidari.ui.vm.ProductImageState
import ir.chidari.ui.screens.ProfileScreen
import ir.chidari.ui.vm.AuthViewModel
import ir.chidari.ui.vm.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as ChiDariApp
            val settings by app.settings.settings.collectAsStateWithLifecycle(
                initialValue = ir.chidari.data.prefs.AppSettings()
            )
            val dark = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // تنظیمات نمایشی را روی قالب‌بندی سراسری اعمال می‌کنیم
            Fa.usePersianDigits = settings.persianDigits
            Fa.useCompactPrices = settings.compactPrices
            Fa.showDiscountBadges = settings.showDiscountBadges
            Fa.hapticFeedback = settings.hapticFeedback
            Fa.confirmBeforeCall = settings.confirmBeforeCall
            Fa.showDistanceOnCards = settings.showDistanceOnCards
            Fa.showRatings = settings.showRatings
            Fa.loadImages = settings.loadImages
            Fa.distanceUnitMode = when (settings.distanceUnit) {
                ir.chidari.data.prefs.DistanceUnit.KM -> 1
                ir.chidari.data.prefs.DistanceUnit.METER -> 2
                else -> 0
            }

            ChiDariTheme(darkTheme = dark, fontScale = settings.fontScale.scale) {
                // کل رابط کاربری راست‌به‌چپ
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ChiDariRoot()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChiDariRoot() {
    val vm: MainViewModel = viewModel(factory = MainViewModel.Factory)
    val authVm: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
    val navController = rememberNavController()
    val snackbarHost = remember { SnackbarHostState() }

    val location by vm.location.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val offerStats by vm.offerStats.collectAsStateWithLifecycle()
    val favoriteStores by vm.favoriteStores.collectAsStateWithLifecycle()
    val favoriteProducts by vm.favoriteProducts.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val appSettings by vm.settings.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    fun closeDrawer() = scope.launch { drawerState.close() }
    val nearest by vm.nearestStore.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val myStores by vm.myStores.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val permissionTrigger by vm.requestPermission.collectAsStateWithLifecycle()
    val authState by authVm.authState.collectAsStateWithLifecycle()
    val authScreenState by authVm.state.collectAsStateWithLifecycle()
    val followedIds by vm.followedIds.collectAsStateWithLifecycle()
    val followedStores by vm.followedStores.collectAsStateWithLifecycle()

    val currentUser = (authState as? AuthState.SignedIn)?.user

    // شناسه کاربر را به لایه داده می‌دهیم تا علاقه‌مندی و فالو به حساب او گره بخورد
    LaunchedEffect(currentUser?.id) {
        vm.setUserId(currentUser?.id.orEmpty())
    }

    /** اجرای کاری که نیاز به ورود دارد؛ در غیر این صورت صفحه ورود باز می‌شود. */
    val requireAuth: (() -> Boolean) -> Unit = { action ->
        if (!action()) {
            vm.showMessage("برای این کار باید وارد حساب خود شوید")
            navController.navigate(Routes.AUTH)
        }
    }

    var showFilters by remember { mutableStateOf(false) }
    // بارکد خوانده‌شده که به فرم محصول برگردانده می‌شود
    var scannedBarcode by remember { mutableStateOf("") }
    // تصویر آماده‌شده که به فرم محصول برمی‌گردد
    var processedImage by remember { mutableStateOf("") }
    var showImageSheet by remember { mutableStateOf(false) }
    var cameraTarget by remember { mutableStateOf<android.net.Uri?>(null) }
    val imageState by vm.imageState.collectAsStateWithLifecycle()
    // یک‌بار محاسبه می‌شود نه در هر رسم
    val ownedIds = remember(myStores) { myStores.map { it.id }.toSet() }
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    // تب شروع فقط یک‌بار در ابتدای اجرا اعمال می‌شود
    var startTabApplied by remember { mutableStateOf(false) }
    LaunchedEffect(appSettings.startTab) {
        if (!startTabApplied) {
            currentTab = when (appSettings.startTab) {
                ir.chidari.data.prefs.StartTab.HOME -> Tab.HOME
                ir.chidari.data.prefs.StartTab.PRODUCTS -> Tab.PRODUCTS
                ir.chidari.data.prefs.StartTab.FAVORITES -> Tab.FAVORITES
                ir.chidari.data.prefs.StartTab.PROFILE -> Tab.PROFILE
            }
            startTabApplied = true
        }
    }

    // تشخیص خودکار موقعیت هنگام اجرا، در صورت فعال بودن
    var autoLocateDone by remember { mutableStateOf(false) }
    LaunchedEffect(appSettings.autoDetectLocation, location.onboarded) {
        if (appSettings.autoDetectLocation && location.onboarded && !autoLocateDone) {
            autoLocateDone = true
            vm.requestGpsLocation()
        }
    }

    // درخواست مجوز موقعیت
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        vm.onPermissionResult(granted.values.any { it })
    }

    LaunchedEffect(permissionTrigger) {
        if (permissionTrigger > 0) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it.text)
            vm.clearMessage()
        }
    }

    // پنجره به‌روزرسانی روی هر صفحه‌ای که باشیم نمایش داده می‌شود
    UpdateDialog(
        state = updateState,
        currentVersion = BuildConfig.VERSION_NAME,
        onDownload = vm::downloadUpdate,
        onInstall = vm::installUpdate,
        onGrantPermission = { vm.grantInstallPermission() },
        onOpenBrowser = { vm.openReleasesPage() },
        onRetry = { vm.checkForUpdate() },
        onDismiss = { vm.dismissUpdate() }
    )

    // بررسی بی‌صدا هنگام اجرا: فقط اگر نسخه تازه‌ای باشد پنجره باز می‌شود
    LaunchedEffect(Unit) { vm.checkForUpdate(silent = true) }

    // انتخاب از گالری
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.prepareCrop(it) } }

    // گرفتن عکس با دوربین
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) cameraTarget?.let { vm.prepareCrop(it) } }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val f = vm.newCameraFile()
            val u = androidx.core.content.FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", f
            )
            cameraTarget = u
            cameraLauncher.launch(u)
        } else vm.showMessage("بدون اجازه دوربین، امکان عکس گرفتن نیست")
    }

    if (showImageSheet) {
        ImagePickerSheet(
            hasImage = processedImage.isNotBlank(),
            onGallery = { galleryLauncher.launch("image/*") },
            onCamera = {
                cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
            },
            onRemove = {
                vm.deleteImageFile(processedImage)
                processedImage = ""
            },
            onDismiss = { showImageSheet = false }
        )
    }

    // صفحه برش وقتی تصویری انتخاب شده باشد
    val cropState = imageState
    if (cropState is ProductImageState.Cropping || cropState is ProductImageState.Processing ||
        cropState is ProductImageState.Loading
    ) {
        val c = cropState as? ProductImageState.Cropping
        ImageCropScreen(
            bitmap = c?.preview,
            sourceWidth = c?.sourceWidth ?: 1,
            sourceHeight = c?.sourceHeight ?: 1,
            busy = cropState is ProductImageState.Processing,
            onConfirm = { rect -> vm.cropAndCompress(rect) { processedImage = it } },
            onBack = { vm.cancelImage() }
        )
        return
    }
    if (cropState is ProductImageState.Failed) {
        LaunchedEffect(cropState) {
            vm.showMessage(cropState.message)
            vm.cancelImage()
        }
    }

    val startDestination = if (location.onboarded) Routes.MAIN else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDestination) {

        // ----- صفحه خوش‌آمد / انتخاب موقعیت اولیه -----
        composable(Routes.ONBOARDING) {
            LocationScreen(
                isOnboarding = true,
                currentProvince = location.province,
                currentCity = location.city,
                locating = locating,
                onUseGps = { vm.requestGpsLocation() },
                onSelect = { p, c ->
                    vm.selectManualLocation(p, c)
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onWholeCountry = {
                    vm.selectWholeCountry()
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onBack = {}
            )
            // پس از تشخیص موفق GPS، وارد صفحه اصلی می‌شویم
            LaunchedEffect(location.onboarded) {
                if (location.onboarded) {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            }
        }

        // ----- صفحه اصلی با نوار پایین -----
        composable(Routes.MAIN) {
          ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                AppDrawer(
                    user = currentUser,
                    settings = appSettings,
                    locationLabel = location.label,
                    appVersion = BuildConfig.VERSION_NAME,
                    onChangeCity = { closeDrawer(); navController.navigate(Routes.LOCATION) },
                    onUseGps = { closeDrawer(); vm.requestGpsLocation() },
                    onThemeChange = vm::setTheme,
                    onFontScaleChange = vm::setFontScale,
                    onCompactPricesChange = vm::setCompactPrices,
                    onPersianDigitsChange = vm::setPersianDigits,
                    onDiscountBadgesChange = vm::setDiscountBadges,
                    onListDensityChange = vm::setListDensity,
                    onShowDistanceChange = vm::setShowDistance,
                    onShowRatingsChange = vm::setShowRatings,
                    onUnitChange = vm::setDistanceUnit,
                    onDefaultSortChange = vm::setDefaultSort,
                    onSearchRadiusChange = vm::setSearchRadius,
                    onStartTabChange = vm::setStartTab,
                    onOnlyAvailableChange = vm::setOnlyAvailableDefault,
                    onSearchHistoryChange = vm::setSaveSearchHistory,
                    onHideEmptyStoresChange = vm::setHideEmptyStores,
                    onAutoDetectLocationChange = vm::setAutoDetectLocation,
                    onAutoSyncChange = vm::setAutoSync,
                    onWifiOnlyChange = vm::setSyncOnWifiOnly,
                    onLoadImagesChange = vm::setLoadImages,
                    onConfirmDeleteChange = vm::setConfirmBeforeDelete,
                    onHapticChange = vm::setHapticFeedback,
                    onConfirmCallChange = vm::setConfirmBeforeCall,
                    onBackBehaviorChange = vm::setBackBehavior,
                    onKeepScreenOnChange = vm::setKeepScreenOn,
                    onNotifyProductsChange = vm::setNotifyNewProducts,
                    onNotifyDiscountsChange = vm::setNotifyDiscounts,
                    onNotifyNewStoresChange = vm::setNotifyNewStores,
                    onLocationHistoryChange = vm::setStoreLocationHistory,
                    onPublicProfileChange = vm::setPublicProfile,
                    onRefreshData = { closeDrawer(); vm.refresh() },
                    onClearCache = { closeDrawer(); vm.clearCache() },
                    onResetSettings = { vm.resetSettings() },
                    onShareApp = {
                        closeDrawer()
                        ctx.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "«چی داری؟» — فروشگاه‌ها و خدمات نزدیک خودت را پیدا کن و قیمت‌ها را مقایسه کن."
                                    )
                                }, "معرفی برنامه"
                            )
                        )
                    },
                    onHelp = { closeDrawer(); vm.showMessage("راهنما: با انتخاب شهر یا GPS، نزدیک‌ترین فروشگاه‌ها را ببینید.") },
                    onAbout = { closeDrawer(); vm.showMessage("چی داری؟ نسخه ${BuildConfig.VERSION_NAME}") },
                    onCheckUpdate = { closeDrawer(); vm.checkForUpdate() },
                    onSignIn = { closeDrawer(); navController.navigate(Routes.AUTH) },
                    onSignOut = { closeDrawer(); authVm.signOut() }
                )
            }
          ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHost) },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(currentTab.label, fontSize = 16.sp) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "منو")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                },
                bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Text(tab.emoji, fontSize = 19.sp) },
                                label = { Text(tab.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (currentTab) {
                        Tab.HOME -> HomeScreen(
                            location = location,
                            filters = filters,
                            stores = stores,
                            nearestStore = nearest,
                            favorites = favorites,
                            locating = locating,
                            onQueryChange = vm::setQuery,
                            onCategoryChange = vm::setStoreCategory,
                            onSortChange = vm::setSort,
                            onOpenLocation = { navController.navigate(Routes.LOCATION) },
                            onUseGps = { vm.requestGpsLocation() },
                            onOpenFilters = { showFilters = true },
                            onStoreClick = { navController.navigate(Routes.store(it)) },
                            onToggleFavorite = { key -> requireAuth { vm.toggleFavorite(key) } },
                            syncStatus = syncStatus,
                            onRetrySync = { vm.refresh() },
                            ownedIds = ownedIds
                        )

                        Tab.PRODUCTS -> ProductsScreen(
                            locationLabel = location.label,
                            filters = filters,
                            products = products,
                            offerStats = offerStats,
                            favorites = favorites,
                            onQueryChange = vm::setQuery,
                            onCategoryChange = vm::setProductCategory,
                            onSortChange = vm::setSort,
                            onOnlyAvailableChange = vm::setOnlyAvailable,
                            onOpenFilters = { showFilters = true },
                            onProductClick = { navController.navigate(Routes.product(it)) },
                            onCompareClick = { navController.navigate(Routes.compare(it)) },
                            onToggleFavorite = { key -> requireAuth { vm.toggleFavorite(key) } }
                        )

                        Tab.FAVORITES -> {
                            FavoritesScreen(
                                favoriteStores = favoriteStores,
                                favoriteProducts = favoriteProducts,
                                favorites = favorites,
                                isSignedIn = currentUser != null,
                                onSignInClick = { navController.navigate(Routes.AUTH) },
                                onStoreClick = { navController.navigate(Routes.store(it)) },
                                onProductClick = { navController.navigate(Routes.product(it)) },
                                onToggleFavorite = { key -> requireAuth { vm.toggleFavorite(key) } }
                            )
                        }

                        Tab.PROFILE -> ProfileScreen(
                            user = currentUser,
                            myStores = myStores,
                            followedStores = followedStores,
                            onSignInClick = { navController.navigate(Routes.AUTH) },
                            onCreateStore = {
                                if (currentUser == null) {
                                    vm.showMessage("برای ساخت فروشگاه ابتدا وارد شوید")
                                    navController.navigate(Routes.AUTH)
                                } else navController.navigate(Routes.storeEditor())
                            },
                            onOpenStore = { navController.navigate(Routes.store(it)) },
                            onEditStore = { navController.navigate(Routes.storeEditor(it)) },
                            onAddProduct = { navController.navigate(Routes.productEditor(it)) }
                        )
                    }
                }
            }

            if (showFilters) {
                FiltersSheet(
                    filters = filters,
                    locationLabel = location.label,
                    hasCoordinates = location.hasCoordinates,
                    onSortChange = vm::setSort,
                    onMaxDistanceChange = vm::setMaxDistance,
                    onOnlyAvailableChange = vm::setOnlyAvailable,
                    onIgnoreCityChange = vm::setIgnoreCityFilter,
                    onReset = { vm.resetFilters() },
                    onDismiss = { showFilters = false }
                )
            }
          }
        }

        // ----- انتخاب موقعیت از داخل برنامه -----
        composable(Routes.LOCATION) {
            LocationScreen(
                isOnboarding = false,
                currentProvince = location.province,
                currentCity = location.city,
                locating = locating,
                onUseGps = { vm.requestGpsLocation() },
                onSelect = { p, c ->
                    vm.selectManualLocation(p, c)
                    navController.popBackStack()
                },
                onWholeCountry = {
                    vm.selectWholeCountry()
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ----- صفحه فروشگاه -----
        composable(
            Routes.STORE,
            arguments = listOf(navArgument("storeId") { type = NavType.LongType })
        ) { entry ->
            val storeId = entry.arguments?.getLong("storeId") ?: 0L
            val storeFlow = remember(storeId) { vm.storeFlow(storeId) }
            val productsFlow = remember(storeId) { vm.productsOf(storeId) }
            val store by storeFlow.collectAsState(initial = null)
            val storeProducts by productsFlow.collectAsState(initial = emptyList())

            val followerFlow = remember(storeId) { vm.followerCount(storeId) }
            val followerCount by followerFlow.collectAsStateWithLifecycle()

            // ردیف فروشگاه (شامل شمارنده دنبال‌کننده) و محصولاتش را از سرور تازه می‌کنیم
            LaunchedEffect(storeId, currentUser?.id) { vm.syncStore(storeId) }

            StoreDetailScreen(
                store = store,
                products = storeProducts,
                isFavorite = favorites.contains("store:$storeId"),
                isFollowing = followedIds.contains(storeId),
                isOwner = store?.ownerId?.isNotBlank() == true && store?.ownerId == currentUser?.id,
                followerCount = followerCount,
                onToggleFollow = { requireAuth { vm.toggleFollow(storeId) } },
                userLat = location.lat,
                userLng = location.lng,
                onBack = { navController.popBackStack() },
                onToggleFavorite = { requireAuth { vm.toggleFavorite("store:$storeId") } },
                onProductClick = { navController.navigate(Routes.product(it)) },
                onAddProduct = { navController.navigate(Routes.productEditor(storeId)) },
                onEditProduct = { navController.navigate(Routes.productEditor(storeId, it)) },
                onEditStore = { navController.navigate(Routes.storeEditor(storeId)) },
                onDeleteProduct = vm::deleteProduct,
                onRate = { score -> requireAuth { vm.rateStore(storeId, score) } }
            )
        }

        // ----- جزئیات محصول -----
        composable(
            Routes.PRODUCT,
            arguments = listOf(navArgument("productId") { type = NavType.LongType })
        ) { entry ->
            val productId = entry.arguments?.getLong("productId") ?: 0L
            // جریان‌ها با remember نگه داشته می‌شوند تا در هر رسم دوباره ساخته نشوند
            val productFlow = remember(productId) { vm.productFlow(productId) }
            val product by productFlow.collectAsState(initial = null)

            // برای گرفتن اطلاعات فروشگاه، از فهرست ارائه‌ها استفاده می‌کنیم
            val title = product?.title ?: ""
            val offersFlow = remember(title) {
                vm.offersFor(if (title.isBlank()) "__none__" else title)
            }
            val allOffers by offersFlow.collectAsStateWithLifecycle()

            val current: ProductOffer? =
                remember(allOffers, productId) { allOffers.firstOrNull { it.product.id == productId } }
            val others =
                remember(allOffers, productId) { allOffers.filter { it.product.id != productId } }

            ProductDetailScreen(
                offer = current,
                otherOffers = others,
                isFavorite = favorites.contains("product:$productId"),
                onBack = { navController.popBackStack() },
                onToggleFavorite = { requireAuth { vm.toggleFavorite("product:$productId") } },
                onStoreClick = { navController.navigate(Routes.store(it)) },
                onOfferClick = { navController.navigate(Routes.product(it)) },
                onCompareAll = { navController.navigate(Routes.compare(it)) }
            )
        }

        // ----- مقایسه -----
        composable(
            Routes.COMPARE,
            arguments = listOf(navArgument("title") { type = NavType.StringType })
        ) { entry ->
            val title = entry.arguments?.getString("title").orEmpty()
            val compareFlow = remember(title) { vm.offersFor(title) }
            val offers by compareFlow.collectAsStateWithLifecycle()

            CompareScreen(
                title = title,
                offers = offers,
                onBack = { navController.popBackStack() },
                onOfferClick = { navController.navigate(Routes.product(it)) },
                onStoreClick = { navController.navigate(Routes.store(it)) }
            )
        }

        // ----- اسکن بارکد -----
        composable(Routes.SCANNER) {
            BarcodeScannerScreen(
                onResult = { code ->
                    scannedBarcode = code
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ----- ورود و ثبت‌نام -----
        composable(Routes.AUTH) {
            // به محض ورود موفق، به صفحه قبلی برمی‌گردیم
            LaunchedEffect(authState) {
                if (authState is AuthState.SignedIn) navController.popBackStack()
            }
            AuthScreen(
                state = authScreenState,
                isConfigured = authVm.isConfigured,
                onSignIn = authVm::signIn,
                onSignUp = authVm::signUp,
                onResetPassword = authVm::resetPassword,
                onResendConfirmation = authVm::resendConfirmation,
                onVerifyCode = authVm::verifyCode,
                onVerifyRecoveryCode = authVm::verifyRecoveryCode,
                onSubmitNewPassword = authVm::submitNewPassword,
                onResendRecoveryCode = authVm::resendRecoveryCode,
                onCancelRecovery = authVm::cancelRecovery,
                onModeChange = authVm::setMode,
                onDismissConfirmation = authVm::dismissConfirmation,
                onBack = { navController.popBackStack() }
            )
        }

        // ----- ویرایشگر فروشگاه -----
        composable(
            Routes.STORE_EDITOR,
            arguments = listOf(navArgument("storeId") {
                type = NavType.LongType; defaultValue = -1L
            })
        ) { entry ->
            val storeId = entry.arguments?.getLong("storeId") ?: -1L
            var existing by remember { mutableStateOf<StoreEntity?>(null) }
            var loaded by remember { mutableStateOf(storeId <= 0L) }

            LaunchedEffect(storeId) {
                if (storeId > 0L) {
                    existing = vm.loadStore(storeId)
                    loaded = true
                }
            }

            if (loaded) {
                StoreEditorScreen(
                    existing = existing,
                    defaultProvince = location.province,
                    defaultCity = location.city,
                    userLat = location.lat.takeIf { location.useGps },
                    userLng = location.lng.takeIf { location.useGps },
                    locating = locating,
                    onUseGps = { vm.requestGpsLocation() },
                    onSave = { store ->
                        vm.saveStore(store) { newId ->
                            navController.popBackStack()
                            if (storeId <= 0L) navController.navigate(Routes.store(newId))
                        }
                    },
                    onDelete = { store ->
                        vm.deleteStore(store)
                        navController.popBackStack(Routes.MAIN, inclusive = false)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ----- ویرایشگر محصول -----
        composable(
            Routes.PRODUCT_EDITOR,
            arguments = listOf(
                navArgument("storeId") { type = NavType.LongType },
                navArgument("productId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { entry ->
            val storeId = entry.arguments?.getLong("storeId") ?: 0L
            val productId = entry.arguments?.getLong("productId") ?: -1L
            var existing by remember { mutableStateOf<ProductEntity?>(null) }
            var storeCategory by remember { mutableStateOf("") }
            var loaded by remember { mutableStateOf(false) }

            LaunchedEffect(storeId, productId) {
                // تصویر محصول قبلی نباید به این فرم نشت کند
                processedImage = ""
                storeCategory = vm.loadStore(storeId)?.category.orEmpty()
                if (productId > 0L) existing = vm.loadProduct(productId)
                loaded = true
            }

            if (loaded) {
                ProductEditorScreen(
                    existing = existing,
                    storeId = storeId,
                    storeCategory = storeCategory,
                    scannedBarcode = scannedBarcode,
                    onScanBarcode = { navController.navigate(Routes.SCANNER) },
                    processedImagePath = processedImage,
                    onPickImage = { showImageSheet = true },
                    onRemoveImage = { path -> vm.deleteImageFile(path); processedImage = "" },
                    onSave = { product ->
                        vm.saveProduct(product) { navController.popBackStack() }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
