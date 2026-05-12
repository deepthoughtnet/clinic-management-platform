import * as React from "react";
import { Alert, Card, CardContent, Chip, Stack, Typography } from "@mui/material";

export type CarePilotPlaceholderViewProps = {
  moduleName: string;
  summary: string;
};

/**
 * Reusable placeholder shell for future CarePilot modules.
 *
 * This keeps route/page scaffolding intentionally lightweight while making
 * module intent visible to product and engineering teams.
 */
export default function CarePilotPlaceholderView({ moduleName, summary }: CarePilotPlaceholderViewProps) {
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
        <Typography variant="h5" sx={{ fontWeight: 900 }}>{moduleName}</Typography>
        <Chip label="CarePilot" size="small" color="primary" variant="outlined" />
        <Chip label="Coming Soon" size="small" variant="outlined" />
      </Stack>
      <Card>
        <CardContent>
          <Typography variant="body2" color="text.secondary">{summary}</Typography>
        </CardContent>
      </Card>
      <Alert severity="info">
        This placeholder is intentionally non-operational. No production workflows are executed from this module yet.
      </Alert>
    </Stack>
  );
}
