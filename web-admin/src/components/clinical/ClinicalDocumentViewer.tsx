import * as React from "react";
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Slider, Stack, Typography } from "@mui/material";
import ZoomInRoundedIcon from "@mui/icons-material/ZoomInRounded";
import ZoomOutRoundedIcon from "@mui/icons-material/ZoomOutRounded";
import RestartAltRoundedIcon from "@mui/icons-material/RestartAltRounded";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";

import type { ClinicalDocument } from "../../api/clinicApi";

type Props = {
  open: boolean;
  document: ClinicalDocument | null;
  url: string | null;
  onClose: () => void;
};

export function ClinicalDocumentViewer({ open, document, url, onClose }: Props) {
  const [zoom, setZoom] = React.useState(1);
  const [pan, setPan] = React.useState({ x: 0, y: 0 });
  const dragRef = React.useRef<{ x: number; y: number; panX: number; panY: number } | null>(null);

  React.useEffect(() => {
    if (open) {
      setZoom(1);
      setPan({ x: 0, y: 0 });
    }
  }, [open, document?.id]);

  const isPdf = document?.mediaType === "application/pdf";
  const isImage = document?.mediaType?.startsWith("image/");

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
      <DialogTitle sx={{ display: "flex", justifyContent: "space-between", gap: 2, alignItems: "center" }}>
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 900 }}>{document?.originalFilename || "Clinical document"}</Typography>
          <Typography variant="caption" color="text.secondary">{document?.documentType?.replaceAll("_", " ") || "Document"}</Typography>
        </Box>
        {isImage ? (
          <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 260 }}>
            <IconButton onClick={() => setZoom((value) => Math.max(0.5, value - 0.25))}><ZoomOutRoundedIcon /></IconButton>
            <Slider size="small" min={0.5} max={4} step={0.25} value={zoom} onChange={(_, value) => setZoom(value as number)} />
            <IconButton onClick={() => setZoom((value) => Math.min(4, value + 0.25))}><ZoomInRoundedIcon /></IconButton>
            <IconButton onClick={() => { setZoom(1); setPan({ x: 0, y: 0 }); }}><RestartAltRoundedIcon /></IconButton>
          </Stack>
        ) : null}
      </DialogTitle>
      <DialogContent dividers sx={{ height: "75vh", bgcolor: "grey.100", p: 0, overflow: "hidden" }}>
        {!url ? (
          <Box sx={{ display: "grid", placeItems: "center", height: "100%" }}><Typography color="text.secondary">Preview URL unavailable.</Typography></Box>
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
          <Box sx={{ display: "grid", placeItems: "center", height: "100%" }}><Typography color="text.secondary">This file type cannot be embedded. Open it in a new tab.</Typography></Box>
        )}
      </DialogContent>
      <DialogActions>
        {url ? <Button startIcon={<OpenInNewRoundedIcon />} onClick={() => window.open(url, "_blank", "noopener,noreferrer")}>Open</Button> : null}
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
