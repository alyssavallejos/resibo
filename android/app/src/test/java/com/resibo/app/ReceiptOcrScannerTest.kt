package com.resibo.app

import com.resibo.app.model.ExtractedInfo
import com.resibo.app.model.Payment
import com.resibo.app.model.Receipt
import com.resibo.app.model.ReceiptVerdict
import com.resibo.app.ocr.ReceiptOcrScanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptOcrScannerTest {

    @Test
    fun testGCashMaskedSenderLogicReasoning() {
        // Juan Dela Cruz matched against JU** D.
        val r1 = ReceiptOcrScanner.isSenderNameSimilar("Juan Dela Cruz", "JU** D.")
        assertTrue("JU** D. should match Juan Dela Cruz: ${r1.explanation}", r1.matches)

        // Jhon Lloyd Cabahug matched against JH** LL*** C.
        val r2 = ReceiptOcrScanner.isSenderNameSimilar("Jhon Lloyd Cabahug", "JH** LL*** C.")
        assertTrue("JH** LL*** C. should match Jhon Lloyd Cabahug: ${r2.explanation}", r2.matches)

        // Jhon Lloyd Cabahug matched against J*** L*** C.
        val r3 = ReceiptOcrScanner.isSenderNameSimilar("Jhon Lloyd Cabahug", "J*** L*** C.")
        assertTrue("J*** L*** C. should match Jhon Lloyd Cabahug: ${r3.explanation}", r3.matches)

        // Alyssa Vallejos matched against A*** V***
        val r4 = ReceiptOcrScanner.isSenderNameSimilar("Alyssa Vallejos", "A*** V***")
        assertTrue("A*** V*** should match Alyssa Vallejos: ${r4.explanation}", r4.matches)

        // Notification with phone number suffix: JU** D. 0917****123
        val r5 = ReceiptOcrScanner.isSenderNameSimilar("Juan Dela Cruz", "JU** D. 0917****123")
        assertTrue("JU** D. with phone number should match Juan Dela Cruz: ${r5.explanation}", r5.matches)

        // Unmasked MariBank name
        val r6 = ReceiptOcrScanner.isSenderNameSimilar("Alyssa Vallejos", "Alyssa Vallejos")
        assertTrue("Exact name should match: ${r6.explanation}", r6.matches)
    }

    @Test
    fun testFraudNameMismatchDetected() {
        // Different person claiming GCash payment: Maria Santos vs JU** D. (Juan Dela Cruz)
        val r1 = ReceiptOcrScanner.isSenderNameSimilar("Maria Santos", "JU** D.")
        assertFalse("Maria Santos should NOT match JU** D.: ${r1.explanation}", r1.matches)

        // Min Yoongi vs JU** D.
        val r2 = ReceiptOcrScanner.isSenderNameSimilar("Min Yoongi", "JU** D.")
        assertFalse("Min Yoongi should NOT match JU** D.: ${r2.explanation}", r2.matches)

        // Jungkook vs JU** D.
        val r3 = ReceiptOcrScanner.isSenderNameSimilar("Jungkook", "JU** D.")
        assertFalse("Jungkook should NOT match JU** D.: ${r3.explanation}", r3.matches)

        // Alyssa Vallejos vs Pedro Penduko
        val r4 = ReceiptOcrScanner.isSenderNameSimilar("Alyssa Vallejos", "Pedro Penduko")
        assertFalse("Alyssa Vallejos should NOT match Pedro Penduko", r4.matches)
    }

    @Test
    fun testOnlineVerificationWithMatchingNameAndAmount() = runBlocking {
        val receipt = Receipt(
            id = "rec_123",
            buyerName = "Juan Dela Cruz",
            expectedAmount = 150.0,
            channel = "online",
            extracted = ExtractedInfo(
                provider = "GCASH",
                amount = 150.0,
                senderName = "Juan Dela Cruz",
                referenceNumber = "102938475610"
            )
        )

        val payments = listOf(
            Payment(
                id = "pay_1",
                provider = "gcash",
                amount = 150.0,
                senderNameMasked = "JU** D.",
                referenceNumber = "102938475610"
            )
        )

        val verified = ReceiptOcrScanner.scanAndVerify(receipt, emptyList(), payments)
        assertEquals(ReceiptVerdict.RECEIPT_RECEIVED, verified.verdict)
        assertTrue(verified.reasons.any { it.contains("Kumpirmadong Natanggap") })
    }

    @Test
    fun testOnlineVerificationFraudWhenNameMismatches() = runBlocking {
        // Fake receipt where Maria Santos uploaded a receipt claiming Juan's payment
        val receipt = Receipt(
            id = "rec_fake",
            buyerName = "Maria Santos",
            expectedAmount = 150.0,
            channel = "online",
            extracted = ExtractedInfo(
                provider = "GCASH",
                amount = 150.0,
                senderName = "Maria Santos",
                referenceNumber = "102938475610"
            )
        )

        val payments = listOf(
            Payment(
                id = "pay_1",
                provider = "gcash",
                amount = 150.0,
                senderNameMasked = "JU** D.", // From Juan Dela Cruz
                referenceNumber = "102938475610"
            )
        )

        val verified = ReceiptOcrScanner.scanAndVerify(receipt, emptyList(), payments)
        assertEquals(ReceiptVerdict.FRAUD_RECEIPT, verified.verdict)
        assertTrue(verified.reasons.any { it.contains("Maling nagpadala") || it.contains("Walang natanggap na notification") })
    }

    @Test
    fun testOnlineVerificationFraudWhenAmountMismatches() = runBlocking {
        val receipt = Receipt(
            id = "rec_fake_amt",
            buyerName = "Juan Dela Cruz",
            expectedAmount = 500.0, // Claiming 500
            channel = "online",
            extracted = ExtractedInfo(
                provider = "GCASH",
                amount = 500.0,
                senderName = "Juan Dela Cruz",
                referenceNumber = "102938475610"
            )
        )

        val payments = listOf(
            Payment(
                id = "pay_1",
                provider = "gcash",
                amount = 150.0, // Only 150 was received
                senderNameMasked = "JU** D.",
                referenceNumber = "102938475610"
            )
        )

        val verified = ReceiptOcrScanner.scanAndVerify(receipt, emptyList(), payments)
        assertEquals(ReceiptVerdict.FRAUD_RECEIPT, verified.verdict)
        assertTrue(verified.reasons.any { it.contains("Maling halaga") })
    }
}
