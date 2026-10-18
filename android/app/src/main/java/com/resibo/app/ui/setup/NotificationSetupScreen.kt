package com.resibo.app.ui.setup

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.resibo.app.theme.*
import com.resibo.app.viewmodel.PaymentsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSetupScreen(
    paymentsViewModel: PaymentsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isGranted by paymentsViewModel.isNotificationAccessGranted.collectAsState()

    // Re-check access when user returns to app from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                paymentsViewModel.checkNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Capture Setup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ResiboPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGranted) VerdictVerifiedContainer else VerdictPendingContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isGranted) VerdictVerified else VerdictPending,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isGranted) "Payment capture ON ✅" else "Payment capture OFF ⚠️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isGranted) VerdictVerified else VerdictPending
                        )
                        Text(
                            text = if (isGranted) "Resibo is actively capturing incoming payments." else "Grant notification access below so Resibo can record payments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mandatory Privacy & Security Disclosure
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = ResiboSurface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "🔒 Privacy & Security Disclosure",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ResiboPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Before granting access, please note:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Resibo ONLY reads incoming payment notifications from GCash, Maya, and selected bank apps.\n" +
                               "• Resibo NEVER logs into, opens, or connects to your banking apps.\n" +
                               "• Resibo NEVER sees your PIN, password, account balance, or personal messages.\n" +
                               "• All other notifications (Messenger, WhatsApp, texts) are ignored immediately on arrival.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) ResiboPrimaryHover else ResiboPrimary
                )
            ) {
                Text(
                    text = if (isGranted) "Notification Settings (Enabled)" else "Grant Notification Access in Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFFBEB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "⚠️ Getting 'Restricted setting' error?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF92400E)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Android 13/14+ restricts notification access for downloaded APKs until unlocked in App Info:\n" +
                                   "1. Tap 'Open Resibo App Info' below\n" +
                                   "2. Tap the ⋮ (3 dots) in the top-right corner\n" +
                                   "3. Tap 'Allow restricted settings' and confirm PIN/fingerprint\n" +
                                   "4. Return here and tap the green button above!",
                            fontSize = 12.sp,
                            color = Color(0xFF78350F),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF92400E)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706))
                        ) {
                            Text("Open Resibo App Info ➔", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // OEM Battery Optimization & Sideload tips
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = ResiboSurface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "💡 Troubleshooting & Device Tips",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Android 13+ \"Restricted Settings\" Notice:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "If the toggle in Settings is grayed out: Open Settings → Apps → Resibo → tap the ⋮ (3-dots) menu on top right → tap \"Allow restricted settings\", then return here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Background App Killing (Xiaomi / Oppo / Realme / Vivo / Samsung):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "To prevent the system from closing Resibo when the screen is locked: Set Resibo's battery usage to \"Unrestricted / No restrictions\" and enable \"Autostart\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
