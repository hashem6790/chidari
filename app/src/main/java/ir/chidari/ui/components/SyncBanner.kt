package ir.chidari.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.chidari.data.remote.SyncStatus

/**
 * نوار باریک بالای فهرست که وضعیت ارتباط با سرور را نشان می‌دهد.
 * در حالت عادی (Idle یا Done) چیزی نمایش داده نمی‌شود تا صفحه شلوغ نشود.
 */
@Composable
fun SyncBanner(
    status: SyncStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = status is SyncStatus.Running ||
            status is SyncStatus.Failed ||
            status is SyncStatus.SchemaMissing

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        val (bg, fg, text) = when (status) {
            is SyncStatus.Running -> Triple(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
                "در حال دریافت فروشگاه‌ها از سرور…"
            )
            is SyncStatus.SchemaMissing -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                "جدول‌های سرور ساخته نشده‌اند — فایل supabase-schema.sql را اجرا کنید"
            )
            is SyncStatus.Failed -> Triple(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                status.message.ifBlank { "ارتباط با سرور برقرار نشد — داده‌های ذخیره‌شده نمایش داده می‌شوند" }
            )
            else -> Triple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                ""
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status is SyncStatus.Running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = fg
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (status is SyncStatus.Failed) {
                TextButton(onClick = onRetry) {
                    Text("تلاش دوباره", style = MaterialTheme.typography.labelMedium, color = fg)
                }
            }
        }
    }
}
