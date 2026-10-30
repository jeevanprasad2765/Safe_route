package com.example.saferoute.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.example.R
import com.example.saferoute.util.AppLanguage
import com.example.saferoute.util.LanguageManager
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.GlassSurfaceBorder
import com.example.ui.theme.SafeRouteBlue
import com.example.ui.theme.SafeRouteTeal
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyRed
import com.example.ui.theme.bounceClick

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onFinishOnboarding: (contactName: String, contactPhone: String, contactRel: String) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var selectedLang by remember { mutableStateOf(AppLanguage.ENGLISH) } // Kept for future multi-language support

    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactRelationship by remember { mutableStateOf("") }
    var contactError by remember { mutableStateOf<String?>(null) }

    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    val isLocationGranted = locationPermissionsState.permissions.any { it.status.isGranted }
    var isNotificationGranted by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .testTag("onboarding_screen")
            .fillMaxSize()
            .background(DarkNavyBackground)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar with Language Selector and Skip Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step Indicator dots
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentStep) 24.dp else 8.dp, 8.dp)
                                .clip(CircleShape)
                                .background(if (index == currentStep) SafeRouteTeal else Color(0x33FFFFFF))
                        )
                    }
                }

                if (currentStep < 3) {
                    TextButton(
                        onClick = { currentStep = 3 },
                        modifier = Modifier.testTag("skip_onboarding_button")
                    ) {
                        Text(LanguageManager.get("skip"), color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Step Content Body
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "OnboardingContent"
            ) { step ->
                when (step) {
                    0 -> CarouselStep(
                        imageRes = R.drawable.img_onboarding_safety_1785820016563,
                        title = LanguageManager.get("onboarding_title_1"),
                        description = LanguageManager.get("onboarding_desc_1")
                    )
                    1 -> CarouselStep(
                        imageRes = R.drawable.img_onboarding_sos_1785820032227,
                        title = LanguageManager.get("onboarding_title_2"),
                        description = LanguageManager.get("onboarding_desc_2")
                    )
                    2 -> CarouselStep(
                        imageRes = R.drawable.app_icon_saferoute,
                        title = LanguageManager.get("onboarding_title_3"),
                        description = LanguageManager.get("onboarding_desc_3")
                    )
                    3 -> PermissionStep(
                        isLocationGranted = isLocationGranted,
                        isNotificationGranted = isNotificationGranted,
                        onGrantLocation = { locationPermissionsState.launchMultiplePermissionRequest() },
                        onGrantNotification = { isNotificationGranted = true }
                    )
                    4 -> ContactSetupStep(
                        name = contactName,
                        phone = contactPhone,
                        relationship = contactRelationship,
                        onNameChange = { contactName = it },
                        onPhoneChange = { contactPhone = it },
                        onRelChange = { contactRelationship = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            contactError?.let { error ->
                Text(
                    text = error,
                    color = SafetyRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // Bottom Navigation Button
            Box(
                modifier = Modifier
                    .testTag("onboarding_next_button")
                    .fillMaxWidth()
                    .height(54.dp)
                    .bounceClick(scaleDown = 0.95f) {
                        if (currentStep < 4) {
                            currentStep += 1
                        } else {
                            val normalisedPhone = com.example.saferoute.util.SosManager
                                .normalizePhoneNumber(contactPhone)
                            when {
                                contactName.isBlank() -> {
                                    contactError = "Enter the name of your emergency contact."
                                }
                                normalisedPhone == null -> {
                                    contactError = "Enter a valid phone number (10-digit Indian number or with country code)."
                                }
                                else -> {
                                    contactError = null
                                    onFinishOnboarding(
                                        contactName.trim(),
                                        normalisedPhone,
                                        contactRelationship.ifBlank { "Contact" }
                                    )
                                }
                            }
                        }
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(SafeRouteBlue, SafeRouteTeal)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (currentStep) {
                            3 -> LanguageManager.get("continue_to_contacts")
                            4 -> LanguageManager.get("get_started")
                            else -> LanguageManager.get("next")
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CarouselStep(
    imageRes: Int,
    title: String,
    description: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = title,
            modifier = Modifier
                .size(230.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(26.dp))

        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.4).sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = description,
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun PermissionStep(
    isLocationGranted: Boolean,
    isNotificationGranted: Boolean,
    onGrantLocation: () -> Unit,
    onGrantNotification: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = LanguageManager.get("onboarding_perm_title"),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = (-0.4).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = LanguageManager.get("onboarding_perm_desc"),
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Location Permission Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkNavyCard)
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SafeRouteTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = SafeRouteTeal,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(LanguageManager.get("precise_location"), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text(
                        LanguageManager.get("precise_location_desc"),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                if (isLocationGranted) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = SafetyGreen)
                } else {
                    Box(
                        modifier = Modifier
                            .bounceClick(scaleDown = 0.92f) { onGrantLocation() }
                            .clip(RoundedCornerShape(10.dp))
                            .background(SafeRouteBlue)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(LanguageManager.get("allow"), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Notification Permission Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkNavyCard)
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SafeRouteTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = SafeRouteTeal,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(LanguageManager.get("notifications_title"), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text(
                        LanguageManager.get("notifications_desc"),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                if (isNotificationGranted) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = SafetyGreen)
                } else {
                    Box(
                        modifier = Modifier
                            .bounceClick(scaleDown = 0.92f) { onGrantNotification() }
                            .clip(RoundedCornerShape(10.dp))
                            .background(SafeRouteBlue)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(LanguageManager.get("allow"), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ContactSetupStep(
    name: String,
    phone: String,
    relationship: String,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onRelChange: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(SafeRouteTeal.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = SafeRouteTeal,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = LanguageManager.get("primary_emergency_contact"),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = (-0.4).sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = LanguageManager.get("add_trusted_contact_desc"),
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(LanguageManager.get("contact_full_name"), color = Color(0xFF94A3B8)) },
            modifier = Modifier
                .testTag("contact_name_input")
                .fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkNavyCard,
                unfocusedContainerColor = DarkNavyCard,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = SafeRouteBlue,
                unfocusedIndicatorColor = Color(0x22FFFFFF)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text(LanguageManager.get("contact_phone_number"), color = Color(0xFF94A3B8)) },
            modifier = Modifier
                .testTag("contact_phone_input")
                .fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkNavyCard,
                unfocusedContainerColor = DarkNavyCard,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = SafeRouteBlue,
                unfocusedIndicatorColor = Color(0x22FFFFFF)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = relationship,
            onValueChange = onRelChange,
            label = { Text(LanguageManager.get("relationship_label"), color = Color(0xFF94A3B8)) },
            modifier = Modifier
                .testTag("contact_rel_input")
                .fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkNavyCard,
                unfocusedContainerColor = DarkNavyCard,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = SafeRouteBlue,
                unfocusedIndicatorColor = Color(0x22FFFFFF)
            )
        )
    }
}
