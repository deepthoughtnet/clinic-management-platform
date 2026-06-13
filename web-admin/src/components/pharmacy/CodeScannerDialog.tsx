import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { BarcodeFormat, DecodeHintType } from "@zxing/library";
import { BrowserMultiFormatReader, type IScannerControls } from "@zxing/browser";
import type { Result } from "@zxing/library";

const SCAN_FORMATS = [
  BarcodeFormat.QR_CODE,
  BarcodeFormat.EAN_13,
  BarcodeFormat.EAN_8,
  BarcodeFormat.UPC_A,
  BarcodeFormat.CODE_128,
  BarcodeFormat.CODE_39,
];

type CodeScannerDialogProps = {
  open: boolean;
  title: string;
  description?: string;
  value?: string;
  onClose: () => void;
  onDetected: (code: string) => void | Promise<void>;
  manualLabel?: string;
  manualPlaceholder?: string;
};

function cameraUnavailableMessage(name?: string) {
  if (name === "NotAllowedError" || name === "SecurityError") {
    return "Camera access is required for scanning. You can enter the code manually.";
  }
  if (name === "NotFoundError" || name === "OverconstrainedError" || name === "DevicesNotFoundError") {
    return "No camera found. Please enter the code manually.";
  }
  return "Unable to start the camera. You can enter the code manually.";
}

export default function CodeScannerDialog({
  open,
  title,
  description,
  value,
  onClose,
  onDetected,
  manualLabel = "Manual entry",
  manualPlaceholder = "barcode / QR code",
}: CodeScannerDialogProps) {
  const [cameraState, setCameraState] = React.useState<"idle" | "starting" | "scanning" | "error">("idle");
  const [scanError, setScanError] = React.useState<string | null>(null);
  const [manualCode, setManualCode] = React.useState(value || "");
  const [lastScannedCode, setLastScannedCode] = React.useState<string | null>(null);
  const [scanStatus, setScanStatus] = React.useState("Align the code inside the frame.");
  const videoRef = React.useRef<HTMLVideoElement | null>(null);
  const controlsRef = React.useRef<IScannerControls | null>(null);
  const handledRef = React.useRef(false);

  const stopScanner = React.useCallback(() => {
    handledRef.current = false;
    controlsRef.current?.stop();
    controlsRef.current = null;
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    setCameraState("idle");
  }, []);

  const closeDialog = React.useCallback(() => {
    stopScanner();
    setScanError(null);
    setScanStatus("Align the code inside the frame.");
    onClose();
  }, [onClose, stopScanner]);

  const acceptCode = React.useCallback((code: string) => {
    const trimmed = code.trim();
    if (!trimmed) return;
    setLastScannedCode(trimmed);
    setManualCode(trimmed);
    void Promise.resolve(onDetected(trimmed));
    closeDialog();
  }, [closeDialog, onDetected]);

  React.useEffect(() => {
    if (!open) {
      stopScanner();
      return;
    }

    async function startScanner() {
      setManualCode(value || "");
      setLastScannedCode(null);
      setScanError(null);
      setScanStatus("Starting camera...");
      setCameraState("starting");

      if (!navigator.mediaDevices?.getUserMedia) {
        setCameraState("error");
        setScanError("No camera found. Please enter the code manually.");
        return;
      }

      try {
        const hints = new Map<DecodeHintType, unknown>([
          [DecodeHintType.POSSIBLE_FORMATS, SCAN_FORMATS],
          [DecodeHintType.TRY_HARDER, true],
          [DecodeHintType.ASSUME_GS1, true],
          [DecodeHintType.ENABLE_CODE_39_EXTENDED_MODE, true],
        ]);
        const reader = new BrowserMultiFormatReader(hints, {
          delayBetweenScanAttempts: 250,
          delayBetweenScanSuccess: 250,
          tryPlayVideoTimeout: 5_000,
        });
        reader.possibleFormats = SCAN_FORMATS;
        if (!videoRef.current) {
          setCameraState("error");
          setScanError("Unable to start the camera. You can enter the code manually.");
          return;
        }
        setCameraState("scanning");
        setScanStatus("Point the camera at a barcode or QR code.");
        const controls = await reader.decodeFromVideoDevice(undefined, videoRef.current, (result: Result | undefined, error: Error | undefined, activeControls) => {
          if (handledRef.current) {
            return;
          }
          if (result) {
            const code = result.getText().trim();
            if (!code) {
              return;
            }
            handledRef.current = true;
            setLastScannedCode(code);
            setManualCode(code);
            setScanStatus(`Detected code: ${code}`);
            activeControls.stop();
            controlsRef.current = activeControls;
            void Promise.resolve(onDetected(code));
            closeDialog();
            return;
          }
          if (!error) {
            return;
          }
          const errorName = error instanceof Error ? error.name : "";
          if (errorName === "NotFoundException" || errorName === "ChecksumException" || errorName === "FormatException") {
            return;
          }
          setCameraState("error");
          setScanError(cameraUnavailableMessage(errorName));
        });
        controlsRef.current = controls;
      } catch (err) {
        setCameraState("error");
        const errorName = err instanceof DOMException ? err.name : err instanceof Error ? err.name : undefined;
        setScanError(cameraUnavailableMessage(errorName));
      }
    }

    void startScanner();
    return () => {
      stopScanner();
    };
  }, [closeDialog, open, stopScanner]);

  return (
    <Dialog open={open} onClose={closeDialog} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {description ? <Typography variant="body2" color="text.secondary">{description}</Typography> : null}
          {scanError ? <Alert severity="error">{scanError}</Alert> : null}
          <Box
            sx={{
              position: "relative",
              aspectRatio: "4 / 3",
              borderRadius: 2,
              overflow: "hidden",
              bgcolor: "grey.900",
              display: "grid",
              placeItems: "center",
            }}
          >
            <video
              ref={videoRef}
              muted
              playsInline
              autoPlay
              style={{ width: "100%", height: "100%", objectFit: "cover" }}
            />
            {cameraState !== "scanning" ? (
              <Typography variant="body2" color="common.white" sx={{ position: "absolute", px: 2, textAlign: "center" }}>
                {cameraState === "starting" ? "Starting camera..." : "Manual entry available"}
              </Typography>
            ) : null}
          </Box>
          <Stack spacing={0.5}>
            <Typography variant="caption" color="text.secondary">
              Last scanned code
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>
              {lastScannedCode || "-"}
            </Typography>
          </Stack>
          <TextField
            fullWidth
            label={manualLabel}
            value={manualCode}
            onChange={(event) => setManualCode(event.target.value)}
            placeholder={manualPlaceholder}
            helperText="Manual entry stays available if camera access is denied or unavailable."
            autoComplete="off"
            inputProps={{ inputMode: "text" }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={closeDialog}>Cancel</Button>
        <Button variant="contained" onClick={() => acceptCode(manualCode)} disabled={!manualCode.trim()}>
          Use code
        </Button>
      </DialogActions>
    </Dialog>
  );
}
