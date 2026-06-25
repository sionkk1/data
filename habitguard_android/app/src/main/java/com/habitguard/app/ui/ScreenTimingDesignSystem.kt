package com.habitguard.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ScreenTimingColors {
    val Blue = Color(0xFF1E40AF)
    val BlueSoft = Color(0xFFDCE7FF)
    val Green = Color(0xFF147A73)
    val GreenSoft = Color(0xFFDDF4EA)
    val Amber = Color(0xFFB7791F)
    val AmberSoft = Color(0xFFFFF0C2)
    val Rose = Color(0xFFB42318)
    val RoseSoft = Color(0xFFFFE4E0)
    val Ink = Color(0xFF172033)
    val Muted = Color(0xFF5B6475)
    val Hairline = Color(0xFFDDE3EC)
    val Canvas = Color(0xFFF7F9FC)
    val DarkCanvas = Color(0xFF101521)
    val DarkSurface = Color(0xFF182131)
}

object ScreenTimingSpacing {
    val XSmall = 6.dp
    val Small = 10.dp
    val Medium = 14.dp
    val Large = 18.dp
    val XLarge = 24.dp
    val MinTouch = 48.dp
    val CardRadius = 8.dp
}

object ScreenTimingUiCopy {
    data class PermissionItem(
        val title: String,
        val statusLabel: String,
        val body: String,
        val actionLabel: String,
        val contentDescription: String,
    )

    data class StateMessage(
        val title: String,
        val body: String,
        val actionLabel: String,
    )

    data class RiskPresentation(
        val title: String,
        val body: String,
        val iconText: String,
        val iconDescription: String,
        val tone: Tone,
    )

    enum class Tone {
        Good,
        Notice,
        Warning,
        Neutral,
    }

    fun permissionItems(
        hasUsageAccess: Boolean,
        hasAccessibilityAccess: Boolean,
        hasNotificationAccess: Boolean,
    ): List<PermissionItem> =
        listOf(
            PermissionItem(
                title = "사용 기록 접근",
                statusLabel = if (hasUsageAccess) "켜짐" else "필요",
                body = "앱별 사용 시간과 실행 횟수를 측정합니다. 화면 내용이나 입력 텍스트는 읽지 않습니다.",
                actionLabel = "사용 기록 설정",
                contentDescription = "사용 기록 접근 권한 ${if (hasUsageAccess) "켜짐" else "필요"}",
            ),
            PermissionItem(
                title = "접근성 서비스",
                statusLabel = if (hasAccessibilityAccess) "켜짐" else "필요",
                body = "승인된 제한 규칙의 대상 앱 실행만 감지합니다. 창 내용, 입력 텍스트, 알림 본문은 저장하지 않습니다.",
                actionLabel = "접근성 설정",
                contentDescription = "접근성 서비스 ${if (hasAccessibilityAccess) "켜짐" else "필요"}",
            ),
            PermissionItem(
                title = "알림 접근",
                statusLabel = if (hasNotificationAccess) "켜짐" else "선택",
                body = "알림 본문이 아니라 앱별 알림 개수만 분석에 반영합니다.",
                actionLabel = "알림 접근 설정",
                contentDescription = "알림 접근 권한 ${if (hasNotificationAccess) "켜짐" else "선택"}",
            ),
        )

    fun predictionReadiness(
        personalizedPredictionReady: Boolean,
        collectedCompleteDays: Int,
        predictedTomorrowMinutes: Long,
    ): StateMessage =
        if (personalizedPredictionReady) {
            StateMessage(
                title = "AI 예측 준비됨",
                body = "최근 사용 기록을 바탕으로 내일 예상 사용 시간은 ${formatScreenTimingMinutes(predictedTomorrowMinutes)}입니다.",
                actionLabel = "예측 이유 보기",
            )
        } else {
            StateMessage(
                title = "측정값 ${collectedCompleteDays}일 수집됨",
                body = "개인화 예측은 완전한 사용 기록이 7일 이상 쌓인 뒤 표시합니다. 지금은 가짜 예측값을 보여주지 않습니다.",
                actionLabel = "사용 기록 계속 수집",
            )
        }

    fun riskPresentation(rawRiskLabel: String): RiskPresentation {
        val normalized = rawRiskLabel.lowercase()
        return when {
            "high" in normalized || "높" in rawRiskLabel || "위험" in rawRiskLabel -> RiskPresentation(
                title = "주의 필요",
                body = "최근 측정값에서 사용량이나 야간 사용이 높게 나타났습니다. 색상만으로 구분하지 않도록 텍스트와 아이콘을 함께 표시합니다.",
                iconText = "!",
                iconDescription = "상승 아이콘",
                tone = Tone.Warning,
            )
            "medium" in normalized || "보통" in rawRiskLabel || "중간" in rawRiskLabel -> RiskPresentation(
                title = "살펴보기",
                body = "일부 시간대에서 사용이 늘었습니다. 목표와 실제 측정값을 함께 확인해 보세요.",
                iconText = "~",
                iconDescription = "변동 아이콘",
                tone = Tone.Notice,
            )
            else -> RiskPresentation(
                title = "안정적",
                body = "현재 측정값 기준으로 큰 변화는 보이지 않습니다. 계속 기록을 모아 개인 기준을 정교하게 만듭니다.",
                iconText = "✓",
                iconDescription = "안정 아이콘",
                tone = Tone.Good,
            )
        }
    }
}

@Composable
fun ScreenTimingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkScreenTimingScheme() else lightScreenTimingScheme(),
        typography = MaterialTheme.typography,
        content = content,
    )
}

@Composable
fun ScreenTimingCard(
    modifier: Modifier = Modifier,
    tone: ScreenTimingUiCopy.Tone = ScreenTimingUiCopy.Tone.Neutral,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.then(
            if (contentDescription == null) {
                Modifier
            } else {
                Modifier.semantics { this.contentDescription = contentDescription }
            },
        ),
        shape = RoundedCornerShape(ScreenTimingSpacing.CardRadius),
        border = BorderStroke(1.dp, colors.outline),
        colors = CardDefaults.cardColors(containerColor = toneContainer(tone, colors)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

@Composable
fun ScreenTimingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ScreenTimingSpacing.MinTouch)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(ScreenTimingSpacing.CardRadius),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ScreenTimingSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ScreenTimingSpacing.MinTouch)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(ScreenTimingSpacing.CardRadius),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ScreenTimingStatusBadge(
    text: String,
    tone: ScreenTimingUiCopy.Tone,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 32.dp),
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = toneContainer(tone, MaterialTheme.colorScheme)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = toneContent(tone, MaterialTheme.colorScheme),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun ScreenTimingMetricCard(
    label: String,
    value: String,
    sourceLabel: String,
    modifier: Modifier = Modifier,
) {
    ScreenTimingCard(
        modifier = modifier,
        contentDescription = "$label, $value, $sourceLabel",
    ) {
        Column(
            modifier = Modifier.padding(ScreenTimingSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.XSmall),
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(sourceLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ScreenTimingButtonRow(content: @Composable RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small),
        content = content,
    )
}

fun formatScreenTimingMinutes(minutes: Long): String {
    val safeMinutes = minutes.coerceAtLeast(0)
    val hours = safeMinutes / 60
    val rest = safeMinutes % 60
    return if (hours > 0) "${hours}시간 ${rest}분" else "${rest}분"
}

private fun lightScreenTimingScheme(): ColorScheme =
    lightColorScheme(
        primary = ScreenTimingColors.Blue,
        onPrimary = Color.White,
        secondary = ScreenTimingColors.Green,
        onSecondary = Color.White,
        background = ScreenTimingColors.Canvas,
        onBackground = ScreenTimingColors.Ink,
        surface = Color.White,
        onSurface = ScreenTimingColors.Ink,
        surfaceVariant = Color(0xFFEAF0F8),
        onSurfaceVariant = ScreenTimingColors.Muted,
        outline = ScreenTimingColors.Hairline,
        error = ScreenTimingColors.Rose,
        onError = Color.White,
    )

private fun darkScreenTimingScheme(): ColorScheme =
    darkColorScheme(
        primary = Color(0xFF9CBcff),
        onPrimary = Color(0xFF081225),
        secondary = Color(0xFF75D6C9),
        onSecondary = Color(0xFF06221E),
        background = ScreenTimingColors.DarkCanvas,
        onBackground = Color(0xFFE8EEF8),
        surface = ScreenTimingColors.DarkSurface,
        onSurface = Color(0xFFE8EEF8),
        surfaceVariant = Color(0xFF223047),
        onSurfaceVariant = Color(0xFFB7C1D1),
        outline = Color(0xFF3A465A),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )

@Composable
private fun toneContainer(tone: ScreenTimingUiCopy.Tone, colors: ColorScheme): Color =
    when (tone) {
        ScreenTimingUiCopy.Tone.Good -> if (isSystemInDarkTheme()) Color(0xFF12322F) else ScreenTimingColors.GreenSoft
        ScreenTimingUiCopy.Tone.Notice -> if (isSystemInDarkTheme()) Color(0xFF3A2D0B) else ScreenTimingColors.AmberSoft
        ScreenTimingUiCopy.Tone.Warning -> if (isSystemInDarkTheme()) Color(0xFF3D1815) else ScreenTimingColors.RoseSoft
        ScreenTimingUiCopy.Tone.Neutral -> colors.surface
    }

@Composable
private fun toneContent(tone: ScreenTimingUiCopy.Tone, colors: ColorScheme): Color =
    when (tone) {
        ScreenTimingUiCopy.Tone.Good -> if (isSystemInDarkTheme()) Color(0xFF9DE7DA) else ScreenTimingColors.Green
        ScreenTimingUiCopy.Tone.Notice -> if (isSystemInDarkTheme()) Color(0xFFFFD26A) else ScreenTimingColors.Amber
        ScreenTimingUiCopy.Tone.Warning -> if (isSystemInDarkTheme()) Color(0xFFFFB4AB) else ScreenTimingColors.Rose
        ScreenTimingUiCopy.Tone.Neutral -> colors.onSurfaceVariant
    }
