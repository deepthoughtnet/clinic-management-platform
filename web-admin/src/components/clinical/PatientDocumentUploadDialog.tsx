import * as React from "react";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from "@mui/material";
import { documentUploadSchema, firstZodError } from "@deepthoughtnet/form-validation-kit";
import type { ClinicalDocumentType, ClinicalDocumentUploadSource, ClinicalDocumentVisibility } from "../../api/clinicApi";

const DOCUMENT_TYPES: Array<{ value: ClinicalDocumentType; label: string }> = [
  { value: "EXTERNAL_LAB_REPORT", label: "External Lab Report" },
  { value: "RADIOLOGY_REPORT", label: "Radiology Report" },
  { value: "REFERRAL_LETTER", label: "Referral Letter" },
  { value: "DISCHARGE_SUMMARY", label: "Discharge Summary" },
  { value: "OLD_PRESCRIPTION", label: "Old Prescription" },
  { value: "INTERNAL_LAB_REPORT", label: "Internal Lab Report" },
  { value: "INSURANCE_DOCUMENT", label: "Insurance Document" },
  { value: "IDENTITY_DOCUMENT", label: "Identity Document" },
  { value: "OTHER", label: "Other" },
  { value: "LAB_REPORT", label: "External Lab Report" },
  { value: "PRESCRIPTION", label: "Old Prescription" },
  { value: "X_RAY", label: "Radiology Report" },
  { value: "MRI_CT", label: "Radiology Report" },
  { value: "REFERRAL", label: "Referral Letter" },
  { value: "INSURANCE", label: "Insurance Document" },
  { value: "VACCINATION", label: "Other" },
  { value: "ATTACHMENT", label: "Other" },
];

type Props = {
  open: boolean;
  onClose: () => void;
  onSubmit: (body: {
    file: File;
    documentType: ClinicalDocumentType;
    title: string;
    reportDate: string | null;
    notes: string | null;
    consultationId: string | null;
    uploadSource: ClinicalDocumentUploadSource;
    visibility: ClinicalDocumentVisibility;
  }) => Promise<void>;
  defaultUploadSource: ClinicalDocumentUploadSource;
  consultationId?: string | null;
  submitLabel?: string;
  title?: string;
};

const MAX_DOCUMENT_BYTES = 25 * 1024 * 1024;

export function PatientDocumentUploadDialog({ open, onClose, onSubmit, defaultUploadSource, consultationId = null, submitLabel = "Upload", title = "Upload document" }: Props) {
  const [documentType, setDocumentType] = React.useState<ClinicalDocumentType>("EXTERNAL_LAB_REPORT");
  const [file, setFile] = React.useState<File | null>(null);
  const [documentTitle, setDocumentTitle] = React.useState("");
  const [reportDate, setReportDate] = React.useState("");
  const [notes, setNotes] = React.useState("");
  const [uploadSource, setUploadSource] = React.useState<ClinicalDocumentUploadSource>(defaultUploadSource);
  const [visibility, setVisibility] = React.useState<ClinicalDocumentVisibility>("INTERNAL_ONLY");
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!open) {
      setDocumentType("EXTERNAL_LAB_REPORT");
      setFile(null);
      setDocumentTitle("");
      setReportDate("");
      setNotes("");
      setUploadSource(defaultUploadSource);
      setVisibility("INTERNAL_ONLY");
      setBusy(false);
      setError(null);
    }
  }, [defaultUploadSource, open]);

  const submit = async () => {
    if (!file) {
      setError("File is required");
      return;
    }
    if (!documentTitle.trim()) {
      setError("Title is required");
      return;
    }

    const parsed = documentUploadSchema({
      required: true,
      allowedMimeTypes: ["application/pdf", "image/jpeg", "image/png"],
      allowedExtensions: ["pdf", "jpg", "jpeg", "png"],
      maxBytes: MAX_DOCUMENT_BYTES,
    }).safeParse(file);
    if (!parsed.success) {
      setError(firstZodError(parsed.error));
      return;
    }

    setBusy(true);
    setError(null);
    try {
      await onSubmit({
        file,
        documentType,
        title: documentTitle.trim(),
        reportDate: reportDate || null,
        notes: notes.trim() || null,
        consultationId,
        uploadSource,
        visibility,
      });
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to upload document");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={1.5} sx={{ mt: 0.5 }}>
          <FormControl size="small" fullWidth>
            <InputLabel>Document type</InputLabel>
            <Select label="Document type" value={documentType} onChange={(event) => setDocumentType(event.target.value as ClinicalDocumentType)}>
              {DOCUMENT_TYPES.map((item) => <MenuItem key={item.value} value={item.value}>{item.label}</MenuItem>)}
            </Select>
          </FormControl>
          <TextField size="small" fullWidth label="Title" value={documentTitle} onChange={(event) => setDocumentTitle(event.target.value)} />
          <TextField size="small" fullWidth label="Report date" type="date" value={reportDate} onChange={(event) => setReportDate(event.target.value)} InputLabelProps={{ shrink: true }} />
          <FormControl size="small" fullWidth>
            <InputLabel>Upload source</InputLabel>
            <Select label="Upload source" value={uploadSource} onChange={(event) => setUploadSource(event.target.value as ClinicalDocumentUploadSource)}>
              <MenuItem value="RECEPTION">Reception</MenuItem>
              <MenuItem value="DOCTOR">Doctor</MenuItem>
              <MenuItem value="LABORATORY">Laboratory</MenuItem>
              <MenuItem value="PATIENT_PORTAL">Patient Portal</MenuItem>
              <MenuItem value="IMPORT">Import</MenuItem>
              <MenuItem value="OTHER">Other</MenuItem>
            </Select>
          </FormControl>
          <FormControl size="small" fullWidth>
            <InputLabel>Visibility</InputLabel>
            <Select label="Visibility" value={visibility} onChange={(event) => setVisibility(event.target.value as ClinicalDocumentVisibility)}>
              <MenuItem value="INTERNAL_ONLY">Internal only</MenuItem>
              <MenuItem value="PATIENT_VISIBLE">Patient visible</MenuItem>
            </Select>
          </FormControl>
          <TextField size="small" fullWidth multiline minRows={3} label="Notes" value={notes} onChange={(event) => setNotes(event.target.value)} />
          <Button component="label" variant="outlined" disabled={busy}>
            {file ? file.name : "Select file"}
            <input hidden type="file" accept="application/pdf,image/png,image/jpeg,.pdf,.png,.jpg,.jpeg" onChange={(event) => setFile(event.target.files?.[0] || null)} />
          </Button>
          {error ? <div style={{ color: "#b00020" }}>{error}</div> : null}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>Cancel</Button>
        <Button variant="contained" onClick={() => void submit()} disabled={busy}> {busy ? "Uploading..." : submitLabel} </Button>
      </DialogActions>
    </Dialog>
  );
}
