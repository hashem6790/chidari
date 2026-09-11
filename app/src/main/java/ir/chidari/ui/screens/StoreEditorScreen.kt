package ir.chidari.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.data.Categories
import ir.chidari.data.IranGeo
import ir.chidari.data.local.StoreEntity
import ir.chidari.util.Fa

private val emojiChoices = listOf(
    "🏪", "🛒", "🍎", "🥖", "🥩", "🍽️", "☕", "👕", "🔌", "📱", "💊", "📚",
    "🔧", "🛠️", "🚗", "💄", "💇", "🌸", "⚽", "🧸", "🎓", "🏠", "🧁", "🥗"
)

/** فرم ثبت/ویرایش فروشگاه توسط فروشنده. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreEditorScreen(
    existing: StoreEntity?,
    defaultProvince: String,
    defaultCity: String,
    userLat: Double?,
    userLng: Double?,
    locating: Boolean,
    onUseGps: () -> Unit,
    onSave: (StoreEntity) -> Unit,
    onDelete: (StoreEntity) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: Categories.names.first()) }
    var province by remember { mutableStateOf(existing?.province ?: defaultProvince.ifBlank { IranGeo.provinceNames.first() }) }
    var city by remember {
        mutableStateOf(
            existing?.city ?: defaultCity.ifBlank { IranGeo.cityNamesOf(province).firstOrNull() ?: "" }
        )
    }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var bio by remember { mutableStateOf(existing?.bio ?: "") }
    var hours by remember { mutableStateOf(existing?.openHours ?: "") }
    var instagram by remember { mutableStateOf(existing?.instagram ?: "") }
    var emoji by remember { mutableStateOf(existing?.coverEmoji ?: "🏪") }
    var lat by remember { mutableStateOf(existing?.lat) }
    var lng by remember { mutableStateOf(existing?.lng) }
    var showDelete by remember { mutableStateOf(false) }
    var attemptedSave by remember { mutableStateOf(false) }

    val nameError = attemptedSave && name.isBlank()
    val cityError = attemptedSave && city.isBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "ثبت فروشگاه جدید" else "ویرایش فروشگاه") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف فروشگاه")
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
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("اطلاعات پایه", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام فروشگاه *") },
                    placeholder = { Text("مثلاً سوپرمارکت بهار") },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("نام فروشگاه الزامی است") } } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                DropdownField(
                    label = "دسته‌بندی فروشگاه *",
                    value = category,
                    options = Categories.names,
                    display = { "${Categories.emojiOf(it)} $it" },
                    onSelect = {
                        category = it
                        emoji = Categories.emojiOf(it)
                    }
                )
            }

            item {
                Column {
                    Text("آیکون فروشگاه", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(emojiChoices) { e ->
                            Card(
                                onClick = { emoji = e },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (emoji == e) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(e, fontSize = 24.sp, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("معرفی فروشگاه (بیو)") },
                    placeholder = { Text("در چند خط بگویید چه چیزی ارائه می‌دهید و چه مزیتی دارید") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("موقعیت مکانی", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            item {
                DropdownField(
                    label = "استان *",
                    value = province,
                    options = IranGeo.provinceNames,
                    onSelect = {
                        province = it
                        city = IranGeo.cityNamesOf(it).firstOrNull() ?: ""
                    }
                )
            }

            item {
                DropdownField(
                    label = "شهر *",
                    value = city,
                    options = IranGeo.cityNamesOf(province),
                    isError = cityError,
                    onSelect = { city = it }
                )
            }

            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("نشانی دقیق") },
                    placeholder = { Text("خیابان، کوچه، پلاک") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "مختصات GPS فروشگاه",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (lat != null && lng != null)
                                "ثبت‌شده: ${Fa.digits(String.format("%.5f", lat))} , ${Fa.digits(String.format("%.5f", lng))}"
                            else "ثبت نشده — اگر ثبت نکنید، مرکز شهر انتخابی استفاده می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (userLat != null && userLng != null) {
                                        lat = userLat; lng = userLng
                                    } else onUseGps()
                                },
                                enabled = !locating,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (locating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Filled.MyLocation, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("ثبت موقعیت فعلی", maxLines = 1)
                                }
                            }
                            if (lat != null) {
                                OutlinedButton(
                                    onClick = { lat = null; lng = null },
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("پاک کردن") }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("راه‌های ارتباطی", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = Fa.toLatinDigits(it).filter { c -> c.isDigit() } },
                    label = { Text("شماره تماس") },
                    placeholder = { Text("مثلاً 02133445566") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text("ساعت کاری") },
                    placeholder = { Text("مثلاً ۹ صبح تا ۹ شب") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = instagram,
                    onValueChange = { instagram = it },
                    label = { Text("اینستاگرام") },
                    placeholder = { Text("@my_shop") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        attemptedSave = true
                        if (name.isBlank() || city.isBlank()) return@Button
                        val center = IranGeo.findCity(province, city)
                        val finalLat = lat ?: center?.lat ?: 35.6892
                        val finalLng = lng ?: center?.lng ?: 51.3890
                        onSave(
                            (existing ?: StoreEntity(
                                name = "", category = "", province = "", city = "",
                                lat = 0.0, lng = 0.0
                            )).copy(
                                name = name.trim(),
                                category = category,
                                province = province,
                                city = city,
                                address = address.trim(),
                                phone = phone.trim(),
                                bio = bio.trim(),
                                openHours = hours.trim(),
                                instagram = instagram.trim(),
                                coverEmoji = emoji,
                                lat = finalLat,
                                lng = finalLng,
                                isOwnedByMe = true
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (existing == null) "ثبت فروشگاه" else "ذخیره تغییرات",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("حذف فروشگاه") },
            text = { Text("با حذف «${existing.name}» تمام محصولات آن هم حذف می‌شوند. مطمئنید؟") },
            confirmButton = {
                Button(onClick = {
                    showDelete = false
                    onDelete(existing)
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("انصراف") } }
        )
    }
}

/** فیلد انتخابی کشویی با ظاهر Material 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    display: (String) -> String = { it },
    isError: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = if (value.isBlank()) "" else display(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = isError,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
