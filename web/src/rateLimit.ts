const RATE_LIMIT_KEY = "resibo_upload_timestamps";
const MAX_UPLOADS_PER_HOUR = 10;
const ONE_HOUR_MS = 60 * 60 * 1000;

/**
 * Check if the current client has exceeded the 10 uploads/hour limit
 */
export function checkRateLimit(): boolean {
  try {
    const raw = localStorage.getItem(RATE_LIMIT_KEY);
    const timestamps: number[] = raw ? JSON.parse(raw) : [];
    const now = Date.now();
    const recent = timestamps.filter((t) => now - t < ONE_HOUR_MS);
    return recent.length < MAX_UPLOADS_PER_HOUR;
  } catch {
    return true;
  }
}

/**
 * Record an upload timestamp in localStorage
 */
export function recordUpload(): void {
  try {
    const raw = localStorage.getItem(RATE_LIMIT_KEY);
    const timestamps: number[] = raw ? JSON.parse(raw) : [];
    const now = Date.now();
    const recent = timestamps.filter((t) => now - t < ONE_HOUR_MS);
    recent.push(now);
    localStorage.setItem(RATE_LIMIT_KEY, JSON.stringify(recent));
  } catch (err) {
    console.warn("Could not save rate limit entry", err);
  }
}

/**
 * Get remaining uploads allowed in the current hour window
 */
export function getRemainingUploads(): number {
  try {
    const raw = localStorage.getItem(RATE_LIMIT_KEY);
    const timestamps: number[] = raw ? JSON.parse(raw) : [];
    const now = Date.now();
    const recent = timestamps.filter((t) => now - t < ONE_HOUR_MS);
    return Math.max(0, MAX_UPLOADS_PER_HOUR - recent.length);
  } catch {
    return MAX_UPLOADS_PER_HOUR;
  }
}
