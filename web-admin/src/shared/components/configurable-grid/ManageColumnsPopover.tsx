import * as React from "react";
import {
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  Popover,
  Stack,
  Typography,
} from "@mui/material";

import type { ColumnDefinitionBase } from "./columnVisibility.js";

type ManageColumnsPopoverProps<T extends ColumnDefinitionBase> = {
  columns: readonly T[];
  visibleColumnIds: readonly string[];
  onToggleColumn: (columnId: string) => void;
  onReset: () => void;
  label?: string;
  disabled?: boolean;
};

export default function ManageColumnsPopover<T extends ColumnDefinitionBase>({
  columns,
  visibleColumnIds,
  onToggleColumn,
  onReset,
  label = "Manage Columns",
  disabled,
}: ManageColumnsPopoverProps<T>) {
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  const open = Boolean(anchorEl);

  return (
    <>
      <Button
        size="small"
        variant="outlined"
        onClick={(event) => setAnchorEl(event.currentTarget)}
        disabled={disabled}
      >
        {label}
      </Button>
      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
        slotProps={{ paper: { sx: { p: 1.5, width: 280, maxWidth: "calc(100vw - 32px)" } } }}
      >
        <Stack spacing={1.25}>
          <Box>
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
              Visible columns
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Mandatory columns stay visible.
            </Typography>
          </Box>
          <Stack spacing={0.25}>
            {columns.map((column) => {
              const checked = visibleColumnIds.includes(column.id);
              return (
                <FormControlLabel
                  key={column.id}
                  control={(
                    <Checkbox
                      size="small"
                      checked={checked}
                      disabled={column.mandatory}
                      onChange={() => onToggleColumn(column.id)}
                    />
                  )}
                  label={column.mandatory ? `${column.label} (required)` : column.label}
                />
              );
            })}
          </Stack>
          <Stack direction="row" spacing={1} justifyContent="space-between">
            <Button size="small" variant="text" onClick={onReset}>
              Reset to default
            </Button>
            <Button size="small" variant="outlined" onClick={() => setAnchorEl(null)}>
              Close
            </Button>
          </Stack>
        </Stack>
      </Popover>
    </>
  );
}
