package ir.chidari.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.chidari.ChiDariApp
import ir.chidari.data.IranGeo
import ir.chidari.data.local.ProductEntity
import ir.chidari.data.local.ProductWithStore
import ir.chidari.data.local.StoreEntity
import ir.chidari.data.local.TitleStats
import ir.chidari.data.prefs.AppSettings
import ir.chidari.data.prefs.BackBehavior
import ir.chidari.data.prefs.DefaultSort
import ir.chidari.data.prefs.ListDensity
import ir.chidari.data.prefs.FontScale
import ir.chidari.data.prefs.SearchRadius
import ir.chidari.data.prefs.StartTab
import ir.chidari.data.prefs.DistanceUnit
import ir.chidari.data.prefs.ThemeMode
import ir.chidari.data.prefs.UserLocation
import ir.chidari.data.repo.ProductOffer
import ir.chidari.data.repo.SortMode
import ir.chidari.data.repo.StoreWithDistance
import ir.chidari.data.image.ImageResult
import ir.chidari.data.remote.SyncResult
import ir.chidari.data.update.DownloadState
import ir.chidari.data.update.UpdateInfo
import ir.chidari.data.update.UpdateResult
import ir.chidari.ui.components.UpdateUiState
import kotlinx.coroutines.delay
import ir.chidari.data.remote.SyncStatus
import ir.chidari.location.LocationResult
import ir.chidari.util.Fa
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** وضعیت فیلترهای جست‌وجو. */
data class FilterState(
    val query: String = "",
    val storeCategory: String = "",
    val productCategory: String = "",
    val sort: SortMode = SortMode.NEAREST,
    val onlyAvailable: Boolean = false,
    val maxDistanceKm: Float = 0f,     // ۰ یعنی بدون محدودیت
    val ignoreCityFilter: Boolean = false // «همه ایران» موقت
)

/** پیام‌های گذرا برای نمایش در Snackbar. */
data class UiMessage(val text: String, val id: Long = System.currentTimeMillis())

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as ChiDariApp
    private val repo = application.repository
    private val prefs = application.prefs
    private val locationProvider = application.locationProvider
    private val sync = application.syncManager
    private val settingsRepo = application.settings
    private val updater = application.updateChecker
    private val images = application.imageProcessor

    /**
     * حافظه‌ی جریان‌های وابسته به کلید.
     *
     * بدون این کش، هر بار رسم صفحه یک StateFlow تازه ساخته می‌شد که با
     * مقدار خالی شروع می‌کرد؛ نتیجه‌اش چشمک زدن صفحه و حلقه‌ی بی‌پایان
     * رسم بود (هر رسم → جریان نو → داده نو → رسم دوباره).
     */
    private val offersCache = mutableMapOf<String, StateFlow<List<ProductOffer>>>()
    private val followerCache = mutableMapOf<Long, StateFlow<Int>>()


    // ---------- وضعیت پایه ----------

    val location: StateFlow<UserLocation> = prefs.location
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserLocation())

    val sellerMode: StateFlow<Boolean> = prefs.sellerMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _filters = MutableStateFlow(FilterState())
    val filters: StateFlow<FilterState> = _filters.asStateFlow()

    /**
     * جریان فیلترها برای کوئری‌های پایگاه داده.
     * تایپ کردن در کادر جست‌وجو ۲۵۰ میلی‌ثانیه تأخیر می‌گیرد تا با هر حرف
     * یک کوئری کامل روی پایگاه داده اجرا نشود؛ بقیه فیلترها بی‌درنگ اعمال می‌شوند.
     */
    private val debouncedFilters: Flow<FilterState> = _filters
        .debounce { if (it.query.isBlank()) 0L else 250L }
        .distinctUntilChanged()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    /** تنظیمات کاربر (تم، واحد فاصله، همگام‌سازی و …). */
    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setTheme(v: ThemeMode) { viewModelScope.launch { settingsRepo.setTheme(v) } }
    fun setFontScale(v: FontScale) { viewModelScope.launch { settingsRepo.setFontScale(v) } }
    fun setCompactPrices(v: Boolean) { viewModelScope.launch { settingsRepo.setCompactPrices(v) } }
    fun setPersianDigits(v: Boolean) { viewModelScope.launch { settingsRepo.setPersianDigits(v) } }
    fun setDiscountBadges(v: Boolean) { viewModelScope.launch { settingsRepo.setDiscountBadges(v) } }
    fun setListDensity(v: ListDensity) { viewModelScope.launch { settingsRepo.setListDensity(v) } }
    fun setShowDistance(v: Boolean) { viewModelScope.launch { settingsRepo.setShowDistance(v) } }
    fun setShowRatings(v: Boolean) { viewModelScope.launch { settingsRepo.setShowRatings(v) } }

    fun setDistanceUnit(v: DistanceUnit) { viewModelScope.launch { settingsRepo.setDistanceUnit(v) } }
    fun setDefaultSort(v: DefaultSort) {
        viewModelScope.launch {
            settingsRepo.setDefaultSort(v)
            setSort(
                when (v) {
                    DefaultSort.NEAREST -> SortMode.NEAREST
                    DefaultSort.CHEAPEST -> SortMode.CHEAPEST
                    DefaultSort.TOP_RATED -> SortMode.TOP_RATED
                    DefaultSort.NEWEST -> SortMode.NEWEST
                }
            )
        }
    }
    fun setSearchRadius(v: SearchRadius) {
        viewModelScope.launch {
            settingsRepo.setSearchRadius(v)
            setMaxDistance(v.km)
        }
    }
    fun setOnlyAvailableDefault(v: Boolean) {
        viewModelScope.launch {
            settingsRepo.setOnlyAvailableByDefault(v)
            setOnlyAvailable(v)
        }
    }
    fun setStartTab(v: StartTab) { viewModelScope.launch { settingsRepo.setStartTab(v) } }
    fun setSaveSearchHistory(v: Boolean) { viewModelScope.launch { settingsRepo.setSaveSearchHistory(v) } }
    fun setHideEmptyStores(v: Boolean) { viewModelScope.launch { settingsRepo.setHideEmptyStores(v) } }

    fun setAutoDetectLocation(v: Boolean) { viewModelScope.launch { settingsRepo.setAutoDetectLocation(v) } }

    fun setAutoSync(v: Boolean) { viewModelScope.launch { settingsRepo.setAutoSync(v) } }
    fun setSyncOnWifiOnly(v: Boolean) { viewModelScope.launch { settingsRepo.setSyncOnWifiOnly(v) } }
    fun setLoadImages(v: Boolean) { viewModelScope.launch { settingsRepo.setLoadImages(v) } }

    fun setConfirmBeforeDelete(v: Boolean) { viewModelScope.launch { settingsRepo.setConfirmBeforeDelete(v) } }
    fun setHapticFeedback(v: Boolean) { viewModelScope.launch { settingsRepo.setHapticFeedback(v) } }
    fun setConfirmBeforeCall(v: Boolean) { viewModelScope.launch { settingsRepo.setConfirmBeforeCall(v) } }
    fun setBackBehavior(v: BackBehavior) { viewModelScope.launch { settingsRepo.setBackBehavior(v) } }
    fun setKeepScreenOn(v: Boolean) { viewModelScope.launch { settingsRepo.setKeepScreenOn(v) } }

    fun setNotifyNewProducts(v: Boolean) { viewModelScope.launch { settingsRepo.setNotifyNewProducts(v) } }
    fun setNotifyDiscounts(v: Boolean) { viewModelScope.launch { settingsRepo.setNotifyDiscounts(v) } }
    fun setNotifyNewStores(v: Boolean) { viewModelScope.launch { settingsRepo.setNotifyNewStores(v) } }
    fun setStoreLocationHistory(v: Boolean) { viewModelScope.launch { settingsRepo.setStoreLocationHistory(v) } }
    fun setPublicProfile(v: Boolean) { viewModelScope.launch { settingsRepo.setPublicProfile(v) } }

    fun resetSettings() {
        viewModelScope.launch {
            settingsRepo.resetAll()
            showMessage("تنظیمات به حالت پیش‌فرض بازگشت")
        }
    }

    /** پاک کردن داده‌های ذخیره‌شده محلی (حافظه پنهان). */
    fun clearCache() {
        viewModelScope.launch {
            repo.clearCachedData()
            sync.invalidate()
            val loc = location.value
            sync.syncRegion(loc.province, loc.city, _userId.value, force = true)
            showMessage("حافظه پنهان پاک شد و داده‌ها دوباره دریافت شدند")
        }
    }

    /** وضعیت همگام‌سازی با سرور، برای نوار بالای فهرست. */
    val syncStatus: StateFlow<SyncStatus> = sync.status
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncStatus.Idle)

    val isOnlineMode: Boolean get() = sync.isConfigured

    /** در حال ذخیره روی سرور (برای غیرفعال کردن دکمه‌ها). */
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    /** درخواست مجوز موقعیت از سمت UI. */
    private val _requestPermission = MutableStateFlow(0)
    val requestPermission: StateFlow<Int> = _requestPermission.asStateFlow()

    /**
     * شناسه کاربر واردشده. رشته خالی یعنی مهمان.
     * علاقه‌مندی‌ها و فالوها به این شناسه گره خورده‌اند تا هر حساب فهرست خودش را داشته باشد.
     */
    private val _userId = MutableStateFlow("")
    val userId: StateFlow<String> = _userId.asStateFlow()

    /** آیا کاربر وارد شده است؟ */
    val isSignedIn: StateFlow<Boolean> = _userId
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setUserId(id: String) {
        if (_userId.value == id) return
        _userId.value = id
        viewModelScope.launch {
            repo.refreshOwnership(id)
            sync.invalidate()
            val loc = location.value
            sync.syncRegion(loc.province, loc.city, id, force = true)
            // فروشگاه‌های خود کاربر و فالوهایش، مستقل از شهر انتخابی
            if (id.isNotBlank()) sync.syncUserData(id)
        }
    }

    init {
        // با هر تغییر استان/شهر، داده‌های آن منطقه از سرور گرفته می‌شود
        viewModelScope.launch {
            location
                .map { it.province to it.city }
                .distinctUntilChanged()
                .collect { (province, city) ->
                    sync.syncRegion(province, city, _userId.value)
                }
        }
    }

    /** به‌روزرسانی دستی (کشیدن به پایین). */
    fun refresh() {
        viewModelScope.launch {
            val loc = location.value
            if (_userId.value.isNotBlank()) sync.syncUserData(_userId.value)
            when (val r = sync.syncRegion(loc.province, loc.city, _userId.value, force = true)) {
                is SyncResult.Success ->
                    showMessage("به‌روزرسانی شد: ${Fa.number(r.storeCount.toLong())} فروشگاه")
                is SyncResult.Error -> showMessage(r.message)
                SyncResult.NotConfigured -> showMessage("حالت آفلاین — سرور تنظیم نشده است")
            }
        }
    }

    val favorites: StateFlow<Set<String>> = _userId
        .flatMapLatest { uid ->
            if (uid.isBlank()) kotlinx.coroutines.flow.flowOf(emptySet())
            else repo.observeFavorites(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** شناسه فروشگاه‌هایی که کاربر فالو کرده. */
    val followedIds: StateFlow<Set<Long>> = _userId
        .flatMapLatest { uid ->
            if (uid.isBlank()) kotlinx.coroutines.flow.flowOf(emptySet())
            else repo.observeFollowedIds(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val followedStores: StateFlow<List<StoreEntity>> = _userId
        .flatMapLatest { uid ->
            if (uid.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
            else repo.observeFollowedStores(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------- فهرست فروشگاه‌ها ----------

    val stores: StateFlow<List<StoreWithDistance>> =
        combine(location, debouncedFilters) { loc, f -> loc to f }
            .flatMapLatest { (loc, f) ->
                val province = if (f.ignoreCityFilter) "" else loc.province
                val city = if (f.ignoreCityFilter) "" else loc.city
                repo.searchStores(province, city, f.storeCategory, f.query)
                    .map { list ->
                        var withDistance = repo.attachDistance(list, loc.lat, loc.lng)
                        if (f.maxDistanceKm > 0f) {
                            withDistance = withDistance.filter {
                                (it.distanceKm ?: Double.MAX_VALUE) <= f.maxDistanceKm
                            }
                        }
                        repo.sortStores(withDistance, f.sort)
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** نزدیک‌ترین فروشگاه به کاربر (برای کارت برجسته صفحه اصلی). */
    val nearestStore: StateFlow<StoreWithDistance?> = stores
        .map { list -> list.filter { it.distanceKm != null }.minByOrNull { it.distanceKm!! } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ---------- فهرست محصولات ----------

    val products: StateFlow<List<ProductOffer>> =
        combine(location, debouncedFilters) { loc, f -> loc to f }
            .flatMapLatest { (loc, f) ->
                val province = if (f.ignoreCityFilter) "" else loc.province
                val city = if (f.ignoreCityFilter) "" else loc.city
                repo.searchProducts(province, city, f.productCategory, f.query, f.onlyAvailable)
                    .map { list ->
                        var offers = repo.attachDistanceToOffers(list, loc.lat, loc.lng)
                        if (f.maxDistanceKm > 0f) {
                            offers = offers.filter {
                                (it.distanceKm ?: Double.MAX_VALUE) <= f.maxDistanceKm
                            }
                        }
                        repo.sortOffers(offers, f.sort)
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * آمار «این کالا در چند فروشگاه هست و ارزان‌ترینش چند است» را پایگاه داده
     * یک‌جا محاسبه می‌کند؛ رابط کاربری فقط از این نگاشت می‌خواند (O(1) به‌جای O(n)).
     */
    val offerStats: StateFlow<Map<String, TitleStats>> =
        combine(location, debouncedFilters) { loc, f ->
            if (f.ignoreCityFilter) "" to "" else loc.province to loc.city
        }
            .distinctUntilChanged()
            .flatMapLatest { (province, city) ->
                repo.observeOfferStats(province, city)
                    .map { list -> list.associateBy { it.title } }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** علاقه‌مندی‌ها مستقیم از پایگاه داده خوانده می‌شوند تا به فیلتر شهر جاری وابسته نباشند. */
    val favoriteStores: StateFlow<List<StoreWithDistance>> =
        combine(
            _userId.flatMapLatest { uid ->
                if (uid.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
                else repo.observeFavoriteStores(uid)
            },
            location
        ) { list, loc -> repo.attachDistance(list, loc.lat, loc.lng) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteProducts: StateFlow<List<ProductOffer>> =
        combine(
            _userId.flatMapLatest { uid ->
                if (uid.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
                else repo.observeFavoriteProducts(uid)
            },
            location
        ) { list, loc -> repo.attachDistanceToOffers(list, loc.lat, loc.lng) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myStores: StateFlow<List<StoreEntity>> = _userId
        .flatMapLatest { uid ->
            if (uid.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
            else repo.observeMyStores(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------- عملیات فیلتر ----------

    fun setQuery(q: String) { _filters.value = _filters.value.copy(query = q) }
    fun setStoreCategory(c: String) { _filters.value = _filters.value.copy(storeCategory = c) }
    fun setProductCategory(c: String) { _filters.value = _filters.value.copy(productCategory = c) }
    fun setSort(s: SortMode) { _filters.value = _filters.value.copy(sort = s) }
    fun setOnlyAvailable(v: Boolean) { _filters.value = _filters.value.copy(onlyAvailable = v) }
    fun setMaxDistance(km: Float) { _filters.value = _filters.value.copy(maxDistanceKm = km) }
    fun setIgnoreCityFilter(v: Boolean) { _filters.value = _filters.value.copy(ignoreCityFilter = v) }
    fun resetFilters() {
        _filters.value = FilterState(sort = _filters.value.sort)
    }

    // ---------- موقعیت مکانی ----------

    fun selectManualLocation(province: String, city: String) {
        viewModelScope.launch {
            prefs.setManualLocation(province, city)
            _filters.value = _filters.value.copy(ignoreCityFilter = false)
            showMessage("موقعیت روی «$city، $province» تنظیم شد")
        }
    }

    fun selectWholeCountry() {
        viewModelScope.launch {
            prefs.clearAllFilters()
            showMessage("نمایش فروشگاه‌های سراسر کشور")
        }
    }

    /** درخواست تعیین موقعیت با GPS. اگر مجوز نباشد، از UI درخواست مجوز می‌شود. */
    fun requestGpsLocation() {
        if (!locationProvider.hasPermission()) {
            _requestPermission.value = _requestPermission.value + 1
            return
        }
        detectLocation()
    }

    /** پس از گرفتن مجوز از کاربر، از UI فراخوانی می‌شود. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) detectLocation()
        else showMessage("بدون اجازه دسترسی به موقعیت، امکان یافتن فروشگاه‌های نزدیک نیست")
    }

    fun detectLocation() {
        if (_locating.value) return
        viewModelScope.launch {
            _locating.value = true
            when (val result = locationProvider.current()) {
                is LocationResult.Success -> {
                    val nearest = IranGeo.nearestCity(result.lat, result.lng)
                    val province = nearest?.first?.name ?: ""
                    val city = nearest?.second?.name ?: ""
                    prefs.setGpsLocation(result.lat, result.lng, province, city)
                    _filters.value = _filters.value.copy(
                        sort = SortMode.NEAREST,
                        ignoreCityFilter = false
                    )
                    showMessage(
                        if (city.isNotBlank()) "موقعیت شما شناسایی شد: $city، $province"
                        else "موقعیت شما شناسایی شد"
                    )
                }
                LocationResult.PermissionDenied -> {
                    _requestPermission.value = _requestPermission.value + 1
                }
                LocationResult.GpsDisabled ->
                    showMessage("لطفاً GPS دستگاه را روشن کنید و دوباره تلاش کنید")
                is LocationResult.Failure ->
                    showMessage(result.message)
            }
            _locating.value = false
        }
    }

    // ---------- علاقه‌مندی ----------

    /** افزودن/حذف علاقه‌مندی. اگر کاربر وارد نشده باشد false برمی‌گرداند تا UI صفحه ورود را باز کند. */
    fun toggleFavorite(key: String): Boolean {
        val uid = _userId.value
        if (uid.isBlank()) return false
        viewModelScope.launch {
            repo.toggleFavorite(uid, key, favorites.value.contains(key))
        }
        return true
    }

    /**
     * فالو/آنفالو فروشگاه — **به‌روزرسانی خوش‌بینانه**.
     *
     * ابتدا وضعیت محلی (و شمارنده) بی‌درنگ تغییر می‌کند تا کاربر نتیجه را
     * فوراً ببیند، سپس درخواست به سرور می‌رود. اگر سرور خطا داد، تغییر
     * برگردانده می‌شود تا رابط کاربری دروغ نگوید.
     */
    fun toggleFollow(storeId: Long): Boolean {
        val uid = _userId.value
        if (uid.isBlank()) return false

        val wasFollowing = followedIds.value.contains(storeId)
        val delta = if (wasFollowing) -1 else 1

        viewModelScope.launch {
            // ۱) تغییر فوری محلی
            repo.toggleFollow(uid, storeId, wasFollowing)
            repo.bumpFollowerCount(storeId, delta)

            // ۲) ارسال به سرور
            sync.pushFollow(storeId, uid, !wasFollowing)
                .onSuccess {
                    showMessage(if (wasFollowing) "دنبال کردن لغو شد" else "✓ فروشگاه دنبال شد")
                    // عمداً اینجا دوباره همگام‌سازی نمی‌کنیم: مقدار محلی از قبل
                    // درست است و درخواست فوری باعث پرش عدد روی صفحه می‌شد.
                }
                .onFailure { e ->
                    // ۳) بازگرداندن تغییر در صورت خطا
                    repo.toggleFollow(uid, storeId, !wasFollowing)
                    repo.bumpFollowerCount(storeId, -delta)
                    showMessage(e.message ?: "ارتباط با سرور برقرار نشد؛ تغییر لغو شد")
                }
        }
        return true
    }

    // ---------- مدیریت فروشگاه (پنل فروشنده) ----------

    fun saveStore(store: StoreEntity, onSaved: (Long) -> Unit = {}) {
        val uid = _userId.value
        if (uid.isBlank()) {
            showMessage("برای ثبت فروشگاه ابتدا وارد شوید")
            return
        }
        viewModelScope.launch {
            _saving.value = true
            sync.pushStore(store, uid)
                .onSuccess { saved ->
                    prefs.setSellerMode(true)
                    showMessage(
                        if (store.id == 0L) "فروشگاه ثبت شد و برای همه قابل مشاهده است"
                        else "تغییرات ذخیره شد"
                    )
                    onSaved(saved.id)
                }
                .onFailure { showMessage(it.message ?: "ثبت فروشگاه ناموفق بود") }
            _saving.value = false
        }
    }

    fun deleteStore(store: StoreEntity) {
        viewModelScope.launch {
            sync.pushDeleteStore(store)
                .onSuccess { showMessage("فروشگاه حذف شد") }
                .onFailure { showMessage(it.message ?: "حذف ناموفق بود") }
        }
    }

    fun saveProduct(product: ProductEntity, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            _saving.value = true
            // تصاویر از فهرست زنده‌ی فرم گرفته می‌شوند؛ آن‌هایی که قبلاً
            // در پس‌زمینه بارگذاری شده‌اند دوباره ارسال نمی‌شوند.
            val withImages = product.copy(
                imageUri = productCoverValue(),
                images = productImagesValue()
            )
            val pending = ir.chidari.data.local.ProductImages.split(withImages.images)
                .count { !it.startsWith("http") }
            if (pending > 0) showMessage("در حال بارگذاری ${Fa.number(pending.toLong())} تصویر…")

            sync.pushProduct(withImages, _userId.value)
                .onSuccess {
                    showMessage(if (product.id == 0L) "محصول اضافه شد" else "محصول به‌روزرسانی شد")
                    clearProductImages()
                    onSaved()
                }
                .onFailure { showMessage(it.message ?: "ذخیره محصول ناموفق بود") }
            _saving.value = false
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            sync.pushDeleteProduct(product)
                .onSuccess { showMessage("محصول حذف شد") }
                .onFailure { showMessage(it.message ?: "حذف ناموفق بود") }
        }
    }

    /** امتیاز ۱ تا ۵. نیازمند ورود. */
    fun rateStore(id: Long, score: Int): Boolean {
        val uid = _userId.value
        if (uid.isBlank()) return false
        viewModelScope.launch {
            sync.pushRating(id, uid, score)
                .onSuccess {
                    showMessage("امتیاز شما ثبت شد")
                    sync.syncRegion(location.value.province, location.value.city, uid, force = true)
                }
                .onFailure { showMessage(it.message ?: "ثبت امتیاز ناموفق بود") }
        }
        return true
    }

    // ---------- جریان‌های جزئیات ----------

    fun storeFlow(id: Long) = repo.observeStore(id)

    /** تازه‌سازی محصولات یک فروشگاه هنگام باز کردن صفحه‌اش. */
    fun syncStore(storeId: Long) {
        viewModelScope.launch { sync.syncStore(storeId, _userId.value) }
    }

    /**
     * تعداد دنبال‌کنندگان یک فروشگاه — از ردیف محلی فروشگاه خوانده می‌شود
     * تا با فالو کردن، عدد بی‌درنگ تغییر کند.
     */
    fun followerCount(storeId: Long): StateFlow<Int> =
        followerCache.getOrPut(storeId) {
            repo.observeStoreFollowerCount(storeId)
                .distinctUntilChanged()  // جلوگیری از انتشار مقدار تکراری و پرش عدد
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        }
    fun productsOf(storeId: Long) = repo.observeProducts(storeId)
    fun productFlow(id: Long) = repo.observeProduct(id)

    /** ارائه‌های یک محصول در فروشگاه‌های مختلف، مرتب‌شده بر اساس قیمت، با فاصله. */
    fun offersFor(title: String): StateFlow<List<ProductOffer>> =
        offersCache.getOrPut(title) {
            combine(repo.offersFor(title), location) { list, loc ->
                repo.attachDistanceToOffers(list, loc.lat, loc.lng)
                    .sortedBy { it.product.finalPrice }
            }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    suspend fun loadStore(id: Long) = repo.getStore(id)
    suspend fun loadProduct(id: Long) = repo.getProduct(id)

    /**
     * جست‌وجوی محصولی که قبلاً با همین بارکد ثبت شده.
     * اگر فروشنده‌ی دیگری آن کالا را وارد کرده باشد، عنوان و مشخصاتش
     * برای پرکردن خودکار فرم پیشنهاد می‌شود.
     */
    suspend fun lookupBarcode(barcode: String) = repo.findByBarcode(barcode)

    // ---------- پیام‌ها ----------

    // ---------- به‌روزرسانی برنامه ----------

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    /** بررسی وجود نسخه تازه روی گیت‌هاب. */
    fun checkForUpdate(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _updateState.value = UpdateUiState.Checking
            when (val r = updater.check()) {
                is UpdateResult.Available -> {
                    // اگر قبلاً دانلود شده، مستقیم به مرحله نصب می‌رویم
                    _updateState.value =
                        if (updater.downloadedFile(r.info.versionName) != null)
                            UpdateUiState.ReadyToInstall(r.info)
                        else UpdateUiState.Available(r.info)
                }
                is UpdateResult.UpToDate ->
                    _updateState.value = if (silent) UpdateUiState.Idle else UpdateUiState.UpToDate
                is UpdateResult.Error ->
                    _updateState.value =
                        if (silent) UpdateUiState.Idle else UpdateUiState.Failed(r.message)
            }
        }
    }

    /**
     * دانلود فایل نصبی و دنبال کردن پیشرفت واقعی.
     *
     * پیشرفت از خود DownloadManager خوانده می‌شود، نه از وجود فایل —
     * چون فایل از ابتدا ساخته می‌شود و اگر نیمه‌کاره نصب شود،
     * اندروید خطای «بسته نامعتبر» می‌دهد.
     */
    fun downloadUpdate(info: UpdateInfo) {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading(0)

            val id = runCatching { updater.startDownload(info) }.getOrElse {
                _updateState.value = UpdateUiState.Failed("دانلود شروع نشد.")
                return@launch
            }

            // حداکثر ۱۰ دقیقه انتظار، هر ثانیه یک بار بررسی
            repeat(600) {
                delay(1000)
                when (val st = updater.downloadStatus(id)) {
                    is DownloadState.Done -> {
                        val f = updater.downloadedFile(info.versionName)
                        _updateState.value = if (f != null)
                            UpdateUiState.ReadyToInstall(info)
                        else
                            UpdateUiState.Failed("فایل دانلودشده پیدا نشد.")
                        return@launch
                    }
                    is DownloadState.Failed -> {
                        _updateState.value = UpdateUiState.Failed(
                            "دانلود ناموفق بود (کد ${st.reason}). اینترنت را بررسی کنید."
                        )
                        return@launch
                    }
                    is DownloadState.Running ->
                        _updateState.value = UpdateUiState.Downloading(st.percent)
                    DownloadState.Unknown -> Unit
                }
            }
            _updateState.value = UpdateUiState.Failed("دانلود بیش از حد طول کشید.")
        }
    }

    /** اجرای نصب‌کننده اندروید. */
    fun installUpdate(info: UpdateInfo) {
        if (!updater.canInstallPackages()) {
            _updateState.value = UpdateUiState.NeedsPermission(info)
            return
        }
        val f = updater.downloadedFile(info.versionName)
        if (f == null) {
            _updateState.value = UpdateUiState.Failed("فایل نصبی پیدا نشد.")
            return
        }
        runCatching { updater.installApk(f) }
            .onFailure { _updateState.value = UpdateUiState.Failed("نصب‌کننده باز نشد.") }
    }

    fun grantInstallPermission() = updater.openInstallPermissionSettings()
    fun openReleasesPage() = updater.openReleasesPage()
    fun dismissUpdate() { _updateState.value = UpdateUiState.Idle }

    // ---------- تصویر محصول ----------

    private val _imageState = MutableStateFlow<ProductImageState>(ProductImageState.Idle)
    val imageState: StateFlow<ProductImageState> = _imageState.asStateFlow()

    /**
     * بارگذاری تصویر انتخاب‌شده برای نمایش در صفحه برش.
     *
     * **نکته مهم:** حالت `Loading` بیرون از کوروتین و به‌صورت همزمان ست
     * می‌شود. `viewModelScope.launch` بدنه را روی صف حلقه‌ی پیام می‌گذارد،
     * پس اگر داخل کوروتین ست می‌شد، در لحظه‌ی `navigate` هنوز `Idle` بود و
     * صفحه برش نمی‌توانست بین «در حال بارگذاری» و «حالت کهنه» فرق بگذارد.
     */
    fun prepareCrop(uri: android.net.Uri, replaceId: Long = 0L) {
        _imageState.value = ProductImageState.Loading
        cropReplaceId = replaceId
        viewModelScope.launch {
            when (val r = images.loadForCrop(uri)) {
                is ir.chidari.data.image.ImageLoad.Failure ->
                    _imageState.value = ProductImageState.Failed(r.message)

                is ir.chidari.data.image.ImageLoad.Success -> {
                    val (w, h) = displaySize(uri)
                    _imageState.value = ProductImageState.Cropping(uri, r.bitmap, w, h)
                }
            }
        }
    }

    /**
     * ابعاد تصویر آن‌طور که کاربر می‌بیند (با احتساب چرخش EXIF).
     * صفحه برش باید با همین کار کند وگرنه برای عکس‌های چرخیده،
     * کادر به ناحیه اشتباه نگاشت می‌شود.
     */
    private fun displaySize(uri: android.net.Uri): Pair<Int, Int> =
        images.displaySize(uri) ?: (1 to 1)

    /**
     * برش و فشرده‌سازی نهایی، سپس افزودن به فهرست تصاویر محصول و شروع
     * فوری بارگذاری در پس‌زمینه (رفتار دیوار: کاربر منتظر نمی‌ماند).
     */
    fun cropAndCompress(
        rect: android.graphics.Rect,
        circular: Boolean = false,
        onDone: () -> Unit
    ) {
        val st = _imageState.value
        if (st !is ProductImageState.Cropping) return
        val replaceId = cropReplaceId
        viewModelScope.launch {
            _imageState.value = ProductImageState.Processing

            // نسخه‌ی اصلی را نگه می‌داریم تا «برش دوباره» همیشه ممکن باشد.
            // اگر همین تصویر قبلاً نسخه اصلی داشت، دوباره کپی نمی‌کنیم.
            val existing = _productImages.value.firstOrNull { it.id == replaceId }
            val originalPath = existing?.originalPath?.takeIf { java.io.File(it).exists() }
                ?: images.copyOriginal(st.source)?.absolutePath.orEmpty()

            when (val r = images.process(st.source, rect, circular)) {
                is ImageResult.Success -> {
                    _imageState.value = ProductImageState.Idle
                    showMessage(
                        "تصویر آماده شد: ${Fa.number(r.width.toLong())}×${Fa.number(r.height.toLong())} " +
                            "• ${Fa.digits(r.sizeLabel)}"
                    )
                    putCroppedImage(replaceId, r.file.absolutePath, originalPath)
                    onDone()
                }
                is ImageResult.Error -> _imageState.value = ProductImageState.Failed(r.message)
            }
        }
    }

    // ---------- فهرست تصاویر محصول در حال ویرایش ----------

    private val _productImages = MutableStateFlow<List<DraftImage>>(emptyList())
    val productImages: StateFlow<List<DraftImage>> = _productImages.asStateFlow()

    /** اگر برش برای جایگزینی یک تصویر موجود باشد، شناسه‌اش اینجاست. */
    private var cropReplaceId: Long = 0L
    private var nextDraftId: Long = 1L

    /** آیا هنوز تصویری در حال بارگذاری است؟ (برای غیرفعال کردن دکمه ذخیره) */
    val imagesUploading: StateFlow<Boolean> = _productImages
        .map { list -> list.any { it.state == UploadState.UPLOADING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** آماده‌سازی فهرست هنگام باز شدن فرم محصول. */
    /**
     * آماده‌سازی فهرست هنگام باز شدن فرم محصول.
     *
     * **باگی که رفع شد:** این تابع از یک `LaunchedEffect` در مقصد «فرم محصول»
     * صدا زده می‌شود. وقتی به صفحه برش می‌رویم، Navigation Compose ترکیب‌بندی
     * فرم را **از بین می‌برد**؛ هنگام بازگشت، فرم از نو ترکیب می‌شود و همان
     * `LaunchedEffect` دوباره اجرا می‌شود. نتیجه این بود که فهرست به تصاویرِ
     * **ذخیره‌شده‌ی** محصول برمی‌گشت — و برای محصول تازه یعنی فهرست خالی.
     * تصویری که همین الان برش خورده بود، درست در لحظه‌ی بازگشت ناپدید می‌شد.
     *
     * حالا با کلید محصول محافظت می‌شود: تا وقتی روی همان فرم هستیم، فهرست
     * دوباره مقداردهی نمی‌شود.
     *
     * @param key شناسه‌ی یکتای فرم، مثلاً «۱۲:-۱» برای محصول تازه در فروشگاه ۱۲
     */
    fun startProductImages(key: String, stored: String, cover: String) {
        if (imagesFormKey == key) return          // همان فرم، دست نزن
        imagesFormKey = key
        val urls = ir.chidari.data.local.ProductImages.of(stored, cover)
        _productImages.value = urls.map { url ->
            DraftImage(
                id = nextDraftId++,
                localPath = if (url.startsWith("http")) "" else url,
                remoteUrl = if (url.startsWith("http")) url else "",
                state = if (url.startsWith("http")) UploadState.DONE else UploadState.LOCAL_ONLY
            )
        }
    }

    /** کلید فرمی که فهرست تصاویر برایش ساخته شده. */
    private var imagesFormKey: String? = null

    /** پاک کردن فهرست هنگام بستن فرم (بدون حذف فایل‌های ذخیره‌شده). */
    fun clearProductImages() {
        _productImages.value = emptyList()
        cropReplaceId = 0L
        imagesFormKey = null
    }

    /** مقداری که در ستون `images` محصول ذخیره می‌شود. */
    fun productImagesValue(): String =
        ir.chidari.data.local.ProductImages.join(_productImages.value.map { it.storedValue })

    /** عکس اصلی = اولین تصویر. */
    fun productCoverValue(): String = _productImages.value.firstOrNull()?.storedValue.orEmpty()

    /** افزودن تصویر تازه یا جایگزینی تصویر موجود، سپس شروع بارگذاری. */
    private fun putCroppedImage(replaceId: Long, path: String, originalPath: String) {
        val current = _productImages.value
        val old = current.firstOrNull { it.id == replaceId }

        val draft = DraftImage(
            id = if (old != null) old.id else nextDraftId++,
            localPath = path,
            originalPath = originalPath,
            state = UploadState.UPLOADING
        )

        if (old != null) {
            // فایل‌های قدیمی همین جایگاه پاک می‌شوند تا فضا هدر نرود
            if (old.localPath.isNotBlank() && old.localPath != path) images.delete(old.localPath)
            if (old.originalPath.isNotBlank() && old.originalPath != originalPath) {
                images.delete(old.originalPath)
            }
            if (old.remoteUrl.isNotBlank()) deleteRemoteImage(old.remoteUrl)
            _productImages.value = current.map { if (it.id == old.id) draft else it }
        } else {
            if (current.size >= ir.chidari.data.local.ProductImages.MAX) {
                showMessage("حداکثر ${Fa.number(ir.chidari.data.local.ProductImages.MAX.toLong())} تصویر مجاز است")
                images.delete(path)
                images.delete(originalPath)
                return
            }
            _productImages.value = current + draft
        }
        uploadDraft(draft.id)
    }

    /** بارگذاری یک تصویر در پس‌زمینه با گزارش پیشرفت. */
    private fun uploadDraft(id: Long) {
        val draft = _productImages.value.firstOrNull { it.id == id } ?: return
        val uid = _userId.value

        if (!sync.isRemoteConfigured || uid.isBlank()) {
            // حالت آفلاین یا مهمان: تصویر روی گوشی می‌ماند و هنگام ذخیره ارسال می‌شود
            updateDraft(id) { it.copy(state = UploadState.LOCAL_ONLY, progress = 1f) }
            return
        }

        viewModelScope.launch {
            updateDraft(id) { it.copy(state = UploadState.UPLOADING, progress = 0f, error = "") }
            val file = java.io.File(draft.localPath)
            sync.uploadImage(file, uid) { p ->
                updateDraft(id) { it.copy(progress = p) }
            }.onSuccess { url ->
                updateDraft(id) { it.copy(remoteUrl = url, state = UploadState.DONE, progress = 1f) }
            }.onFailure { e ->
                updateDraft(id) {
                    it.copy(state = UploadState.FAILED, error = e.message ?: "بارگذاری ناموفق بود")
                }
            }
        }
    }

    /** تلاش دوباره برای تصویری که بارگذاری‌اش شکست خورده. */
    fun retryImageUpload(id: Long) = uploadDraft(id)

    private fun updateDraft(id: Long, transform: (DraftImage) -> DraftImage) {
        _productImages.value = _productImages.value.map { if (it.id == id) transform(it) else it }
    }

    /** حذف یک تصویر: از فهرست، از گوشی و از سرور. */
    fun removeProductImage(id: Long) {
        val draft = _productImages.value.firstOrNull { it.id == id } ?: return
        _productImages.value = _productImages.value.filterNot { it.id == id }
        if (draft.localPath.isNotBlank()) images.delete(draft.localPath)
        if (draft.originalPath.isNotBlank()) images.delete(draft.originalPath)
        if (draft.remoteUrl.isNotBlank()) deleteRemoteImage(draft.remoteUrl)
        showMessage("تصویر حذف شد")
    }

    private fun deleteRemoteImage(url: String) {
        viewModelScope.launch { sync.deleteImage(url) }
    }

    /** جابه‌جایی تصویر یک جایگاه به چپ یا راست. */
    fun moveProductImage(id: Long, delta: Int) {
        val list = _productImages.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        _productImages.value = list
    }

    /** بردن یک تصویر به جایگاه اول (عکس اصلی). */
    fun makeCoverImage(id: Long) {
        val list = _productImages.value
        val item = list.firstOrNull { it.id == id } ?: return
        if (list.firstOrNull()?.id == id) return
        _productImages.value = listOf(item) + list.filterNot { it.id == id }
        showMessage("به‌عنوان عکس اصلی تنظیم شد")
    }

    /** نشانی فایل اصلی برای «برش دوباره». */
    fun originalUriOf(id: Long): android.net.Uri? {
        val d = _productImages.value.firstOrNull { it.id == id } ?: return null
        val f = java.io.File(d.originalPath)
        if (!f.exists()) return null
        return android.net.Uri.fromFile(f)
    }

    fun cancelImage() { _imageState.value = ProductImageState.Idle }

    fun deleteImageFile(path: String) = images.delete(path)

    /** فایل موقت برای عکس دوربین. */
    fun newCameraFile(): java.io.File = images.newCameraFile()

    fun showMessage(text: String) { _message.value = UiMessage(text) }
    fun clearMessage() { _message.value = null }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras
            ): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                return MainViewModel(app) as T
            }
        }
    }
}
