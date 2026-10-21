package io.github.Calvariaa.oshwhub.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.Calvariaa.oshwhub.data.SessionManager
import io.github.Calvariaa.oshwhub.data.model.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginState(
    val account: String = "",
    val password: String = "",
    val phone: String = "",
    val smsCode: String = "",
    val mode: Int = 0, // 0=密码登录 1=验证码登录
    val busy: Boolean = false,
    val error: String? = null,
    val smsHint: String? = null,
)

class LoginViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun setAccount(v: String) { _state.value = _state.value.copy(account = v, error = null) }
    fun setPassword(v: String) { _state.value = _state.value.copy(password = v, error = null) }
    fun setPhone(v: String) { _state.value = _state.value.copy(phone = v, error = null) }
    fun setCode(v: String) { _state.value = _state.value.copy(smsCode = v, error = null) }
    fun setMode(mode: Int) { _state.value = _state.value.copy(mode = mode, error = null) }

    fun login(onSuccess: (UserInfo) -> Unit) {
        val s = _state.value
        if (s.busy) return
        viewModelScope.launch {
            _state.value = s.copy(busy = true, error = null)
            val result = runCatching {
                if (s.mode == 0) {
                    if (s.account.isBlank() || s.password.isBlank()) error("请输入账号与密码")
                    session.loginByPassword(s.account.trim(), s.password)
                } else {
                    if (s.phone.isBlank() || s.smsCode.isBlank()) error("请输入手机号与验证码")
                    session.loginBySms(s.phone.trim(), s.smsCode.trim())
                }
            }
            result.onSuccess { user ->
                _state.value = _state.value.copy(busy = false)
                onSuccess(user)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    busy = false,
                    error = e.message ?: "登录失败，请稍后再试",
                )
            }
        }
    }

    fun sendSmsCode() {
        val phone = _state.value.phone.trim()
        if (phone.isBlank()) {
            _state.value = _state.value.copy(error = "请先输入手机号")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, smsHint = null)
            runCatching { session.sendSmsCode(phone) }
                .onSuccess { hint ->
                    _state.value = _state.value.copy(busy = false, smsHint = hint)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(busy = false, error = e.message ?: "验证码发送失败")
                }
        }
    }
}

@Composable
fun LoginScreen(
    vm: LoginViewModel,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    onWebLogin: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("登录", style = MaterialTheme.typography.titleMedium)
        }

        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(
                "立创开源硬件平台",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "使用立创集团账号（立创EDA / 嘉立创 / 立创商城）登录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.mode == 0,
                    onClick = { vm.setMode(0) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("账号密码") }
                SegmentedButton(
                    selected = state.mode == 1,
                    onClick = { vm.setMode(1) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("手机验证码") }
            }

            Spacer(Modifier.height(16.dp))

            if (state.mode == 0) {
                OutlinedTextField(
                    value = state.account,
                    onValueChange = vm::setAccount,
                    label = { Text("手机号 / 邮箱 / 用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = vm::setPassword,
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                "切换密码可见",
                            )
                        }
                    },
                )
            } else {
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = vm::setPhone,
                    label = { Text("手机号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.smsCode,
                        onValueChange = vm::setCode,
                        label = { Text("短信验证码") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = vm::sendSmsCode, enabled = !state.busy) { Text("获取验证码") }
                }
                state.smsHint?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.login { onLoggedIn() } },
                enabled = !state.busy,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (state.mode == 0) "登录" else "登录 / 注册")
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onWebLogin,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text("用浏览器登录（安全验证 / 手机验证码）")
            }

            if (state.error?.contains("风险") == true || state.error?.contains("验证") == true) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "接口侧触发了风控环境校验（检测到当前环境有风险）。点上面的“用浏览器登录”，在真实浏览器内核里完成验证码/滑块后，应用会自动接管会话。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "提示：登录请求走 passport.jlc.com 的 CAS 流程，账号密码使用 SM2 加密后提交，本应用不会保存你的密码，只保存登录后的会话 Cookie。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
