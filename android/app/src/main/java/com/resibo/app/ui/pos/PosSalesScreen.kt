package com.resibo.app.ui.pos

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resibo.app.model.Business
import com.resibo.app.model.Payment
import com.resibo.app.theme.*
import com.resibo.app.ui.payments.ProviderBadgePill
import com.resibo.app.viewmodel.PaymentsUiState
import com.resibo.app.viewmodel.PaymentsViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class TimeframeFilter {
    TODAY,
    YESTERDAY,
    THIS_MONTH,
    THIS_YEAR,
    ALL_TIME,
    SPECIFIC_DATE
}

data class ChannelStats(
    val provider: String,
    val displayName: String,
    val totalAmount: Double,
    val count: Int,
    val percentage: Float,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosSalesScreen(
    business: Business,
    paymentsViewModel: PaymentsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by paymentsViewModel.uiState.collectAsState()

    var selectedTimeframe by remember { mutableStateOf(TimeframeFilter.ALL_TIME) }
    var selectedSpecificCalendar by remember { mutableStateOf<Calendar?>(null) }
    var selectedChannelFilter by remember { mutableStateOf<String?>(null) } // null = all channels
    var showRecordSaleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(business.id) {
        paymentsViewModel.listenToPayments(business.id)
    }

    val allPayments = remember(uiState) {
        when (val state = uiState) {
            is PaymentsUiState.Success -> state.payments
            else -> emptyList()
        }
    }

    // Reference Calendars
    val now = remember { Calendar.getInstance() }
    val curYear = now.get(Calendar.YEAR)
    val curMonth = now.get(Calendar.MONTH)
    val curDayOfYear = now.get(Calendar.DAY_OF_YEAR)

    // 1. Overall Metrics: Daily, Monthly, Annual Sales
    val dailySales = remember(allPayments) {
        allPayments.filter { p ->
            p.receivedAt?.toDate()?.let { d ->
                val c = Calendar.getInstance().apply { time = d }
                c.get(Calendar.YEAR) == curYear && c.get(Calendar.DAY_OF_YEAR) == curDayOfYear
            } ?: false
        }
    }
    val dailyTotalAmount = remember(dailySales) { dailySales.sumOf { it.amount } }
    val dailyTxnCount = remember(dailySales) { dailySales.size }

    val monthlySales = remember(allPayments) {
        allPayments.filter { p ->
            p.receivedAt?.toDate()?.let { d ->
                val c = Calendar.getInstance().apply { time = d }
                c.get(Calendar.YEAR) == curYear && c.get(Calendar.MONTH) == curMonth
            } ?: false
        }
    }
    val monthlyTotalAmount = remember(monthlySales) { monthlySales.sumOf { it.amount } }
    val monthlyTxnCount = remember(monthlySales) { monthlySales.size }

    val annualSales = remember(allPayments) {
        allPayments.filter { p ->
            p.receivedAt?.toDate()?.let { d ->
                val c = Calendar.getInstance().apply { time = d }
                c.get(Calendar.YEAR) == curYear
            } ?: false
        }
    }
    val annualTotalAmount = remember(annualSales) { annualSales.sumOf { it.amount } }
    val annualTxnCount = remember(annualSales) { annualSales.size }

    // 2. Filtered Payments based on timeframe & date picker
    val timeFilteredPayments = remember(allPayments, selectedTimeframe, selectedSpecificCalendar) {
        when (selectedTimeframe) {
            TimeframeFilter.TODAY -> dailySales
            TimeframeFilter.YESTERDAY -> {
                val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                val yYear = yesterday.get(Calendar.YEAR)
                val yDay = yesterday.get(Calendar.DAY_OF_YEAR)
                allPayments.filter { p ->
                    p.receivedAt?.toDate()?.let { d ->
                        val c = Calendar.getInstance().apply { time = d }
                        c.get(Calendar.YEAR) == yYear && c.get(Calendar.DAY_OF_YEAR) == yDay
                    } ?: false
                }
            }
            TimeframeFilter.THIS_MONTH -> monthlySales
            TimeframeFilter.THIS_YEAR -> annualSales
            TimeframeFilter.SPECIFIC_DATE -> {
                val target = selectedSpecificCalendar
                if (target == null) allPayments else {
                    val tYear = target.get(Calendar.YEAR)
                    val tDay = target.get(Calendar.DAY_OF_YEAR)
                    allPayments.filter { p ->
                        p.receivedAt?.toDate()?.let { d ->
                            val c = Calendar.getInstance().apply { time = d }
                            c.get(Calendar.YEAR) == tYear && c.get(Calendar.DAY_OF_YEAR) == tDay
                        } ?: false
                    }
                }
            }
            TimeframeFilter.ALL_TIME -> allPayments
        }
    }

    // 3. Channel breakdown calculation for the filtered timeframe
    val channelStatsList = remember(timeFilteredPayments) {
        val total = timeFilteredPayments.sumOf { it.amount }
        val grouped = timeFilteredPayments.groupBy { it.provider.lowercase() }

        val providerOrder = listOf(
            "gcash", "maya", "maribank", "seabank", "bdo", "bpi",
            "gotyme", "unionbank", "dbp", "metrobank", "landbank", "rcbc", "cash"
        )
        val allKnownKeys = (providerOrder + grouped.keys).distinct()

        allKnownKeys.mapNotNull { key ->
            val paymentsInKey = grouped[key] ?: emptyList()
            if (paymentsInKey.isEmpty() && total > 0) null
            else {
                val sum = paymentsInKey.sumOf { it.amount }
                val count = paymentsInKey.size
                val pct = if (total > 0) (sum / total).toFloat() else 0f
                val (dispName, color) = when (key) {
                    "gcash" -> "GCash" to GCashBlue
                    "maya" -> "Maya" to MayaGreen
                    "maribank" -> "MariBank" to Color(0xFFE64A19)
                    "seabank" -> "SeaBank" to Color(0xFFEF6C00)
                    "gotyme" -> "GoTyme" to Color(0xFF00796B)
                    "cash" -> "Cash" to Color(0xFF059669)
                    "bpi" -> "BPI" to Color(0xFFC62828)
                    "bdo" -> "BDO" to Color(0xFF0D47A1)
                    "dbp" -> "DBP" to Color(0xFF1B5E20)
                    "unionbank" -> "UnionBank" to Color(0xFFE65100)
                    "metrobank" -> "Metrobank" to Color(0xFF4A148C)
                    "landbank" -> "LandBank" to Color(0xFF2E7D32)
                    "rcbc" -> "RCBC" to Color(0xFF1565C0)
                    "bank" -> "Bank" to Color(0xFF6B21A8)
                    else -> key.replaceFirstChar { it.uppercase() } to Color(0xFF475569)
                }
                ChannelStats(
                    provider = key,
                    displayName = dispName,
                    totalAmount = sum,
                    count = count,
                    percentage = pct,
                    color = color
                )
            }
        }.sortedByDescending { it.totalAmount }
    }

    // 4. Final payments list (filtered by timeframe AND provider channel if clicked)
    val displayPayments = remember(timeFilteredPayments, selectedChannelFilter) {
        if (selectedChannelFilter == null) {
            timeFilteredPayments
        } else {
            timeFilteredPayments.filter { it.provider.equals(selectedChannelFilter, ignoreCase = true) }
        }
    }

    // Native Date Picker Launcher
    fun openDatePicker() {
        val initCal = selectedSpecificCalendar ?: Calendar.getInstance()
        val dialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedSpecificCalendar = chosen
                selectedTimeframe = TimeframeFilter.SPECIFIC_DATE
            },
            initCal.get(Calendar.YEAR),
            initCal.get(Calendar.MONTH),
            initCal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "POS & Sales Analytics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = business.name,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showRecordSaleDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White,
                            contentColor = ResiboPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Record Sale", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            when (uiState) {
                is PaymentsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ResiboPrimary)
                    }
                }
                is PaymentsUiState.Error -> {
                    val msg = (uiState as PaymentsUiState.Error).message
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(text = "Error: $msg", color = MaterialTheme.colorScheme.error)
                    }
                }
                is PaymentsUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section 1: Overview Cards (Daily, Monthly, Annual Sales)
                        item {
                            SalesMetricsRow(
                                dailyTotal = dailyTotalAmount,
                                dailyCount = dailyTxnCount,
                                monthlyTotal = monthlyTotalAmount,
                                monthlyCount = monthlyTxnCount,
                                annualTotal = annualTotalAmount,
                                annualCount = annualTxnCount,
                                onSelectToday = { selectedTimeframe = TimeframeFilter.TODAY },
                                onSelectMonth = { selectedTimeframe = TimeframeFilter.THIS_MONTH },
                                onSelectYear = { selectedTimeframe = TimeframeFilter.THIS_YEAR }
                            )
                        }

                        // Section 2: Date History Filter & Timeframe Selector
                        item {
                            DateHistoryFilterCard(
                                selectedTimeframe = selectedTimeframe,
                                selectedDate = selectedSpecificCalendar,
                                onSelectTimeframe = { tf ->
                                    selectedTimeframe = tf
                                    if (tf != TimeframeFilter.SPECIFIC_DATE) {
                                        selectedSpecificCalendar = null
                                    }
                                },
                                onOpenDatePicker = { openDatePicker() },
                                onClearFilter = {
                                    selectedTimeframe = TimeframeFilter.ALL_TIME
                                    selectedSpecificCalendar = null
                                    selectedChannelFilter = null
                                },
                                filteredTotalAmount = timeFilteredPayments.sumOf { it.amount },
                                filteredTxnCount = timeFilteredPayments.size
                            )
                        }

                        // Section 3: Channel Breakdown (GCash, Maya, MariBank, SeaBank, Cash, etc.)
                        item {
                            ChannelBreakdownCard(
                                channelStats = channelStatsList,
                                selectedChannel = selectedChannelFilter,
                                onToggleChannel = { key ->
                                    selectedChannelFilter = if (selectedChannelFilter == key) null else key
                                }
                            )
                        }

                        // Section 4: Transaction List Header & Count
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Payment History",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "${displayPayments.size} transaction${if (displayPayments.size == 1) "" else "s"}" +
                                            if (selectedChannelFilter != null) " · Filtered by ${selectedChannelFilter?.uppercase()}" else "",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (selectedChannelFilter != null || selectedTimeframe != TimeframeFilter.ALL_TIME) {
                                    TextButton(
                                        onClick = {
                                            selectedChannelFilter = null
                                            selectedTimeframe = TimeframeFilter.ALL_TIME
                                            selectedSpecificCalendar = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reset Filters", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Section 5: Transaction History Items
                        if (displayPayments.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = ResiboSurface,
                                    shadowElevation = 1.dp
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = "🧾", fontSize = 40.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No payments found",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "No transactions match the selected date or payment channel. Tap '+ Record Sale' to record one.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                        )
                                        OutlinedButton(
                                            onClick = { showRecordSaleDialog = true },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Record POS Sale")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(displayPayments, key = { it.id }) { payment ->
                                PosPaymentItemCard(payment = payment)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRecordSaleDialog) {
        RecordPosSaleDialog(
            onDismiss = { showRecordSaleDialog = false },
            onSave = { provider, amount, customer, ref ->
                paymentsViewModel.recordPosSale(
                    context = context,
                    businessId = business.id,
                    provider = provider,
                    amount = amount,
                    customerName = customer,
                    referenceNumber = ref
                )
                showRecordSaleDialog = false
            }
        )
    }
}

// -------------------------------------------------------------
// Component 1: Daily, Monthly, and Annual Sales Metrics Row
// -------------------------------------------------------------
@Composable
fun SalesMetricsRow(
    dailyTotal: Double,
    dailyCount: Int,
    monthlyTotal: Double,
    monthlyCount: Int,
    annualTotal: Double,
    annualCount: Int,
    onSelectToday: () -> Unit,
    onSelectMonth: () -> Unit,
    onSelectYear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Sales Performance",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Daily Sales Card
            SalesMetricCard(
                modifier = Modifier.weight(1f),
                title = "Today",
                amount = dailyTotal,
                txnCount = dailyCount,
                accentColor = Color(0xFF007DFE),
                icon = Icons.Default.Today,
                onClick = onSelectToday
            )

            // 2. Monthly Sales Card
            SalesMetricCard(
                modifier = Modifier.weight(1f),
                title = "This Month",
                amount = monthlyTotal,
                txnCount = monthlyCount,
                accentColor = Color(0xFF00D66E),
                icon = Icons.Default.DateRange,
                onClick = onSelectMonth
            )

            // 3. Annual Sales Card
            SalesMetricCard(
                modifier = Modifier.weight(1f),
                title = "Annual (${Calendar.getInstance().get(Calendar.YEAR)})",
                amount = annualTotal,
                txnCount = annualCount,
                accentColor = Color(0xFF7C3AED),
                icon = Icons.Default.TrendingUp,
                onClick = onSelectYear
            )
        }
    }
}

@Composable
fun SalesMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: Double,
    txnCount: Int,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = ResiboSurface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "₱${String.format(Locale.US, "%,.2f", amount)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "$txnCount txn${if (txnCount == 1) "" else "s"}",
                fontSize = 11.sp,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// -------------------------------------------------------------
// Component 2: Date History Filter & DatePicker Card
// -------------------------------------------------------------
@Composable
fun DateHistoryFilterCard(
    selectedTimeframe: TimeframeFilter,
    selectedDate: Calendar?,
    onSelectTimeframe: (TimeframeFilter) -> Unit,
    onOpenDatePicker: () -> Unit,
    onClearFilter: () -> Unit,
    filteredTotalAmount: Double,
    filteredTxnCount: Int
) {
    val dateDisplayFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ResiboSurface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = ResiboPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "History by Date",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                FilledTonalButton(
                    onClick = onOpenDatePicker,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = ResiboPrimary.copy(alpha = 0.12f),
                        contentColor = ResiboPrimary
                    )
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pick a Date", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timeframe quick filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    TimeframeFilter.ALL_TIME to "All Time",
                    TimeframeFilter.TODAY to "Today",
                    TimeframeFilter.YESTERDAY to "Yesterday",
                    TimeframeFilter.THIS_MONTH to "This Month",
                    TimeframeFilter.THIS_YEAR to "This Year"
                ).forEach { (tf, label) ->
                    FilterChip(
                        selected = selectedTimeframe == tf,
                        onClick = { onSelectTimeframe(tf) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (selectedTimeframe == tf) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Date Filter Summary Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF8FAFC)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val activeLabel = when {
                            selectedTimeframe == TimeframeFilter.SPECIFIC_DATE && selectedDate != null ->
                                dateDisplayFormat.format(selectedDate.time)
                            selectedTimeframe == TimeframeFilter.TODAY -> "Today's Sales"
                            selectedTimeframe == TimeframeFilter.YESTERDAY -> "Yesterday's Sales"
                            selectedTimeframe == TimeframeFilter.THIS_MONTH -> "This Month's Sales"
                            selectedTimeframe == TimeframeFilter.THIS_YEAR -> "Annual Sales (${Calendar.getInstance().get(Calendar.YEAR)})"
                            else -> "All-Time Sales"
                        }

                        Text(
                            text = activeLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$filteredTxnCount transaction${if (filteredTxnCount == 1) "" else "s"} recorded",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "₱${String.format(Locale.US, "%,.2f", filteredTotalAmount)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = ResiboPrimary
                        )
                        if (selectedTimeframe != TimeframeFilter.ALL_TIME) {
                            Text(
                                text = "Clear Filter ›",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ResiboPrimary,
                                modifier = Modifier.clickable { onClearFilter() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Component 3: Sales by Payment Channel & Categorization
// -------------------------------------------------------------
@Composable
fun ChannelBreakdownCard(
    channelStats: List<ChannelStats>,
    selectedChannel: String?,
    onToggleChannel: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ResiboSurface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = ResiboPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sales by Payment Channel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                if (selectedChannel != null) {
                    Text(
                        text = "Filter: ${selectedChannel.uppercase()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ResiboPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (channelStats.isEmpty()) {
                Text(
                    text = "No channel breakdown available for this timeframe.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                // Stacked Ratio Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    channelStats.forEach { stat ->
                        if (stat.percentage > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(stat.percentage)
                                    .fillMaxHeight()
                                    .background(stat.color)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Channel breakdown list / chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    channelStats.forEach { stat ->
                        val isSelected = selectedChannel?.equals(stat.provider, ignoreCase = true) == true
                        Surface(
                            onClick = { onToggleChannel(stat.provider) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) stat.color.copy(alpha = 0.12f) else Color(0xFFF8FAFC),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, stat.color) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(stat.color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stat.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = stat.color.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${stat.count} txn${if (stat.count == 1) "" else "s"}",
                                            color = stat.color,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "₱${String.format(Locale.US, "%,.2f", stat.totalAmount)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(${String.format(Locale.US, "%.0f%%", stat.percentage * 100)})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Component 4: Transaction Item Card
// -------------------------------------------------------------
@Composable
fun PosPaymentItemCard(payment: Payment) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    val formattedTime = remember(payment.receivedAt) {
        payment.receivedAt?.toDate()?.let { dateFormat.format(it) } ?: "Just now"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ResiboSurface,
        shadowElevation = 1.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderBadgePill(provider = payment.provider)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = payment.senderNameMasked.ifBlank { "Walk-in Customer" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (!payment.referenceNumber.isNullOrBlank()) "Ref: ${payment.referenceNumber}" else "No Reference Number",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "$formattedTime • ${if (payment.source == "pos") "POS Entry" else "Live Alert"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Text(
                text = "₱${String.format(Locale.US, "%,.2f", payment.amount)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = ResiboPrimary
            )
        }
    }
}

// -------------------------------------------------------------
// Component 5: Record POS Sale Dialog (Manual Entry)
// -------------------------------------------------------------
@Composable
fun RecordPosSaleDialog(
    onDismiss: () -> Unit,
    onSave: (provider: String, amount: Double, customer: String, ref: String) -> Unit
) {
    var provider by remember { mutableStateOf("cash") }
    var amountText by remember { mutableStateOf("") }
    var customerText by remember { mutableStateOf("") }
    var refText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record POS Sale", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Ring up an over-the-counter payment. This updates daily, monthly, and annual sales in real time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Payment Method", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))

                // Provider Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "cash" to "Cash",
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
                            selected = provider == key,
                            onClick = { provider = key },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₱)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customerText,
                    onValueChange = { customerText = it },
                    label = { Text("Customer Name / Order Note") },
                    placeholder = { Text("e.g., Table 4 or Walk-in") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = refText,
                    onValueChange = { refText = it },
                    label = { Text("Reference Number (Optional)") },
                    placeholder = { Text("Auto-generated if empty") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        onSave(provider, amount, customerText, refText)
                    }
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = ResiboPrimary)
            ) {
                Text("Save Sale")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
