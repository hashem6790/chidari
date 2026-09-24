package ir.chidari.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * برگه انتخاب منبع تصویر محصول.
 * گالری، دوربین و در صورت وجود تصویر فعلی، گزینه حذف.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePickerSheet(
    hasImage: Boolean,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "تصویر محصول",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "تصویر به‌صورت خودکار بهینه می‌شود (حداکثر ۱ مگابایت)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            SourceRow("🖼️", "انتخاب از گالری", "از تصاویر ذخیره‌شده گوشی") {
                onGallery(); onDismiss()
            }
            SourceRow("📷", "گرفتن عکس", "با دوربین گوشی") {
                onCamera(); onDismiss()
            }

            if (hasImage) {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                SourceRow(
                    emoji = "🗑️",
                    title = "حذف تصویر",
                    subtitle = null,
                    danger = true
                ) { onRemove(); onDismiss() }
            }
        }
    }
}

@Composable
private fun SourceRow(
    emoji: String,
    title: String,
    subtitle: String?,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 24.sp, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text("‹", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
