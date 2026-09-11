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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.chidari.data.auth.AuthClient.Companion.MIN_OTP_LENGTH
import ir.chidari.util.Fa
import ir.chidari.ui.vm.AuthMode
import ir.chidari.ui.vm.RecoveryStage
import ir.chidari.ui.vm.AuthScreenState

/** بلندترین کدی که Supabase ممکن است بفرستد. */
private const val MAX_OTP_LENGTH = 10

/**
 * صفحه ثبت‌نام و ورود با ایمیل (Supabase + SMTP جیمیل).
 * سه حالت دارد: ورود، ثبت‌نام، و بازیابی رمز.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    state: AuthScreenState,
    isConfigured: Boolean,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    onResendConfirmation: () -> Unit,
    onVerifyCode: (String) -> Unit,
    onVerifyRecoveryCode: (String) -> Unit,
    onSubmitNewPassword: (String, String) -> Unit,
    onResendRecoveryCode: () -> Unit,
    onCancelRecovery: () -> Unit,
    onModeChange: (AuthMode) -> Unit,
    onDismissConfirmation: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // ---- جریان بازیابی رمز ----
    when (state.recoveryStage) {
        RecoveryStage.CODE_SENT -> {
            RecoveryCodeScreen(
                email = state.recoveryEmail,
                busy = state.busy,
                info = state.info,
                error = state.error,
                onVerify = onVerifyRecoveryCode,
                onResend = onResendRecoveryCode,
                onCancel = onCancelRecovery
            )
            return
        }
        RecoveryStage.NEW_PASSWORD -> {
            NewPasswordScreen(
                email = state.recoveryEmail,
                busy = state.busy,
                error = state.error,
                onSubmit = onSubmitNewPassword,
                onCancel = onCancelRecovery
            )
            return
        }
        RecoveryStage.NONE -> Unit
    }

    // صفحه «ایمیلت را تأیید کن»
    if (state.awaitingConfirmation != null) {
        EmailSentScreen(
            email = state.awaitingConfirmation,
            busy = state.busy,
            info = state.info,
            error = state.error,
            onVerify = onVerifyCode,
            onResend = onResendConfirmation,
            onBackToSignIn = onDismissConfirmation
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state.mode) {
                            AuthMode.SIGN_IN -> "ورود به حساب"
                            AuthMode.SIGN_UP -> "ساخت حساب کاربری"
                            AuthMode.RESET -> "بازیابی رمز عبور"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🏪", fontSize = 46.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "چی داری؟",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (state.mode) {
                            AuthMode.SIGN_IN -> "برای فالو کردن فروشگاه‌ها و ذخیره علاقه‌مندی‌ها وارد شوید"
                            AuthMode.SIGN_UP -> "با ایمیل خود ثبت‌نام کنید — رایگان و سریع"
                            AuthMode.RESET -> "ایمیل خود را وارد کنید تا لینک بازیابی بفرستیم"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // هشدار عدم پیکربندی
            if (!isConfigured) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "⚠️ اتصال به سرور تنظیم نشده",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "برای فعال شدن ثبت‌نام، مقادیر URL و ANON_KEY پروژه Supabase را در فایل " +
                                        "SupabaseConfig.kt وارد کنید. راهنمای کامل در SUPABASE_SETUP.md است.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // نام (فقط ثبت‌نام)
            if (state.mode == AuthMode.SIGN_UP) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام شما") },
                        placeholder = { Text("مثلاً علی رضایی") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        singleLine = true,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text("ایمیل") },
                    placeholder = { Text("you@gmail.com") },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (state.mode != AuthMode.RESET) {
                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("رمز عبور") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = if (showPassword) "پنهان کردن رمز" else "نمایش رمز"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        supportingText = if (state.mode == AuthMode.SIGN_UP) {
                            { Text("حداقل ۶ نویسه") }
                        } else null,
                        singleLine = true,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // پیام خطا / اطلاع
            state.error?.let { err ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            state.info?.let { info ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            "✅ $info",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        when (state.mode) {
                            AuthMode.SIGN_IN -> onSignIn(email, password)
                            AuthMode.SIGN_UP -> onSignUp(name, email, password)
                            AuthMode.RESET -> onResetPassword(email)
                        }
                    },
                    enabled = !state.busy && isConfigured,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            when (state.mode) {
                                AuthMode.SIGN_IN -> "ورود"
                                AuthMode.SIGN_UP -> "ثبت‌نام"
                                AuthMode.RESET -> "ارسال لینک بازیابی"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }

            // جابه‌جایی بین حالت‌ها
            item {
                when (state.mode) {
                    AuthMode.SIGN_IN -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("حساب ندارید؟", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onModeChange(AuthMode.SIGN_UP) }) {
                                Text("ثبت‌نام کنید")
                            }
                        }
                        TextButton(
                            onClick = { onModeChange(AuthMode.RESET) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("رمز عبور را فراموش کرده‌ام") }
                    }
                    AuthMode.SIGN_UP -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("قبلاً ثبت‌نام کرده‌اید؟", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onModeChange(AuthMode.SIGN_IN) }) {
                                Text("وارد شوید")
                            }
                        }
                    }
                    AuthMode.RESET -> {
                        TextButton(
                            onClick = { onModeChange(AuthMode.SIGN_IN) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("بازگشت به صفحه ورود") }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "بدون حساب هم می‌توانید همه فروشگاه‌ها و محصولات را ببینید. " +
                            "حساب فقط برای فالو کردن، علاقه‌مندی و ساخت فروشگاه لازم است.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * صفحه پس از ثبت‌نام: کاربر کد داخل ایمیل را اینجا وارد می‌کند.
 *
 * طول کد را سرور تعیین می‌کند (۶ یا ۸ رقم)، پس طول ثابتی فرض نمی‌شود.
 *
 * از کد عددی استفاده می‌کنیم نه لینک، چون لینک ایمیل به یک آدرس وب
 * هدایت می‌شود که روی گوشی باز نمی‌شود.
 */
@Composable
private fun EmailSentScreen(
    email: String,
    busy: Boolean,
    info: String?,
    error: String?,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onBackToSignIn: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("📬", fontSize = 58.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            "کد تأیید را وارد کنید",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "یک ایمیل حاوی کد تأیید به این نشانی فرستادیم:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            email,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { input ->
                // فقط رقم، حداکثر ۶ تا؛ ارقام فارسی هم پذیرفته می‌شود
                // طول کد بسته به تنظیمات سرور ۶ یا ۸ رقم است
                code = Fa.toLatinDigits(input).filter { it.isDigit() }.take(MAX_OTP_LENGTH)
            },
            label = { Text("کد تأیید") },
            placeholder = { Text("······") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
                letterSpacing = 5.sp,
                fontWeight = FontWeight.Bold
            ),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "کد را کامل از ایمیل کپی کنید — هر طولی باشد پذیرفته می‌شود " +
                "(${Fa.digits(MIN_OTP_LENGTH.toString())} تا ${Fa.digits(MAX_OTP_LENGTH.toString())} رقم).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        error?.let {
            Spacer(Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        info?.let {
            Spacer(Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    "✅ $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onVerify(code) },
            enabled = !busy && code.length >= MIN_OTP_LENGTH,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("تأیید و ورود", style = MaterialTheme.typography.titleSmall)
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onResend,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text("ارسال دوباره کد") }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBackToSignIn) { Text("بازگشت به صفحه ورود") }

        Spacer(Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "ایمیل را پیدا نمی‌کنید؟",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                listOf(
                    "پوشه اسپم (Spam) را بررسی کنید.",
                    "چند ثانیه صبر کنید؛ گاهی با تأخیر می‌رسد.",
                    "اگر روی لینک داخل ایمیل زدید و صفحه باز نشد، اشکالی ندارد — فقط کد را وارد کنید."
                ).forEach {
                    Text(
                        "• $it",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "نسخه ${Fa.digits(ir.chidari.BuildConfig.VERSION_NAME)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}


/** گام ۱ بازیابی: وارد کردن کد ارسال‌شده به ایمیل. */
@Composable
private fun RecoveryCodeScreen(
    email: String,
    busy: Boolean,
    info: String?,
    error: String?,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Text("🔑", fontSize = 56.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            "کد بازیابی را وارد کنید",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "کدی به این نشانی فرستادیم:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            email,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = Fa.toLatinDigits(it).filter { c -> c.isDigit() }.take(MAX_OTP_LENGTH) },
            label = { Text("کد بازیابی") },
            placeholder = { Text("······") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center,
                fontSize = 24.sp,
                letterSpacing = 5.sp,
                fontWeight = FontWeight.Bold
            ),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "کد را کامل از ایمیل کپی کنید — هر طولی باشد پذیرفته می‌شود.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        error?.let { MessageCard(it, isError = true) }
        info?.let { MessageCard(it, isError = false) }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onVerify(code) },
            enabled = !busy && code.length >= MIN_OTP_LENGTH,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("تأیید کد", style = MaterialTheme.typography.titleSmall)
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onResend,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) { Text("ارسال دوباره کد") }

        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onCancel) { Text("انصراف و بازگشت به ورود") }
        Spacer(Modifier.height(24.dp))
    }
}

/** گام ۲ بازیابی: تعیین رمز تازه. */
@Composable
private fun NewPasswordScreen(
    email: String,
    busy: Boolean,
    error: String?,
    onSubmit: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var pass1 by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    val tooShort = pass1.isNotEmpty() && pass1.length < 6
    val mismatch = pass2.isNotEmpty() && pass1 != pass2
    val valid = pass1.length >= 6 && pass1 == pass2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))
        Text("🔒", fontSize = 56.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            "رمز عبور تازه",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "کد تأیید شد. حالا برای حساب $email یک رمز تازه بگذارید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pass1,
            onValueChange = { pass1 = it },
            label = { Text("رمز جدید") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(
                        if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (show) "پنهان کردن" else "نمایش"
                    )
                }
            },
            visualTransformation = if (show) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            isError = tooShort,
            supportingText = {
                Text(if (tooShort) "حداقل ۶ نویسه لازم است" else "حداقل ۶ نویسه")
            },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pass2,
            onValueChange = { pass2 = it },
            label = { Text("تکرار رمز جدید") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            visualTransformation = if (show) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            isError = mismatch,
            supportingText = {
                if (mismatch) Text("دو رمز یکسان نیستند")
                else if (valid) Text("✓ رمزها یکسان‌اند")
            },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        error?.let { MessageCard(it, isError = true) }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onSubmit(pass1, pass2) },
            enabled = !busy && valid,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("ثبت رمز و ورود", style = MaterialTheme.typography.titleSmall)
            }
        }

        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onCancel) { Text("انصراف") }
        Spacer(Modifier.height(24.dp))
    }
}

/** کارت پیام خطا یا اطلاع، مشترک بین صفحه‌های بازیابی. */
@Composable
private fun MessageCard(text: String, isError: Boolean) {
    Spacer(Modifier.height(14.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            if (isError) text else "✅ $text",
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}
