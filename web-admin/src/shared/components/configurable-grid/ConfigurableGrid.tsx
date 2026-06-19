import * as React from "react";
import {
  Box,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import type { SxProps, Theme } from "@mui/material/styles";

import { getVisibleColumns, type ColumnDefinitionBase } from "./columnVisibility.js";

export type ConfigurableGridColumn<T> = ColumnDefinitionBase & {
  label: React.ReactNode;
  render: (row: T) => React.ReactNode;
  align?: "inherit" | "left" | "center" | "right" | "justify";
  width?: number | string;
  minWidth?: number | string;
};

type ConfigurableGridProps<T> = {
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  columns: readonly ConfigurableGridColumn<T>[];
  visibleColumnIds: readonly string[];
  rows: readonly T[];
  getRowKey: (row: T) => React.Key;
  toolbar?: React.ReactNode;
  emptyState?: React.ReactNode;
  maxHeight?: number | string;
  tableSx?: SxProps<Theme>;
  rowSx?: SxProps<Theme>;
};

function getColumnMinWidth(column: ConfigurableGridColumn<any>) {
  if (typeof column.minWidth === "number") return `${column.minWidth}px`;
  if (column.minWidth) return column.minWidth;
  if (typeof column.width === "number") return `${column.width}px`;
  if (column.width) return column.width;
  return undefined;
}

export default function ConfigurableGrid<T>({
  title,
  subtitle,
  columns,
  visibleColumnIds,
  rows,
  getRowKey,
  toolbar,
  emptyState,
  maxHeight = 520,
  tableSx,
  rowSx,
}: ConfigurableGridProps<T>) {
  const visibleColumns = React.useMemo(
    () => getVisibleColumns(columns, visibleColumnIds),
    [columns, visibleColumnIds],
  );

  const minTableWidth = React.useMemo(
    () => visibleColumns.reduce((sum, column) => sum + (typeof column.minWidth === "number" ? column.minWidth : 0), 0),
    [visibleColumns],
  );

  if (rows.length === 0 && emptyState) {
    return <>{emptyState}</>;
  }

  return (
    <Stack spacing={1.25}>
      {(title || subtitle || toolbar) ? (
        <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
          <Box>
            {title ? (
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                {title}
              </Typography>
            ) : null}
            {subtitle ? (
              <Typography variant="body2" color="text.secondary">
                {subtitle}
              </Typography>
            ) : null}
          </Box>
          {toolbar}
        </Box>
      ) : null}
      <TableContainer sx={{ maxHeight, overflowX: "auto" }}>
        <Table size="small" stickyHeader sx={{ minWidth: minTableWidth || undefined, ...tableSx }}>
          <TableHead>
            <TableRow>
              {visibleColumns.map((column) => (
                <TableCell
                  key={column.id}
                  align={column.align}
                  sx={{
                    minWidth: getColumnMinWidth(column),
                    whiteSpace: "nowrap",
                  }}
                >
                  {column.label}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={getRowKey(row)} hover sx={rowSx}>
                {visibleColumns.map((column) => (
                  <TableCell
                    key={column.id}
                    align={column.align}
                    sx={{
                      minWidth: getColumnMinWidth(column),
                      whiteSpace: "nowrap",
                      verticalAlign: "top",
                    }}
                  >
                    {column.render(row)}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Stack>
  );
}
