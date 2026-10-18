package com.resibo.app.ui.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resibo.app.model.Business
import com.resibo.app.model.Payment
import com.resibo.app.theme.*
import com.resibo.app.viewmodel.PaymentsUiState
import com.resibo.app.viewmodel.PaymentsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsReceivedScreen(
    business: Business,
    paymentsViewModel: PaymentsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by paymentsViewModel.uiState.collectAsState()
    var showSimulateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(business.id) {
        paymentsViewModel.listenToPayments(business.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payments Received (Ledger)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // DEBUG ONLY: Simulate incoming payment button
                    FilledTonalButton(
                        onClick = { showSimulateDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simulate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ResiboPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is PaymentsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ResiboPrimary)
                    }
                }
                is PaymentsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
                is PaymentsUiState.Success -> {
                    if (state.payments.isEmpty()) {
                        EmptyPaymentsView(onSimulateClick = { showSimulateDialog = true })
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.payments, key = { it.id }) { payment ->
                                PaymentCard(payment = payment)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSimulateDialog) {
        SimulatePaymentDialog(
            onDismiss = { showSimulateDialog = false },
            onSimulate = { provider, amount, sender, ref ->
                paymentsViewModel.simulateIncomingPayment(
                    context = context,
                    businessId = business.id,
                    provider = provider,
                    amount = amount,
                    senderName = sender,
                    referenceNumber = ref
                )
                showSimulateDialog = false
            }
        )
    }
}

@Composable
fun PaymentCard(payment: Payment) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    val formattedTime = remember(payment.receivedAt) {
        payment.receivedAt?.toDate()?.let { dateFormat.format(it) } ?: "Just now"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ResiboSurface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderBadgePill(provider = payment.provider)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = payment.senderNameMasked,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (!payment.referenceNumber.isNullOrBlank()) "Ref: ${payment.referenceNumber}" else "No Reference Number",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "$formattedTime • via ${payment.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Text(
                text = "₱${String.format(Locale.US, "%,.2f", payment.amount)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = ResiboPrimary
            )
        }
    }
}

@Composable
fun ProviderBadgePill(provider: String) {
    val (label, bg, fg) = when (provider.lowercase()) {
        "maya" -> Triple("MAYA", MayaContainer, MayaGreen)
        "maribank" -> Triple("MARIBANK", Color(0xFFFFECE5), Color(0xFFE64A19))
        "seabank" -> Triple("SEABANK", Color(0xFFFFF3E0), Color(0xFFEF6C00))
        "gotyme" -> Triple("GOTYME", Color(0xFFE0F7F6), Color(0xFF00796B))
        "bpi" -> Triple("BPI", Color(0xFFFFEBEE), Color(0xFFC62828))
        "bdo" -> Triple("BDO", Color(0xFFE3F2FD), Color(0xFF0D47A1))
        "dbp" -> Triple("DBP", Color(0xFFE8F5E9), Color(0xFF1B5E20))
        "unionbank" -> Triple("UNIONBANK", Color(0xFFFFF3E0), Color(0xFFE65100))
        "metrobank" -> Triple("METROBANK", Color(0xFFEDE7F6), Color(0xFF4A148C))
        "landbank" -> Triple("LANDBANK", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "rcbc" -> Triple("RCBC", Color(0xFFE3F2FD), Color(0xFF1565C0))
        "cash" -> Triple("CASH", Color(0xFFECFDF5), Color(0xFF059669))
        "bank" -> Triple("BANK", Color(0xFFF3E8FF), Color(0xFF6B21A8))
        else -> Triple("GCASH", GCashContainer, GCashBlue)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun EmptyPaymentsView(onSimulateClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "💰", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No payments in ledger yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "When money arrives via GCash, Maya, or Bank notifications, it will be automatically recorded here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )
        Button(
            onClick = onSimulateClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ResiboPrimary)
        ) {
            Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Simulate Incoming Payment")
        }
    }
}

@Composable
fun SimulatePaymentDialog(
    onDismiss: () -> Unit,
    onSimulate: (provider: String, amount: Double, sender: String, ref: String) -> Unit
) {
    var provider by remember { mutableStateOf("gcash") }
    var amountText by remember { mutableStateOf("150.00") }
    var senderText by remember { mutableStateOf("JUAN DELA CRUZ 0917****123") }
    var refText by remember {
        mutableStateOf(System.currentTimeMillis().toString().takeLast(12))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simulate Incoming Payment (Demo)", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Posts a real local notification to test notification capture and ledger recording.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        "rcbc" to "RCBC"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = provider == key,
                            onClick = { provider = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₱)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = senderText,
                    onValueChange = { senderText = it },
                    label = { Text("Sender Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = refText,
                    onValueChange = { refText = it },
                    label = { Text("Reference Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 100.0
                    onSimulate(provider, amount, senderText, refText)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ResiboPrimary)
            ) {
                Text("Post & Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
