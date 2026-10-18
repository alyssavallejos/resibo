package com.resibo.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resibo.app.audio.PaymentAudioAnnouncer
import com.resibo.app.model.Business
import com.resibo.app.model.PaymentAccount
import com.resibo.app.theme.ResiboPrimary
import com.resibo.app.theme.ResiboSurface
import com.resibo.app.viewmodel.BusinessState
import com.resibo.app.viewmodel.BusinessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessSetupScreen(
    businessViewModel: BusinessViewModel,
    initialBusiness: Business? = null,
    onSetupComplete: (Business) -> Unit,
    onSignOut: (() -> Unit)? = null,
    onNavigateToOnboarding: (() -> Unit)? = null
) {
    val businessState by businessViewModel.businessState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var businessName by remember { mutableStateOf(initialBusiness?.name ?: "") }
    var slug by remember { mutableStateOf(initialBusiness?.slug ?: "") }
    var accounts by remember {
        mutableStateOf(
            initialBusiness?.accounts?.ifEmpty {
                listOf(PaymentAccount("gcash", "", ""))
            } ?: listOf(PaymentAccount("gcash", "", ""))
        )
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-sync slug if the user hasn't explicitly customized it
    var isSlugManuallyEdited by remember { mutableStateOf(initialBusiness != null) }
    var isAudioAnnouncerEnabled by remember { mutableStateOf(PaymentAudioAnnouncer.isEnabled(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialBusiness == null) "Setup Your Business" else "Business Settings") },
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
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = ResiboSurface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Store Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = businessName,
                        onValueChange = {
                            businessName = it
                            if (!isSlugManuallyEdited) {
                                slug = businessViewModel.generateSlug(it)
                            }
                        },
                        label = { Text("Business Name *") },
                        placeholder = { Text("e.g. Kape ni Aling Rosa") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = slug,
                        onValueChange = {
                            isSlugManuallyEdited = true
                            slug = it.lowercase().trim()
                        },
                        label = { Text("Shop URL Slug *") },
                        placeholder = { Text("e.g. kape-ni-aling-rosa") },
                        singleLine = true,
                        supportingText = {
                            Text("Buyer link: https://resibo-ph.web.app/pay/${slug.ifBlank { "your-shop" }}")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Accepted Payment Accounts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Text(
                text = "Used to cross-check the recipient name & account number on receipts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            accounts.forEachIndexed { index, account ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = ResiboSurface,
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Account #${index + 1}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (accounts.size > 1) {
                                IconButton(
                                    onClick = {
                                        accounts = accounts.toMutableList().also { it.removeAt(index) }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Account",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Provider selection pills
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "gcash" to "GCash",
                                "maya" to "Maya",
                                "maribank" to "MariBank",
                                "seabank" to "SeaBank",
                                "bdo" to "BDO",
                                "bpi" to "BPI",
                                "gotyme" to "GoTyme",
                                "unionbank" to "UnionBank",
                                "dbp" to "DBP",
                                "metrobank" to "Metrobank",
                                "landbank" to "LandBank",
                                "rcbc" to "RCBC",
                                "bank" to "Other Bank"
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = account.provider == key,
                                    onClick = {
                                        accounts = accounts.toMutableList().also {
                                            it[index] = account.copy(provider = key)
                                        }
                                    },
                                    label = { Text(label, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = account.accountName,
                            onValueChange = { newName ->
                                accounts = accounts.toMutableList().also {
                                    it[index] = account.copy(accountName = newName)
                                }
                            },
                            label = { Text("Account Name *") },
                            placeholder = { Text("e.g. Rosa S.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = account.last4,
                            onValueChange = { newLast4 ->
                                accounts = accounts.toMutableList().also {
                                    it[index] = account.copy(last4 = newLast4.take(4))
                                }
                            },
                            label = { Text("Last 4 Digits of Phone/Account *") },
                            placeholder = { Text("e.g. 8899") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            TextButton(
                onClick = {
                    accounts = accounts + PaymentAccount("gcash", "", "")
                },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = ResiboPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Another Account", color = ResiboPrimary, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Announcer Settings (100% Free Built-in Soundbox)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = ResiboSurface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔊 Free Voice Announcer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Speaks incoming payments & dictates reference numbers out loud like a store soundbox.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAudioAnnouncerEnabled,
                            onCheckedChange = {
                                isAudioAnnouncerEnabled = it
                                PaymentAudioAnnouncer.setEnabled(context, it)
                            }
                        )
                    }

                    if (isAudioAnnouncerEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                PaymentAudioAnnouncer.announcePayment(
                                    context = context,
                                    provider = "gcash",
                                    amount = 150.0,
                                    senderName = "Juan Dela Cruz",
                                    referenceNumber = "901234567890"
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("▶️ Test Voice Announcement", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (businessName.isBlank()) {
                        errorMessage = "Please enter your business name"
                        return@Button
                    }
                    if (accounts.any { it.accountName.isBlank() || it.last4.isBlank() }) {
                        errorMessage = "Please fill in all account names and last 4 digits"
                        return@Button
                    }
                    errorMessage = null

                    businessViewModel.saveBusiness(
                        name = businessName,
                        slugInput = slug,
                        accounts = accounts,
                        context = context,
                        onSuccess = { savedBiz ->
                            onSetupComplete(savedBiz)
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ResiboPrimary),
                enabled = businessState !is BusinessState.Loading
            ) {
                if (businessState is BusinessState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("Save & Open Dashboard", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            if (onNavigateToOnboarding != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onNavigateToOnboarding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ResiboPrimary)
                ) {
                    Text("📖 Replay App Tour & Feature Guide", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            if (initialBusiness != null && onSignOut != null) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("🚪 Sign Out", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
