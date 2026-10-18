package com.resibo.app

import com.resibo.app.parser.PaymentParsers
import com.resibo.app.audio.PaymentAudioAnnouncer
import org.junit.Assert.*
import org.junit.Test

class PaymentParsersTest {

    @Test
    fun testGCashEnglishNotification() {
        val title = "GCash"
        val text = "You have received PHP 150.00 of GCash from JUAN DELA CRUZ 0917****123 with Ref No. 123456789012."
        val result = PaymentParsers.parse(PaymentParsers.PKG_GCASH, title, text)

        assertNotNull("Should parse valid GCash notification", result)
        assertEquals("gcash", result?.provider)
        assertEquals(150.0, result?.amount ?: 0.0, 0.001)
        assertEquals("123456789012", result?.referenceNumber)
        assertTrue(result?.senderNameMasked?.contains("JUAN DELA CRUZ") == true)
    }

    @Test
    fun testGCashTagalogNotification() {
        val title = "GCash"
        val text = "Natanggap mo ang PHP 500.00 mula kay MARIA CLARA 0918****456 Ref No. 987654321098."
        val result = PaymentParsers.parse(PaymentParsers.PKG_GCASH, title, text)

        assertNotNull("Should parse Tagalog GCash notification", result)
        assertEquals("gcash", result?.provider)
        assertEquals(500.0, result?.amount ?: 0.0, 0.001)
        assertEquals("987654321098", result?.referenceNumber)
        assertTrue(result?.senderNameMasked?.contains("MARIA CLARA") == true)
    }

    @Test
    fun testGCashExpressSendWithPesoSign() {
        val title = "Express Send"
        val text = "You have received ₱2,500.50 via Express Send from ALYSSA V. Ref. No. 500123456789"
        val result = PaymentParsers.parse(PaymentParsers.PKG_GCASH, title, text)

        assertNotNull(result)
        assertEquals(2500.50, result?.amount ?: 0.0, 0.001)
        assertEquals("500123456789", result?.referenceNumber)
        assertTrue(result?.senderNameMasked?.contains("ALYSSA V") == true)
    }

    @Test
    fun testMayaNotification() {
        val title = "Maya"
        val text = "You received PHP 250.00 from JOSE RIZAL on 10/18/2026. Ref: 20261018123456"
        val result = PaymentParsers.parse(PaymentParsers.PKG_MAYA, title, text)

        assertNotNull("Should parse Maya notification", result)
        assertEquals("maya", result?.provider)
        assertEquals(250.0, result?.amount ?: 0.0, 0.001)
        assertEquals("20261018123456", result?.referenceNumber)
        assertTrue(result?.senderNameMasked?.contains("JOSE RIZAL") == true)
    }

    @Test
    fun testBpiInstaPayNotification() {
        val title = "BPI Online"
        val text = "BPI: You received PHP 1,200.00 from PEDRO SANTOS via InstaPay. Ref: BPI9876543210"
        val result = PaymentParsers.parse(PaymentParsers.PKG_BPI, title, text)

        assertNotNull("Should parse BPI InstaPay notification", result)
        assertEquals("bpi", result?.provider)
        assertEquals(1200.0, result?.amount ?: 0.0, 0.001)
        assertEquals("BPI9876543210", result?.referenceNumber)
    }

    @Test
    fun testIgnoreOutgoingPayment() {
        val title = "GCash"
        val text = "You have paid PHP 350.00 to Starbucks Coffee. Ref No. 112233445566."
        val result = PaymentParsers.parse(PaymentParsers.PKG_GCASH, title, text)

        assertNull("Outgoing payments MUST be ignored", result)
    }

    @Test
    fun testIgnoreTagalogOutgoingPayment() {
        val title = "GCash"
        val text = "Nagbayad ka ng PHP 100.00 sa Tindahan ni Pedro."
        val result = PaymentParsers.parse(PaymentParsers.PKG_GCASH, title, text)

        assertNull("Outgoing Tagalog payment MUST be ignored", result)
    }

    @Test
    fun testIgnoreOtpAndSecurityAlerts() {
        val title = "GCash Security"
        val text = "Your OTP is 654321 to authenticate your transaction. Do not share this code."
        val result = PaymentParsers.parse(PaymentParsers.PKG_GCASH, title, text)

        assertNull("OTPs must be ignored", result)
    }

    @Test
    fun testIgnorePromoAndVouchers() {
        val title = "Maya Deals"
        val text = "Congratulations! Get up to 50% discount and cashback vouchers on your next order."
        val result = PaymentParsers.parse(PaymentParsers.PKG_MAYA, title, text)

        assertNull("Promotions must be ignored", result)
    }

    @Test
    fun testAudioAnnouncementFormattingWithReferenceNumber() {
        val formattedRef = PaymentAudioAnnouncer.formatReferenceForSpeech("901234567890")
        assertEquals("9 0 1 2 3 4 5 6 7 8 9 0", formattedRef)

        val announcement = PaymentAudioAnnouncer.buildAnnouncementText(
            provider = "gcash",
            amount = 150.0,
            senderName = "JUAN DELA CRUZ",
            referenceNumber = "901234567890"
        )
        assertEquals(
            "GCash payment received: 150 pesos from JUAN DELA CRUZ. Reference number: 9 0 1 2 3 4 5 6 7 8 9 0.",
            announcement
        )
    }

    @Test
    fun testAudioAnnouncementMayaWithAlphanumericRef() {
        val announcement = PaymentAudioAnnouncer.buildAnnouncementText(
            provider = "maya",
            amount = 250.50,
            senderName = null,
            referenceNumber = "MAYA1234"
        )
        assertEquals(
            "Maya payment received: 250.50 pesos. Reference number: M A Y A 1 2 3 4.",
            announcement
        )
    }

    @Test
    fun testMariBankRealPushNotification() {
        val title = "Successful Incoming Transfer"
        val text = "You've received PHP 1.00 from MariBank with account ending 3380."
        val result = PaymentParsers.parse(PaymentParsers.PKG_MARIBANK_PH, title, text)

        assertNotNull("Should parse MariBank push notification", result)
        assertEquals("maribank", result?.provider)
        assertEquals(1.0, result?.amount ?: 0.0, 0.001)
        assertEquals("3380", result?.referenceNumber)
        assertTrue("Sender should contain MariBank", result?.senderNameMasked?.contains("MariBank") == true)

        val announcement = PaymentAudioAnnouncer.buildAnnouncementText(
            provider = result!!.provider,
            amount = result.amount,
            senderName = result.senderNameMasked,
            referenceNumber = result.referenceNumber
        )
        assertTrue("Announcement should say 1 peso", announcement.contains("MariBank payment received: 1 peso"))
    }

    @Test
    fun testMariBankEmailAlertNotification() {
        val title = "MariBank PH Alerts"
        val text = "Successful Incoming Transfer Hi AXYN KAYE VALLEJOS, You have received a fund transfer to your account. Transfer from: MariBank - 3380 Transfer amount: PHP1.00 Reference No: BC550000034524173102"
        val result = PaymentParsers.parse(PaymentParsers.PKG_GMAIL, title, text)

        assertNotNull("Should parse MariBank email alert", result)
        assertEquals("maribank", result?.provider)
        assertEquals(1.0, result?.amount ?: 0.0, 0.001)
        assertEquals("BC550000034524173102", result?.referenceNumber)
    }

    @Test
    fun testReceiptVerdictBinaryStatesOnly() {
        val received = com.resibo.app.model.ReceiptVerdict.fromString("verified")
        assertEquals(com.resibo.app.model.ReceiptVerdict.RECEIPT_RECEIVED, received)
        assertTrue(received.isReceived)
        assertFalse(received.isFraud)

        val fraud = com.resibo.app.model.ReceiptVerdict.fromString("fake")
        assertEquals(com.resibo.app.model.ReceiptVerdict.FRAUD_RECEIPT, fraud)
        assertTrue(fraud.isFraud)
        assertFalse(fraud.isReceived)

        // Ensure only two values exist in enum
        assertEquals(2, com.resibo.app.model.ReceiptVerdict.values().size)
    }

    @Test
    fun testReceiptOcrTextParserExtractsMariBank() {
        val sampleMariText = """
            MariBank
            Transfer Successful
            PHP 1.00
            From: ALYSSA V.
            To: Axyn Kaye V.
            Account No: ••••0367
            Reference Number: BC550000034535407831
            18 Oct 2026, 17:36
        """.trimIndent()

        val parsed = com.resibo.app.ocr.ReceiptOcrScanner.parseReceiptText(sampleMariText, 1.0, "Alyssa")
        assertEquals("MARIBANK", parsed.provider)
        assertEquals("BC550000034535407831", parsed.referenceNumber)
        assertEquals(1.0, parsed.amount, 0.001)
        assertEquals("0367", parsed.recipientNumberLast4)
    }

    @Test
    fun testNormalizeRefStripsSpacesAndHyphens() {
        assertEquals("BC550000034535407831", com.resibo.app.ocr.ReceiptOcrScanner.normalizeRef("BC55 0000 0345 3540 7831"))
        assertEquals("102938475610", com.resibo.app.ocr.ReceiptOcrScanner.normalizeRef("1029-3847-5610"))
        assertEquals("MAYA998877", com.resibo.app.ocr.ReceiptOcrScanner.normalizeRef("maya-9988-77"))
    }

    @Test
    fun testOnlineTransactionReceiptModelFlag() {
        val onlineReceipt = com.resibo.app.model.Receipt(
            id = "rec_1",
            channel = "online",
            matchMethod = "reference"
        )
        assertTrue("Online receipt should be flagged as online transaction", onlineReceipt.isOnlineTransaction)

        val inStoreReceipt = com.resibo.app.model.Receipt(
            id = "rec_2",
            channel = "in_store",
            matchMethod = "manual"
        )
        assertFalse("In-store receipt should not be flagged as online transaction", inStoreReceipt.isOnlineTransaction)
    }
}
