package ir.chidari.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.chidari.data.repo.SortMode
import ir.chidari.ui.vm.FilterState
import ir.chidari.util.Fa

/** برگه پایینی فیلترهای پیشرفته. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersSheet(
    filters: FilterState,
    locationLabel: String,
    hasCoordinates: Boolean,
    onSortChange: (SortMode) -> Unit,
    onMaxDistanceChange: (Float) -> Unit,
    onOnlyAvailableChange: (Boolean) -> Unit,
    onIgnoreCityChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("فیلترها و مرتب‌سازی", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Text("مرتب‌سازی بر اساس", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            ChipRow(
                items = SortMode.entries.map { it.label },
                selected = filters.sort.label,
                onSelect = { label ->
                    SortMode.entries.firstOrNull { it.label == label }?.let(onSortChange)
                },
                allLabel = "پیش‌فرض",
                modifier = Modifier.padding(horizontal = (-20).dp)
            )

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("حداکثر فاصله", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (filters.maxDistanceKm <= 0f) "بدون محدودیت"
                    else "تا ${Fa.number(filters.maxDistanceKm.toLong())} کیلومتر",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = filters.maxDistanceKm,
                onValueChange = onMaxDistanceChange,
                valueRange = 0f..50f,
                steps = 9,
                enabled = hasCoordinates
            )
            if (!hasCoordinates) {
                Text(
                    "برای فیلتر فاصله، ابتدا موقعیت خود را با GPS مشخص کنید.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("فقط موارد موجود", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "کالاهای ناموجود نمایش داده نشوند",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = filters.onlyAvailable, onCheckedChange = onOnlyAvailableChange)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("جست‌وجو در سراسر ایران", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "نادیده گرفتن محدودیت «$locationLabel»",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = filters.ignoreCityFilter, onCheckedChange = onIgnoreCityChange)
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("پاک کردن فیلترها") }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("نمایش نتایج") }
            }
        }
    }
}
