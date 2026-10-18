import { GoogleGenerativeAI } from "@google/generative-ai";

export interface ImageForensics {
  edited_signs: string[];
  generator_app_signs: string[];
  tamper_risk: "low" | "medium" | "high";
  explanation: string;
  metadata_software?: string | null;
}

/**
 * Scan image buffer for common editing software signatures in EXIF/header strings
 */
export function inspectImageMetadata(buffer: Buffer): string | null {
  try {
    const rawString = buffer.toString("binary", 0, Math.min(buffer.length, 65536));
    const editingKeywords = [
      "Photoshop",
      "Canva",
      "PicsArt",
      "InShot",
      "GIMP",
      "Pixelmator",
      "Snapseed",
      "Lightroom",
      "IbisPaint",
      "Phonto",
    ];

    for (const kw of editingKeywords) {
      if (rawString.includes(kw)) {
        return kw;
      }
    }
  } catch (err) {
    console.warn("Could not parse image metadata", err);
  }
  return null;
}

/**
 * Perform image forensics using Gemini Vision
 */
export async function analyzeImageForensics(
  imageBase64: string,
  imageBuffer?: Buffer,
  mimeType: string = "image/jpeg"
): Promise<ImageForensics> {
  const softwareTag = imageBuffer ? inspectImageMetadata(imageBuffer) : null;
  const apiKey = process.env.GEMINI_API_KEY;

  if (!apiKey) {
    // Deterministic fallback if API key not supplied
    if (softwareTag) {
      return {
        edited_signs: [`Nakita sa metadata ng larawan ang software na "${softwareTag}".`],
        generator_app_signs: [],
        tamper_risk: "high",
        explanation: `Mataas ang panganib ng pandaraya: na-edit ang screenshot gamit ang ${softwareTag}.`,
        metadata_software: softwareTag,
      };
    }
    return {
      edited_signs: [],
      generator_app_signs: [],
      tamper_risk: "low",
      explanation: "Walang nakitang bakas ng pag-edit (Basic forensics check).",
      metadata_software: null,
    };
  }

  try {
    const genAI = new GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({
      model: "gemini-1.5-flash",
      generationConfig: {
        responseMimeType: "application/json",
      },
    });

    const prompt = `You are a forensic document examiner specializing in digital image tampering and payment receipt forgery (GCash, Maya, Bank transfers).
Inspect this receipt screenshot closely for any indicators of manual photo editing or fake receipt generators:
1. Font inconsistencies: mismatched fonts, incorrect font weights, digits around the amount or reference number that don't match the standard system font.
2. Alignment: misaligned digits, baseline jitter, uneven character spacing.
3. Compression artifacts & smudges: blur, pixelation, or halo artifacts specifically localized around the amount or date.
4. Color mismatches: inconsistent background color or brightness patches behind the numbers.
5. Receipt generator signatures: fake web/app templates, missing elements, or wrong system bar icons.

Return ONLY a valid JSON object matching this schema:
{
  "edited_signs": string[] (list of observed anomalies in simple English or Taglish, empty if clean),
  "generator_app_signs": string[] (list of fake generator app signs, empty if clean),
  "tamper_risk": "low" | "medium" | "high",
  "explanation": string (1-2 sentences in simple Taglish explaining the visual analysis to a Filipino shop owner)
}`;

    const result = await model.generateContent([
      prompt,
      {
        inlineData: {
          data: imageBase64,
          mimeType,
        },
      },
    ]);

    const parsed = JSON.parse(result.response.text()) as ImageForensics;

    if (softwareTag && parsed.tamper_risk !== "high") {
      parsed.tamper_risk = "high";
      parsed.edited_signs.push(`May software tag ng "${softwareTag}" sa file metadata.`);
      parsed.explanation = `Mataas ang panganib: May bakas ng pag-edit gamit ang ${softwareTag}.`;
      parsed.metadata_software = softwareTag;
    }

    return parsed;
  } catch (err) {
    console.warn("Forensics vision analysis failed, falling back to metadata:", err);
    return {
      edited_signs: softwareTag ? [`Software tag "${softwareTag}" detected in image metadata.`] : [],
      generator_app_signs: [],
      tamper_risk: softwareTag ? "high" : "low",
      explanation: softwareTag
        ? `Traces of editing detected using ${softwareTag}.`
        : "No signs of image tampering detected.",
      metadata_software: softwareTag,
    };
  }
}
