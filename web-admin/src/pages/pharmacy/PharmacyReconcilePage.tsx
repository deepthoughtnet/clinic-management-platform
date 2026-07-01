import * as React from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Box,
  Button,
  ButtonBase,
  Card,
  CardContent,
  Grid,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";

import { CompactEmptyState, CompactFilterCard, CompactStatCard, WorkflowGuide, compactCardContentSx } from "../../components/compact/CompactUi";

type ReconcileTab = "supplier-bill-reconciliation" | "physical-count" | "stock-adjustments" | "approval-review";

type ReconciliationRow = {
  id: string;
  reference: string;
  supplier: string;
  status: "Draft" | "Submitted" | "Reviewed" | "Approved" | "Posted";
  variance: number;
};

const TABS: Array<{ value: ReconcileTab; label: string }> = [
  { value: "supplier-bill-reconciliation", label: "Supplier Bill Reconciliation" },
  { value: "physical-count", label: "Physical Count" },
  { value: "stock-adjustments", label: "Stock Adjustments" },
  { value: "approval-review", label: "Approval Review" },
];

function parseTab(value: string | null): ReconcileTab {
  if (value === "physical-count" || value === "stock-adjustments" || value === "approval-review") return value;
  return "supplier-bill-reconciliation";
}

export default function PharmacyReconcilePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const tab = parseTab(searchParams.get("tab"));
  const [rows, setRows] = React.useState<ReconciliationRow[]>([
    { id: "rec-1", reference: "INV-1024", supplier: "Acme Pharma", status: "Submitted", variance: 120 },
  ]);

  const updateTab = (nextTab: ReconcileTab) => {
    navigate(`/pharmacy/reconcile?tab=${nextTab}`);
  };

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Reconcile</Typography>
          <Typography variant="body2" color="text.secondary">Local reconciliation workspace with URL-synced tabs.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => navigate("/inventory")}>Inventory</Button>
          <Button variant="outlined" onClick={() => navigate("/pharmacy/pos")}>POS Sale</Button>
        </Stack>
      </Box>

      <WorkflowGuide
        title="Workflow guidance"
        subtitle="Supplier bill reconciliation → Physical count → Stock adjustments → Approval review"
        steps={[
          { label: "Supplier Bill Reconciliation", tone: tab === "supplier-bill-reconciliation" ? "primary" : "default" },
          { label: "Physical Count", tone: tab === "physical-count" ? "primary" : "default" },
          { label: "Stock Adjustments", tone: tab === "stock-adjustments" ? "primary" : "default" },
          { label: "Approval Review", tone: tab === "approval-review" ? "primary" : "default" },
        ]}
      />

      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Draft / Submitted" value={rows.filter((row) => row.status === "Draft" || row.status === "Submitted").length} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Reviewed" value={rows.filter((row) => row.status === "Reviewed").length} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Approved" value={rows.filter((row) => row.status === "Approved").length} />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Posted" value={rows.filter((row) => row.status === "Posted").length} />
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent sx={compactCardContentSx}>
          <Box role="tablist" aria-label="Reconcile tabs" sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
            {TABS.map((item) => {
              const active = tab === item.value;
              return (
                <ButtonBase
                  key={item.value}
                  role="tab"
                  aria-selected={active}
                  tabIndex={0}
                  onClick={() => updateTab(item.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      updateTab(item.value);
                    }
                  }}
                  sx={{
                    px: 1.5,
                    py: 1,
                    borderRadius: 999,
                    border: "1px solid",
                    borderColor: active ? "primary.main" : "divider",
                    bgcolor: active ? "action.selected" : "background.paper",
                    color: active ? "primary.main" : "text.secondary",
                    fontWeight: active ? 800 : 600,
                    cursor: "pointer",
                    "&:focus-visible": { outline: "2px solid", outlineColor: "primary.main", outlineOffset: 2 },
                  }}
                >
                  {item.label}
                </ButtonBase>
              );
            })}
          </Box>
        </CardContent>
      </Card>

      {tab === "supplier-bill-reconciliation" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <CompactFilterCard title="Supplier bill reconciliation" subtitle="Simple local queue." actions={<Button size="small" variant="contained">Save review</Button>}>
              <Typography variant="body2" color="text.secondary">
                Review supplier bills against purchase orders and goods receipts.
              </Typography>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 7 }}>
            <CompactFilterCard title="Reconciliation list" subtitle="Rows update locally only.">
              {rows.length ? (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Reference</TableCell>
                      <TableCell>Supplier</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Variance</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.map((row) => (
                      <TableRow key={row.id}>
                        <TableCell>{row.reference}</TableCell>
                        <TableCell>{row.supplier}</TableCell>
                        <TableCell>{row.status}</TableCell>
                        <TableCell align="right">{row.variance}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <CompactEmptyState title="No reconciliation rows" subtitle="Create a reconciliation to continue." />
              )}
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}

      {tab === "physical-count" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 6 }}>
            <CompactFilterCard title="Physical count" subtitle="Count sessions remain local here.">
              <Typography variant="body2" color="text.secondary">
                Inventory count capture can be built back in here after this section proves safe.
              </Typography>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 6 }}>
            <CompactFilterCard title="Count sessions" subtitle="No API calls.">
              <CompactEmptyState title="No count sessions" subtitle="Create a session when ready." />
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}

      {tab === "stock-adjustments" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 6 }}>
            <CompactFilterCard title="Stock adjustments" subtitle="Adjustments are isolated in this page.">
              <Typography variant="body2" color="text.secondary">
                Manual stock corrections, write-offs, and stock movement review can be copied back later.
              </Typography>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 6 }}>
            <CompactFilterCard title="Adjustment queue" subtitle="No shared procurement state.">
              <CompactEmptyState title="No adjustments" subtitle="Add an adjustment entry." />
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}

      {tab === "approval-review" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 6 }}>
            <CompactFilterCard title="Approval review" subtitle="Approval step stays isolated.">
              <Typography variant="body2" color="text.secondary">
                Use this screen only after count and adjustment sections prove safe.
              </Typography>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 6 }}>
            <CompactFilterCard title="Review queue" subtitle="No APIs, no old page state.">
              <CompactEmptyState title="No items for review" subtitle="Pending approvals will appear here." />
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  );
}
