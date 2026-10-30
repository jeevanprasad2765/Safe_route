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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    onAddContact: (name: String, phone: String, relationship: String) -> Unit,
    onDeleteContact: (EmergencyContactEntity) -> Unit,
    onUpdateSafetyPref: (pref: String) -> Unit,
    alsoCall112: Boolean = false,
    onToggleAlsoCall112: (Boolean) -> Unit = {},
    onLogout: () -> Unit
) {
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showTransparencyModal by remember { mutableStateOf(false) }

    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }
    var newContactRel by remember { mutableStateOf("") }

    var notificationsEnabled by remember { mutableStateOf(true) }

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
                    Text(
                        text = userProfile.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = userProfile.email,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        // LANGUAGE CARD — single language (English); TAMIL interface removed per product decision
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
                        text = AppLanguage.ENGLISH.nativeName,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "English interface is currently active",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF94A3B8)
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0x22FFFFFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = SafeRouteTeal)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Also call 112", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Text(
                                "After SOS, also start a call to the 112 emergency helpline",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    Switch(
                        checked = alsoCall112,
                        onCheckedChange = { onToggleAlsoCall112(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SafetyRed, checkedTrackColor = SafetyRed.copy(alpha = 0.4f))
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

}
