export type Language = "tl" | "en";

export interface Translations {
  appName: string;
  tagline: string;
  payTo: string;
  acceptedAccounts: string;
  noAccounts: string;
  uploadTitle: string;
  uploadSubtitle: string;
  dropzonePrompt: string;
  dropzoneHint: string;
  changePhoto: string;
  buyerNameLabel: string;
  buyerNamePlaceholder: string;
  amountLabel: string;
  amountPlaceholder: string;
  refLabel: string;
  refPlaceholder: string;
  noteLabel: string;
  notePlaceholder: string;
  submitButton: string;
  submittingButton: string;
  privacyNotice: string;
  privacyDetail: string;
  privacyAck: string;
  successTitle: string;
  successMessage: string;
  statusPending: string;
  uploadAnother: string;
  shopNotFoundTitle: string;
  shopNotFoundMsg: string;
  rateLimitError: string;
  fileTooLargeError: string;
  fileTypeError: string;
  nameRequiredError: string;
  photoRequiredError: string;
  demoModeNotice: string;
}

export const translations: Record<Language, Translations> = {
  tl: {
    appName: "Resibo",
    tagline: "Ligtas at Mabilis na Pag-verify ng Bayad",
    payTo: "Magbayad kay",
    acceptedAccounts: "Mga Tinatanggap na Account",
    noAccounts: "Walang nakalistang mga account para sa tindahang ito.",
    uploadTitle: "I-upload ang Iyong Resibo",
    uploadSubtitle: "Ipadala ang screenshot ng iyong GCash, Maya, o Bank transfer.",
    dropzonePrompt: "Pindutin para pumili ng screenshot o i-drag dito",
    dropzoneHint: "PNG, JPG, o WEBP (hanggang 5 MB)",
    changePhoto: "Palitan ang screenshot",
    buyerNameLabel: "Pangalan ng Nagbayad *",
    buyerNamePlaceholder: "Hal. Juan Dela Cruz",
    amountLabel: "Halaga ng Binayaran (₱) (Opsyonal)",
    amountPlaceholder: "Hal. 150.00",
    refLabel: "Reference Number (Opsyonal - kusa ring babasahin ng system)",
    refPlaceholder: "Hal. BC550000034535407831 o 1029384756",
    noteLabel: "Order Note / Binili (Opsyonal)",
    notePlaceholder: "Hal. 2 Iced Latte, Table 3",
    submitButton: "Ipadala ang Resibo",
    submittingButton: "Ipinapadala...",
    privacyNotice: "Abiso sa Data Privacy (RA 10173)",
    privacyDetail: "Ang impormasyon at screenshot na iyong isusumite ay gagamitin lamang para kumpirmahin ang iyong bayad. Hindi ito ibabahagi sa iba.",
    privacyAck: "Nauunawaan ko at sumasang-ayon ako sa paggamit ng aking datos para sa pag-verify.",
    successTitle: "Natanggap na ang Resibo!",
    successMessage: "Natanggap na ng nagbebenta ang iyong resibo at agad itong susuriin. Salamat!",
    statusPending: "Natanggap ng System (Receipt Received)",
    uploadAnother: "Magpadala ng Iba Pang Resibo",
    shopNotFoundTitle: "Hindi Mahanap ang Tindahan",
    shopNotFoundMsg: "Ang hinahanap mong shop link ay hindi umiiral o maling slug ang nailagay.",
    rateLimitError: "Masyadong maraming upload! Limitado sa 10 resibo bawat oras.",
    fileTooLargeError: "Masyadong malaki ang file. Hanggang 5 MB lamang ang pinapayagan.",
    fileTypeError: "Larawan lamang (JPG/PNG/WEBP) ang maaaring i-upload.",
    nameRequiredError: "Pakilagay ang iyong pangalan.",
    photoRequiredError: "Kailangang mag-upload ng screenshot ng resibo.",
    demoModeNotice: "Demo / Preview Mode: Naka-konekta sa local preview storage."
  },
  en: {
    appName: "Resibo",
    tagline: "Fast & Secure Payment Verification",
    payTo: "Pay to",
    acceptedAccounts: "Accepted Payment Accounts",
    noAccounts: "No registered payment accounts for this merchant.",
    uploadTitle: "Upload Your Payment Receipt",
    uploadSubtitle: "Send a screenshot of your GCash, Maya, or Bank transfer receipt.",
    dropzonePrompt: "Tap to select screenshot or drag here",
    dropzoneHint: "PNG, JPG, or WEBP (up to 5 MB)",
    changePhoto: "Change screenshot",
    buyerNameLabel: "Payer's Name *",
    buyerNamePlaceholder: "e.g. Juan Dela Cruz",
    amountLabel: "Paid Amount (₱) (Optional)",
    amountPlaceholder: "e.g. 150.00",
    refLabel: "Reference Number (Optional - system will also auto-scan)",
    refPlaceholder: "e.g. BC550000034535407831 or 1029384756",
    noteLabel: "Order Note / Items (Optional)",
    notePlaceholder: "e.g. 2 Iced Lattes, Table 3",
    submitButton: "Submit Receipt",
    submittingButton: "Submitting...",
    privacyNotice: "Data Privacy Notice (RA 10173)",
    privacyDetail: "Your submitted receipt and details are used exclusively to verify this transaction. Your data is protected under the Philippine Data Privacy Act.",
    privacyAck: "I understand and consent to the processing of this receipt for payment verification.",
    successTitle: "Receipt Received!",
    successMessage: "Received! The seller will confirm your payment shortly.",
    statusPending: "Receipt Received by System",
    uploadAnother: "Submit Another Receipt",
    shopNotFoundTitle: "Shop Not Found",
    shopNotFoundMsg: "The merchant link you visited does not exist or has an incorrect slug.",
    rateLimitError: "Upload limit reached! Maximum 10 uploads per hour per device.",
    fileTooLargeError: "File is too large. Maximum allowed size is 5 MB.",
    fileTypeError: "Only image files (JPG/PNG/WEBP) are accepted.",
    nameRequiredError: "Please enter your name.",
    photoRequiredError: "Please attach a screenshot of your payment receipt.",
    demoModeNotice: "Demo / Preview Mode: Running with local preview store."
  }
};
