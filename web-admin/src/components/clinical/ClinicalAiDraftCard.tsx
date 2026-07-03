import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import AutoAwesomeRoundedIcon from "@mui/icons-material/AutoAwesomeRounded";

export type ClinicalAiDraftStatus = "DRAFTED" | "ACCEPTED" | "EDITED" | "REJECTED";

export type ClinicalAiDraftCardProps = {
  title: string;
  status: ClinicalAiDraftStatus;
  generatedAt?: string | null;
  sourceSummary?: string | null;
  draftText?: string | null;
  disclaimer?: string;
  error?: string | null;
  loading?: boolean;
  children?: React.ReactNode;
  acceptLabel?: string;
  editLabel?: string;
  rejectLabel?: string;
  copyLabel?: string;
  acceptDisabled?: boolean;
  editDisabled?: boolean;
  rejectDisabled?: boolean;
  copyDisabled?: boolean;
  onAccept?: () => void;
  onEdit?: (nextText: string) => void;
  onReject?: () => void;
  onCopy?: () => void;
};

function statusLabel(status: ClinicalAiDraftStatus) {
  switch (status) {
    case "ACCEPTED":
      return "Accepted";
    case "EDITED":
      return "Edited";
    case "REJECTED":
      return "Rejected";
    default:
      return "Pending";
  }
}

export function ClinicalAiDraftCard({
  title,
  status,
  generatedAt,
  sourceSummary,
  draftText,
  disclaimer = "AI-generated draft. Doctor must verify before use.",
  error,
  loading,
  children,
  acceptLabel = "Accept",
  editLabel = "Edit",
  rejectLabel = "Reject",
  copyLabel = "Copy",
  acceptDisabled,
  editDisabled,
  rejectDisabled,
  copyDisabled,
  onAccept,
  onEdit,
  onReject,
  onCopy,
}: ClinicalAiDraftCardProps) {
  const [isEditing, setIsEditing] = React.useState(false);
  const [editText, setEditText] = React.useState(draftText || "");

  React.useEffect(() => {
    setEditText(draftText || "");
    if (status !== "EDITED") {
      setIsEditing(false);
    }
  }, [draftText, status]);

  const badgeColor = status === "REJECTED" ? "default" : status === "DRAFTED" ? "warning" : "success";
  const isRejected = status === "REJECTED";

  return (
    <Card variant="outlined" sx={{ boxShadow: "none", borderRadius: 2 }}>
      <CardContent sx={{ p: 1.1, "&:last-child": { pb: 1.1 } }}>
        <Stack spacing={1}>
          <Stack direction="row" spacing={0.75} alignItems="flex-start" justifyContent="space-between">
            <Box sx={{ minWidth: 0 }}>
              <Stack direction="row" spacing={0.75} alignItems="center" useFlexGap flexWrap="wrap">
                <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
                  {title}
                </Typography>
                <Chip size="small" variant="outlined" color={badgeColor} label={statusLabel(status)} />
                {generatedAt ? <Chip size="small" variant="outlined" label={new Date(generatedAt).toLocaleString()} /> : null}
              </Stack>
              {sourceSummary ? (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, whiteSpace: "pre-wrap" }}>
                  Context: {sourceSummary}
                </Typography>
              ) : null}
            </Box>
            {loading ? <Chip size="small" color="warning" variant="outlined" label="Running" /> : null}
          </Stack>

          <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
            {disclaimer}
          </Typography>

          {error ? <Alert severity="error" sx={{ py: 0.5 }}>{error}</Alert> : null}

          {!isRejected ? (
            isEditing ? (
              <Stack spacing={0.75}>
                <TextField
                  size="small"
                  fullWidth
                  multiline
                  minRows={3}
                  value={editText}
                  onChange={(event) => setEditText(event.target.value)}
                />
                <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
                  <Button
                    type="button"
                    size="small"
                    variant="contained"
                    onClick={() => {
                      onEdit?.(editText);
                      setIsEditing(false);
                    }}
                  >
                    Save edit
                  </Button>
                  <Button
                    type="button"
                    size="small"
                    variant="outlined"
                    onClick={() => {
                      setEditText(draftText || "");
                      setIsEditing(false);
                    }}
                  >
                    Cancel
                  </Button>
                </Stack>
              </Stack>
            ) : children ? (
              <Box sx={{ minWidth: 0 }}>
                {children}
              </Box>
            ) : draftText ? (
              <Box sx={{ p: 1, border: 1, borderColor: "divider", borderRadius: 1.5, bgcolor: "background.paper" }}>
                <Typography variant="body2" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.5 }}>
                  {draftText}
                </Typography>
              </Box>
            ) : null
          ) : (
            <Typography variant="body2" color="text.secondary">
              Rejected draft is retained until you clear all drafts.
            </Typography>
          )}

          <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap">
            {onAccept ? (
              <Button type="button" size="small" variant="contained" disabled={acceptDisabled} onClick={onAccept}>
                {acceptLabel}
              </Button>
            ) : null}
            {onEdit ? (
              <Button type="button" size="small" variant="outlined" disabled={editDisabled || isEditing} onClick={() => setIsEditing(true)}>
                {editLabel}
              </Button>
            ) : null}
            {onReject ? (
              <Button type="button" size="small" variant="outlined" disabled={rejectDisabled} onClick={onReject}>
                {rejectLabel}
              </Button>
            ) : null}
            {onCopy ? (
              <Button type="button" size="small" variant="outlined" disabled={copyDisabled} onClick={onCopy}>
                {copyLabel}
              </Button>
            ) : null}
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}
