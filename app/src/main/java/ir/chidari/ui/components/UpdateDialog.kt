package ir.chidari.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.data.update.UpdateInfo
import ir.chidari.util.Fa

/** مرحله‌ای که جریان به‌روزرسانی در آن قرار دارد. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val progress: Int) : UpdateUiState
    data class ReadyToInstall(val info: UpdateInfo) : UpdateUiState
    data class NeedsPermission(val info: UpdateInfo) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

/**
 * پنجره به‌روزرسانی برنامه.
 *
 * چون اپ در گوگل‌پلی نیست، نسخه‌های تازه از GitHub Releases گرفته می‌شوند.
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    currentVersion: String,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (UpdateInfo) -> Unit,
    onGrantPermission: () -> Unit,
    onOpenBrowser: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state is UpdateUiState.Idle) return

    AlertDialog(
        onDismissRequest = {
            // در میانه دانلود، پنجره بسته نشود
            if (state !is UpdateUiState.Downloading) onDismiss()
        },
        title = {
            Text(
                when (state) {
                    is UpdateUiState.Checking -> "بررسی به‌روزرسانی"
                    is UpdateUiState.UpToDate -> "به‌روز هستید"
                    is UpdateUiState.Available -> "نسخه تازه موجود است"
                    is UpdateUiState.Downloading -> "در حال دانلود"
                    is UpdateUiState.ReadyToInstall -> "آماده نصب"
                    is UpdateUiState.NeedsPermission -> "اجازه نصب لازم است"
                    is UpdateUiState.Failed -> "بررسی ناموفق"
                    else -> ""
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (state) {
                    is UpdateUiState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp), strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("در حال ارتباط با گیت‌هاب…")
                        }
                    }

                    is UpdateUiState.UpToDate -> {
                        Text("🎉", fontSize = 34.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("جدیدترین نسخه را دارید: ${Fa.digits(currentVersion)}")
                    }

                    is UpdateUiState.Available -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⬆️", fontSize = 26.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "نسخه ${Fa.digits(state.info.versionName)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "نسخه فعلی شما: ${Fa.digits(currentVersion)}" +
                                        if (state.info.sizeLabel.isNotBlank())
                                            " • ${Fa.digits(state.info.sizeLabel)}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (state.info.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    state.info.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    is UpdateUiState.Downloading -> {
                        Text("در حال دریافت فایل نصبی…")
                        Spacer(Modifier.height(12.dp))
                        if (state.progress > 0) {
                            LinearProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                Fa.percent(state.progress),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }

                    is UpdateUiState.ReadyToInstall -> {
                        Text("فایل دانلود شد. برای نصب ادامه دهید.")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "داده‌های شما پاک نمی‌شود — نسخه تازه روی همین نسخه نصب می‌گردد.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is UpdateUiState.NeedsPermission -> {
                        Text(
                            "برای نصب به‌روزرسانی، اندروید نیاز دارد اجازه «نصب برنامه‌های ناشناس» " +
                                "را به «چی داری؟» بدهید."
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "پس از دادن اجازه، به برنامه برگردید و دوباره «نصب» را بزنید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is UpdateUiState.Failed -> {
                        Text(state.message)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "می‌توانید نسخه تازه را مستقیم از گیت‌هاب بگیرید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateUiState.Available ->
                    Button(onClick = { onDownload(state.info) }) { Text("دانلود و نصب") }

                is UpdateUiState.ReadyToInstall ->
                    Button(onClick = { onInstall(state.info) }) { Text("نصب") }

                is UpdateUiState.NeedsPermission ->
                    Button(onClick = onGrantPermission) { Text("باز کردن تنظیمات") }

                is UpdateUiState.Failed ->
                    Button(onClick = onRetry) { Text("تلاش دوباره") }

                is UpdateUiState.UpToDate ->
                    Button(onClick = onDismiss) { Text("باشه") }

                else -> Unit
            }
        },
        dismissButton = {
            when (state) {
                is UpdateUiState.Downloading -> Unit
                is UpdateUiState.UpToDate -> Unit
                is UpdateUiState.Failed ->
                    TextButton(onClick = onOpenBrowser) { Text("باز کردن گیت‌هاب") }
                else ->
                    TextButton(onClick = onDismiss) { Text("بعداً") }
            }
        }
    )
}
