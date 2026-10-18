# 🎙️ Resibo — 3-Minute Live Hackathon Demo Script

A step-by-step presentation script designed to win over judges in under 3 minutes.

---

## ⏱️ Timeline Overview

| Timestamp | Segment | Action |
| :--- | :--- | :--- |
| **0:00 – 0:30** | The Problem | Explain the surge of fake GCash/Maya receipts victimizing small merchants. |
| **0:30 – 1:00** | The Solution & Architecture | Introduce Resibo: Ledger-first verification via Android notification capture. |
| **1:00 – 2:15** | Live Walkthrough | Demo the 5 cases (Fake caught, Duplicate blocked, and Live Status Flip). |
| **2:15 – 2:45** | Key Innovation & Privacy | Why reference lookup isn't enough + zero app login credentials needed. |
| **2:45 – 3:00** | Conclusion & Q&A | Impact on Filipino micro-enterprises and closing pitch. |

---

## 📋 Pre-Demo Checklist (Night Before)

- [ ] Android app installed on the demo phone or running in the emulator.
- [ ] Notification access turned **ON** (*Status: "Payment capture ON ✅"*).
- [ ] Laptop open with web buyer page ready at `http://localhost:3000?shop=kape-ni-aling-rosa` (or live hosting URL).
- [ ] Test cases rehearsed twice using `node scripts/run-demo-cases.js`.
- [ ] **Highlight Moment:** Rehearse Demo Case #5 (⚠️ flipping to ✅ live).

---

## 🎭 Step-by-Step Spoken Script

### Part 1: The Problem (0:00 – 0:30)
> *"Every day, thousands of Filipino small business owners accept GCash, Maya, and bank transfers. But photo-editing tools and fake receipt generators are getting so sophisticated that merchants can no longer distinguish real screenshots from fake ones. By the time they realize no money arrived, the customer has walked away with the goods."*

### Part 2: The Solution (0:30 – 1:00)
> *"Meet **Resibo** — an AI-powered fake-receipt detector built specifically for Filipino small businesses. 
> 
> Here is our core principle: **A screenshot alone can never be marked verified.** The reference number is our matching key. Resibo only marks a payment verified when it matches money that actually arrived on the owner's phone. And the best part? Resibo never logs into GCash, Maya, or bank accounts — it simply listens to system payment notifications using Android's native Notification Access."*

### Part 3: Live Demonstration (1:00 – 2:15)

#### 1. The Buyer Experience (Web, No App Install)
> *"A customer buys from 'Kape ni Aling Rosa'. They scan the merchant's QR code or visit `/pay/kape-ni-aling-rosa`. No app install. No sign-up. They upload their payment screenshot and tap Submit."*

#### 2. Case 1 & 2: Instant Verified & Duplicate Detection
> *"When a legitimate payment has already arrived, Resibo verifies it within seconds — showing `VERIFIED ✅`.
> 
> But what if a scammer tries to reuse that exact same receipt 5 minutes later? Let's upload it again. 
> 
> **Boom:** Resibo immediately flags it as `FAKE ❌` with the plain Taglish explanation: 'Nagamit na ang reference number na ito noong nakaraang transaksyon.' The merchant is protected from double-spending."*

#### 3. Case 3: Altered Amount & AI Image Forensics
> *"Next, a customer buys ₱1,500 worth of coffee, but only paid ₱150, editing the screenshot.
> 
> Resibo's Gemini Vision Forensics catches this in two ways:
> 1. It flags font inconsistencies and smudge marks around the amount.
> 2. It cross-checks the ledger: 'The receipt claims ₱1,500, but only ₱150 arrived.' Verdict: `FAKE ❌`."*

#### 4. Case 5: The Showstopper — The Live Status Flip ⭐
*(This is your strongest moment! Show the phone screen to the judges)*
> *"Now for our best feature. What happens when the buyer uploads a receipt before the money hits the bank?
> 
> On the merchant's phone, the receipt appears as `⚠️ WAITING FOR PAYMENT`. Resibo tells the cashier: 'Huwag munang i-release ang paninda.'
> 
> Now watch closely. When the customer's payment actually clears..."*
*(Tap 'Simulate Incoming Payment' or have an SMS arrive)*
> *"...the phone captures the notification, and **LIVE ON SCREEN**, the receipt flips from ⚠️ Waiting to `✅ VERIFIED`! The cashier releases the item with 100% confidence."*

### Part 4: Privacy & Conclusion (2:15 – 3:00)
> *"Resibo is 100% compliant with the Philippine Data Privacy Act (RA 10173). We never store full notification texts, passwords, or PINs. If Wi-Fi goes down at the store, our offline fallback keeps verifying receipts using deterministic hard checks.
> 
> Resibo puts an end to fake receipt fraud for sari-sari stores, pop-up markets, and Filipino merchants everywhere. Thank you!"*

---

## 🛡️ Emergency Venue / Offline Fallback Tips

- **If venue Wi-Fi fails:** Resibo's Cloud Functions and Android app automatically switch to `"Basic check (AI unavailable)"`. Hard checks, duplicate detection, and ledger matching continue to function seamlessly without internet.
- **If Notification Access toggle is grayed out on Android 13+:** Go to *Settings $\rightarrow$ Apps $\rightarrow$ Resibo $\rightarrow$ ⋮ (3 dots top right) $\rightarrow$ Allow restricted settings*.
- **If OEM phone closes background apps (Xiaomi, Oppo, Vivo):** Set Resibo battery usage to *"Unrestricted / No restrictions"* and enable *"Autostart"*.
