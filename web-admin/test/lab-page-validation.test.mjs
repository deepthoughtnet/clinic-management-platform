import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("lab editor keeps test code required, read-only on edit, and uses the shared required label", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('label={<RequiredLabel text="Test Code" required />}'));
  assert.ok(source.includes('disabled={Boolean(editing)}'));
  assert.ok(source.includes('InputProps={{ readOnly: Boolean(editing) }}'));
  assert.ok(source.includes('label={<RequiredLabel text="Test Name" required />}'));
  assert.ok(source.includes('label={<RequiredLabel text="Price" required />}'));
  assert.ok(source.includes('const [priceInput, setPriceInput] = React.useState("");'));
  assert.ok(source.includes('type="text"'));
  assert.ok(source.includes('inputMode="decimal"'));
  assert.ok(source.includes('event.target.value.replace(/[^0-9.]/g, "")'));
  assert.ok(source.includes('const decimal = decimalParts.join("").slice(0, 2)'));
  assert.ok(!source.includes('type="number" label={<RequiredLabel text="Price" required />}'));
  assert.ok(!source.includes('priceEditedRef'));
  assert.ok(source.includes('const trimmedPrice = priceInput.trim();'));
  assert.ok(source.includes('if (!Number.isFinite(parsedPrice))'));
  assert.ok(source.includes('setPriceInput("");'));
  assert.ok(source.includes('label="Turnaround Time (Hours)"'));
});

test("new lab test price starts blank while edit keeps the stored value", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('setPriceInput("");'));
  assert.ok(source.includes('setPriceInput(String(row.price));'));
  assert.ok(source.includes('const parsedPrice = trimmedPrice === "" ? Number.NaN : Number(trimmedPrice);'));
});

test("lab order referral details are conditional and external doctor name is required", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('requestForm.orderOrigin === "DOCTOR_REFERRAL" ? <Card'));
  assert.ok(source.includes('label={<RequiredLabel text="External Doctor Name" required />}'));
  assert.ok(source.includes('externalDoctorName: "", externalDoctorMobile: "", externalClinicName: "", referralSource: ""'));
});

test("lab create errors remain visible in the originating dialogs", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('{!editorOpen && !requestOpen && !quickRegisterOpen && !resultTarget && error ? <Alert severity="error">{error}</Alert> : null}'));
  assert.ok(source.includes('{error ? <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert> : null}'));
  assert.ok(source.includes('{error ? <Alert severity="error">{error}</Alert> : null}'));
});

test("enter results keeps validation and save errors inside the modal", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('const [resultError, setResultError] = React.useState<string | null>(null);'));
  assert.ok(source.includes('setResultError(firstZodError(parsed.error));'));
  assert.ok(source.includes('setResultError(err instanceof Error ? err.message : "Failed to save results");'));
  assert.ok(source.includes('{resultError ? <Alert severity="error">{resultError}</Alert> : null}'));
  assert.ok(source.includes('!quickRegisterOpen && !resultTarget && error ? <Alert severity="error">{error}</Alert> : null}'));
  assert.ok(source.includes('open={Boolean(resultTarget)}'));
});

test("recollection work is based on current ordered-test state, not rejected sample history", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('function hasPendingRecollection(order: LabOrder)'));
  assert.ok(source.includes('orderedTest.state === "RECOLLECTION_REQUIRED"'));
  assert.ok(source.includes('const recollectionRequiredCount = orders.filter(hasPendingRecollection).length;'));
});

test("receipt printing isolates the selected document from the application shell", () => {
  const page = readSource("pages/lab/LabPage.tsx");
  const print = fs.readFileSync(path.join(process.cwd(), "src", "components/finance/PrintableBillingDocuments.tsx"), "utf8");
  assert.ok(page.includes('document.body.classList.toggle("lab-receipt-printing", active)'));
  assert.ok(print.includes('body.lab-receipt-printing #root > *'));
  assert.ok(print.includes('body.lab-receipt-printing .print-document-sheet'));
});

test("lab result entry scopes by specimen and sends the selected accession id", () => {
  const source = readSource("pages/lab/LabPage.tsx");
  assert.ok(source.includes('label={<RequiredLabel text="Result Value" required />}'));
  assert.ok(source.includes('labOrderSampleId: resultScopeSampleId'));
  assert.ok(source.includes('editableResultOrderedTests(row, sample.id).length'));
  assert.ok(source.includes('onClick={() => onEnterResults(row, sample)}'));
  assert.ok(source.includes('row.samples.length <= 1'));
});
