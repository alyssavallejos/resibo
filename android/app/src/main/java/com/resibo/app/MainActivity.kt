package com.resibo.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import com.resibo.app.model.Business
import com.resibo.app.model.Receipt
import com.resibo.app.service.PaymentNotificationListener
import com.resibo.app.theme.ResiboPrimary
import com.resibo.app.theme.ResiboSurface
import com.resibo.app.theme.ResiboTheme
import com.resibo.app.ui.auth.AuthScreen
import com.resibo.app.ui.onboarding.OnboardingScreen
import com.resibo.app.ui.payments.PaymentsReceivedScreen
import com.resibo.app.ui.pos.PosSalesScreen
import com.resibo.app.ui.receipts.ReceiptDetailScreen
import com.resibo.app.ui.receipts.ReceiptsInboxScreen
import com.resibo.app.ui.setup.BusinessSetupScreen
import com.resibo.app.ui.setup.NotificationSetupScreen
import com.resibo.app.ui.share.ShareLinkScreen
import com.resibo.app.viewmodel.*

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val businessViewModel: BusinessViewModel by viewModels()
    private val receiptsViewModel: ReceiptsViewModel by viewModels()
    private val paymentsViewModel: PaymentsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ResiboTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ResiboNavApp(
                        context = this,
                        authViewModel = authViewModel,
                        businessViewModel = businessViewModel,
                        receiptsViewModel = receiptsViewModel,
                        paymentsViewModel = paymentsViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun ResiboNavApp(
    context: Context,
    authViewModel: AuthViewModel,
    businessViewModel: BusinessViewModel,
    receiptsViewModel: ReceiptsViewModel,
    paymentsViewModel: PaymentsViewModel
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()
    val businessState by businessViewModel.businessState.collectAsState()

    val prefs = remember { context.getSharedPreferences("resibo_prefs", Context.MODE_PRIVATE) }
    var isOnboardingCompleted by remember {
        mutableStateOf(prefs.getBoolean("onboarding_completed", false))
    }

    val savedBizId = prefs.getString("biz_id", null) ?: prefs.getString("active_business_id", null)
    val savedBizName = prefs.getString("biz_name", null)
    val savedBizSlug = prefs.getString("biz_slug", null)
    val savedBizOwner = prefs.getString("biz_owner_uid", null) ?: prefs.getString("auth_uid", null)

    var activeBusiness by remember {
        mutableStateOf<Business?>(
            if (!savedBizId.isNullOrBlank() && !savedBizName.isNullOrBlank() && !savedBizSlug.isNullOrBlank() && !savedBizOwner.isNullOrBlank()) {
                Business(
                    id = savedBizId,
                    name = savedBizName,
                    slug = savedBizSlug,
                    ownerUid = savedBizOwner
                )
            } else null
        )
    }
    var selectedReceipt by remember { mutableStateOf<Receipt?>(null) }

    val isNotificationGranted by paymentsViewModel.isNotificationAccessGranted.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        com.resibo.app.audio.PaymentAudioAnnouncer.initialize(context)
        val savedUid = prefs.getString("auth_uid", null) ?: prefs.getString("biz_owner_uid", null)
        if (!savedUid.isNullOrBlank()) {
            businessViewModel.loadBusinessForOwner(savedUid, context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        paymentsViewModel.checkNotificationAccess(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                paymentsViewModel.checkNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(authState, isOnboardingCompleted) {
        if (!isOnboardingCompleted) {
            return@LaunchedEffect
        }
        when (val state = authState) {
            is AuthState.Authenticated -> {
                businessViewModel.loadBusinessForOwner(state.uid, context)
            }
            is AuthState.Unauthenticated -> {
                val savedUid = prefs.getString("auth_uid", null) ?: prefs.getString("biz_owner_uid", null)
                // Only navigate to auth if there is genuinely no saved session
                if (savedUid.isNullOrBlank()) {
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> Unit
        }
    }

    LaunchedEffect(businessState, isOnboardingCompleted) {
        if (!isOnboardingCompleted) {
            return@LaunchedEffect
        }
        when (val state = businessState) {
            is BusinessState.Loaded -> {
                activeBusiness = state.business
                // Update active business for PaymentNotificationListener
                PaymentNotificationListener.currentBusinessId = state.business.id
                prefs.edit().putString("active_business_id", state.business.id).apply()

                val currentDest = navController.currentBackStackEntry?.destination?.route
                // Only navigate to inbox if currently on a transition screen (auth / setup / onboarding)
                // NEVER interrupt the merchant if they are already using pos, payments, detail, etc.
                if (currentDest == "auth" || currentDest == "setup" || currentDest == "onboarding") {
                    navController.navigate("inbox") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is BusinessState.NotFound -> {
                val currentDest = navController.currentBackStackEntry?.destination?.route
                if (currentDest == "auth" || currentDest == "inbox") {
                    navController.navigate("setup") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> Unit
        }
    }

    val hasSavedSession = !prefs.getString("auth_uid", null).isNullOrBlank() ||
                          !prefs.getString("biz_owner_uid", null).isNullOrBlank() ||
                          authState is AuthState.Authenticated

    val startDestination = if (!isOnboardingCompleted) {
        "onboarding"
    } else if (hasSavedSession) {
        "inbox"
    } else {
        "auth"
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = activeBusiness != null && currentRoute in listOf("inbox", "pos", "payments", "share")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = ResiboSurface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentRoute == "inbox",
                        onClick = {
                            if (currentRoute != "inbox") {
                                navController.navigate("inbox") {
                                    popUpTo("inbox") { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Receipts") },
                        label = { Text("Receipts", fontSize = 10.sp, fontWeight = if (currentRoute == "inbox") FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ResiboPrimary,
                            selectedTextColor = ResiboPrimary,
                            indicatorColor = ResiboPrimary.copy(alpha = 0.12f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentRoute == "pos",
                        onClick = {
                            if (currentRoute != "pos") {
                                navController.navigate("pos") {
                                    popUpTo("inbox")
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.PointOfSale, contentDescription = "POS & Sales") },
                        label = { Text("POS & Sales", fontSize = 10.sp, fontWeight = if (currentRoute == "pos") FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ResiboPrimary,
                            selectedTextColor = ResiboPrimary,
                            indicatorColor = ResiboPrimary.copy(alpha = 0.12f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentRoute == "payments",
                        onClick = {
                            if (currentRoute != "payments") {
                                navController.navigate("payments") {
                                    popUpTo("inbox")
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.AccountBalance, contentDescription = "Ledger") },
                        label = { Text("Ledger", fontSize = 10.sp, fontWeight = if (currentRoute == "payments") FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ResiboPrimary,
                            selectedTextColor = ResiboPrimary,
                            indicatorColor = ResiboPrimary.copy(alpha = 0.12f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentRoute == "share",
                        onClick = {
                            if (currentRoute != "share") {
                                navController.navigate("share") {
                                    popUpTo("inbox")
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.QrCode, contentDescription = "QR & Pay") },
                        label = { Text("QR & Pay", fontSize = 10.sp, fontWeight = if (currentRoute == "share") FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ResiboPrimary,
                            selectedTextColor = ResiboPrimary,
                            indicatorColor = ResiboPrimary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        onFinishOnboarding = {
                            val p = context.getSharedPreferences("resibo_prefs", Context.MODE_PRIVATE)
                            val wasCompleted = p.getBoolean("onboarding_completed", false)
                            p.edit().putBoolean("onboarding_completed", true).apply()
                            isOnboardingCompleted = true

                            if (wasCompleted && navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            } else {
                                val nextDest = when {
                                    authState is AuthState.Authenticated && activeBusiness != null -> "inbox"
                                    authState is AuthState.Authenticated -> "setup"
                                    else -> "auth"
                                }
                                navController.navigate(nextDest) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable("auth") {
                    AuthScreen(
                        authViewModel = authViewModel,
                        onAuthSuccess = {}
                    )
                }

                composable("setup") {
                    BusinessSetupScreen(
                        businessViewModel = businessViewModel,
                        initialBusiness = activeBusiness,
                        onSetupComplete = { savedBiz ->
                            activeBusiness = savedBiz
                            PaymentNotificationListener.currentBusinessId = savedBiz.id
                            prefs.edit().putString("active_business_id", savedBiz.id).apply()

                            // Guide merchant directly to step 2: Notification capture setup
                            navController.navigate("notification_setup") {
                                popUpTo("setup") { inclusive = true }
                            }
                        },
                        onSignOut = {
                            activeBusiness = null
                            PaymentNotificationListener.currentBusinessId = null
                            authViewModel.signOut(context)
                            businessViewModel.clearBusiness(context)
                            paymentsViewModel.clear()
                            val p = context.getSharedPreferences("resibo_prefs", Context.MODE_PRIVATE)
                            p.edit().clear().apply()
                            navController.navigate("auth") {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onNavigateToOnboarding = {
                            navController.navigate("onboarding")
                        }
                    )
                }

                composable("inbox") {
                    activeBusiness?.let { biz ->
                        ReceiptsInboxScreen(
                            business = biz,
                            receiptsViewModel = receiptsViewModel,
                            isNotificationGranted = isNotificationGranted,
                            onReceiptClick = { receipt ->
                                selectedReceipt = receipt
                                navController.navigate("detail")
                            },
                            onNavigateToShare = {
                                navController.navigate("share")
                            },
                            onNavigateToSettings = {
                                navController.navigate("setup")
                            },
                            onNavigateToPayments = {
                                navController.navigate("payments")
                            },
                            onNavigateToPos = {
                                navController.navigate("pos")
                            },
                            onNavigateToNotificationSetup = {
                                navController.navigate("notification_setup")
                            }
                        )
                    }
                }

                composable("pos") {
                    activeBusiness?.let { biz ->
                        PosSalesScreen(
                            business = biz,
                            paymentsViewModel = paymentsViewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }

                composable("detail") {
                    val receipt = selectedReceipt
                    val biz = activeBusiness
                    if (receipt != null && biz != null) {
                        ReceiptDetailScreen(
                            receipt = receipt,
                            businessId = biz.id,
                            receiptsViewModel = receiptsViewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }

                composable("payments") {
                    activeBusiness?.let { biz ->
                        PaymentsReceivedScreen(
                            business = biz,
                            paymentsViewModel = paymentsViewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }

                composable("notification_setup") {
                    NotificationSetupScreen(
                        paymentsViewModel = paymentsViewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable("share") {
                    activeBusiness?.let { biz ->
                        ShareLinkScreen(
                            business = biz,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
