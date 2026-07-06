const MAX_DIMENSION = 1024;
const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
const TARGET_BYTES = 300 * 1024;
const ALLOWED_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);

export class ImageUploadError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ImageUploadError";
  }
}

export type OptimizedImageResult = {
  file: File;
  previewUrl: string;
};

export function formatFileSize(sizeBytes: number): string {
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`;
  }
  if (sizeBytes < 1024 * 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`;
  }
  return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
}

export async function optimizeAvatarUpload(file: File): Promise<OptimizedImageResult> {
  validateImageFile(file);

  const image = await loadImage(file);
  const { width, height } = scaleDimensions(image.naturalWidth || image.width, image.naturalHeight || image.height, MAX_DIMENSION);
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;

  const context = canvas.getContext("2d");
  if (!context) {
    throw new ImageUploadError("Image preview could not be prepared. Try another file.");
  }

  context.drawImage(image, 0, 0, width, height);

  const outputType = supportsType(file.type) ? preferredOutputType(file.type) : "image/jpeg";
  const optimizedBlob = await renderOptimizedBlob(canvas, outputType);
  if (optimizedBlob.size > MAX_UPLOAD_BYTES) {
    throw new ImageUploadError("Image is too large. Please upload an image below 10 MB.");
  }

  const extension = extensionForType(optimizedBlob.type);
  const baseName = stripExtension(file.name) || "doctor-avatar";
  const optimizedFile = new File([optimizedBlob], `${baseName}.${extension}`, {
    type: optimizedBlob.type,
    lastModified: Date.now(),
  });

  return {
    file: optimizedFile,
    previewUrl: URL.createObjectURL(optimizedFile),
  };
}

function validateImageFile(file: File) {
  if (!ALLOWED_TYPES.has(normalizeType(file.type)) && !hasAllowedExtension(file.name)) {
    throw new ImageUploadError("Doctor profile photo must be JPG, PNG, or WEBP.");
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    throw new ImageUploadError("Image is too large. Please upload an image below 10 MB.");
  }
}

function normalizeType(type: string): string {
  return type.trim().toLowerCase() === "image/jpg" ? "image/jpeg" : type.trim().toLowerCase();
}

function hasAllowedExtension(fileName: string): boolean {
  const lower = fileName.trim().toLowerCase();
  return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
}

function preferredOutputType(inputType: string): string {
  const normalized = normalizeType(inputType);
  if (normalized === "image/webp") {
    return "image/webp";
  }
  return "image/jpeg";
}

function supportsType(type: string): boolean {
  return ALLOWED_TYPES.has(normalizeType(type));
}

function extensionForType(type: string): string {
  return type === "image/webp" ? "webp" : "jpg";
}

function stripExtension(fileName: string): string {
  return fileName.replace(/\.[^.]+$/, "");
}

function scaleDimensions(width: number, height: number, maxDimension: number) {
  if (width <= maxDimension && height <= maxDimension) {
    return { width, height };
  }
  const ratio = Math.min(maxDimension / width, maxDimension / height);
  return {
    width: Math.max(1, Math.round(width * ratio)),
    height: Math.max(1, Math.round(height * ratio)),
  };
}

function loadImage(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(objectUrl);
      resolve(image);
    };
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new ImageUploadError("Image preview could not be prepared. Try another file."));
    };
    image.src = objectUrl;
  });
}

async function renderOptimizedBlob(canvas: HTMLCanvasElement, type: string): Promise<Blob> {
  const qualitySteps = [0.9, 0.82, 0.74, 0.66, 0.58];
  let candidate: Blob | null = null;
  for (const quality of qualitySteps) {
    candidate = await canvasToBlob(canvas, type, quality);
    if (candidate.size <= TARGET_BYTES) {
      return candidate;
    }
  }
  return candidate ?? await canvasToBlob(canvas, type, 0.82);
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new ImageUploadError("Image preview could not be prepared. Try another file."));
        return;
      }
      resolve(blob);
    }, type, quality);
  });
}
