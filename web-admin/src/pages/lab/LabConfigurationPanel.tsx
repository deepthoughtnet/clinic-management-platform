import * as React from "react";
import {
  Alert,
  Box,
  Button,
  FormControlLabel,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
} from "@mui/material";

import { CompactFilterCard } from "../../components/compact/CompactUi";
import type { LabCategoryConfig, LabCategoryConfigInput, LabTestCatalogueConfig, LabTestCatalogueConfigInput } from "../../api/clinicApi";

type LabConfigurationPanelProps = {
  categories: LabCategoryConfig[];
  tests: LabTestCatalogueConfig[];
  saving: boolean;
  onSaveCategory: (code: string, patch: LabCategoryConfigInput) => Promise<void>;
  onSaveTest: (id: string, patch: LabTestCatalogueConfigInput) => Promise<void>;
};

type CategoryDraft = {
  displayName: string;
  active: boolean;
  displayOrder: string;
};

type TestDraft = {
  enabled: boolean;
  active: boolean;
  tenantPriceOverride: string;
  tenantTatOverride: string;
  displayOrder: string;
};

function parseOptionalNumber(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

function toCategoryDraft(row: LabCategoryConfig): CategoryDraft {
  return {
    displayName: row.displayName,
    active: row.active,
    displayOrder: row.displayOrder == null ? "" : String(row.displayOrder),
  };
}

function toTestDraft(row: LabTestCatalogueConfig): TestDraft {
  return {
    enabled: row.enabled,
    active: row.active,
    tenantPriceOverride: row.tenantPriceOverride == null ? "" : String(row.tenantPriceOverride),
    tenantTatOverride: row.tenantTatOverride || "",
    displayOrder: row.displayOrder == null ? "" : String(row.displayOrder),
  };
}

export default function LabConfigurationPanel({
  categories,
  tests,
  saving,
  onSaveCategory,
  onSaveTest,
}: LabConfigurationPanelProps) {
  const [categoryDrafts, setCategoryDrafts] = React.useState<Record<string, CategoryDraft>>({});
  const [testDrafts, setTestDrafts] = React.useState<Record<string, TestDraft>>({});
  const [categorySaving, setCategorySaving] = React.useState<string | null>(null);
  const [testSaving, setTestSaving] = React.useState<string | null>(null);

  React.useEffect(() => {
    setCategoryDrafts(Object.fromEntries(categories.map((row) => [row.categoryCode, toCategoryDraft(row)])));
  }, [categories]);

  React.useEffect(() => {
    setTestDrafts(Object.fromEntries(tests.map((row) => [row.id, toTestDraft(row)])));
  }, [tests]);

  const saveCategory = async (code: string) => {
    const draft = categoryDrafts[code];
    if (!draft) return;
    setCategorySaving(code);
    try {
      await onSaveCategory(code, {
        displayName: draft.displayName,
        active: draft.active,
        displayOrder: parseOptionalNumber(draft.displayOrder),
      });
    } finally {
      setCategorySaving(null);
    }
  };

  const saveTest = async (id: string) => {
    const draft = testDrafts[id];
    if (!draft) return;
    setTestSaving(id);
    try {
      await onSaveTest(id, {
        enabled: draft.enabled,
        active: draft.active,
        tenantPriceOverride: parseOptionalNumber(draft.tenantPriceOverride),
        tenantTatOverride: draft.tenantTatOverride.trim() === "" ? null : draft.tenantTatOverride.trim(),
        displayOrder: parseOptionalNumber(draft.displayOrder),
      });
    } finally {
      setTestSaving(null);
    }
  };

  return (
    <Stack spacing={1.5}>
      <Alert severity="info">
        Lab configuration is tenant-scoped. Category toggles affect lab ordering only; historical orders remain unchanged.
      </Alert>

      <CompactFilterCard title="Categories" subtitle="Enable or disable catalogue categories for this tenant.">
        <Box sx={{ overflowX: "auto" }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Code</TableCell>
                <TableCell>Display Name</TableCell>
                <TableCell>Active</TableCell>
                <TableCell>Display Order</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {categories.map((row) => {
                const draft = categoryDrafts[row.categoryCode] || toCategoryDraft(row);
                const busy = categorySaving === row.categoryCode;
                return (
                  <TableRow key={row.categoryCode}>
                    <TableCell sx={{ fontWeight: 700 }}>{row.categoryCode}</TableCell>
                    <TableCell sx={{ minWidth: 220 }}>
                      <TextField
                        fullWidth
                        size="small"
                        value={draft.displayName}
                        onChange={(e) => setCategoryDrafts((current) => ({ ...current, [row.categoryCode]: { ...draft, displayName: e.target.value } }))}
                      />
                    </TableCell>
                    <TableCell>
                      <FormControlLabel
                        control={(
                          <Switch
                            checked={draft.active}
                            onChange={(e) => setCategoryDrafts((current) => ({ ...current, [row.categoryCode]: { ...draft, active: e.target.checked } }))}
                            disabled={saving}
                          />
                        )}
                        label={draft.active ? "Active" : "Inactive"}
                      />
                    </TableCell>
                    <TableCell sx={{ width: 180 }}>
                      <TextField
                        fullWidth
                        size="small"
                        type="number"
                        value={draft.displayOrder}
                        onChange={(e) => setCategoryDrafts((current) => ({ ...current, [row.categoryCode]: { ...draft, displayOrder: e.target.value } }))}
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Button variant="outlined" size="small" onClick={() => void saveCategory(row.categoryCode)} disabled={saving || busy}>
                        Save
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Box>
      </CompactFilterCard>

      <CompactFilterCard title="Tenant Test Catalogue" subtitle="Override availability, price, TAT, and ordering flags for this tenant.">
        <Box sx={{ overflowX: "auto" }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Test Code</TableCell>
                <TableCell>Test Name</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Enabled</TableCell>
                <TableCell>Active</TableCell>
                <TableCell>Price Override</TableCell>
                <TableCell>TAT Override</TableCell>
                <TableCell>Display Order</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {tests.map((row) => {
                const draft = testDrafts[row.id] || toTestDraft(row);
                const busy = testSaving === row.id;
                return (
                  <TableRow key={row.id}>
                    <TableCell sx={{ fontWeight: 700 }}>{row.testCode || "-"}</TableCell>
                    <TableCell>{row.testName}</TableCell>
                    <TableCell>{row.category}</TableCell>
                    <TableCell>
                      <Switch
                        checked={draft.enabled}
                        onChange={(e) => setTestDrafts((current) => ({ ...current, [row.id]: { ...draft, enabled: e.target.checked } }))}
                        disabled={saving}
                      />
                    </TableCell>
                    <TableCell>
                      <Switch
                        checked={draft.active}
                        onChange={(e) => setTestDrafts((current) => ({ ...current, [row.id]: { ...draft, active: e.target.checked } }))}
                        disabled={saving}
                      />
                    </TableCell>
                    <TableCell sx={{ width: 160 }}>
                      <TextField
                        fullWidth
                        size="small"
                        type="number"
                        value={draft.tenantPriceOverride}
                        onChange={(e) => setTestDrafts((current) => ({ ...current, [row.id]: { ...draft, tenantPriceOverride: e.target.value } }))}
                      />
                    </TableCell>
                    <TableCell sx={{ width: 180 }}>
                      <TextField
                        fullWidth
                        size="small"
                        value={draft.tenantTatOverride}
                        onChange={(e) => setTestDrafts((current) => ({ ...current, [row.id]: { ...draft, tenantTatOverride: e.target.value } }))}
                      />
                    </TableCell>
                    <TableCell sx={{ width: 140 }}>
                      <TextField
                        fullWidth
                        size="small"
                        type="number"
                        value={draft.displayOrder}
                        onChange={(e) => setTestDrafts((current) => ({ ...current, [row.id]: { ...draft, displayOrder: e.target.value } }))}
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Button variant="outlined" size="small" onClick={() => void saveTest(row.id)} disabled={saving || busy}>
                        Save
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Box>
      </CompactFilterCard>
    </Stack>
  );
}
