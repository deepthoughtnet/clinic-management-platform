import * as React from "react";
import { Box, Button, Chip, Divider, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Slider, Stack, Typography } from "@mui/material";
import ZoomInRoundedIcon from "@mui/icons-material/ZoomInRounded";
import ZoomOutRoundedIcon from "@mui/icons-material/ZoomOutRounded";
import RestartAltRoundedIcon from "@mui/icons-material/RestartAltRounded";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";
import FullscreenRoundedIcon from "@mui/icons-material/FullscreenRounded";
import FullscreenExitRoundedIcon from "@mui/icons-material/FullscreenExitRounded";
import DownloadRoundedIcon from "@mui/icons-material/DownloadRounded";

import type { ClinicalDocument } from "../../api/clinicApi";
import { documentBusinessStatusLabel, isPublishedLabDocument } from "./documentTypeOptions";

type Props = {
  open: boolean;
  document: ClinicalDocument | null;
  url: string | null;
  onClose: () => void;
  onReprocess?: () => void;
  reprocessBusy?: boolean;
};

export function ClinicalDocumentViewer({ open, document, url, onClose, onReprocess, reprocessBusy }: Props) {
  const [zoom, setZoom] = React.useState(1);
  const [pan, setPan] = React.useState({ x: 0, y: 0 });
  const [fullScreen, setFullScreen] = React.useState(false);
  const dragRef = React.useRef<{ x: number; y: number; panX: number; panY: number } | null>(null);

  React.useEffect(() => {
    if (open) {
      setZoom(1);
      setPan({ x: 0, y: 0 });
      setFullScreen(false);
    }
  }, [open, document?.id]);

  const isPdf = document?.mediaType === "application/pdf";
  const isImage = document?.mediaType?.startsWith("image/");
  const publishedLabDocument = isPublishedLabDocument(document);
  const businessStatus = documentBusinessStatusLabel(document);
  const aiStatus = String(document?.aiExtractionStatus || "").trim().toUpperCase();
  const ocrStatus = String(document?.ocrStatus || "").trim().toUpperCase();
  const canReprocess = Boolean(onReprocess) && (aiStatus === "FAILED" || aiStatus === "REVIEW_REQUIRED" || aiStatus === "PROCESSING" || aiStatus === "QUEUED" || ocrStatus === "FAILED");
  const sizeLabel = formatSize(document?.sizeBytes);
  const metaChips = publishedLabDocument
    ? ["Lab Report", "Published"]
    : [
        document?.documentType ? document.documentType.replaceAll("_", " ") : null,
        document?.mediaType || null,
        document?.ocrStatus ? `OCR ${document.ocrStatus}` : null,
        document?.aiExtractionStatus ? `AI ${document.aiExtractionStatus}` : null,
      ].filter(Boolean) as string[];

  return (
    <Dialog open={open} onClose={onClose} fullScreen={fullScreen} fullWidth maxWidth={fullScreen ? false : "xl"}>
      <DialogTitle sx={{ display: "flex", justifyContent: "space-between", gap: 2, alignItems: "flex-start" }}>
        <Stack spacing={0.75} sx={{ minWidth: 0, flex: 1 }}>
          <Typography variant="h6" sx={{ fontWeight: 900, lineHeight: 1.15 }}>{document?.originalFilename || "Clinical document"}</Typography>
          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
            {metaChips.map((chip) => <Chip key={chip} size="small" variant="outlined" label={chip} />)}
          </Stack>
        </Stack>
        <Stack direction="row" spacing={0.5} alignItems="center" sx={{ flexShrink: 0 }}>
          {isImage ? (
            <>
              <IconButton size="small" onClick={() => setZoom((value) => Math.max(0.5, value - 0.25))}><ZoomOutRoundedIcon fontSize="small" /></IconButton>
              <Slider size="small" sx={{ width: 140 }} min={0.5} max={4} step={0.25} value={zoom} onChange={(_, value) => setZoom(value as number)} />
              <IconButton size="small" onClick={() => setZoom((value) => Math.min(4, value + 0.25))}><ZoomInRoundedIcon fontSize="small" /></IconButton>
              <IconButton size="small" onClick={() => { setZoom(1); setPan({ x: 0, y: 0 }); }}><RestartAltRoundedIcon fontSize="small" /></IconButton>
            </>
          ) : null}
          <IconButton size="small" onClick={() => setFullScreen((value) => !value)}>{fullScreen ? <FullscreenExitRoundedIcon fontSize="small" /> : <FullscreenRoundedIcon fontSize="small" />}</IconButton>
        </Stack>
      </DialogTitle>
      <DialogContent dividers sx={{ bgcolor: "grey.100", p: 0, overflow: "hidden", height: fullScreen ? "calc(100vh - 128px)" : "75vh" }}>
        <Stack direction={{ xs: "column", lg: "row" }} sx={{ height: "100%" }}>
          <Box sx={{ flex: 1, minWidth: 0, bgcolor: isPdf ? "white" : "grey.100" }}>
            {!url ? (
              <Box sx={{ display: "grid", placeItems: "center", height: "100%" }}>
                <Typography color="text.secondary">Preview URL unavailable.</Typography>
              </Box>
            ) : isPdf ? (
              <Box component="iframe" title={document?.originalFilename || "PDF preview"} src={url} sx={{ width: "100%", height: "100%", border: 0, bgcolor: "white" }} />
            ) : isImage ? (
              <Box
                sx={{ width: "100%", height: "100%", overflow: "hidden", cursor: zoom > 1 ? "grab" : "default", display: "grid", placeItems: "center" }}
                onMouseDown={(event) => { dragRef.current = { x: event.clientX, y: event.clientY, panX: pan.x, panY: pan.y }; }}
                onMouseMove={(event) => {
                  if (!dragRef.current) return;
                  setPan({ x: dragRef.current.panX + event.clientX - dragRef.current.x, y: dragRef.current.panY + event.clientY - dragRef.current.y });
                }}
                onMouseUp={() => { dragRef.current = null; }}
                onMouseLeave={() => { dragRef.current = null; }}
              >
                <Box component="img" src={url} alt={document?.originalFilename || "Clinical document"} sx={{ maxWidth: "100%", maxHeight: "100%", transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`, transformOrigin: "center", userSelect: "none", pointerEvents: "none" }} />
              </Box>
            ) : (
              <Box sx={{ display: "grid", placeItems: "center", height: "100%", p: 3, textAlign: "center" }}>
                <Stack spacing={1}>
                  <Typography color="text.secondary">This file type cannot be embedded.</Typography>
                  <Typography variant="caption" color="text.secondary">Use the download button or open it in a new tab.</Typography>
                </Stack>
              </Box>
            )}
          </Box>
          <Divider flexItem orientation="vertical" sx={{ display: { xs: "none", lg: "block" } }} />
          <Box sx={{ width: { xs: "100%", lg: 340 }, borderLeft: { xs: 0, lg: 1 }, borderColor: "divider", bgcolor: "background.paper", p: 2, overflow: "auto" }}>
            <Stack spacing={1.25}>
              <Box>
                <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>Document details</Typography>
                <Typography variant="caption" color="text.secondary">Metadata and clinician notes</Typography>
              </Box>
              <Stack spacing={0.75}>
                <Typography variant="body2"><b>Type:</b> {publishedLabDocument ? "Lab Report" : document?.documentType?.replaceAll("_", " ") || "-"}</Typography>
                <Typography variant="body2"><b>Media:</b> {document?.mediaType || "-"}</Typography>
                <Typography variant="body2"><b>Size:</b> {sizeLabel}</Typography>
                <Typography variant="body2"><b>Uploaded:</b> {document?.createdAt ? new Date(document.createdAt).toLocaleString() : "-"}</Typography>
                <Typography variant="body2"><b>Uploaded by:</b> {document?.uploadedByName || document?.uploadedByUserId || "-"}</Typography>
                {publishedLabDocument ? null : <Typography variant="body2"><b>Source module:</b> {document?.sourceModule || "-"}</Typography>}
                {publishedLabDocument ? null : <Typography variant="body2"><b>Source entity:</b> {document?.sourceEntityId || "-"}</Typography>}
                <Typography variant="body2"><b>Upload source:</b> {publishedLabDocument ? "Published" : document?.uploadSource || "-"}</Typography>
                <Typography variant="body2"><b>Title:</b> {document?.title || "-"}</Typography>
                <Typography variant="body2"><b>Description:</b> {document?.description || "-"}</Typography>
                {publishedLabDocument ? null : <Typography variant="body2"><b>AI summary:</b> {document?.aiExtractionSummary || "-"}</Typography>}
                {publishedLabDocument ? null : <Typography variant="body2"><b>AI review:</b> {document?.aiExtractionReviewNotes || "-"}</Typography>}
                {publishedLabDocument ? null : <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", fontFamily: "monospace" }}><b>AI structured data:</b> {document?.aiExtractionStructuredJson || "-"}</Typography>}
                {publishedLabDocument ? null : <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", fontFamily: "monospace" }}><b>AI accepted JSON:</b> {document?.aiExtractionAcceptedJson || "-"}</Typography>}
                {publishedLabDocument ? null : <Typography variant="body2"><b>AI override reason:</b> {document?.aiExtractionOverrideReason || "-"}</Typography>}
                {publishedLabDocument ? null : <Typography variant="body2"><b>Reviewed by:</b> {document?.aiExtractionReviewedByAppUserId || "-"}</Typography>}
                {publishedLabDocument ? null : <Typography variant="body2"><b>Reviewed at:</b> {document?.aiExtractionReviewedAt || "-"}</Typography>}
                {businessStatus ? <Typography variant="body2"><b>Status:</b> {businessStatus}</Typography> : null}
                <Typography variant="body2"><b>Checksum:</b> {document?.checksumSha256 || "-"}</Typography>
                <Typography variant="body2"><b>Storage:</b> {document?.storageBucket ? `${document.storageBucket}/${document.storageKey}` : document?.storageKey || "-"}</Typography>
              </Stack>
            </Stack>
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ justifyContent: "space-between" }}>
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
          {url ? <Button startIcon={<DownloadRoundedIcon />} component="a" href={url} download target="_blank" rel="noreferrer">Download</Button> : null}
          {url ? <Button startIcon={<OpenInNewRoundedIcon />} onClick={() => window.open(url, "_blank", "noopener,noreferrer")}>Open</Button> : null}
          {canReprocess ? <Button variant="outlined" onClick={onReprocess} disabled={reprocessBusy}>Retry AI Processing</Button> : null}
        </Stack>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}

function formatSize(sizeBytes: number | null | undefined) {
  if (typeof sizeBytes !== "number" || Number.isNaN(sizeBytes)) return "-";
  if (sizeBytes < 1024) return `${sizeBytes} B`;
  if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(1)} KB`;
  return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
}
