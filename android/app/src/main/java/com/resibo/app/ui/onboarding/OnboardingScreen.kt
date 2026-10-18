package com.resibo.app.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import com.resibo.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resibo.app.audio.PaymentAudioAnnouncer
import com.resibo.app.theme.*
import kotlinx.coroutines.launch

data class OnboardingStep(
    val stepNumber: Int,
    val tag: String,
    val title: String,
    val subtitle: String,
    val badgeIcon: ImageVector,
    val badgeColor: Color,
    val badgeBg: Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })

    val steps = listOf(
        OnboardingStep(
            stepNumber = 1,
            tag = "TIGIL-SCAM SA TINDAHAN",
            title = "Never Get Scammed by Fake Receipts Again",
            subtitle = "Resibo is the 100% free fraud detector built for Filipino stores and merchants. Stop edited screenshots, fake reference numbers, and short payments instantly.",
            badgeIcon = Icons.Default.Shield,
            badgeColor = ResiboPrimary,
            badgeBg = ResiboPrimary.copy(alpha = 0.12f)
        ),
        OnboardingStep(
            stepNumber = 2,
            tag = "ONLINE BUYER UPLOAD",
            title = "Buyer QR Code & Online Receipt Upload",
            subtitle = "Print your store QR or share your dedicated link. Buyers upload their payment screenshots at the counter, and Resibo cross-checks them in seconds.",
            badgeIcon = Icons.Default.QrCodeScanner,
            badgeColor = Color(0xFF007DFE),
            badgeBg = Color(0xFFEBF5FF)
        ),
        OnboardingStep(
            stepNumber = 3,
            tag = "FREE IN-STORE SOUNDBOX",
            title = "Turn Your Phone into an Automatic Soundbox",
            subtitle = "No need to buy ₱3,000+ hardware speakers or pay monthly fees. Resibo speaks incoming GCash, Maya, MariBank, SeaBank, BPI, BDO, DBP, GoTyme, and UnionBank payments out loud with reference numbers digit-by-digit.",
            badgeIcon = Icons.Default.VolumeUp,
            badgeColor = Color(0xFF00D66E),
            badgeBg = Color(0xFFE6FAF0)
        ),
        OnboardingStep(
            stepNumber = 4,
            tag = "POS & SALES REPORTS",
            title = "All-In-One POS Register & Sales Analytics",
            subtitle = "Track daily, monthly, and annual sales. Categorize incoming money by GCash, Maya, MariBank, SeaBank, BDO, BPI, DBP, GoTyme, UnionBank, or Cash, and check payment history for any calendar date.",
            badgeIcon = Icons.Default.PointOfSale,
            badgeColor = Color(0xFF7C3AED),
            badgeBg = Color(0xFFF3E8FF)
        )
    )

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_resibo_logo),
                        contentDescription = "Resibo Logo",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Resibo",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = ResiboPrimary
                    )
                }

                TextButton(
                    onClick = onFinishOnboarding,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Skip Tour",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // Page Indicator Dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(steps.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(7.dp)
                                .width(if (isSelected) 26.dp else 7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSelected) ResiboPrimary else ResiboPrimary.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Navigation Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Back", fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            if (pagerState.currentPage < steps.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                onFinishOnboarding()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ResiboPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(if (pagerState.currentPage > 0) 1.5f else 1f),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == steps.size - 1) "Get Started 🚀" else "Next Step ›",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (pagerState.currentPage < steps.size - 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            OnboardingPageContent(
                step = steps[page],
                onTestSoundbox = {
                    PaymentAudioAnnouncer.announcePayment(
                        context = context,
                        provider = "gcash",
                        amount = 250.0,
                        senderName = "Juan Dela Cruz",
                        referenceNumber = "901234567890"
                    )
                }
            )
        }
    }
}

@Composable
fun OnboardingPageContent(
    step: OnboardingStep,
    onTestSoundbox: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tag Pill
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = step.badgeBg,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = step.tag,
                color = step.badgeColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                letterSpacing = 0.5.sp
            )
        }

        // Title
        Text(
            text = step.title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 30.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Subtitle
        Text(
            text = step.subtitle,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Interactive Feature Visual Card
        when (step.stepNumber) {
            1 -> StepOneVisual()
            2 -> StepTwoVisual()
            3 -> StepThreeVisual(onTestSoundbox = onTestSoundbox)
            4 -> StepFourVisual()
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// -------------------------------------------------------------
// Slide 1 Visual: Anti-Fraud Detection Preview
// -------------------------------------------------------------
@Composable
fun StepOneVisual() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = ResiboSurface,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEE2E2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Dangerous, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Fake Receipt Alert", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFDC2626))
                        Text("Detected in 1.2 seconds", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFEE2E2)
                ) {
                    Text(
                        text = "BLOCKED",
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF1F2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚠️ Tampered Screenshot Detected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF9F1239)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Amount edited from ₱50.00 to ₱500.00\n• Ref number recycled from 3 days ago\n• Recipient name does not match store account",
                        fontSize = 11.sp,
                        color = Color(0xFFBE123C),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PillarBadge(icon = "🇵🇭", label = "PH E-Wallets")
                PillarBadge(icon = "🔒", label = "No Fake Scams")
                PillarBadge(icon = "💯", label = "100% Free")
            }
        }
    }
}

// -------------------------------------------------------------
// Slide 2 Visual: Online Receipt Upload Portal
// -------------------------------------------------------------
@Composable
fun StepTwoVisual() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = ResiboSurface,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Customer Step Flow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepPill(number = "1", title = "Customer Scans QR")
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ResiboPrimary, modifier = Modifier.size(16.dp))
                StepPill(number = "2", title = "Uploads Receipt")
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ResiboPrimary, modifier = Modifier.size(16.dp))
                StepPill(number = "3", title = "Auto-Verified")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = ResiboPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "resibo-ph.web.app/pay/your-store",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ResiboPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Buyers simply upload their payment screenshot directly from their browser. No mobile app download needed for customers!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Slide 3 Visual: Free In-Store Soundbox
// -------------------------------------------------------------
@Composable
fun StepThreeVisual(onTestSoundbox: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = ResiboSurface,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Soundbox Speaker Icon
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF4ADE80), Color(0xFF16A34A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Speech Bubble Mockup
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFECFDF5),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📢 Spoken out loud like a retail counter soundbox:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF065F46)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "\"GCash payment received: 250 pesos from Juan Dela Cruz. Reference number: 9 0 1 2 3 4...\"",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF047857),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Test Button
            FilledTonalButton(
                onClick = onTestSoundbox,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF00D66E).copy(alpha = 0.15f),
                    contentColor = Color(0xFF047857)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("▶️ Tap to Hear Soundbox In Action", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

// -------------------------------------------------------------
// Slide 4 Visual: POS & Multi-Channel Sales Analytics
// -------------------------------------------------------------
@Composable
fun StepFourVisual() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = ResiboSurface,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Sales Performance At A Glance",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Daily, Monthly, Annual Preview Mini-Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniMetricCard(title = "Today", amount = "₱1,250", count = "4 txns", color = Color(0xFF007DFE))
                MiniMetricCard(title = "This Month", amount = "₱34,500", count = "120 txns", color = Color(0xFF00D66E))
                MiniMetricCard(title = "Annual", amount = "₱480K", count = "1,450 txns", color = Color(0xFF7C3AED))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-channel badges
            Text(
                text = "Categorized Channels:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChannelPill(label = "GCash", bg = Color(0xFFEBF5FF), fg = Color(0xFF007DFE))
                ChannelPill(label = "Maya", bg = Color(0xFFE6FAF0), fg = Color(0xFF00D66E))
                ChannelPill(label = "MariBank", bg = Color(0xFFFFECE5), fg = Color(0xFFE64A19))
                ChannelPill(label = "SeaBank", bg = Color(0xFFFFF3E0), fg = Color(0xFFEF6C00))
                ChannelPill(label = "Cash", bg = Color(0xFFECFDF5), fg = Color(0xFF059669))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = ResiboPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Pick any calendar date to review historical payment logs.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Small Helpers
// -------------------------------------------------------------
@Composable
fun PillarBadge(icon: String, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFC)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun StepPill(number: String, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = ResiboPrimary,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
fun MiniMetricCard(title: String, amount: String, count: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.width(96.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = color)
            Text(count, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ChannelPill(label: String, bg: Color, fg: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
