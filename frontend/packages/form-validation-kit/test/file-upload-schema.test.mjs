import test from "node:test";
import assert from "node:assert/strict";

import { documentUploadSchema, fileUploadSchema, getFileExtension, hasAllowedExtension, hasAllowedMimeType, imageUploadSchema, isWithinMaxFileSize, normalizeFileName } from "../dist/index.js";

test("file upload schema accepts allowed csv files", () => {
  const result = fileUploadSchema({
    required: true,
    allowedMimeTypes: ["text/csv"],
    allowedExtensions: ["csv"],
    maxBytes: 1024,
  }).safeParse({
    name: "leads.csv",
    size: 512,
    type: "text/csv",
  });

  assert.equal(result.success, true);
});

test("document upload schema rejects invalid mime type", () => {
  const result = documentUploadSchema({
    required: true,
    maxBytes: 1024,
  }).safeParse({
    name: "scan.exe",
    size: 512,
    type: "application/x-msdownload",
  });

  assert.equal(result.success, false);
});

test("image upload schema rejects oversize files", () => {
  const result = imageUploadSchema({
    required: true,
    maxBytes: 128,
  }).safeParse({
    name: "photo.png",
    size: 512,
    type: "image/png",
  });

  assert.equal(result.success, false);
});

test("file helper functions normalize and validate names", () => {
  assert.equal(normalizeFileName("  My  Report.pdf  "), "My Report.pdf");
  assert.equal(getFileExtension("invoice.PDF"), "pdf");
  assert.equal(hasAllowedExtension("invoice.PDF", ["pdf"]), true);
  assert.equal(hasAllowedMimeType({ name: "invoice.pdf", size: 10, type: "application/pdf" }, ["application/pdf"]), true);
  assert.equal(isWithinMaxFileSize({ name: "invoice.pdf", size: 10, type: "application/pdf" }, 5), false);
});
