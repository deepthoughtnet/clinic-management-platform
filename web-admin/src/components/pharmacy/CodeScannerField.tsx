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
  InputAdornment,
} from "@mui/material";

type CodeScannerFieldProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  helperText?: string;
  disabled?: boolean;
};

type CameraState = "idle" | "starting" | "scanning" | "error";

type DetectorResult = { rawValue?: string | null };

export default function CodeScannerField({
  label,
  value,
  onChange,
  placeholder,
  helperText,
  disabled,
}: CodeScannerFieldProps) {
  const [open, setOpen] = React.useState(false);
  const [cameraState, setCameraState] = React.useState<CameraState>("idle");
  const [scanError, setScanError] = React.useState<string | null>(null);
  const [manualCode, setManualCode] = React.useState(value);
  const videoRef = React.useRef<HTMLVideoElement | null>(null);
  const streamRef = React.useRef<MediaStream | null>(null);
  const detectorRef = React.useRef<{ detect: (image: HTMLVideoElement) => Promise<DetectorResult[]> } | null>(null);
  const intervalRef = React.useRef<number | null>(null);

  const stopCamera = React.useCallback(() => {
    if (intervalRef.current != null) {
      window.clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    detectorRef.current = null;
    setCameraState("idle");
  }, []);

  const closeDialog = React.useCallback(() => {
    stopCamera();
    setOpen(false);
  }, [stopCamera]);

  const acceptCode = React.useCallback((code: string) => {
    const trimmed = code.trim();
    if (!trimmed) return;
    onChange(trimmed);
    closeDialog();
  }, [closeDialog, onChange]);

  React.useEffect(() => {
    if (!open) {
      return;
    }
    let cancelled = false;

    async function startCamera() {
      setScanError(null);
      setCameraState("starting");
      setManualCode(value);

      if (!navigator.mediaDevices?.getUserMedia) {
        setCameraState("error");
        setScanError("Camera scanning is not available in this browser.");
        return;
      }

      const BarcodeDetectorCtor = (window as Window & {
        BarcodeDetector?: new (options?: { formats?: string[] }) => { detect(image: HTMLVideoElement): Promise<DetectorResult[]> };
      }).BarcodeDetector;

      if (!BarcodeDetectorCtor) {
        setCameraState("error");
        setScanError("Camera scanning is not supported on this device. Use manual entry instead.");
        return;
      }

      try {
        const detector = new BarcodeDetectorCtor({
          formats: ["qr_code", "code_128", "ean_13", "ean_8", "code_39", "upc_a", "upc_e", "itf", "codabar"],
        });
        const stream = await navigator.mediaDevices.getUserMedia({
          video: {
            facingMode: { ideal: "environment" },
          },
          audio: false,
        });
        if (cancelled) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        detectorRef.current = detector;
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          await videoRef.current.play();
        }
        setCameraState("scanning");
        intervalRef.current = window.setInterval(async () => {
          const video = videoRef.current;
          const activeDetector = detectorRef.current;
          if (!video || !activeDetector || video.readyState < 2) return;
          try {
            const result = await activeDetector.detect(video);
            const detected = result.find((item) => item.rawValue && item.rawValue.trim());
            if (detected?.rawValue) {
              acceptCode(detected.rawValue);
            }
          } catch {
            // transient detection failures are expected while the camera stabilizes
          }
        }, 400);
      } catch (err) {
        setCameraState("error");
        const message = err instanceof DOMException && err.name === "NotAllowedError"
          ? "Camera permission was denied."
          : err instanceof Error
            ? err.message
            : "Unable to start the camera.";
        setScanError(message);
      }
    }

    void startCamera();
    return () => {
      cancelled = true;
      stopCamera();
    };
  }, [acceptCode, open, stopCamera, value]);

  return (
    <>
      <TextField
        fullWidth
        label={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        helperText={helperText}
        disabled={disabled}
        InputProps={{
          endAdornment: (
            <InputAdornment position="end">
              <Button size="small" onClick={() => setOpen(true)} disabled={disabled}>
                Scan
              </Button>
            </InputAdornment>
          ),
        }}
      />

      <Dialog open={open} onClose={closeDialog} fullWidth maxWidth="sm">
        <DialogTitle>Scan code</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Point the camera at a barcode or QR code. You can also type the code manually below.
            </Typography>
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
                style={{ width: "100%", height: "100%", objectFit: "cover" }}
              />
              {cameraState !== "scanning" ? (
                <Typography variant="body2" color="common.white" sx={{ position: "absolute" }}>
                  {cameraState === "starting" ? "Starting camera..." : "Manual entry available"}
                </Typography>
              ) : null}
            </Box>
            <TextField
              fullWidth
              label="Scan or enter code"
              value={manualCode}
              onChange={(event) => setManualCode(event.target.value)}
              placeholder="barcode / QR code"
              helperText="USB barcode scanners can type directly into this field."
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog}>Close</Button>
          <Button variant="contained" onClick={() => acceptCode(manualCode)} disabled={!manualCode.trim()}>
            Use code
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
