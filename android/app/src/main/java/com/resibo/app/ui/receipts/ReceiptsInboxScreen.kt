package com.resibo.app.ui.receipts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resibo.app.model.Business
import com.resibo.app.model.Receipt
import com.resibo.app.model.ReceiptVerdict
import com.resibo.app.theme.*
import com.resibo.app.viewmodel.ReceiptUiState
import com.resibo.app.viewmodel.ReceiptsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class ReceiptFilter {
    ALL, RECEIVED, FRAUD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsInboxScreen(
    business: Business,
    receiptsViewModel: ReceiptsViewModel,
    isNotificationGranted: Boolean = false,
    onReceiptClick: (Receipt) -> Unit,
    onNavigateToShare: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onNavigateToPos: () -> Unit,
    onNavigateToNotificationSetup: () -> Unit
) {
    val uiState by receiptsViewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf(ReceiptFilter.ALL) }

    LaunchedEffect(business.id) {
        receiptsViewModel.listenToReceipts(business.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = business.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { if (!isNotificationGranted) onNavigateToNotificationSetup() }
                                .padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (isNotificationGranted) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isNotificationGranted) "Live Verification Active" else "Verification Inactive (Tap to Enable)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToPos,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.PointOfSale, contentDescription = "POS & Sales", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onNavigateToPayments,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = "Payments Ledger", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onNavigateToNotificationSetup,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notification Capture", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onNavigateToShare,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = "Share Link & QR", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
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
                is ReceiptUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ResiboPrimary)
                    }
                }
                is ReceiptUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is ReceiptUiState.Success -> {
                    val allReceipts = state.receipts

                    val filteredReceipts = remember(allReceipts, selectedFilter) {
                        when (selectedFilter) {
                            ReceiptFilter.ALL -> allReceipts
                            ReceiptFilter.RECEIVED -> allReceipts.filter { it.verdict == ReceiptVerdict.RECEIPT_RECEIVED }
                            ReceiptFilter.FRAUD -> allReceipts.filter { it.verdict == ReceiptVerdict.FRAUD_RECEIPT }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 0. Notification Capture Setup Prompt (if not granted)
                        if (!isNotificationGranted) {
                            item {
                                Surface(
                                    onClick = onNavigateToNotificationSetup,
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFFFFBEB),
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Enable Live Payment Capture",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF92400E),
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Grant access so Resibo auto-verifies incoming GCash & Maya payments.",
                                                fontSize = 12.sp,
                                                color = Color(0xFFB45309),
                                                lineHeight = 16.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Turn On ›",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = ResiboPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // 1. Home Dashboard Metrics Card
                        item {
                            DashboardMetricsCard(receipts = allReceipts)
                        }

                        // 1b. POS & Sales Analytics Quick Access Card
                        item {
                            Surface(
                                onClick = onNavigateToPos,
                                shape = RoundedCornerShape(14.dp),
                                color = ResiboSurface,
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = ResiboPrimary.copy(alpha = 0.12f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.PointOfSale,
                                                    contentDescription = null,
                                                    tint = ResiboPrimary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "POS & Sales Analytics",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Daily, monthly, annual sales & payment history",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = "View ›",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = ResiboPrimary
                                    )
                                }
                            }
                        }

                        // 2. Buyer Upload Link Card
                        item {
                            Surface(
                                onClick = onNavigateToShare,
                                shape = RoundedCornerShape(14.dp),
                                color = ResiboSurface,
                                shadowElevation = 3.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.QrCode,
                                            contentDescription = null,
                                            tint = ResiboPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "resibo-ph.web.app/pay/${business.slug}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ResiboPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "QR Code ›",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ResiboPrimary
                                    )
                                }
                            }
                        }

                        // 2. Filter Tabs
                        item {
                            FilterTabsRow(
                                selected = selectedFilter,
                                onSelect = { selectedFilter = it },
                                allCount = allReceipts.size,
                                receivedCount = allReceipts.count { it.verdict == ReceiptVerdict.RECEIPT_RECEIVED },
                                fraudCount = allReceipts.count { it.verdict == ReceiptVerdict.FRAUD_RECEIPT }
                            )
                        }

                        // 3. Receipt List
                        if (filteredReceipts.isEmpty()) {
                            item {
                                EmptyFilteredView(filter = selectedFilter, onShareClick = onNavigateToShare)
                            }
                        } else {
                            items(filteredReceipts, key = { it.id }) { receipt ->
                                ReceiptCard(
                                    receipt = receipt,
                                    onClick = { onReceiptClick(receipt) }
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
fun DashboardMetricsCard(receipts: List<Receipt>) {
    val calendar = Calendar.getInstance()
    val todayDay = calendar.get(Calendar.DAY_OF_YEAR)
    val todayYear = calendar.get(Calendar.YEAR)

    // Calculate today's received ₱
    val todayReceivedTotal = receipts.filter {
        it.verdict == ReceiptVerdict.RECEIPT_RECEIVED && it.createdAt != null && run {
            val c = Calendar.getInstance().apply { time = it.createdAt.toDate() }
            c.get(Calendar.DAY_OF_YEAR) == todayDay && c.get(Calendar.YEAR) == todayYear
        }
    }.sumOf { it.expectedAmount ?: 0.0 }

    val receivedCount = receipts.count { it.verdict == ReceiptVerdict.RECEIPT_RECEIVED }
    val fraudCaughtTotal = receipts.count { it.verdict == ReceiptVerdict.FRAUD_RECEIPT }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
                    text = "Today's Activity",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Surface(
                    color = VerdictFakeContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = VerdictFake, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$fraudCaughtTotal Fraud Caught",
                            color = VerdictFake,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(
                    label = "Received Total",
                    value = "₱${String.format(Locale.US, "%,.2f", todayReceivedTotal)}",
                    valueColor = ResiboPrimary
                )
                MetricColumn(
                    label = "Received",
                    value = "$receivedCount",
                    valueColor = VerdictVerified
                )
                MetricColumn(
                    label = "Fraud Blocked",
                    value = "$fraudCaughtTotal",
                    valueColor = VerdictFake
                )
            }
        }
    }
}

@Composable
fun MetricColumn(label: String, value: String, valueColor: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = valueColor)
    }
}

@Composable
fun FilterTabsRow(
    selected: ReceiptFilter,
    onSelect: (ReceiptFilter) -> Unit,
    allCount: Int,
    receivedCount: Int,
    fraudCount: Int
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = selected == ReceiptFilter.ALL,
                onClick = { onSelect(ReceiptFilter.ALL) },
                label = { Text("All ($allCount)") }
            )
        }
        item {
            FilterChip(
                selected = selected == ReceiptFilter.RECEIVED,
                onClick = { onSelect(ReceiptFilter.RECEIVED) },
                label = { Text("Received ($receivedCount)") }
            )
        }
        item {
            FilterChip(
                selected = selected == ReceiptFilter.FRAUD,
                onClick = { onSelect(ReceiptFilter.FRAUD) },
                label = { Text("Fraud ($fraudCount)") }
            )
        }
    }
}

@Composable
fun ReceiptCard(receipt: Receipt, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()) }
    val formattedTime = remember(receipt.createdAt) {
        receipt.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Just now"
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ResiboSurface,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VerdictBadge(verdict = receipt.verdict)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (receipt.isOnlineTransaction) ResiboPrimary.copy(alpha = 0.10f) else Color(0xFF059669).copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = if (receipt.isOnlineTransaction) "🌐 Online" else "🏬 In-Store",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (receipt.isOnlineTransaction) ResiboPrimary else Color(0xFF059669),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = receipt.buyerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (receipt.note.isNotBlank()) {
                        Text(
                            text = receipt.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                receipt.expectedAmount?.let { amount ->
                    Text(
                        text = "₱${String.format(Locale.US, "%,.2f", amount)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = ResiboPrimary
                    )
                }
            }

            if (receipt.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                receipt.reasons.take(1).forEach { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyFilteredView(filter: ReceiptFilter, onShareClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "📥", fontSize = 40.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (filter) {
                ReceiptFilter.ALL -> "No receipts found"
                ReceiptFilter.RECEIVED -> "No received receipts yet"
                ReceiptFilter.FRAUD -> "No fraud receipts detected"
            },
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
fun VerdictBadge(verdict: ReceiptVerdict) {
    val (text, bgColor, textColor) = when (verdict) {
        ReceiptVerdict.FRAUD_RECEIPT -> Triple("🚨 Fraud Receipt", VerdictFakeContainer, VerdictFake)
        else -> Triple("✅ Receipt Received", VerdictVerifiedContainer, VerdictVerified)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
