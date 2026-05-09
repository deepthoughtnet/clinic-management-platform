import * as React from "react";
import { Link as RouterLink, useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import {
  getPatient,
  getPatientVaccinations,
  getPatientNotifications,
  getPatientDocuments,
  getPatientDocumentDownloadUrl,
  getPatientTimeline,
  reviewClinicalDocumentExtraction,
  reprocessClinicalDocumentExtraction,
  uploadPatientDocument,
  searchBills,
  type PatientDetail,
  type Appointment,
  type Consultation,
  type Bill,
  type PatientVaccination,
  type NotificationHistory,
  type ClinicalDocument,
  type ClinicalDocumentType,
  type PatientTimelineItem,
} from "../../api/clinicApi";
import { ClinicalDocumentViewer } from "../../components/clinical/ClinicalDocumentViewer";

function statusColor(status: Appointment["status"]) {
  switch (status) {
    case "COMPLETED":
      return "success";
    case "IN_CONSULTATION":
      return "info";
    case "WAITING":
    case "BOOKED":
      return "warning";
    case "CANCELLED":
    case "NO_SHOW":
      return "default";
  }
}

function consultationStatusColor(status: Consultation["status"]) {
  switch (status) {
    case "COMPLETED":
      return "success";
    case "DRAFT":
      return "warning";
    case "CANCELLED":
      return "default";
  }
}

const DOCUMENT_TYPES: Array<{ value: ClinicalDocumentType; label: string }> = [
  { value: "LAB_REPORT", label: "Lab Report" },
  { value: "PRESCRIPTION", label: "Prescription" },
  { value: "X_RAY", label: "X-Ray" },
  { value: "MRI_CT", label: "MRI/CT" },
  { value: "REFERRAL", label: "Referral" },
  { value: "DISCHARGE_SUMMARY", label: "Discharge Summary" },
  { value: "INSURANCE", label: "Insurance" },
  { value: "VACCINATION", label: "Vaccination" },
  { value: "OTHER", label: "Other" },
];

const ALLOWED_DOCUMENT_MIME_TYPES = new Set(["application/pdf", "image/jpeg", "image/png"]);
const ALLOWED_DOCUMENT_EXTENSIONS = new Set(["pdf", "jpg", "jpeg", "png"]);
const MAX_DOCUMENT_BYTES = 25 * 1024 * 1024;

function documentTypeLabel(value: string | null | undefined): string {
  return DOCUMENT_TYPES.find((type) => type.value === value)?.label || (value || "Document").replaceAll("_", " ");
}

function documentExtension(name: string): string {
  const lower = name.toLowerCase();
  const index = lower.lastIndexOf(".");
  return index >= 0 ? lower.slice(index + 1) : "";
}

export default function PatientDetailPage() {
  const auth = useAuth();
  const params = useParams();
  const navigate = useNavigate();
  const id = params.id || "";
  const [detail, setDetail] = React.useState<PatientDetail | null>(null);
  const [bills, setBills] = React.useState<Bill[]>([]);
  const [vaccinations, setVaccinations] = React.useState<PatientVaccination[]>([]);
  const [notifications, setNotifications] = React.useState<NotificationHistory[]>([]);
  const [documents, setDocuments] = React.useState<ClinicalDocument[]>([]);
  const [timeline, setTimeline] = React.useState<PatientTimelineItem[]>([]);
  const [documentType, setDocumentType] = React.useState<ClinicalDocumentType>("LAB_REPORT");
  const [documentFile, setDocumentFile] = React.useState<File | null>(null);
  const [documentNotes, setDocumentNotes] = React.useState("");
  const [referredDoctor, setReferredDoctor] = React.useState("");
  const [referredHospital, setReferredHospital] = React.useState("");
  const [referralNotes, setReferralNotes] = React.useState("");
  const [uploadingDocument, setUploadingDocument] = React.useState(false);
  const [viewerDocument, setViewerDocument] = React.useState<ClinicalDocument | null>(null);
  const [viewerUrl, setViewerUrl] = React.useState<string | null>(null);
  const [reviewBusy, setReviewBusy] = React.useState(false);
  const [reviewNotes, setReviewNotes] = React.useState("");
  const [reviewOverrideReason, setReviewOverrideReason] = React.useState("");
  const [reviewAcceptedJson, setReviewAcceptedJson] = React.useState("");
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const canUploadClinicalDocument = auth.hasPermission("clinic.document.upload");
  const canReviewAiExtraction = auth.hasPermission("consultation.update") || auth.hasPermission("consultation.complete");

  React.useEffect(() => {
    if (!viewerDocument) {
      setReviewNotes("");
      setReviewOverrideReason("");
      setReviewAcceptedJson("");
      return;
    }
    setReviewNotes(viewerDocument.aiExtractionReviewNotes || "");
    setReviewOverrideReason(viewerDocument.aiExtractionOverrideReason || "");
    setReviewAcceptedJson(viewerDocument.aiExtractionAcceptedJson || viewerDocument.aiExtractionStructuredJson || "");
  }, [viewerDocument]);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!auth.accessToken || !auth.tenantId || !id) {
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const [value, billRows, vaccinationRows, notificationRows, documentRows, timelineRows] = await Promise.all([
          getPatient(auth.accessToken, auth.tenantId, id),
          searchBills(auth.accessToken, auth.tenantId, { patientId: id }),
          getPatientVaccinations(auth.accessToken, auth.tenantId, id),
          getPatientNotifications(auth.accessToken, auth.tenantId, id),
          getPatientDocuments(auth.accessToken, auth.tenantId, id),
          getPatientTimeline(auth.accessToken, auth.tenantId, id),
        ]);
        if (!cancelled) {
          setDetail(value);
          setBills(billRows);
          setVaccinations(vaccinationRows);
          setNotifications(notificationRows);
          setDocuments(documentRows);
          setTimeline(timelineRows);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load patient");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, id]);

  const refreshDocuments = async () => {
    if (!auth.accessToken || !auth.tenantId || !id) return;
    const [documentRows, timelineRows] = await Promise.all([
      getPatientDocuments(auth.accessToken, auth.tenantId, id),
      getPatientTimeline(auth.accessToken, auth.tenantId, id),
    ]);
    setDocuments(documentRows);
    setTimeline(timelineRows);
  };

  const uploadDocument = async () => {
    if (!canUploadClinicalDocument) {
      setError("You do not have permission to upload clinical documents.");
      return;
    }
    if (!auth.accessToken || !auth.tenantId || !id || !documentFile) return;
    const extension = documentExtension(documentFile.name);
    if (documentFile.size > MAX_DOCUMENT_BYTES) {
      setError("Document files must be 25 MB or smaller.");
      return;
    }
    if (!ALLOWED_DOCUMENT_EXTENSIONS.has(extension) || !ALLOWED_DOCUMENT_MIME_TYPES.has(documentFile.type || "")) {
      setError("Only PDF, JPG, JPEG, and PNG documents are allowed.");
      return;
    }
    setUploadingDocument(true);
    setError(null);
    try {
      await uploadPatientDocument(auth.accessToken, auth.tenantId, id, {
        file: documentFile,
        documentType,
        notes: documentNotes,
        referredDoctor: documentType === "REFERRAL" ? referredDoctor : null,
        referredHospital: documentType === "REFERRAL" ? referredHospital : null,
        referralNotes: documentType === "REFERRAL" ? referralNotes : null,
      });
      setDocumentFile(null);
      setDocumentNotes("");
      setReferredDoctor("");
      setReferredHospital("");
      setReferralNotes("");
      await refreshDocuments();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to upload clinical document");
    } finally {
      setUploadingDocument(false);
    }
  };

  const openDocument = async (document: ClinicalDocument) => {
    if (!auth.accessToken || !auth.tenantId) return;
    setViewerDocument(document);
    setViewerUrl(null);
    try {
      const response = await getPatientDocumentDownloadUrl(auth.accessToken, auth.tenantId, document.id);
      setViewerUrl(response.url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to open document preview");
    }
  };

  const reviewDocument = async (approved: boolean) => {
    if (!canReviewAiExtraction) {
      setError("You do not have permission to review AI extraction results.");
      return;
    }
    if (!auth.accessToken || !auth.tenantId || !viewerDocument) return;
    setReviewBusy(true);
    setError(null);
    try {
      const updated = await reviewClinicalDocumentExtraction(auth.accessToken, auth.tenantId, viewerDocument.id, {
        approved,
        saveToPatientHistory: approved,
        reviewNotes: reviewNotes.trim() || null,
        acceptedStructuredJson: approved ? reviewAcceptedJson.trim() || null : null,
        overrideReason: reviewOverrideReason.trim() || null,
        editedSummary: reviewNotes.trim() || null,
      });
      setViewerDocument(updated);
      await refreshDocuments();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save AI review");
    } finally {
      setReviewBusy(false);
    }
  };

  const reprocessDocument = async () => {
    if (!auth.accessToken || !auth.tenantId || !viewerDocument) return;
    setReviewBusy(true);
    setError(null);
    try {
      const updated = await reprocessClinicalDocumentExtraction(auth.accessToken, auth.tenantId, viewerDocument.id);
      setViewerDocument(updated);
      await refreshDocuments();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reprocess AI extraction");
    } finally {
      setReviewBusy(false);
    }
  };

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  if (loading) {
    return (
      <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!detail) {
    return <Alert severity="info">{error || "Patient not found"}</Alert>;
  }

  const patient = detail.patient;

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            {patient.firstName} {patient.lastName}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {patient.patientNumber} • {patient.mobile}
          </Typography>
        </Box>
        <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
          <Button variant="outlined" onClick={() => navigate("/patients")}>Back</Button>
          <Button variant="outlined" onClick={() => navigate(`/patients/${patient.id}/edit`)}>Edit</Button>
          <Button
            variant="contained"
            component={RouterLink}
            to={`/appointments?patientId=${patient.id}`}
            state={{ patient }}
          >
            Create Appointment
          </Button>
        </Box>
      </Box>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Demographics</Typography>
                <Typography variant="body2">Gender: {patient.gender}</Typography>
                <Typography variant="body2">DOB: {patient.dateOfBirth || "Not set"}</Typography>
                <Typography variant="body2">Age: {patient.ageYears ?? "Not set"}</Typography>
                <Typography variant="body2">Mobile: {patient.mobile}</Typography>
                <Typography variant="body2">Email: {patient.email || "Not set"}</Typography>
                <Typography variant="body2">Address: {[patient.addressLine1, patient.addressLine2, patient.city, patient.state, patient.country, patient.postalCode].filter(Boolean).join(", ") || "Not set"}</Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Medical summary</Typography>
                <Typography variant="body2">Emergency contact: {patient.emergencyContactName || "Not set"}</Typography>
                <Typography variant="body2">Emergency mobile: {patient.emergencyContactMobile || "Not set"}</Typography>
                <Typography variant="body2">Blood group: {patient.bloodGroup || "Not set"}</Typography>
                <Typography variant="body2">Allergies: {patient.allergies || "None recorded"}</Typography>
                <Typography variant="body2">Existing conditions: {patient.existingConditions || "None recorded"}</Typography>
                <Typography variant="body2">Long-term medications: {patient.longTermMedications || "None recorded"}</Typography>
                <Typography variant="body2">Surgeries/history: {patient.surgicalHistory || "None recorded"}</Typography>
                <Typography variant="body2">Notes: {patient.notes || "None recorded"}</Typography>
                <Chip size="small" label={patient.active ? "Active" : "Inactive"} color={patient.active ? "success" : "default"} />
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {viewerDocument ? (
        <Card>
          <CardContent>
            <Stack spacing={1.25}>
              <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>AI Extraction Review</Typography>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  <Chip size="small" label={`OCR ${viewerDocument.ocrStatus || "PENDING"}`} />
                  <Chip size="small" color={viewerDocument.aiExtractionStatus === "APPROVED" ? "success" : viewerDocument.aiExtractionStatus === "REJECTED" ? "error" : "warning"} label={`AI ${viewerDocument.aiExtractionStatus || "PENDING"}`} />
                  {viewerDocument.aiExtractionConfidence != null ? <Chip size="small" label={`Confidence ${(viewerDocument.aiExtractionConfidence * 100).toFixed(0)}%`} /> : null}
                </Stack>
              </Box>
              <Typography variant="body2" color="text.secondary">
                {viewerDocument.aiExtractionProvider ? `${viewerDocument.aiExtractionProvider}${viewerDocument.aiExtractionModel ? ` • ${viewerDocument.aiExtractionModel}` : ""}` : "Queued for extraction."}
              </Typography>
              <Alert severity="info">AI suggestions are assistive only and must be clinically reviewed.</Alert>
              <Typography variant="body2">{viewerDocument.aiExtractionSummary || "No AI summary available yet."}</Typography>
              {viewerDocument.aiExtractionStructuredJson ? <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", fontFamily: "monospace" }}>{viewerDocument.aiExtractionStructuredJson}</Typography> : null}
              <Stack spacing={1}>
                <TextField
                  size="small"
                  label="Clinician review notes"
                  value={reviewNotes}
                  onChange={(event) => setReviewNotes(event.target.value)}
                  multiline
                  minRows={2}
                  disabled={!canReviewAiExtraction}
                />
                <TextField
                  size="small"
                  label="Override reason"
                  value={reviewOverrideReason}
                  onChange={(event) => setReviewOverrideReason(event.target.value)}
                  multiline
                  minRows={2}
                  helperText="Use this when you materially change or reject the AI suggestion."
                  disabled={!canReviewAiExtraction}
                />
                <TextField
                  size="small"
                  label="Accepted structured JSON"
                  value={reviewAcceptedJson}
                  onChange={(event) => setReviewAcceptedJson(event.target.value)}
                  multiline
                  minRows={4}
                  helperText="Optional. Edited structured data that should be saved after review."
                  disabled={!canReviewAiExtraction}
                />
              </Stack>
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                {canReviewAiExtraction ? <Button variant="contained" disabled={reviewBusy || viewerDocument.aiExtractionStatus === "APPROVED"} onClick={() => void reviewDocument(true)}>Approve & Save</Button> : null}
                {canReviewAiExtraction ? <Button variant="outlined" color="error" disabled={reviewBusy || viewerDocument.aiExtractionStatus === "REJECTED"} onClick={() => void reviewDocument(false)}>Reject</Button> : null}
                {canReviewAiExtraction ? <Button variant="outlined" disabled={reviewBusy} onClick={() => void reprocessDocument()}>Reprocess</Button> : null}
              </Stack>
              {!canReviewAiExtraction ? <Alert severity="info">You can view the extraction, but approval and reprocessing are restricted by role.</Alert> : null}
              <Typography variant="caption" color="text.secondary">
                Reviewed by {viewerDocument.aiExtractionReviewedByAppUserId || "n/a"}
                {viewerDocument.aiExtractionReviewedAt ? ` • ${new Date(viewerDocument.aiExtractionReviewedAt).toLocaleString()}` : ""}
                {viewerDocument.aiExtractionOverrideReason ? ` • Override: ${viewerDocument.aiExtractionOverrideReason}` : ""}
              </Typography>
            </Stack>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>Clinical Documents & History</Typography>
                <Typography variant="body2" color="text.secondary">Upload reports, referrals, discharge summaries, insurance papers, and imaging for this patient.</Typography>
              </Box>
              <Chip label={`${documents.length} document(s)`} size="small" color="info" />
            </Box>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 4 }}>
                {canUploadClinicalDocument ? (
                  <Stack spacing={1.5}>
                    <FormControl size="small" fullWidth>
                      <InputLabel>Document type</InputLabel>
                      <Select label="Document type" value={documentType} onChange={(event) => setDocumentType(event.target.value as ClinicalDocumentType)}>
                        {DOCUMENT_TYPES.map((type) => <MenuItem key={type.value} value={type.value}>{type.label}</MenuItem>)}
                      </Select>
                    </FormControl>
                    <Button component="label" variant="outlined" disabled={uploadingDocument}>
                      {documentFile ? documentFile.name : "Select PDF/Image"}
                      <input hidden type="file" accept="application/pdf,image/png,image/jpeg,.jpg,.jpeg,.pdf,.png" onChange={(event) => setDocumentFile(event.target.files?.[0] || null)} />
                    </Button>
                    <TextField size="small" label="Notes" value={documentNotes} onChange={(event) => setDocumentNotes(event.target.value)} multiline minRows={2} />
                    {documentType === "REFERRAL" ? (
                      <>
                        <TextField size="small" label="Referred doctor" value={referredDoctor} onChange={(event) => setReferredDoctor(event.target.value)} />
                        <TextField size="small" label="Referred hospital" value={referredHospital} onChange={(event) => setReferredHospital(event.target.value)} />
                        <TextField size="small" label="Referral notes" value={referralNotes} onChange={(event) => setReferralNotes(event.target.value)} multiline minRows={2} />
                      </>
                    ) : null}
                    <Button variant="contained" disabled={!documentFile || uploadingDocument} onClick={uploadDocument}>
                      {uploadingDocument ? "Uploading..." : "Upload document"}
                    </Button>
                    <Typography variant="caption" color="text.secondary">Supported: PDF, JPG, JPEG, PNG. Future AI/OCR status is tracked with each document.</Typography>
                  </Stack>
                ) : (
                  <Alert severity="info">You have read-only access to clinical documents.</Alert>
                )}
              </Grid>
              <Grid size={{ xs: 12, md: 8 }}>
                <Stack spacing={1.5}>
                  {documents.length === 0 ? (
                    <Alert severity="info">No clinical documents have been uploaded for this patient.</Alert>
                  ) : documents.slice(0, 8).map((document) => (
                    <Box key={document.id} sx={{ p: 1.25, border: "1px solid", borderColor: "divider", borderRadius: 2, display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
                      <Box>
                        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                          <Chip size="small" label={documentTypeLabel(document.documentType)} color={document.documentType === "REFERRAL" ? "secondary" : "default"} />
                          <Typography variant="caption" color="text.secondary">{new Date(document.createdAt).toLocaleString()}</Typography>
                        </Stack>
                        <Typography variant="body2" sx={{ fontWeight: 800 }}>{document.originalFilename}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {document.notes || document.referralNotes || "No notes"} · OCR {document.ocrStatus || "PENDING"} · AI {document.aiExtractionStatus || "PENDING"}{document.aiExtractionConfidence != null ? ` · ${(document.aiExtractionConfidence * 100).toFixed(0)}%` : ""}
                        </Typography>
                        {document.documentType === "REFERRAL" ? <Typography variant="caption" display="block" color="text.secondary">Referral: {[document.referredDoctor, document.referredHospital].filter(Boolean).join(" · ") || "Not specified"}</Typography> : null}
                      </Box>
                      <Button size="small" onClick={() => void openDocument(document)}>Preview</Button>
                    </Box>
                  ))}
                </Stack>
              </Grid>
            </Grid>
            <Divider />
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 900, mb: 1 }}>Patient Timeline</Typography>
              {timeline.length === 0 ? (
                <Alert severity="info">No clinical timeline events are available yet.</Alert>
              ) : (
                <Stack spacing={1}>
                  {timeline.slice(0, 12).map((item) => (
                    <Box key={`${item.itemType}-${item.id}`} sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "150px 1fr" }, gap: 1, p: 1, borderLeft: "3px solid", borderColor: item.itemType === "DOCUMENT" ? "info.main" : item.itemType === "PRESCRIPTION" ? "success.main" : "warning.main", bgcolor: "background.default", borderRadius: 1 }}>
                      <Typography variant="caption" color="text.secondary">{new Date(item.occurredAt).toLocaleString()}</Typography>
                      <Box>
                        <Typography variant="body2" sx={{ fontWeight: 800 }}>{item.title}</Typography>
                        <Typography variant="caption" color="text.secondary">{item.itemType} {item.subtitle ? `· ${item.subtitle}` : ""}</Typography>
                      </Box>
                    </Box>
                  ))}
                </Stack>
              )}
            </Box>
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Upcoming appointments</Typography>
            {detail.upcomingAppointments.length === 0 ? (
              <Alert severity="info">No upcoming appointments were found for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Doctor</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Reason</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {detail.upcomingAppointments.map((appointment) => (
                    <TableRow key={appointment.id}>
                      <TableCell>{appointment.appointmentDate}{appointment.appointmentTime ? ` ${appointment.appointmentTime}` : ""}</TableCell>
                      <TableCell>{appointment.doctorName || appointment.doctorUserId}</TableCell>
                      <TableCell>{appointment.type}</TableCell>
                      <TableCell><Chip size="small" label={appointment.status} color={statusColor(appointment.status)} /></TableCell>
                      <TableCell>{appointment.reason || "-"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Bills</Typography>
            {bills.length === 0 ? (
              <Alert severity="info">No bills were found for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Bill</TableCell>
                    <TableCell>Date</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Total</TableCell>
                    <TableCell align="right">Paid</TableCell>
                    <TableCell align="right">Due</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {bills.map((bill) => (
                    <TableRow key={bill.id}>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{bill.billNumber}</Typography>
                          <Typography variant="caption" color="text.secondary">{bill.notes || "No notes"}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>{bill.billDate}</TableCell>
                      <TableCell><Chip size="small" label={bill.status} color={bill.status === "PAID" ? "success" : bill.status === "PARTIALLY_PAID" ? "warning" : bill.status === "CANCELLED" ? "default" : "info"} /></TableCell>
                      <TableCell align="right">{bill.totalAmount.toFixed(2)}</TableCell>
                      <TableCell align="right">{bill.paidAmount.toFixed(2)}</TableCell>
                      <TableCell align="right">{bill.dueAmount.toFixed(2)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Vaccinations</Typography>
            {vaccinations.length === 0 ? (
              <Alert severity="info">No vaccinations were recorded for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Vaccine</TableCell>
                    <TableCell>Given</TableCell>
                    <TableCell>Next due</TableCell>
                    <TableCell>Dose</TableCell>
                    <TableCell>Batch</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {vaccinations.map((vaccination) => (
                    <TableRow key={vaccination.id}>
                      <TableCell>{vaccination.vaccineName}</TableCell>
                      <TableCell>{vaccination.givenDate}</TableCell>
                      <TableCell>{vaccination.nextDueDate || "-"}</TableCell>
                      <TableCell>{vaccination.doseNumber ?? "-"}</TableCell>
                      <TableCell>{vaccination.batchNumber || "-"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Recent appointments</Typography>
            {detail.recentAppointments.length === 0 ? (
              <Alert severity="info">No recent appointments were found for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Doctor</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Reason</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {detail.recentAppointments.map((appointment) => (
                    <TableRow key={appointment.id}>
                      <TableCell>{appointment.appointmentDate}{appointment.appointmentTime ? ` ${appointment.appointmentTime}` : ""}</TableCell>
                      <TableCell>{appointment.doctorName || appointment.doctorUserId}</TableCell>
                      <TableCell>{appointment.type}</TableCell>
                      <TableCell><Chip size="small" label={appointment.status} color={statusColor(appointment.status)} /></TableCell>
                      <TableCell>{appointment.reason || "-"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Previous consultations</Typography>
            {detail.previousConsultations.length === 0 ? (
              <Alert severity="info">No previous consultations were found for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Doctor</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Diagnosis</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {detail.previousConsultations.map((consultation) => (
                    <TableRow key={consultation.id}>
                      <TableCell>{new Date(consultation.createdAt).toLocaleString()}</TableCell>
                      <TableCell>{consultation.doctorName || consultation.doctorUserId}</TableCell>
                      <TableCell><Chip size="small" label={consultation.status} color={consultationStatusColor(consultation.status)} /></TableCell>
                      <TableCell>{consultation.diagnosis || "-"}</TableCell>
                      <TableCell align="right">
                        <Button size="small" onClick={() => navigate(`/consultations/${consultation.id}`)}>Open</Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Notifications</Typography>
            {notifications.length === 0 ? (
              <Alert severity="info">No notification history was found for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Event</TableCell>
                    <TableCell>Channel</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Recipient</TableCell>
                    <TableCell>Created</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {notifications.map((notification) => (
                    <TableRow key={notification.id}>
                      <TableCell>{notification.eventType}</TableCell>
                      <TableCell>{notification.channel}</TableCell>
                      <TableCell><Chip size="small" label={notification.status} color={notification.status === "SENT" ? "success" : notification.status === "FAILED" ? "error" : notification.status === "PENDING" ? "info" : "default"} /></TableCell>
                      <TableCell>{notification.recipient}</TableCell>
                      <TableCell>{new Date(notification.createdAt).toLocaleString()}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>
      <ClinicalDocumentViewer open={!!viewerDocument} document={viewerDocument} url={viewerUrl} onClose={() => { setViewerDocument(null); setViewerUrl(null); }} />
    </Stack>
  );
}
