package com.example.saferoute.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.saferoute.data.db.EmergencyContactEntity
import com.example.saferoute.data.db.UserProfileEntity
import com.example.saferoute.data.firebase.FirebaseConnectionState
import com.example.saferoute.data.firebase.FirebaseStatusInfo
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

@Composable
fun ProfileScreen(
    userProfile: UserProfileEntity,
    contacts: List<EmergencyContactEntity>,
    appLanguage: AppLanguage = AppLanguage.ENGLISH,
    isVoiceSosEnabled: Boolean = false,
    voiceSosTriggerPhrase: String = "Help me SafeRoute",
    firebaseStatus: FirebaseStatusInfo = FirebaseStatusInfo(),
    onSetLanguage: (AppLanguage) -> Unit = {},
    onToggleVoiceSos: (Boolean) -> Unit = {},
    onSetVoiceSosTriggerPhrase: (String) -> Unit = {},
    onAddContact: (name: String, phone: String, relationship: String) -> Unit,
    onDeleteContact: (EmergencyContactEntity) -> Unit,
    onUpdateSafetyPref: (pref: String) -> Unit,
    onTogglePlus: (Boolean) -> Unit,
    onTriggerSync: (onComplete: (Boolean, String) -> Unit) -> Unit = {},
    onConfigureCustomFirebase: (projectId: String, apiKey: String, appId: String, onComplete: (Boolean, String) -> Unit) -> Unit = { _, _, _, _ -> },
    onLogout: () -> Unit
) {
    var showAddContactDialog by remember { mutableStateOf(false) }
    // var showPaywallDialog by remember { mutableStateOf(false) } // removed
    var showTransparencyModal by remember { mutableStateOf(false) }
    var showFirebaseConfigDialog by remember { mutableStateOf(false) }

    var isSyncingNow by remember { mutableStateOf(false) }
    var syncResultMessage by remember { mutableStateOf<String?>(null) }

    var customProjectId by remember { mutableStateOf(firebaseStatus.projectId.ifBlank { "saferoute-navsec" }) }
    var customApiKey by remember { mutableStateOf("") }
    var customAppId by remember { mutableStateOf(firebaseStatus.applicationId.ifBlank { "1:315928590358:android:4b7a69f21e089201" }) }
    var configResultMessage by remember { mutableStateOf<String?>(null) }
    var isSavingConfig by remember { mutableStateOf(false) }

    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }
    var newContactRel by remember { mutableStateOf("") }

    var notificationsEnabled by remember { mutableStateOf(true) }
    var triggerPhraseInput by remember { mutableStateOf(voiceSosTriggerPhrase) }

    Column(
        modifier = Modifier
            .testTag("profile_screen")
            .fillMaxSize()
            .background(DarkNavyBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Account & Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // PROFILE USER HEADER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(DarkNavyCard)
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(SafeRouteBlue, SafeRouteTeal)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = userProfile.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (userProfile.isPlusMember) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFFD166))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PLUS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                    Text(
                        text = userProfile.email,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // [PLUS UPGRADE CARD REMOVED]
        // Plus upgrade UI removed

        // FIREBASE CLOUD SYNC & STATUS CARD
        Text("Firebase Cloud Sync", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkNavyCard)
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isConnected = firebaseStatus.state == FirebaseConnectionState.CONNECTED
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) SafetyGreen.copy(alpha = 0.15f) else Color(0xFFFFD166).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = if (isConnected) SafetyGreen else Color(0xFFFFD166),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isConnected) "Firebase Connected" else "Firebase Connecting...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isConnected) SafetyGreen else Color(0xFFFFD166))
                                )
                            }
                            Text(
                                text = "Project: ${firebaseStatus.projectId.ifBlank { "saferoute-navsec" }}",
                                fontSize = 11.sp,
                                color = SafeRouteTeal
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            customProjectId = firebaseStatus.projectId.ifBlank { "saferoute-navsec" }
                            customAppId = firebaseStatus.applicationId.ifBlank { "1:315928590358:android:4b7a69f21e089201" }
                            configResultMessage = null
                            showFirebaseConfigDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Firebase Settings",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cloud services tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Auth", "Firestore Sync", "Realtime").forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkNavySurface)
                                .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeRouteTeal, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(tag, fontSize = 10.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                if (syncResultMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = syncResultMessage ?: "",
                        fontSize = 11.sp,
                        color = if (syncResultMessage?.contains("Success", ignoreCase = true) == true) SafetyGreen else Color(0xFFFFD166),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .bounceClick(scaleDown = 0.95f) {
                                isSyncingNow = true
                                syncResultMessage = null
                                onTriggerSync { success, message ->
                                    isSyncingNow = false
                                    syncResultMessage = message
                                }
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(SafeRouteBlue)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSyncingNow) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (isSyncingNow) "Syncing..." else "Sync to Firestore",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .bounceClick(scaleDown = 0.95f) {
                                customProjectId = firebaseStatus.projectId.ifBlank { "saferoute-navsec" }
                                customAppId = firebaseStatus.applicationId.ifBlank { "1:315928590358:android:4b7a69f21e089201" }
                                configResultMessage = null
                                showFirebaseConfigDialog = true
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkNavySurface)
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Settings",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // EMERGENCY CONTACTS MANAGEMENT
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Emergency Contacts", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Box(
                modifier = Modifier
                    .bounceClick(scaleDown = 0.9f) { showAddContactDialog = true }
                    .clip(RoundedCornerShape(10.dp))
                    .background(SafeRouteTeal.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact", tint = SafeRouteTeal, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SafeRouteTeal)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        contacts.forEach { contact ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkNavyCard)
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SafeRouteTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = SafeRouteTeal, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Text("${contact.relationship} • ${contact.phone}", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        }
                    }
                    IconButton(onClick = { onDeleteContact(contact) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Contact", tint = SafetyRed.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ROUTE SAFETY PREFERENCES
        Text("Routing Preferences", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkNavyCard)
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("Default Route Ranking:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALWAYS_SAFETY" to "Safety First", "BALANCED" to "Balanced", "SPEED_FIRST" to "Speed First").forEach { (prefKey, label) ->
                        val isSel = userProfile.safetyPreference == prefKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick(scaleDown = 0.94f) { onUpdateSafetyPref(prefKey) }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) SafeRouteBlue else DarkNavySurface)
                                .border(1.dp, if (isSel) SafeRouteBlue else Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // MULTI-LANGUAGE SELECTION CARD
        Text(com.example.saferoute.util.appText("language"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkNavyCard)
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SafeRouteBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = SafeRouteBlue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = com.example.saferoute.util.appText("select_language"),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = if (appLanguage == AppLanguage.TAMIL) "தமிழ் இடைமுகம் இயக்கத்தில் உள்ளது" else "English interface is currently active",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Full-width balanced segmented toggle with 52dp touch target and comfortable Tamil vowel spacing
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkNavySurface)
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        val isSelected = appLanguage == lang
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Brush.horizontalGradient(listOf(SafeRouteBlue, SafeRouteTeal))
                                    else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                )
                                .border(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) Color(0x88FFFFFF) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .bounceClick(scaleDown = 0.94f) { onSetLanguage(lang) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                    }
                                    Text(
                                        text = lang.nativeName,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                    )
                                }
                                Text(
                                    text = lang.subtitle,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = if (isSelected) Color(0xCCFFFFFF) else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // VOICE-ACTIVATED SOS SETTINGS CARD
        Text(com.example.saferoute.util.appText("voice_sos"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkNavyCard)
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = SafeRouteTeal)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(LanguageManager.get("voice_sos"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                text = LanguageManager.get("voice_sos_desc"),
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Switch(
                        checked = isVoiceSosEnabled,
                        onCheckedChange = { onToggleVoiceSos(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SafeRouteTeal, checkedTrackColor = SafeRouteTeal.copy(alpha = 0.4f))
                    )
                }

                if (isVoiceSosEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = triggerPhraseInput,
                        onValueChange = {
                            triggerPhraseInput = it
                            onSetVoiceSosTriggerPhrase(it)
                        },
                        label = { Text("Trigger Phrase", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkNavySurface,
                            unfocusedContainerColor = DarkNavySurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = SafeRouteTeal,
                            unfocusedIndicatorColor = Color(0x22FFFFFF)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // TRANSPARENCY & PREFERENCES
        Text("Settings & Transparency", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkNavyCard)
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(scaleDown = 0.98f) { showTransparencyModal = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SafeRouteTeal)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("How Safety Scoring Works", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0x22FFFFFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = SafeRouteTeal)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Push Safety Alerts", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = SafeRouteTeal, checkedTrackColor = SafeRouteTeal.copy(alpha = 0.4f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick(scaleDown = 0.96f) { onLogout() }
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x15FF3B30))
                .border(1.dp, SafetyRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("LOG OUT", color = SafetyRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    // ADD CONTACT DIALOG
    if (showAddContactDialog) {
        Dialog(onDismissRequest = { showAddContactDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkNavyCard)
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text("Add Emergency Contact", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newContactName,
                        onValueChange = { newContactName = it },
                        label = { Text("Name", color = Color(0xFF94A3B8)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkNavySurface,
                            unfocusedContainerColor = DarkNavySurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = SafeRouteBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newContactPhone,
                        onValueChange = { newContactPhone = it },
                        label = { Text("Phone", color = Color(0xFF94A3B8)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkNavySurface,
                            unfocusedContainerColor = DarkNavySurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = SafeRouteBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newContactRel,
                        onValueChange = { newContactRel = it },
                        label = { Text("Relationship", color = Color(0xFF94A3B8)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkNavySurface,
                            unfocusedContainerColor = DarkNavySurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = SafeRouteBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showAddContactDialog = false }) { Text("Cancel", color = Color(0xFF94A3B8)) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .bounceClick(scaleDown = 0.92f) {
                                    if (newContactName.isNotBlank() && newContactPhone.isNotBlank()) {
                                        onAddContact(newContactName, newContactPhone, newContactRel)
                                        showAddContactDialog = false
                                    }
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(SafeRouteBlue)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Save Contact", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }



    // HOW SCORING WORKS TRANSPARENCY MODAL
    if (showTransparencyModal) {
        Dialog(onDismissRequest = { showTransparencyModal = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkNavyCard)
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text("How Scoring Works", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "SafeRoute's safety factors are derived from real OpenStreetMap data and documented hazard sources:\n" +
                                "• Lighting (35%): Verified OSM lit tags on mapped road segments\n" +
                                "• Activity & Shops (25%): Real open businesses & density within 150m\n" +
                                "• Incident Proximity (20%): Official traffic police accident black-spots & verified user reports\n" +
                                "• Help Point Coverage (20%): Proximity to hospitals, police and pharmacies\n" +
                                "• Time of Day: Real-time visibility conditions multiplier\n\n" +
                                "Data confidence badges (Low/Medium/High) reflect coverage. When data is insufficient, scores are withheld.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(scaleDown = 0.95f) { showTransparencyModal = false }
                            .clip(RoundedCornerShape(12.dp))
                            .background(SafeRouteBlue)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Understood", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // FIREBASE CONNECTION & CONFIGURATION DIALOG
    if (showFirebaseConfigDialog) {
        Dialog(onDismissRequest = { showFirebaseConfigDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkNavyCard)
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = SafeRouteTeal, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Firebase Connection", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (firebaseStatus.state == FirebaseConnectionState.CONNECTED) SafetyGreen.copy(alpha = 0.2f) else Color(0x33FFD166))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (firebaseStatus.state == FirebaseConnectionState.CONNECTED) "Active" else "Connecting",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (firebaseStatus.state == FirebaseConnectionState.CONNECTED) SafetyGreen else Color(0xFFFFD166)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkNavySurface)
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Project ID", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text(firebaseStatus.projectId.ifBlank { "saferoute-navsec" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Firestore Cloud Sync", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text("Enabled & Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SafetyGreen)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Authentication", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text(if (firebaseStatus.isAuthAvailable) "Firebase Auth" else "Local + Cloud Auth", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SafeRouteTeal)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Custom Firebase Project (Optional)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = customProjectId,
                        onValueChange = { customProjectId = it },
                        label = { Text("Project ID", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkNavySurface,
                            unfocusedContainerColor = DarkNavySurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = SafeRouteBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it },
                        label = { Text("API Key (Web / Android)", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                        placeholder = { Text("AIzaSy...", fontSize = 11.sp, color = Color(0x66FFFFFF)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkNavySurface,
                            unfocusedContainerColor = DarkNavySurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = SafeRouteBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = customAppId,
                        onValueChange = { customAppId = it },
                        label = { Text("Mobile App ID", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                        placeholder = { Text("1:315928590358:android:...", fontSize = 11.sp, color = Color(0x66FFFFFF)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkNavySurface,
                            unfocusedContainerColor = DarkNavySurface,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = SafeRouteBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (configResultMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = configResultMessage ?: "",
                            fontSize = 11.sp,
                            color = if (configResultMessage?.contains("Connect", ignoreCase = true) == true) SafetyGreen else SafetyRed
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showFirebaseConfigDialog = false }) {
                            Text("Done", color = Color(0xFF94A3B8))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .bounceClick(scaleDown = 0.95f) {
                                    if (customProjectId.isNotBlank() && customApiKey.isNotBlank() && customAppId.isNotBlank()) {
                                        isSavingConfig = true
                                        configResultMessage = null
                                        onConfigureCustomFirebase(customProjectId, customApiKey, customAppId) { success, msg ->
                                            isSavingConfig = false
                                            configResultMessage = msg
                                        }
                                    } else {
                                        configResultMessage = "Please fill Project ID, API Key, and App ID to switch projects."
                                    }
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(SafeRouteBlue)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isSavingConfig) "Connecting..." else "Connect",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
