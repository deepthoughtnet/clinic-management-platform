import * as React from "react";
import { Box, ButtonBase, Chip, Stack, Tooltip, Typography } from "@mui/material";

export type DocumentRelationshipState = "completed" | "current" | "future" | "cancelled";

export type DocumentRelationshipStage = {
  label: string;
  documentNumber?: string | null;
  badgeLabel?: string | null;
  state: DocumentRelationshipState;
  onClick?: () => void;
  tooltip?: string | null;
};

type DocumentRelationshipStripProps = {
  title?: string;
  stages: DocumentRelationshipStage[];
};

function stageStyles(state: DocumentRelationshipState) {
  if (state === "completed") {
    return { color: "success.main", borderColor: "success.main", bgcolor: "success.main" };
  }
  if (state === "current") {
    return { color: "primary.main", borderColor: "primary.main", bgcolor: "primary.main" };
  }
  if (state === "cancelled") {
    return { color: "error.main", borderColor: "error.main", bgcolor: "error.main" };
  }
  return { color: "text.disabled", borderColor: "divider", bgcolor: "transparent" };
}

function stateLabel(state: DocumentRelationshipState) {
  if (state === "completed") return "Completed";
  if (state === "current") return "Current";
  if (state === "cancelled") return "Cancelled";
  return "Future";
}

export default function DocumentRelationshipStrip({ title = "Document Relationship", stages }: DocumentRelationshipStripProps) {
  return (
    <Stack spacing={1} sx={{ width: "100%", maxWidth: "100%", minWidth: 0 }}>
      <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.8 }}>
        {title}
      </Typography>
      <Box
        sx={{
          display: "flex",
          width: "100%",
          maxWidth: "100%",
          minWidth: 0,
          gap: 1,
          overflowX: "auto",
          overflowY: "hidden",
          pb: 0.5,
          pr: 0.5,
          scrollSnapType: "x proximity",
          alignItems: "stretch",
          position: "relative",
          "&::-webkit-scrollbar": { height: 8 },
          "&::-webkit-scrollbar-thumb": { borderRadius: 999, bgcolor: "divider" },
        }}
      >
        {stages.map((stage, index) => {
          const styles = stageStyles(stage.state);
          const content = (
            <Stack
              spacing={0.55}
              sx={{
                minWidth: 170,
                maxWidth: "100%",
                px: 1.25,
                py: 1,
                border: "1px solid",
                borderColor: styles.borderColor,
                borderRadius: 2,
                bgcolor: stage.state === "current" ? "action.selected" : "background.paper",
                scrollSnapAlign: "start",
                flex: "0 0 auto",
              }}
            >
              <Stack direction="row" spacing={1} alignItems="center">
                <Box
                  aria-hidden
                  sx={{
                    width: 11,
                    height: 11,
                    borderRadius: "50%",
                    border: "2px solid",
                    borderColor: styles.borderColor,
                    bgcolor: styles.bgcolor,
                    flex: "0 0 auto",
                  }}
                />
                <Typography variant="body2" sx={{ fontWeight: 800, color: stage.state === "future" ? "text.secondary" : "text.primary" }}>
                  {stage.label}
                </Typography>
              </Stack>
              <Typography variant="body2" color="text.secondary" sx={{ wordBreak: "break-word" }}>
                {stage.documentNumber || "Pending"}
              </Typography>
              <Stack direction="row" spacing={0.5} alignItems="center" flexWrap="wrap" useFlexGap>
                <Chip size="small" label={stage.badgeLabel || stateLabel(stage.state)} color={stage.state === "future" ? "default" : stage.state === "cancelled" ? "error" : stage.state === "current" ? "primary" : "success"} variant={stage.state === "future" ? "outlined" : "filled"} />
              </Stack>
            </Stack>
          );
          const wrapped = stage.onClick ? (
            <Tooltip key={`${stage.label}-${index}`} title={stage.tooltip || `Open ${stage.label}`} arrow>
              <ButtonBase
                onClick={stage.onClick}
                sx={{
                  maxWidth: "100%",
                  borderRadius: 2,
                  textAlign: "left",
                  "&:focus-visible": { outline: "2px solid", outlineColor: "primary.main", outlineOffset: 2 },
                }}
              >
                {content}
              </ButtonBase>
            </Tooltip>
          ) : (
            <Box key={`${stage.label}-${index}`}>{content}</Box>
          );

          return (
            <React.Fragment key={`${stage.label}-${index}`}>
              {wrapped}
              {index < stages.length - 1 ? (
                <Box
                  aria-hidden
                  sx={{
                    pointerEvents: "none",
                    alignSelf: "center",
                    color: stage.state === "future" ? "text.disabled" : "text.secondary",
                    fontSize: 20,
                    px: 0.25,
                  }}
                >
                  {"->"}
                </Box>
              ) : null}
            </React.Fragment>
          );
        })}
      </Box>
    </Stack>
  );
}
