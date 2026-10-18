# 🧾 Resibo — AI Fake-Receipt Detector for Small Businesses

> **Resibo** (*Tagalog for "receipt"*): An AI-powered fraud detector built for Filipino micro, small, and medium enterprises (MSMEs) accepting GCash, Maya, and bank transfers (BDO, BPI, UnionBank, Metrobank, LandBank).

Buyers submit their payment screenshot via a web link (`/pay/{shopSlug}`), and Resibo cross-references the receipt with real incoming payment notifications captured on the merchant's Android phone, returning an instant verdict:
- **`✅ VERIFIED`** — Money arrived in the business account and receipt details match.
- **`⚠️ NOT YET RECEIVED`** — Receipt looks fine, but no payment has arrived yet (*do not release items*). Automatically flips to verified when money clears.
- **`❌ FAKE`** — Altered amount, duplicate reference, wrong recipient account, failed transfer, or tampered image.

---

## 🏛️ Architecture & Core Principles

```
   [ Customer Phone ]                           [ Merchant Android Phone ]
         │                                                 │
   Opens /pay/{shopSlug}                        Incoming GCash / Maya Alert
   Uploads Receipt Screenshot                              │
         │                                     NotificationListenerService
         ▼                                                 │
 [ Firebase Storage ]                                      ▼
         │                                      [ Firestore Payments Ledger ]
         ▼                                                 │
[ Cloud Function Verification ] ◄──────────────────────────┘
  ├─ Step 1: Gemini Vision AI Extraction (Ref, Amount, Recipient)
  ├─ Step 1b: Image Forensics (Font tampering, smudges, EXIF tags)
  ├─ Step 2: Hard Checks (Duplicate ref, Recipient account, Date)
  └─ Step 3: Ledger Match (Matches against actual money received)
         │
         ▼
 [ Receipts Inbox ] ──► Real-time verdict + FCM Push Alert
```

### Core Privacy Boundary
- **Zero Account Credentials**: Resibo **NEVER** logs into, opens, or connects to GCash, Maya, or any bank account. It has no access to account balances, passwords, or personal chats.
- **Strict Allowlist**: Android’s `NotificationListenerService` immediately drops any notification not from approved payment apps (Facebook, Messenger, WhatsApp are ignored on line 1).
- **RA 10173 Compliance**: Complies with the Philippine Data Privacy Act. Full notification texts are never stored.

---

## 🚀 Quick Setup & Installation

### Prerequisites
- Node.js 18+ and npm
- Android Studio (Electric Eel or newer with JDK 17)
- Firebase Account (Free Spark or Blaze tier)
- Gemini API Key from [Google AI Studio](https://aistudio.google.com/)

---

### 1. Web Buyer Page Setup
```bash
cd web
npm install
npm run dev
```
Open `http://localhost:3000?shop=kape-ni-aling-rosa` to test the buyer upload experience with instant local preview.

To build for production:
```bash
npm run build
```

---

### 2. Cloud Functions Setup
```bash
cd functions
npm install
npm run build
```

Set your Gemini API key in your Firebase Functions environment:
```bash
firebase functions:secrets:set GEMINI_API_KEY
```

---

### 3. Android Owner App Setup
1. Open the [**`android/`**](file:///Users/alyssavallejos/Desktop/Resibo/android) folder in Android Studio.
2. Select an Android emulator (e.g. `Pixel 8 API 34+`) or plug in a physical phone with USB Debugging enabled.
3. Click the green **Run (▶)** button.
4. Tap **"Instant Demo Sign-In"** to launch directly into the pre-configured demo store *"Kape ni Aling Rosa"*.

---

## 📱 Troubleshooting & Android Configuration Tips

### The "Notification Access" Switch is Grayed Out (Android 13+)
Android 13 and above restricts notification access for apps installed from an APK (sideloaded):
1. Go to **Settings $\rightarrow$ Apps $\rightarrow$ Resibo**.
2. Tap the **⋮ (three dots)** menu in the top right corner.
3. Tap **"Allow restricted settings"**.
4. Return to Resibo and enable Notification Access.

### Background App Killing (Xiaomi, Oppo, Realme, Vivo, Samsung)
Aggressive OEM battery managers may close background listeners when the screen locks:
1. Open phone **Settings $\rightarrow$ Apps $\rightarrow$ Resibo $\rightarrow$ Battery**.
2. Choose **"Unrestricted"** or **"No restrictions"**.
3. Enable **"Autostart"** in your phone's permission manager.

### Offline / Wi-Fi Fallback
If the venue Wi-Fi or Gemini API is unreachable, Resibo automatically runs deterministic rule-based checks and ledger matching labeled **"Basic check (AI unavailable)"**.

---

## ⚠️ Known Limitations
1. **Merchant Phone Must Be On**: The owner's phone must be on and connected to capture incoming push notifications. If the phone is powered off when money arrives, the receipt safely remains `⚠️ NOT YET RECEIVED` (never falsely verified), allowing the owner to verify manually via GCash and tap **"Mark as received"**.
2. **Missing Reference Numbers on Certain Bank Notifications**: Some banks show only the amount and masked sender name in push alerts. Resibo handles this through its secondary **Amount + Sender + Timestamp ($\pm 10$ mins)** matching algorithm.

---

## 🧪 Testing the 5 Official Demo Cases

You can execute the automated demo test suite at any time:
```bash
node scripts/run-demo-cases.js
```

| Case | Scenario | Expected Verdict | Reason |
|:---:|:---|:---:|:---|
| **#1** | Real receipt, payment arrived first | **`VERIFIED ✅`** | Exact match on reference number and amount |
| **#2** | Duplicate receipt upload | **`FAKE ❌`** | Reference number already used on earlier receipt |
| **#3** | Altered amount (₱150 edited to ₱1,500) | **`FAKE ❌`** | Amount mismatch + high tamper risk font signs |
| **#4** | Paid to different GCash number | **`FAKE ❌`** | Recipient does not match registered shop accounts |
| **#5** | Uploaded before money arrives $\rightarrow$ Payment simulated | **`⚠️` $\rightarrow$ `✅`** | Live status flip from Waiting to Verified |

---

## 📄 License
MIT License. Built for hackathon demonstration.
