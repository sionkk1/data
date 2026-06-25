package com.habitguard.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.habitguard.app.data.GuardEventEntity
import com.habitguard.app.data.MissionLogEntity
import com.habitguard.app.data.ServiceLocator
import com.habitguard.app.data.UnlockSessionEntity
import com.habitguard.app.guard.GuardPreferences
import com.habitguard.app.guard.MissionExitReason
import com.habitguard.app.guard.RestrictionRuleSpec
import com.habitguard.app.guard.UnlockSessionReason
import com.habitguard.app.ui.ScreenTimingCard
import com.habitguard.app.ui.ScreenTimingPrimaryButton
import com.habitguard.app.ui.ScreenTimingSecondaryButton
import com.habitguard.app.ui.ScreenTimingSpacing
import com.habitguard.app.ui.ScreenTimingStatusBadge
import com.habitguard.app.ui.ScreenTimingTheme
import com.habitguard.app.ui.ScreenTimingUiCopy
import com.habitguard.app.ui.formatScreenTimingMinutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LockActivity : ComponentActivity() {
    private var missionCompleted = false
    private var abandonLogged = false
    private var missionStartedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        missionStartedAt = System.currentTimeMillis()
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty().ifEmpty { packageName }
        val missionType = intent.getStringExtra(EXTRA_MISSION_TYPE) ?: "1분 대기 + 이유 입력"
        val unlockMinutes = intent.getIntExtra(EXTRA_UNLOCK_MINUTES, 10)
        val allowMissionUnlock = intent.getBooleanExtra(EXTRA_ALLOW_MISSION_UNLOCK, true)
        val emergencyUnlockMinutes = intent.getIntExtra(EXTRA_EMERGENCY_UNLOCK_MINUTES, 5)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    leaveWithoutUnlock(packageName, missionType, MissionExitReason.BackPressed)
                }
            },
        )

        setContent {
            ScreenTimingTheme {
                LockScreen(
                    appName = appName,
                    missionType = missionType,
                    unlockMinutes = unlockMinutes,
                    allowMissionUnlock = allowMissionUnlock,
                    emergencyUnlockMinutes = emergencyUnlockMinutes,
                    onComplete = { reason ->
                        missionCompleted = true
                        lifecycleScope.launch {
                            val session = grantUnlockSession(packageName, unlockMinutes, UnlockSessionReason.MissionSuccess)
                            ServiceLocator.database(this@LockActivity).missionLogDao().insert(
                                MissionLogEntity(
                                    targetPackage = packageName,
                                    missionType = missionType,
                                    startedAt = missionStartedAt,
                                    completedAt = System.currentTimeMillis(),
                                    success = true,
                                    unlockMinutesGranted = RestrictionRuleSpec.allowedUnlockMinutes(unlockMinutes),
                                    reasonText = reason,
                                ),
                            )
                            ServiceLocator.database(this@LockActivity).guardEventDao().insert(
                                GuardEventEntity(
                                    packageName = packageName,
                                    eventType = "mission_finished",
                                    blocked = false,
                                    unlockTokenValid = true,
                                    missionResult = "success",
                                    createdAt = System.currentTimeMillis(),
                                ),
                            )
                            ServiceLocator.database(this@LockActivity).unlockSessionDao().insert(session)
                            finish()
                        }
                    },
                    onEmergency = {
                        missionCompleted = true
                        lifecycleScope.launch {
                            val session = grantUnlockSession(packageName, emergencyUnlockMinutes, UnlockSessionReason.Emergency)
                            ServiceLocator.database(this@LockActivity).missionLogDao().insert(
                                MissionLogEntity(
                                    targetPackage = packageName,
                                    missionType = "emergency_unlock",
                                    startedAt = missionStartedAt,
                                    completedAt = System.currentTimeMillis(),
                                    success = true,
                                    unlockMinutesGranted = RestrictionRuleSpec.allowedUnlockMinutes(emergencyUnlockMinutes),
                                    reasonText = "emergency_unlock",
                                ),
                            )
                            ServiceLocator.database(this@LockActivity).guardEventDao().insert(
                                GuardEventEntity(
                                    packageName = packageName,
                                    eventType = "emergency_unlock_granted",
                                    blocked = false,
                                    unlockTokenValid = true,
                                    missionResult = "emergency",
                                    createdAt = System.currentTimeMillis(),
                                ),
                            )
                            ServiceLocator.database(this@LockActivity).unlockSessionDao().insert(session)
                            finish()
                        }
                    },
                    onLeave = {
                        leaveWithoutUnlock(packageName, missionType, MissionExitReason.Unknown)
                    },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!missionCompleted && !isFinishing) {
            leaveWithoutUnlock(
                packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty(),
                missionType = intent.getStringExtra(EXTRA_MISSION_TYPE) ?: "unknown",
                exitReason = MissionExitReason.HomeOrRecentApps,
            )
        }
    }

    private fun leaveWithoutUnlock(
        packageName: String,
        missionType: String = "unknown",
        exitReason: MissionExitReason = MissionExitReason.Unknown,
    ) {
        if (abandonLogged) return
        abandonLogged = true
        lifecycleScope.launch {
            if (packageName.isNotBlank()) {
                ServiceLocator.database(this@LockActivity).missionLogDao().insert(
                    MissionLogEntity(
                        targetPackage = packageName,
                        missionType = missionType,
                        startedAt = missionStartedAt,
                        completedAt = System.currentTimeMillis(),
                        success = false,
                        unlockMinutesGranted = 0,
                        reasonText = exitReason.eventType,
                    ),
                )
                ServiceLocator.database(this@LockActivity).guardEventDao().insert(
                    GuardEventEntity(
                        packageName = packageName,
                        eventType = exitReason.eventType,
                        blocked = false,
                        unlockTokenValid = false,
                        missionResult = "abandoned",
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            sendHome()
            finish()
        }
    }

    private fun sendHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(homeIntent)
    }

    private fun grantUnlockSession(
        packageName: String,
        minutes: Int,
        reason: UnlockSessionReason,
    ): UnlockSessionEntity {
        val session = GuardPreferences.grantTemporaryUnlock(this, packageName, minutes, reason)
        return UnlockSessionEntity(
            packageName = packageName,
            reason = reason.name,
            issuedAtElapsedRealtime = session.issuedAtElapsedRealtime,
            expiresAtElapsedRealtime = session.expiresAtElapsedRealtime,
            issuedAtWallClock = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
        )
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_MISSION_TYPE = "mission_type"
        const val EXTRA_UNLOCK_MINUTES = "unlock_minutes"
        const val EXTRA_ALLOW_MISSION_UNLOCK = "allow_mission_unlock"
        const val EXTRA_EMERGENCY_UNLOCK_MINUTES = "emergency_unlock_minutes"
    }
}

@Composable
private fun LockScreen(
    appName: String,
    missionType: String,
    unlockMinutes: Int,
    allowMissionUnlock: Boolean,
    emergencyUnlockMinutes: Int,
    onComplete: (String) -> Unit,
    onEmergency: () -> Unit,
    onLeave: () -> Unit,
) {
    val missionSpec = remember(missionType) { MissionSpec.from(missionType) }
    var secondsLeft by remember(missionSpec) { mutableIntStateOf(missionSpec.seconds) }
    var answer by remember(missionSpec) { mutableStateOf("") }
    val canUnlock = secondsLeft == 0 && missionSpec.isAnswerValid(answer)
    val canMissionUnlock = canUnlock && allowMissionUnlock && unlockMinutes > 0

    LaunchedEffect(missionSpec) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ScreenTimingSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Large),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
                Text("HabitGuard", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(
                    text = "잠깐 멈추기",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "$appName 사용을 바로 막는 화면이 아니라, 사용자가 승인한 규칙에 따라 한 번 확인하는 미션입니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ScreenTimingCard(contentDescription = "남은 대기 시간 ${secondsLeft}초") {
                Column(
                    modifier = Modifier.padding(ScreenTimingSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Medium),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("남은 대기 시간", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        ScreenTimingStatusBadge(if (secondsLeft == 0) "입력 확인 가능" else "진행 중", ScreenTimingUiCopy.Tone.Notice)
                    }
                    Text(
                        "%02d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text("타이머가 끝나고 조건을 만족하면 임시 해제를 선택할 수 있습니다.")
                }
            }

            ScreenTimingCard(tone = ScreenTimingUiCopy.Tone.Notice) {
                Column(
                    modifier = Modifier.padding(ScreenTimingSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small),
                ) {
                    Text(missionSpec.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(missionSpec.prompt)
                }
            }

            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .semantics { contentDescription = missionSpec.inputLabel },
                label = { Text(missionSpec.inputLabel) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                shape = RoundedCornerShape(ScreenTimingSpacing.CardRadius),
            )

            ScreenTimingCard {
                Column(
                    modifier = Modifier.padding(ScreenTimingSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small),
                ) {
                    Text("해제 조건", fontWeight = FontWeight.Bold)
                    Text(
                        if (canMissionUnlock) {
                            "${formatScreenTimingMinutes(unlockMinutes.toLong())} 동안 임시 해제가 가능합니다."
                        } else {
                            "대기 시간과 입력 조건을 모두 완료해야 합니다."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(ScreenTimingSpacing.Small)) {
                ScreenTimingPrimaryButton(
                    text = "${formatScreenTimingMinutes(unlockMinutes.toLong())} 임시 해제",
                    enabled = canMissionUnlock,
                    onClick = { onComplete("${missionSpec.kind}: ${answer.trim()}") },
                )
                if (emergencyUnlockMinutes > 0) {
                    ScreenTimingSecondaryButton(
                        text = "긴급 ${formatScreenTimingMinutes(emergencyUnlockMinutes.toLong())} 해제",
                        onClick = onEmergency,
                    )
                }
                ScreenTimingSecondaryButton(
                    text = "홈으로 돌아가기",
                    onClick = onLeave,
                )
                Text(
                    "미션을 완료하지 않고 나가면 임시 해제는 발급되지 않습니다. 이 기록은 로컬 점검 이벤트로만 남습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class MissionSpec(
    val kind: String,
    val title: String,
    val seconds: Int,
    val inputLabel: String,
    val prompt: String,
    val validator: (String) -> Boolean,
) {
    fun isAnswerValid(answer: String): Boolean = validator(answer.trim())

    companion object {
        fun from(missionType: String): MissionSpec {
            val seconds = secondsFor(missionType)
            return when {
                missionType.contains("문장") || missionType.contains("명언") -> {
                    val sentence = "지금 선택을 한 번 더 생각합니다"
                    MissionSpec(
                        kind = missionType,
                        title = "문장 따라쓰기",
                        seconds = seconds,
                        inputLabel = "문장을 그대로 입력",
                        prompt = "\"$sentence\"를 그대로 입력하세요.",
                        validator = { it == sentence },
                    )
                }
                missionType.contains("계산") -> MissionSpec(
                    kind = missionType,
                    title = "간단 계산",
                    seconds = seconds,
                    inputLabel = "8 + 9의 답",
                    prompt = "충동적인 실행을 늦추기 위해 간단한 계산을 완료합니다.",
                    validator = { it == "17" },
                )
                missionType.contains("대체") -> MissionSpec(
                    kind = missionType,
                    title = "대체 행동 입력",
                    seconds = seconds,
                    inputLabel = "지금 할 수 있는 대체 행동",
                    prompt = "물 마시기, 자리 정리, 문제 1개 풀기처럼 짧은 행동을 적어 보세요.",
                    validator = { it.length >= 4 },
                )
                missionType.contains("목표") -> MissionSpec(
                    kind = missionType,
                    title = "목표 확인",
                    seconds = seconds,
                    inputLabel = "목표 문장",
                    prompt = "\"목표를 지키겠습니다\"를 입력하면 다음 단계로 진행합니다.",
                    validator = { it == "목표를 지키겠습니다" },
                )
                missionType.contains("30") -> MissionSpec(
                    kind = missionType,
                    title = "짧은 확인",
                    seconds = 30,
                    inputLabel = "확인 문구",
                    prompt = "\"확인\"을 입력하면 30초 뒤 임시 해제를 선택할 수 있습니다.",
                    validator = { it == "확인" },
                )
                else -> MissionSpec(
                    kind = missionType,
                    title = "이유 입력",
                    seconds = seconds,
                    inputLabel = "지금 이 앱이 필요한 이유",
                    prompt = "6자 이상 적으면 임시 해제를 선택할 수 있습니다. 입력 내용은 미션 로그의 이유로만 저장됩니다.",
                    validator = { it.length >= 6 },
                )
            }
        }

        private fun secondsFor(missionType: String): Int =
            when {
                missionType.startsWith("3") || missionType.contains("3분") -> 180
                missionType.contains("30") -> 30
                else -> 60
            }
    }
}
