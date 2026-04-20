package com.photovault.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val Bg = Color(0xFF05080D)
private val KeypadSurface = Color(0xFF131C29)
private val KeypadStroke = Color(0xFF243348)
private val Blue = Color(0xFF4A9EFF)
private val TextMain = Color(0xFFEAF1FF)
private val TextSub = Color(0xFF7E90AB)
private val Error = Color(0xFFFF4372)
private val Success = Color(0xFF21C277)

@Composable
fun LockScreen(
    onUnlockSuccess: () -> Unit,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    if (state.unlockSuccess) {
        viewModel.consumeUnlockEvent()
        onUnlockSuccess()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中...", color = TextSub)
            }
        } else if (state.success) {
            LockSuccessContent(onContinue = onUnlockSuccess)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                state.stepLabel?.let {
                    Text(
                        text = "安全设置  $it",
                        color = TextSub,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )
                }
                Text(
                    text = state.title,
                    color = if (state.stage == LockStage.SETUP_CONFIRM_ERROR) Error else TextMain,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    text = state.subtitle,
                    color = TextSub,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                PinDots(
                    length = state.enteredPin.length,
                    error = state.stage == LockStage.SETUP_CONFIRM_ERROR || state.error != null,
                    modifier = Modifier.padding(top = 20.dp),
                )

                state.helper?.let {
                    Text(text = it, color = Success, modifier = Modifier.padding(top = 12.dp))
                }
                state.error?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .border(1.dp, Error, RoundedCornerShape(12.dp))
                            .background(Color(0x33FF4372), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(text = it, color = Error)
                    }
                }

                NumberPad(
                    modifier = Modifier.padding(top = 20.dp),
                    showBiometric = !state.isSetup,
                    onNumber = viewModel::onNumber,
                    onDelete = viewModel::deleteLast,
                )

                if (state.stage == LockStage.SETUP_CONFIRM_ERROR) {
                    Button(
                        onClick = viewModel::resetSetup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A202C)),
                    ) {
                        Text("重新设置 PIN 码", color = Error)
                    }
                }
            }
        }
    }
}

@Composable
private fun LockSuccessContent(
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 80.dp)
                .size(112.dp)
                .background(Color(0x3321C277), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = Success, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "PIN 码设置成功",
            color = TextMain,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text("您的 PIN 码已安全加密存储在本设备", color = TextSub, modifier = Modifier.padding(top = 10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp)
                .border(1.dp, KeypadStroke, RoundedCornerShape(16.dp))
                .background(Color(0xFF0E1622), RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text("PIN 安全须知", color = TextMain, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = KeypadStroke)
            Text("• PIN 码仅存储在您的设备本地，不会上传至云端", color = TextSub)
            Text("• 忘记 PIN 码时，可通过主密码重置", color = TextSub, modifier = Modifier.padding(top = 8.dp))
            Text("• 连续 5 次错误后账户将临时锁定", color = TextSub, modifier = Modifier.padding(top = 8.dp))
        }
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue),
        ) {
            Text("开始使用 VaultSafe", color = Color.White, fontSize = 20.sp)
        }
    }
}

@Composable
private fun PinDots(
    length: Int,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(6) { idx ->
            val active = idx < length
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .border(2.dp, if (error) Error else Blue, CircleShape)
                    .background(
                        color = if (active) {
                            if (error) Error else Blue
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun NumberPad(
    modifier: Modifier = Modifier,
    showBiometric: Boolean,
    onNumber: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { n ->
                    KeypadKey(label = n.toString(), onClick = { onNumber(n) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showBiometric) {
                KeypadKey(label = "🆔", onClick = {})
            } else {
                Box(modifier = Modifier.size(86.dp))
            }
            KeypadKey(label = "0", onClick = { onNumber(0) })
            KeypadKey(label = "⌫", onClick = onDelete)
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(86.dp)
            .background(KeypadSurface, CircleShape)
            .border(1.dp, KeypadStroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextMain, fontSize = 34.sp)
    }
}
