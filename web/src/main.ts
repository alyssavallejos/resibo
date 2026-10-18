import { translations, type Language } from "./i18n.js";
import { checkRateLimit, recordUpload } from "./rateLimit.js";
import {
  getBusinessBySlug,
  submitReceipt,
  type Business,
} from "./firebase.js";

let currentLang: Language = "tl";
let currentBusiness: Business | null = null;
let selectedFile: File | null = null;

// DOM Elements
const langToggleBtn = document.getElementById("langToggleBtn") as HTMLButtonElement;
const langFlag = document.getElementById("langFlag") as HTMLSpanElement;
const langText = document.getElementById("langText") as HTMLSpanElement;

const loadingView = document.getElementById("loadingView") as HTMLDivElement;
const notFoundView = document.getElementById("notFoundView") as HTMLDivElement;
const shopView = document.getElementById("shopView") as HTMLDivElement;
const successView = document.getElementById("successView") as HTMLDivElement;

const shopNameEl = document.getElementById("shopName") as HTMLHeadingElement;
const shopTaglineEl = document.getElementById("shopTagline") as HTMLParagraphElement;
const accountsListEl = document.getElementById("accountsList") as HTMLDivElement;
const accountsLabelEl = document.getElementById("accountsLabel") as HTMLDivElement;

const dropzone = document.getElementById("dropzone") as HTMLDivElement;
const fileInput = document.getElementById("receiptFileInput") as HTMLInputElement;
const previewContainer = document.getElementById("previewContainer") as HTMLDivElement;
const previewImage = document.getElementById("previewImage") as HTMLImageElement;
const changePhotoBtn = document.getElementById("changePhotoBtn") as HTMLButtonElement;

const uploadForm = document.getElementById("uploadForm") as HTMLFormElement;
const buyerNameInput = document.getElementById("buyerNameInput") as HTMLInputElement;
const amountInput = document.getElementById("amountInput") as HTMLInputElement;
const refInput = document.getElementById("refInput") as HTMLInputElement;
const noteInput = document.getElementById("noteInput") as HTMLInputElement;
const submitBtn = document.getElementById("submitBtn") as HTMLButtonElement;
const submitBtnText = document.getElementById("submitBtnText") as HTMLSpanElement;
const alertBox = document.getElementById("alertBox") as HTMLDivElement;

// Labels & i18n placeholders
const dropzonePrompt = document.getElementById("dropzonePrompt") as HTMLDivElement;
const dropzoneHint = document.getElementById("dropzoneHint") as HTMLDivElement;
const buyerNameLabel = document.getElementById("buyerNameLabel") as HTMLLabelElement;
const amountLabel = document.getElementById("amountLabel") as HTMLLabelElement;
const refLabel = document.getElementById("refLabel") as HTMLLabelElement;
const noteLabel = document.getElementById("noteLabel") as HTMLLabelElement;
const privacyNoticeTitle = document.getElementById("privacyNoticeTitle") as HTMLSpanElement;
const privacyDetailText = document.getElementById("privacyDetailText") as HTMLParagraphElement;
const notFoundTitle = document.getElementById("notFoundTitle") as HTMLHeadingElement;
const notFoundMsg = document.getElementById("notFoundMsg") as HTMLParagraphElement;

const successTitle = document.getElementById("successTitle") as HTMLHeadingElement;
const successMsg = document.getElementById("successMsg") as HTMLParagraphElement;
const statusPendingText = document.getElementById("statusPendingText") as HTMLSpanElement;
const summaryBuyerName = document.getElementById("summaryBuyerName") as HTMLSpanElement;
const summaryAmountRow = document.getElementById("summaryAmountRow") as HTMLDivElement;
const summaryAmount = document.getElementById("summaryAmount") as HTMLSpanElement;
const summaryTime = document.getElementById("summaryTime") as HTMLSpanElement;
const uploadAnotherBtn = document.getElementById("uploadAnotherBtn") as HTMLButtonElement;

/**
 * Extract shop slug from path (/pay/{slug}, /{slug}) or URL search params (?shop={slug})
 */
function extractShopSlug(): string {
  const url = new URL(window.location.href);
  const searchSlug =
    url.searchParams.get("shop") ||
    url.searchParams.get("slug") ||
    url.searchParams.get("store");
  if (searchSlug) return searchSlug.trim().toLowerCase();

  const pathSegments = url.pathname
    .split("/")
    .map((s) => decodeURIComponent(s).trim())
    .filter(Boolean);

  // Example: /pay/alyssa -> "alyssa"
  const payIndex = pathSegments.indexOf("pay");
  if (payIndex !== -1 && pathSegments.length > payIndex + 1) {
    return pathSegments[payIndex + 1].toLowerCase();
  }

  // Example: /shop/alyssa or /store/alyssa -> "alyssa"
  const shopIndex = pathSegments.findIndex((s) => s === "shop" || s === "store");
  if (shopIndex !== -1 && pathSegments.length > shopIndex + 1) {
    return pathSegments[shopIndex + 1].toLowerCase();
  }

  // Direct path: /alyssa or /alyssa/ -> "alyssa"
  if (pathSegments.length >= 1 && !pathSegments[0].includes(".")) {
    return pathSegments[0].toLowerCase();
  }

  // Default to demo if at root or nothing specified
  return "kape-ni-aling-rosa";
}

/**
 * Update UI text based on chosen language
 */
function applyLanguage(lang: Language) {
  currentLang = lang;
  const t = translations[lang];

  langFlag.textContent = lang === "tl" ? "🇵🇭" : "🇺🇸";
  langText.textContent = lang === "tl" ? "English" : "Tagalog";

  if (accountsLabelEl) accountsLabelEl.textContent = t.acceptedAccounts;
  if (dropzonePrompt) dropzonePrompt.textContent = t.dropzonePrompt;
  if (dropzoneHint) dropzoneHint.textContent = t.dropzoneHint;
  if (changePhotoBtn) changePhotoBtn.textContent = t.changePhoto;

  if (buyerNameLabel) buyerNameLabel.textContent = t.buyerNameLabel;
  if (buyerNameInput) buyerNameInput.placeholder = t.buyerNamePlaceholder;

  if (amountLabel) amountLabel.textContent = t.amountLabel;
  if (amountInput) amountInput.placeholder = t.amountPlaceholder;

  if (refLabel) refLabel.textContent = t.refLabel;
  if (refInput) refInput.placeholder = t.refPlaceholder;

  if (noteLabel) noteLabel.textContent = t.noteLabel;
  if (noteInput) noteInput.placeholder = t.notePlaceholder;

  if (privacyNoticeTitle) privacyNoticeTitle.textContent = t.privacyNotice;
  if (privacyDetailText) privacyDetailText.textContent = t.privacyDetail;

  if (submitBtnText) submitBtnText.textContent = t.submitButton;
  if (uploadAnotherBtn) uploadAnotherBtn.textContent = t.uploadAnother;

  if (notFoundTitle) notFoundTitle.textContent = t.shopNotFoundTitle;
  if (notFoundMsg) notFoundMsg.textContent = t.shopNotFoundMsg;

  if (successTitle) successTitle.textContent = t.successTitle;
  if (successMsg) successMsg.textContent = t.successMessage;
  if (statusPendingText) statusPendingText.textContent = t.statusPending;

  if (shopTaglineEl) shopTaglineEl.textContent = t.uploadSubtitle;
}

function showAlert(message: string) {
  alertBox.textContent = message;
  alertBox.style.display = "block";
  alertBox.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

function hideAlert() {
  alertBox.style.display = "none";
  alertBox.textContent = "";
}

/**
 * Render business details and payment accounts
 */
function renderBusiness(business: Business) {
  currentBusiness = business;
  shopNameEl.textContent = business.name;

  accountsListEl.innerHTML = "";
  if (!business.accounts || business.accounts.length === 0) {
    accountsListEl.innerHTML = `<div style="font-size: 13px; color: var(--text-muted);">${translations[currentLang].noAccounts}</div>`;
  } else {
    business.accounts.forEach((acc) => {
      const item = document.createElement("div");
      item.className = "account-item";
      item.innerHTML = `
        <span class="provider-badge ${acc.provider}">${acc.provider.toUpperCase()}</span>
        <div class="account-details">
          <div class="account-name">${acc.accountName}</div>
          <div class="account-number">•••• •••• •••• ${acc.last4}</div>
        </div>
      `;
      accountsListEl.appendChild(item);
    });
  }

  loadingView.style.display = "none";
  notFoundView.style.display = "none";
  shopView.style.display = "block";
}

function renderNotFound() {
  loadingView.style.display = "none";
  shopView.style.display = "none";
  notFoundView.style.display = "block";
}

/**
 * Handle image file selection
 */
function handleFile(file: File) {
  hideAlert();
  const t = translations[currentLang];

  if (!file.type.startsWith("image/")) {
    showAlert(t.fileTypeError);
    return;
  }

  const MAX_SIZE = 5 * 1024 * 1024; // 5 MB
  if (file.size > MAX_SIZE) {
    showAlert(t.fileTooLargeError);
    return;
  }

  selectedFile = file;
  const reader = new FileReader();
  reader.onload = (e) => {
    previewImage.src = e.target?.result as string;
    dropzone.style.display = "none";
    previewContainer.style.display = "block";
  };
  reader.readAsDataURL(file);
}

// Event Listeners
langToggleBtn.addEventListener("click", () => {
  const nextLang = currentLang === "tl" ? "en" : "tl";
  applyLanguage(nextLang);
});

dropzone.addEventListener("click", () => fileInput.click());
dropzone.addEventListener("keydown", (e) => {
  if (e.key === "Enter" || e.key === " ") {
    e.preventDefault();
    fileInput.click();
  }
});

dropzone.addEventListener("dragover", (e) => {
  e.preventDefault();
  dropzone.classList.add("dragover");
});

dropzone.addEventListener("dragleave", () => {
  dropzone.classList.remove("dragover");
});

dropzone.addEventListener("drop", (e) => {
  e.preventDefault();
  dropzone.classList.remove("dragover");
  if (e.dataTransfer?.files && e.dataTransfer.files[0]) {
    handleFile(e.dataTransfer.files[0]);
  }
});

fileInput.addEventListener("change", () => {
  if (fileInput.files && fileInput.files[0]) {
    handleFile(fileInput.files[0]);
  }
});

changePhotoBtn.addEventListener("click", () => {
  fileInput.value = "";
  fileInput.click();
});

// Form Submission
uploadForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  hideAlert();
  const t = translations[currentLang];

  if (!currentBusiness) {
    showAlert(t.shopNotFoundMsg);
    return;
  }

  if (!selectedFile) {
    showAlert(t.photoRequiredError);
    return;
  }

  const buyerName = buyerNameInput.value.trim();
  if (!buyerName) {
    showAlert(t.nameRequiredError);
    buyerNameInput.focus();
    return;
  }

  if (!checkRateLimit()) {
    showAlert(t.rateLimitError);
    return;
  }

  // Disable submit button & show loading state
  submitBtn.disabled = true;
  submitBtnText.textContent = t.submittingButton;

  try {
    const rawAmount = amountInput.value.trim();
    const expectedAmount = rawAmount ? parseFloat(rawAmount) : undefined;
    const referenceNumber = refInput.value.trim() || undefined;
    const note = noteInput.value.trim();

    const uploadPromise = submitReceipt({
      businessId: currentBusiness.id,
      buyerName,
      note,
      expectedAmount,
      referenceNumber,
      imageFile: selectedFile,
    });

    const timeoutPromise = new Promise<never>((_, reject) =>
      setTimeout(() => reject(new Error("Upload timed out. Please try again.")), 10000)
    );

    await Promise.race([uploadPromise, timeoutPromise]);

    recordUpload();

    // Show success view
    shopView.style.display = "none";
    summaryBuyerName.textContent = buyerName;
    if (expectedAmount) {
      summaryAmount.textContent = `₱${expectedAmount.toLocaleString("en-PH", { minimumFractionDigits: 2 })}`;
      summaryAmountRow.style.display = "block";
    } else {
      summaryAmountRow.style.display = "none";
    }
    summaryTime.textContent = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    successView.style.display = "block";
    window.scrollTo({ top: 0, behavior: "smooth" });
  } catch (err: unknown) {
    console.error("Submission failed", err);
    showAlert("Failed to upload receipt. Please try again.");
  } finally {
    submitBtn.disabled = false;
    submitBtnText.textContent = t.submitButton;
  }
});

uploadAnotherBtn.addEventListener("click", () => {
  uploadForm.reset();
  selectedFile = null;
  previewContainer.style.display = "none";
  dropzone.style.display = "block";
  successView.style.display = "none";
  shopView.style.display = "block";
  hideAlert();
});

// Initialization
async function init() {
  applyLanguage("tl");
  const slug = extractShopSlug();

  try {
    const business = await getBusinessBySlug(slug);
    if (business) {
      renderBusiness(business);
    } else {
      renderNotFound();
    }
  } catch (err) {
    console.error("Error loading business profile", err);
    renderNotFound();
  }
}

init();
