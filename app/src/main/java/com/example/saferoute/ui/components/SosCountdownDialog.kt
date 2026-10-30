package com.example.saferoute.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.saferoute.data.db.EmergencyContactEntity
import com.example.saferoute.ui.viewmodel.SosPhase
import com.example.saferoute.ui.viewmodel.SosStep
import com.example.saferoute.ui.viewmodel.SosStepStatus
import com.example.saferoute.ui.viewmodel.SosUiState
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.GlassSurfaceBorder
import com.example.ui.theme.SafeRouteBlue
import com.example.ui.theme.SafeRouteTeal
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyRed
import com.example.ui.theme.bounceClick
import kotlinx.coroutines.delay

/**
 * State-driven SOS flow: countdown -> permission rationale (first time) -> sending -> result.
 * No success wording is shown unless at least one real call/SMS was actually started.
 */
@Composable
fun SosCountdownDialog(
    contacts: List<EmergencyContactEntity>,
    sosState: SosUiState,
    onDismissRequest: () -> Unit,
    onCountdownFinished: () -> Unit,
    onGrantPermissions: () -> Unit,
    onRetry: () -> Unit,
    onStandDown: () -> Unit
) {
    var secondsRemaining by remember { mutableIntStateOf(5) }

    LaunchedEffect(sosState.phase) {
        if (sosState.phase == SosPhase.COUNTDOWN) {
            secondsRemaining = 5
            while (secondsRemaining > 0) {
                delay(1000L)
                secondsRemaining -= 1
            }
            onCountdownFinished()
        }
    }

    Dialog(
        onDismissRequest = {
            if (!sosState.isSending) onDismissRequest()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF380B0B), Color(0xFF090D16)),
                        radius = 1200f
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Concentric pulse rings behind the countdown / permission screens.
            if (sosState.phase == SosPhase.COUNTDOWN || sosState.phase == SosPhase.PERMISSION_RATIONALE) {
                val infiniteTransition = rememberInfiniteTransition(label = "SosRings")
                val ringScale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 2.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "RingScale"
                )
                val ringAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 0.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "RingAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .border(2.dp, SafetyRed.copy(alpha = ringAlpha), CircleShape)
                )
            }

            AnimatedContent(
                targetState = sosState.phase,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(350)) + slideInVertically { it / 4 })
                        .togetherWith(fadeOut(animationSpec = tween(250)))
                },
                label = "SosPhaseTransition"
            ) { phase ->
                when (phase) {
                    SosPhase.COUNTDOWN -> SosCountdownContent(
                        secondsRemaining = secondsRemaining,
                        contacts = contacts,
                        onCancel = onDismissRequest
                    )

                    SosPhase.PERMISSION_RATIONALE -> SosPermissionContent(
                        contacts = contacts,
                        onGrant = onGrantPermissions,
                        onCancel = onDismissRequest
                    )

                    SosPhase.SENDING -> SosSendingContent(steps = sosState.steps)

                    SosPhase.RESULT -> SosResultContent(
                        sosState = sosState,
                        contacts = contacts,
                        onRetry = onRetry,
                        onClose = {
                            onStandDown()
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SosCountdownContent(
    secondsRemaining: Int,
    contacts: List<EmergencyContactEntity>,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
            CircularProgressIndicator(
                progress = { secondsRemaining / 5.0f },
                modifier = Modifier.fillMaxSize(),
                color = SafetyRed,
                strokeWidth = 10.dp,
                strokeCap = StrokeCap.Round,
                trackColor = Color(0x33FFFFFF)
            )
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF453A).copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$secondsRemaining",
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-2).sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "EMERGENCY SOS",
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = SafetyRed,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (contacts.isEmpty()) {
                "No emergency contacts are saved. Add one in Profile before using SOS."
            } else {
                "Your phone will call ${contacts.first().name} and text all ${contacts.size} " +
                    "emergency ${if (contacts.size == 1) "contact" else "contacts"} with your live location " +
                    "in $secondsRemaining seconds."
            },
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xDD12192B))
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ShareLocation,
                        contentDescription = null,
                        tint = SafeRouteTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ALERT RECIPIENTS (${contacts.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = SafeRouteTeal
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (contacts.isEmpty()) {
                    Text(
                        text = "No contacts saved yet",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                } else {
                    contacts.take(4).forEach { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${contact.name} (${contact.relationship})",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = contact.phone,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .testTag("sos_cancel_button")
                .fillMaxWidth()
                .height(56.dp)
                .bounceClick(scaleDown = 0.95f) { onCancel() }
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x22FFFFFF))
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CANCEL (ACCIDENTAL TAP)",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun SosPermissionContent(
    contacts: List<EmergencyContactEntity>,
    onGrant: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(SafetyAmber.copy(alpha = 0.18f))
                .border(2.dp, SafetyAmber, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = SafetyAmber,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Allow SOS call & SMS",
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "SafeRoute needs your permission to place the emergency call and send the " +
                "location SMS. Nothing is sent until you allow it.",
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkNavyCard)
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "What will happen",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SafeRouteTeal
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Call ${contacts.firstOrNull()?.name ?: "your first emergency contact"}",
                    fontSize = 13.sp,
                    color = Color.White
                )
                Text(
                    text = "2. SMS your live location link to all ${contacts.size} saved contacts",
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .testTag("sos_allow_permissions_button")
                .fillMaxWidth()
                .height(56.dp)
                .bounceClick(scaleDown = 0.95f) { onGrant() }
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(SafetyRed, Color(0xFFB91C1C)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ALLOW & SEND ALERT",
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .bounceClick(scaleDown = 0.95f) { onCancel() }
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x22FFFFFF))
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CANCEL ALERT",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun SosSendingContent(steps: List<SosStep>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = SafetyRed,
            strokeWidth = 4.dp,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Sending emergency alert…",
            fontWeight = FontWeight.Black,
            fontSize = 19.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Keep the app open while the call and messages start.",
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(22.dp))
        SosStepsCard(steps)
    }
}

@Composable
private fun SosResultContent(
    sosState: SosUiState,
    contacts: List<EmergencyContactEntity>,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val success = sosState.alertStarted
    val accent = if (success) SafetyAmber else SafetyRed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f))
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (success) Icons.Default.Shield else Icons.Default.Warning,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = sosState.summary ?: if (success) {
                "Emergency alert started"
            } else {
                "Emergency alert could not be sent"
            },
            fontWeight = FontWeight.Black,
            fontSize = 19.sp,
            color = if (success) Color.White else SafetyRed,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (success) {
                "Review each step below. The dialer may still need one tap to connect the call."
            } else {
                "Nothing was sent. Check your contacts and permissions, then retry."
            },
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = Color.White.copy(alpha = 0.82f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        SosStepsCard(sosState.steps)

        if (contacts.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Tip: add an emergency contact in Profile so SOS can reach someone.",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        if (sosState.canRetry) {
            Box(
                modifier = Modifier
                    .testTag("sos_retry_button")
                    .fillMaxWidth()
                    .height(54.dp)
                    .bounceClick(scaleDown = 0.95f) { onRetry() }
                    .clip(RoundedCornerShape(16.dp))
                    .background(SafeRouteBlue),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RETRY ALERT",
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Box(
            modifier = Modifier
                .testTag("sos_stand_down_button")
                .fillMaxWidth()
                .height(54.dp)
                .bounceClick(scaleDown = 0.95f) { onClose() }
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(SafetyGreen, Color(0xFF24A148))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CLOSE ALERT VIEW",
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun SosStepsCard(steps: List<SosStep>) {
    if (steps.isEmpty()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkNavyCard)
            .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column {
            steps.forEach { step ->
                SosStepRow(step)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SosStepRow(step: SosStep) {
    val (icon, tint) = when (step.status) {
        SosStepStatus.SUCCESS -> Icons.Default.CheckCircle to SafetyGreen
        SosStepStatus.FAILED -> Icons.Default.Close to SafetyRed
        SosStepStatus.RUNNING -> Icons.Default.ShareLocation to SafetyAmber
        SosStepStatus.SKIPPED -> Icons.Default.Close to Color(0xFF64748B)
        SosStepStatus.PENDING -> Icons.Default.Sms to Color(0xFF64748B)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (step.status == SosStepStatus.RUNNING) {
            CircularProgressIndicator(
                color = tint,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = step.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            step.detail?.let { detail ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}
