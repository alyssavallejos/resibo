# Resibo — Project Brief

You are building "Resibo" (Tagalog for "receipt"): an AI fake-receipt detector for Filipino small businesses and organizations that accept GCash, Maya, and bank transfer payments. Buyers send their payment receipt (screenshot) directly through Resibo, and Resibo tells the business owner within seconds whether the payment is VERIFIED, NOT YET RECEIVED, or FAKE. This is for a hackathon demo.

## Core principle (most important rule)
A receipt image alone can NEVER be marked "Verified". The reference number is the matching key: a receipt is VERIFIED only when it matches a real incoming payment captured on the business owner's phone. Image analysis can only RAISE suspicion; it cannot confirm a payment.

Resibo NEVER logs into, opens, or connects to GCash, Maya, or any bank app. It only reads the notifications those apps post on the owner's phone when money arrives.

## Three parts
1. **Buyer upload page** (web, no app install, no login)
   - URL per business: `/pay/{shopSlug}`. The business shares it as a link or QR code.
   - Shows business name, accepted payment accounts (GCash/Maya/bank, masked numbers), and an upload form: receipt screenshot, buyer name, order note (optional), expected amount (optional).
   - After upload: "Received! The seller will confirm your payment shortly."
   - Mobile-first, works on slow connections, max image 5 MB, Tagalog/English.

2. **Business owner Android app** (Kotlin, Jetpack Compose)
   - Payment capture via NotificationListenerService (allowlisted apps only)
   - Receipts inbox with verdicts, receipt detail, push notifications
   - One-time setup, manual override

3. **Backend: Firebase**
   - Firebase Auth (owner), Firestore, Cloud Storage (receipt images), Cloud Functions (verification), Firebase Hosting (buyer page), FCM (push notifications).
   - Gemini API (current Gemini Flash model, vision + structured JSON output) called ONLY from Cloud Functions. API key stored in Functions secrets, never in the app or web page.

## Data model (Firestore)
- `businesses/{id}`: `name`, `slug`, `ownerUid`, `accounts[{provider, accountName, last4}]`
- `businesses/{id}/payments/{id}`: `provider`, `amount`, `senderNameMasked`, `referenceNumber` (nullable), `receivedAt`, `source` ("notification"|"sms"|"manual")
- `businesses/{id}/receipts/{id}`: `imagePath`, `buyerName`, `note`, `expectedAmount`, `extracted{...}`, `forensics{...}`, `verdict`, `reasons[]`, `checks[]`, `matchedPaymentId`, `matchMethod` ("reference"|"amount_time"|"manual"), `createdAt`

## Verdict rules
- **VERIFIED ✅** — matched to a real payment in the ledger
- **NOT YET RECEIVED ⚠️** — looks valid but no matching payment yet (do not release goods)
- **FAKE ❌** — duplicate reference, amount mismatch, wrong recipient, failed/pending status, high tamper risk, or no payment after 24 hours

## Security & privacy
- **Firestore rules**: owners read/write only their own business; buyers can only CREATE receipts (write-only) for an existing shop slug.
- **Rate-limit uploads**: e.g. 10 per hour per shop per device/IP.
- **Data retention**: Receipt images auto-deleted after 90 days (configurable). The payment ledger stores only provider, amount, masked sender name, reference number, timestamp.
- **No sensitive storage**: Never log full notification text or images.
- **Compliance**: Philippine Data Privacy Act (RA 10173) notice on the buyer page and owner app.

## How we work
The app is built in 5 phases. Only build the phase I ask for, then STOP and report:
1. What you built and which files changed
2. How to test it
3. What I should see if it works
