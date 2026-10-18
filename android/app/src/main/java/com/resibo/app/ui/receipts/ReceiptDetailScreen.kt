package com.resibo.app.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.resibo.app.model.Receipt
import com.resibo.app.model.ReceiptVerdict
import com.resibo.app.ocr.ReceiptOcrScanner
import com.resibo.app.theme.*
import com.resibo.app.viewmodel.ReceiptsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    receipt: Receipt,
    businessId: String,
    receiptsViewModel: ReceiptsViewModel,
    onNavigateBack: () -> Unit
) {
    var currentReceipt by remember { mutableStateOf(receipt) }
    var isOcrScanning by remember { mutableStateOf(false) }
    var receiptBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(currentReceipt.id) {
        withContext(Dispatchers.IO) {
            val bmp = ReceiptOcrScanner.decodeBitmap(currentReceipt.imageUrl.ifBlank { currentReceipt.imagePath })
            withContext(Dispatchers.Main) {
                receiptBitmap = bmp
            }
        }

        val payments = receiptsViewModel.getPayments()
        isOcrScanning = true
        val updated = ReceiptOcrScanner.scanAndVerify(
            receipt = currentReceipt,
            incomingPayments = payments
        )
        currentReceipt = updated
        receiptsViewModel.saveVerifiedReceipt(businessId, updated)
        isOcrScanning = false
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    val formattedTime = remember(currentReceipt.createdAt) {
        currentReceipt.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Just now"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipt Details") },
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
                .padding(16.dp)
        ) {

            // Top Verdict Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (currentReceipt.verdict == ReceiptVerdict.FRAUD_RECEIPT) VerdictFakeContainer else VerdictVerifiedContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VerdictBadge(verdict = currentReceipt.verdict)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (currentReceipt.isOnlineTransaction) ResiboPrimary.copy(alpha = 0.12f) else Color(0xFF059669).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = if (currentReceipt.isOnlineTransaction) "🌐 Online" else "🏬 In-Store",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentReceipt.isOnlineTransaction) ResiboPrimary else Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        currentReceipt.expectedAmount?.let { amt ->
                            Text(
                                text = "₱${String.format(Locale.US, "%,.2f", amt)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                color = if (currentReceipt.verdict == ReceiptVerdict.FRAUD_RECEIPT) VerdictFake else VerdictVerified
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Submitted by ${currentReceipt.buyerName} on $formattedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (currentReceipt.reasons.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        currentReceipt.reasons.forEach { reason ->
                            Text(
                                text = "• $reason",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manual Override Buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ResiboSurface,
                shadowElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Manual Overrides",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                currentReceipt = currentReceipt.copy(
                                    verdict = ReceiptVerdict.RECEIPT_RECEIVED,
                                    matchMethod = "manual",
                                    reasons = listOf("Manually marked as received by merchant (Manual override).")
                                )
                                receiptsViewModel.overrideReceipt(businessId, currentReceipt.id, "mark_received")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Mark as Received", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                currentReceipt = currentReceipt.copy(
                                    verdict = ReceiptVerdict.FRAUD_RECEIPT,
                                    matchMethod = "manual",
                                    reasons = listOf("Manually marked as fraud by merchant (Manual override).")
                                )
                                receiptsViewModel.overrideReceipt(businessId, currentReceipt.id, "mark_fake")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Mark as Fraud", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Forensics Analysis Card
            val forensics = currentReceipt.forensics
            if (forensics != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = ResiboSurface,
                    shadowElevation = 3.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔬 Image Forensics Analysis",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )

                            val (riskText, riskBg, riskFg) = when (forensics.tamperRisk.lowercase()) {
                                "high" -> Triple("HIGH RISK", VerdictFakeContainer, VerdictFake)
                                "medium" -> Triple("MEDIUM RISK", VerdictPendingContainer, VerdictPending)
                                else -> Triple("LOW RISK", VerdictVerifiedContainer, VerdictVerified)
                            }
                            Surface(color = riskBg, shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    text = riskText,
                                    color = riskFg,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (forensics.explanation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = forensics.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (forensics.editedSigns.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Detected editing signs:",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            forensics.editedSigns.forEach { sign ->
                                Text(
                                    text = "• $sign",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Receipt Image Preview
            if (receiptBitmap != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = ResiboSurface,
                    shadowElevation = 3.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "🧾 Receipt Image",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Image(
                            bitmap = receiptBitmap!!.asImageBitmap(),
                            contentDescription = "Uploaded Receipt",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Extracted Fields Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ResiboSurface,
                shadowElevation = 3.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📋 Extracted Information",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (isOcrScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = ResiboPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    val ext = currentReceipt.extracted
                    val providerText = when {
                        isOcrScanning -> "Scanning receipt..."
                        ext != null && ext.provider != "UNKNOWN" -> ext.provider.uppercase()
                        else -> "Pending scan"
                    }
                    val refText = when {
                        isOcrScanning -> "Reading reference number..."
                        ext != null && !ext.referenceNumber.isNullOrBlank() -> ext.referenceNumber
                        else -> "None detected"
                    }
                    val recipientDisplay = if (ext != null && ext.recipientName.isNotBlank()) {
                        if (ext.recipientNumberLast4.isNotBlank()) "${ext.recipientName} (••••${ext.recipientNumberLast4})" else ext.recipientName
                    } else {
                        "Store Owner"
                    }

                    ExtractedRow("Provider", providerText)
                    ExtractedRow("Reference Number", refText)
                    ExtractedRow("Sender", ext?.senderName?.ifBlank { currentReceipt.buyerName } ?: currentReceipt.buyerName)
                    ExtractedRow("Recipient", recipientDisplay)
                    ExtractedRow("Status", ext?.transactionStatus?.uppercase() ?: "SUCCESSFUL")
                    if (currentReceipt.note.isNotBlank()) {
                        ExtractedRow("Order Note", currentReceipt.note)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Verification Checklist Card
            if (currentReceipt.checks.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = ResiboSurface,
                    shadowElevation = 3.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "✔️ Verification Checks",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        currentReceipt.checks.forEach { check ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (check.passed) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (check.passed) VerdictVerified else VerdictFake,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = check.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExtractedRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}
