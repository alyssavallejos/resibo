# Resibo — Antigravity Prompts by Phase


# Phase 1 — Firebase, buyer page and business setup

```
First, save the Project Brief below as PROJECT_BRIEF.md in the project root.
Then build PHASE 1 ONLY and stop.

# Resibo — Project Brief

You are building "Resibo" (Tagalog for "receipt"): an AI fake-receipt detector for
Filipino small businesses and organizations that accept GCash, Maya, and bank
transfer payments. Buyers send their payment receipt (screenshot) directly through
Resibo, and Resibo tells the business owner within seconds whether the payment is
VERIFIED, NOT YET RECEIVED, or FAKE. This is for a hackathon demo.

## Core principle (most important rule)
A receipt image alone can NEVER be marked "Verified". The reference number is the
matching key: a receipt is VERIFIED only when it matches a real incoming payment
captured on the business owner's phone. Image analysis can only RAISE suspicion;
it cannot confirm a payment.

Resibo NEVER logs into, opens, or connects to GCash, Maya, or any bank app. It only
reads the notifications those apps post on the owner's phone when money arrives.

## Three parts
1. Buyer upload page (web, no app install, no login)
   - URL per business: /pay/{shopSlug}. The business shares it as a link or QR code.
   - Shows business name, accepted payment accounts (GCash/Maya/bank, masked
     numbers), and an upload form: receipt screenshot, buyer name, order note
     (optional), expected amount (optional).
   - After upload: "Received! The seller will confirm your payment shortly."
   - Mobile-first, works on slow connections, max image 5 MB, Tagalog/English.

2. Business owner Android app (Kotlin, Jetpack Compose)
   - Payment capture via NotificationListenerService (allowlisted apps only)
   - Receipts inbox with verdicts, receipt detail, push notifications
   - One-time setup, manual override

3. Backend: Firebase
   - Firebase Auth (owner), Firestore, Cloud Storage (receipt images),
     Cloud Functions (verification), Firebase Hosting (buyer page),
     FCM (push notifications).
   - Gemini API (current Gemini Flash model, vision + structured JSON output)
     called ONLY from Cloud Functions. API key stored in Functions secrets,
     never in the app or web page.

## Data model (Firestore)
- businesses/{id}: name, slug, ownerUid, accounts[{provider, accountName, last4}]
- businesses/{id}/payments/{id}: provider, amount, senderNameMasked,
  referenceNumber (nullable), receivedAt, source ("notification"|"sms"|"manual")
- businesses/{id}/receipts/{id}: imagePath, buyerName, note, expectedAmount,
  extracted{...}, forensics{...}, verdict, reasons[], checks[],
  matchedPaymentId, matchMethod ("reference"|"amount_time"|"manual"), createdAt

## Verdict rules
- VERIFIED ✅ — matched to a real payment in the ledger
- NOT YET RECEIVED ⚠️ — looks valid but no matching payment yet (do not release goods)
- FAKE ❌ — duplicate reference, amount mismatch, wrong recipient, failed/pending
  status, high tamper risk, or no payment after 24 hours

## Security & privacy
- Firestore rules: owners read/write only their own business; buyers can only
  CREATE receipts (write-only) for an existing shop slug.
- Rate-limit uploads (e.g. 10 per hour per shop per device/IP).
- Receipt images auto-deleted after 90 days (configurable). The payment ledger
  stores only provider, amount, masked sender name, reference number, timestamp.
- Never log full notification text or images.
- Philippine Data Privacy Act (RA 10173) notice on the buyer page and owner app.

## How we work
The app is built in 5 phases. Only build the phase I ask for, then STOP and report:
1. What you built and which files changed
2. How to test it
3. What I should see if it works

---

## PHASE 1 — Firebase, buyer upload page, and business setup
Build:
1. Project structure: /android (Kotlin + Compose, package com.resibo.app),
   /web (buyer page), /functions (Cloud Functions, TypeScript).
2. Firebase project config: Auth, Firestore, Storage, Hosting, Functions, FCM.
   Firestore + Storage security rules exactly as described in the brief.
3. Buyer upload page at /pay/{shopSlug}:
   - Business name, accepted accounts (masked), upload form
     (screenshot, buyer name, note, expected amount)
   - Uploads image to Storage and creates a receipt document with
     verdict "pending_check"
   - Confirmation screen; Tagalog/English toggle; mobile-first
   - Shows a "Shop not found" page for unknown slugs
4. Android owner app:
   - Google / email sign-in (Firebase Auth)
   - Business setup: name, auto-generated slug, payment accounts
     (provider, account name, last 4 digits)
   - Share screen: buyer link + QR code for the link
   - Placeholder receipts list reading receipts in real time
5. Basic upload rate limit on the buyer page.

When done, give me test steps for: creating a business, opening the buyer link
on my phone, uploading a screenshot, and seeing it appear in the owner app.

STOP after Phase 1.
```


# Phase 2 — Capturing real payments from notifications

```
Read PROJECT_BRIEF.md. Phase 1 is done. Build PHASE 2 ONLY and stop.

## PHASE 2 — Payment capture (NotificationListenerService)
Build:
1. PaymentNotificationListener (NotificationListenerService):
   - Allowlist ONLY: com.globe.gcash.android (GCash), com.paymaya (Maya),
     and bank apps the owner selects during setup (show installed apps such as
     BDO, BPI, UnionBank, Metrobank, LandBank). Also the default SMS app, but
     only for messages from bank/e-wallet sender IDs.
   - The FIRST line of onNotificationPosted must return immediately for any
     package not on the allowlist — no parsing, no storage, no logging.
2. Parsers (PaymentParsers.kt, one per provider, easy to edit):
   - Extract: provider, amount, sender name (masked as shown), reference number
     (may be MISSING — store null), timestamp.
   - Only incoming payments ("received", "natanggap", "credited").
     Ignore outgoing payments, promos, OTPs, and cash-in from own bank.
   - Use regex first. If regex fails but the text looks like an incoming
     payment, call a Cloud Function that uses Gemini text extraction
     (send the notification text only, never store it).
   - I will give you real notification samples; build the regex from them and
     add unit tests for each sample.
3. Save each payment to businesses/{id}/payments with source "notification" or
   "sms". De-duplicate (same provider + amount + reference/time within 1 min).
4. Setup flow additions:
   - Disclosure screen BEFORE asking for access: Resibo only reads payment
     notifications from the apps you choose; it never opens or logs into
     GCash/Maya/your bank and never sees your balance or password.
   - Button opening Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
   - Detect when access is granted → "Payment capture ON ✅"
   - Battery optimization guidance for Xiaomi/Oppo/Realme/Vivo/Samsung
   - Android 13+ sideload tip: App info → ⋮ → "Allow restricted settings"
5. "Payments Received" screen: list of captured payments (amount, provider,
   masked sender, reference number or "no ref", time).
6. DEBUG builds only: "Simulate incoming payment" button that posts a local
   GCash-style notification (amount + reference number of my choice), so the
   demo works without real transfers.

When done, give me test steps using the simulate button and a real ₱1 transfer.

STOP after Phase 2.
```


# Phase 3 — Verification engine

```
Read PROJECT_BRIEF.md. Phases 1–2 are done. Build PHASE 3 ONLY and stop.

## PHASE 3 — Verification Cloud Function
Trigger: new receipt document created.

Step 1 — AI extraction (Gemini vision, structured JSON):
  { provider: "gcash"|"maya"|"bank"|"unknown", bank_name, amount, currency,
    reference_number, date_time, recipient_name, recipient_number_last4,
    sender_name, transaction_status: "successful"|"pending"|"failed"|"scheduled"|"unknown",
    is_screenshot_of_real_app: boolean, confidence: 0-1 }
  If the image is not a payment receipt at all → verdict FAKE with reason
  "This is not a payment receipt".

Step 2 — Hard checks (plain code, not AI):
  a. Reference number format valid for that provider. Make formats
     configurable and base them on my real sample receipts.
  b. DUPLICATE: reference number already used on an earlier receipt for this
     business → FAKE ("This reference number was already used on <date>").
  c. Recipient last 4 digits / name matches a registered business account →
     otherwise FAKE ("Paid to a different account").
  d. Status must be "successful" → pending/scheduled/failed = FAKE.
  e. Date not in the future and not older than 7 days (configurable).

Step 3 — Ledger match (decisive step). Normalize reference numbers
(digits/letters only, no spaces). Search payments within ±48 hours:
  - Same reference number + same amount → VERIFIED ✅ (matchMethod "reference")
  - Same reference number, different amount → FAKE ❌
    ("Receipt says ₱5,000 but you received ₱500")
  - If the provider's notifications have NO reference number: match on exact
    amount + similar masked sender name + time within ±10 minutes, and a payment
    can only be matched ONCE → VERIFIED ✅ (matchMethod "amount_time"),
    shown as "Verified by amount and time"
  - No match yet → NOT YET RECEIVED ⚠️
  - Any Step 2 failure → FAKE ❌ (unless a reference match succeeded — real
    money arrived; then VERIFIED with the failed checks shown as notes)

Step 4 — Re-check: a Firestore trigger on NEW payments re-runs Step 3 for
receipts that are NOT YET RECEIVED (up to 24 hours old). If matched → flip to
VERIFIED. A scheduled function marks receipts still unmatched after 24 hours as
FAKE ("No payment arrived within 24 hours").

Save to the receipt: extracted, verdict, reasons (plain Taglish, max 4),
checks [{name, passed, detail}], matchedPaymentId, matchMethod.
If Gemini fails, run Steps 2–4 using whatever the buyer typed and label the
result "Basic check (AI unavailable)".

When done, give me test steps covering: verified, duplicate, wrong amount,
wrong recipient, and not-yet-received-then-verified.

STOP after Phase 3.
```


# Phase 4 — Image forensics, inbox and alerts

```
Read PROJECT_BRIEF.md. Phases 1–3 are done. Build PHASE 4 ONLY and stop.

## PHASE 4 — Image forensics, inbox, details, and push notifications
Build:
1. Image forensics (second Gemini vision call in the verification function):
   { edited_signs: string[] (mismatched fonts, misaligned digits, blurred or
     smudged area around the amount, inconsistent colors, wrong app layout),
     generator_app_signs: string[],
     tamper_risk: "low"|"medium"|"high",
     explanation: string (simple Taglish) }
   Also read image metadata if present (editing-software tags).
   tamper_risk "high" → FAKE, unless a reference-number ledger match succeeded
   (then VERIFIED with a note: "Image looks edited, but the payment did arrive").
2. Receipts inbox (Android): real-time list with a big colored verdict badge,
   buyer name, amount, provider, time. Filters: All / Verified / Waiting / Fake.
3. Receipt detail: screenshot (zoomable), extracted fields, verdict, checks
   list (✔ / ✘ with plain explanations), matched payment, forensics notes.
4. Push notifications (FCM) on every verdict and every verdict change:
   "✅ ₱1,500 from Juan — Verified"
   "❌ Fake receipt from Juan — reference number already used"
   "⚠️ Receipt from Juan — waiting for payment"
5. Manual override: "Mark as received (cash/other)" and "Mark as fake",
   recorded with matchMethod "manual".
6. Home dashboard: today's totals (verified ₱, waiting, fakes caught),
   "Fakes caught this month" counter.
7. Visual polish: Material 3, Resibo brand color, receipt-check app icon.

When done, give me test steps for an edited receipt and for each push type.

STOP after Phase 4.
```


# Phase 5 — Demo kit

```
Read PROJECT_BRIEF.md. Phases 1–4 are done. Build PHASE 5 ONLY and stop.

## PHASE 5 — Demo kit
Build:
1. Seed script: demo shop "Kape ni Aling Rosa" with one GCash account.
2. Demo receipts (I will provide real screenshots for 1, 4, and 5; generate 3
   by editing the amount on 1):
   1. Real receipt, payment simulated first → VERIFIED
   2. Same receipt uploaded again by another "buyer" → FAKE (duplicate)
   3. Amount edited from ₱150 to ₱1,500 → FAKE (amount mismatch + tamper signs)
   4. Paid to a different GCash number → FAKE (wrong recipient)
   5. Real receipt, money not sent yet → NOT YET RECEIVED, then press
      "Simulate incoming payment" → flips to VERIFIED live on screen
3. Demo script (DEMO.md): the exact order of steps for a 3-minute live demo.
4. Offline fallback: if Gemini or Wi-Fi fails, run hard checks + ledger match
   only, labeled "Basic check (AI unavailable)".
5. Release build of the Android app (R8 enabled), deploy web + functions,
   and report the APK size.
6. README.md: setup, Firebase config, Gemini secret, how to run the demo, the
   notification-access and "Allow restricted settings" tips, known limitations
   (owner's phone must be on to capture payments; notifications may lack a
   reference number for some providers).
7. Run all 5 demo cases and report a table: receipt → expected → actual.

STOP after Phase 5.
```
