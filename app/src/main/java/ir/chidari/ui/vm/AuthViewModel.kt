package ir.chidari.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.chidari.ChiDariApp
import ir.chidari.data.auth.AuthResult
import ir.chidari.data.auth.AuthState
import ir.chidari.data.auth.RecoveryResult
import ir.chidari.data.auth.SupabaseConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** فرم فعال در صفحه ورود. */
enum class AuthMode { SIGN_IN, SIGN_UP, RESET }

/** مرحله جاری در جریان بازیابی رمز. */
enum class RecoveryStage {
    /** هنوز وارد جریان نشده‌ایم. */
    NONE,
    /** کد به ایمیل ارسال شد؛ منتظر وارد کردن کد هستیم. */
    CODE_SENT,
    /** کد تأیید شد؛ حالا رمز جدید گرفته می‌شود. */
    NEW_PASSWORD
}

/** وضعیت صفحه احراز هویت. */
data class AuthScreenState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    /** ایمیلی که منتظر تأیید ثبت‌نام است. */
    val awaitingConfirmation: String? = null,
    /** مرحله بازیابی رمز. */
    val recoveryStage: RecoveryStage = RecoveryStage.NONE,
    /** ایمیلی که در حال بازیابی رمزش هستیم. */
    val recoveryEmail: String = ""
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val client = (app as ChiDariApp).authClient

    private val _state = MutableStateFlow(AuthScreenState())
    val state: StateFlow<AuthScreenState> = _state.asStateFlow()

    /** نشست موقت بازیابی — عمداً در وضعیت UI نگه داشته نمی‌شود. */
    private var recoveryToken: String? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isConfigured: Boolean get() = SupabaseConfig.isConfigured

    init {
        // بازیابی نشست ذخیره‌شده هنگام اجرای برنامه
        viewModelScope.launch {
            val user = client.restoreSession()
            _authState.value = if (user != null) AuthState.SignedIn(user) else AuthState.Guest
        }
    }

    fun setMode(mode: AuthMode) {
        recoveryToken = null
        _state.value = AuthScreenState(mode = mode)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    fun dismissConfirmation() {
        _state.value = _state.value.copy(awaitingConfirmation = null, mode = AuthMode.SIGN_IN)
    }

    // ---------- اعتبارسنجی سمت کلاینت ----------

    private fun validate(email: String, password: String?, name: String? = null): String? = when {
        email.isBlank() -> "ایمیل را وارد کنید."
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
            "قالب ایمیل درست نیست."
        name != null && name.isBlank() -> "نام خود را وارد کنید."
        password != null && password.length < 6 -> "رمز عبور باید حداقل ۶ نویسه باشد."
        else -> null
    }

    // ---------- عملیات ----------

    fun signIn(email: String, password: String) {
        validate(email, password)?.let {
            _state.value = _state.value.copy(error = it); return
        }
        run(client = { client.signIn(email, password) })
    }

    fun signUp(name: String, email: String, password: String) {
        validate(email, password, name)?.let {
            _state.value = _state.value.copy(error = it); return
        }
        run(client = { client.signUp(email, password, name) })
    }

    /** گام ۰: ارسال کد بازیابی به ایمیل. */
    fun resetPassword(email: String) {
        validate(email, null)?.let {
            _state.value = _state.value.copy(error = it); return
        }
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, info = null)
            when (val r = client.resetPassword(email)) {
                is AuthResult.Message -> _state.value = AuthScreenState(
                    mode = AuthMode.RESET,
                    recoveryStage = RecoveryStage.CODE_SENT,
                    recoveryEmail = email.trim(),
                    info = "کد بازیابی به ایمیل شما ارسال شد."
                )
                is AuthResult.Error -> _state.value =
                    _state.value.copy(busy = false, error = r.message)
                else -> _state.value = _state.value.copy(busy = false)
            }
        }
    }

    /** گام ۱: تأیید کد بازیابی و گرفتن نشست موقت. */
    fun verifyRecoveryCode(code: String) {
        val email = _state.value.recoveryEmail
        if (email.isBlank()) return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, info = null)
            when (val r = client.verifyRecoveryCode(email, code)) {
                is RecoveryResult.Verified -> {
                    recoveryToken = r.recoveryToken
                    _state.value = _state.value.copy(
                        busy = false,
                        recoveryStage = RecoveryStage.NEW_PASSWORD,
                        info = null
                    )
                }
                is RecoveryResult.Error -> _state.value =
                    _state.value.copy(busy = false, error = r.message)
            }
        }
    }

    /** گام ۲: ثبت رمز جدید. در صورت موفقیت کاربر مستقیم وارد می‌شود. */
    fun submitNewPassword(newPassword: String, repeat: String) {
        val token = recoveryToken
        if (token == null) {
            _state.value = _state.value.copy(
                error = "مهلت بازیابی تمام شد. از ابتدا دوباره تلاش کنید.",
                recoveryStage = RecoveryStage.NONE
            )
            return
        }
        if (newPassword.length < 6) {
            _state.value = _state.value.copy(error = "رمز عبور باید حداقل ۶ نویسه باشد.")
            return
        }
        if (newPassword != repeat) {
            _state.value = _state.value.copy(error = "دو رمز واردشده یکسان نیستند.")
            return
        }
        if (_state.value.busy) return

        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, info = null)
            when (val r = client.updatePassword(token, newPassword)) {
                is AuthResult.Success -> {
                    recoveryToken = null
                    _authState.value = AuthState.SignedIn(r.user)
                    _state.value = AuthScreenState()
                }
                is AuthResult.Error -> _state.value =
                    _state.value.copy(busy = false, error = r.message)
                else -> _state.value = _state.value.copy(busy = false)
            }
        }
    }

    /** ارسال دوباره کد بازیابی. */
    fun resendRecoveryCode() {
        val email = _state.value.recoveryEmail
        if (email.isBlank() || _state.value.busy) return
        run(client = { client.resetPassword(email) }, keepConfirmation = true)
    }

    /** خروج از جریان بازیابی. */
    fun cancelRecovery() {
        recoveryToken = null
        _state.value = AuthScreenState(mode = AuthMode.SIGN_IN)
    }

    /** تأیید حساب با کد ۶ رقمی ایمیل. */
    fun verifyCode(code: String) {
        val email = _state.value.awaitingConfirmation ?: return
        run(client = { client.verifyOtp(email, code) }, keepConfirmation = true)
    }

    fun resendConfirmation() {
        val email = _state.value.awaitingConfirmation ?: return
        run(client = { client.resendConfirmation(email) }, keepConfirmation = true)
    }

    private fun run(client: suspend () -> AuthResult, keepConfirmation: Boolean = false) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, info = null)
            when (val result = client()) {
                is AuthResult.Success -> {
                    _authState.value = AuthState.SignedIn(result.user)
                    _state.value = AuthScreenState()
                }
                is AuthResult.NeedsEmailConfirmation -> {
                    _state.value = AuthScreenState(awaitingConfirmation = result.email)
                }
                is AuthResult.Message -> {
                    val cur = _state.value
                    _state.value = cur.copy(
                        busy = false,
                        info = result.text,
                        awaitingConfirmation =
                            if (keepConfirmation) cur.awaitingConfirmation else null,
                        recoveryStage =
                            if (keepConfirmation) cur.recoveryStage else RecoveryStage.NONE
                    )
                }
                is AuthResult.Error -> {
                    _state.value = _state.value.copy(busy = false, error = result.message)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            client.signOut()
            _authState.value = AuthState.Guest
            _state.value = AuthScreenState()
        }
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: androidx.lifecycle.viewmodel.CreationExtras
            ): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                return AuthViewModel(app) as T
            }
        }
    }
}
