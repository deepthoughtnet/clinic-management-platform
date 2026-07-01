import * as React from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Box,
  Button,
  ButtonBase,
  Card,
  CardContent,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";

import { CompactEmptyState, CompactFilterCard, CompactStatCard, WorkflowGuide, compactCardContentSx } from "../../components/compact/CompactUi";

type Workspace = "suppliers" | "purchase-orders" | "supplier-invoices" | "goods-receipt";

type SupplierRow = {
  id: string;
  name: string;
  gstin: string;
  contact: string;
  phone: string;
  email: string;
  status: "Active" | "Inactive";
};

type SupplierForm = {
  name: string;
  gstin: string;
  contact: string;
  phone: string;
  email: string;
  status: "Active" | "Inactive";
};

type PurchaseOrderRow = {
  id: string;
  poNumber: string;
  supplierId: string;
  orderDate: string;
  expectedDelivery: string;
  status: "Draft" | "Generated" | "Sent" | "Received";
  totalQty: number;
  totalValue: number;
};

type SupplierInvoiceRow = {
  id: string;
  invoiceNumber: string;
  supplierId: string;
  purchaseOrderId: string;
  amount: number;
  status: "Pending" | "Matched" | "Variance";
};

type GoodsReceiptRow = {
  id: string;
  receiptNumber: string;
  supplierId: string;
  purchaseOrderId: string;
  receivedQty: number;
  status: "Pending" | "Posted";
};

const WORKSPACES: Array<{ value: Workspace; label: string }> = [
  { value: "suppliers", label: "Suppliers" },
  { value: "purchase-orders", label: "Purchase Orders" },
  { value: "supplier-invoices", label: "Supplier Invoices" },
  { value: "goods-receipt", label: "Goods Receipt" },
];

const emptySupplier: SupplierForm = { name: "", gstin: "", contact: "", phone: "", email: "", status: "Active" };
const emptyPoLine = { medicine: "", name: "", qty: "", unitCost: "" };
const emptyPoForm = { supplierId: "", poNumber: "", orderDate: "", expectedDelivery: "", notes: "" };
const emptyInvoiceForm = { supplierId: "", purchaseOrderId: "", invoiceNumber: "", invoiceDate: "", amount: "" };
const emptyGrnForm = { supplierId: "", purchaseOrderId: "", receiptNumber: "", receivedAt: "", receivedQty: "", batch: "", expiry: "" };

function parseWorkspace(value: string | null): Workspace {
  if (value === "purchase-orders" || value === "supplier-invoices" || value === "goods-receipt") return value;
  return "suppliers";
}

export default function PharmacyProcurePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const workspace = parseWorkspace(searchParams.get("workspace"));
  const [suppliers, setSuppliers] = React.useState<SupplierRow[]>([
    { id: "sup-1", name: "Acme Pharma", gstin: "27ABCDE1234F1Z5", contact: "Ravi", phone: "9876543210", email: "ravi@acme.example", status: "Active" },
  ]);
  const [purchaseOrders, setPurchaseOrders] = React.useState<PurchaseOrderRow[]>([]);
  const [invoices, setInvoices] = React.useState<SupplierInvoiceRow[]>([]);
  const [grns, setGrns] = React.useState<GoodsReceiptRow[]>([]);
  const [supplierForm, setSupplierForm] = React.useState<SupplierForm>(emptySupplier);
  const [editingSupplierId, setEditingSupplierId] = React.useState<string | null>(null);
  const [poForm, setPoForm] = React.useState(emptyPoForm);
  const [poLines, setPoLines] = React.useState([emptyPoLine]);
  const [invoiceForm, setInvoiceForm] = React.useState(emptyInvoiceForm);
  const [grnForm, setGrnForm] = React.useState(emptyGrnForm);

  const supplierById = React.useMemo(() => new Map(suppliers.map((supplier) => [supplier.id, supplier])), [suppliers]);
  const poById = React.useMemo(() => new Map(purchaseOrders.map((po) => [po.id, po])), [purchaseOrders]);

  const currentSupplierCount = suppliers.length;
  const currentPurchaseOrderCount = purchaseOrders.length;
  const currentInvoiceCount = invoices.length;
  const currentGrnCount = grns.length;

  const updateWorkspace = (next: Workspace) => {
    navigate(`/pharmacy/procure?workspace=${next}${next === "suppliers" ? "&focus=supplier" : ""}`);
  };

  const saveSupplier = () => {
    const next: SupplierRow = {
      id: editingSupplierId || `sup-${Date.now()}`,
      name: supplierForm.name.trim() || "New Supplier",
      gstin: supplierForm.gstin.trim(),
      contact: supplierForm.contact.trim(),
      phone: supplierForm.phone.trim(),
      email: supplierForm.email.trim(),
      status: supplierForm.status,
    };
    setSuppliers((current) => {
      const without = current.filter((item) => item.id !== next.id);
      return [next, ...without];
    });
    setEditingSupplierId(null);
    setSupplierForm(emptySupplier);
  };

  const addPoLine = () => setPoLines((current) => [...current, emptyPoLine]);

  const savePurchaseOrder = () => {
    const totalQty = poLines.reduce((sum, line) => sum + Number(line.qty || 0), 0);
    const totalValue = poLines.reduce((sum, line) => sum + Number(line.qty || 0) * Number(line.unitCost || 0), 0);
    setPurchaseOrders((current) => [{
      id: `po-${Date.now()}`,
      poNumber: poForm.poNumber || `PO-${current.length + 1}`,
      supplierId: poForm.supplierId || suppliers[0]?.id || "",
      orderDate: poForm.orderDate || new Date().toISOString().slice(0, 10),
      expectedDelivery: poForm.expectedDelivery || "",
      status: "Generated",
      totalQty,
      totalValue,
    }, ...current]);
    setPoForm(emptyPoForm);
    setPoLines([emptyPoLine]);
  };

  const saveInvoice = () => {
    setInvoices((current) => [{
      id: `inv-${Date.now()}`,
      invoiceNumber: invoiceForm.invoiceNumber || `INV-${current.length + 1}`,
      supplierId: invoiceForm.supplierId || suppliers[0]?.id || "",
      purchaseOrderId: invoiceForm.purchaseOrderId || purchaseOrders[0]?.id || "",
      amount: Number(invoiceForm.amount || 0),
      status: "Pending",
    }, ...current]);
    setInvoiceForm(emptyInvoiceForm);
  };

  const saveGrn = () => {
    setGrns((current) => [{
      id: `grn-${Date.now()}`,
      receiptNumber: grnForm.receiptNumber || `GRN-${current.length + 1}`,
      supplierId: grnForm.supplierId || suppliers[0]?.id || "",
      purchaseOrderId: grnForm.purchaseOrderId || purchaseOrders[0]?.id || "",
      receivedQty: Number(grnForm.receivedQty || 0),
      status: "Posted",
    }, ...current]);
    setGrnForm(emptyGrnForm);
  };

  return (
    <Stack spacing={2}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "center" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>Procure</Typography>
          <Typography variant="body2" color="text.secondary">Supplier-first procurement workspace with local state and URL-synced tabs.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={() => navigate("/inventory")}>Inventory</Button>
          <Button variant="outlined" onClick={() => navigate("/pharmacy/pos")}>POS Sale</Button>
        </Stack>
      </Box>

      <WorkflowGuide
        title="Workflow guidance"
        subtitle="Supplier → Purchase Order → Generate / Send PO → Invoice → Goods Receipt → Stock Added"
        steps={[
          { label: "Supplier", tone: workspace === "suppliers" ? "primary" : "default" },
          { label: "Purchase Order", tone: workspace === "purchase-orders" ? "primary" : "default" },
          { label: "Invoice", tone: workspace === "supplier-invoices" ? "primary" : "default" },
          { label: "Goods Receipt", tone: workspace === "goods-receipt" ? "primary" : "default" },
        ]}
      />

      <Grid container spacing={2}>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Suppliers" value={currentSupplierCount} helper="Master records" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Purchase Orders" value={currentPurchaseOrderCount} helper="PO workflow" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Supplier Invoices" value={currentInvoiceCount} helper="Matching queue" />
        </Grid>
        <Grid size={{ xs: 6, md: 3 }}>
          <CompactStatCard label="Goods Receipt" value={currentGrnCount} helper="Receiving queue" />
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent sx={compactCardContentSx}>
          <Box role="tablist" aria-label="Procure workspaces" sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
            {WORKSPACES.map((item) => {
              const active = workspace === item.value;
              return (
                <ButtonBase
                  key={item.value}
                  role="tab"
                  aria-selected={active}
                  tabIndex={0}
                  onClick={() => updateWorkspace(item.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      updateWorkspace(item.value);
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

      {workspace === "suppliers" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 4 }}>
            <CompactFilterCard
              title={editingSupplierId ? "Edit supplier" : "Add supplier"}
              subtitle="Local supplier master."
              actions={<Button size="small" variant="contained" onClick={saveSupplier}>Save supplier</Button>}
            >
              <Stack spacing={1}>
                <TextField size="small" label="Supplier name *" value={supplierForm.name} onChange={(e) => setSupplierForm((current) => ({ ...current, name: e.target.value }))} />
                <TextField size="small" label="GSTIN" value={supplierForm.gstin} onChange={(e) => setSupplierForm((current) => ({ ...current, gstin: e.target.value }))} />
                <TextField size="small" label="Contact person" value={supplierForm.contact} onChange={(e) => setSupplierForm((current) => ({ ...current, contact: e.target.value }))} />
                <TextField size="small" label="Phone" value={supplierForm.phone} onChange={(e) => setSupplierForm((current) => ({ ...current, phone: e.target.value }))} />
                <TextField size="small" label="Email" value={supplierForm.email} onChange={(e) => setSupplierForm((current) => ({ ...current, email: e.target.value }))} />
                <FormControl size="small">
                  <InputLabel>Status</InputLabel>
                  <Select label="Status" value={supplierForm.status} onChange={(e) => setSupplierForm((current) => ({ ...current, status: e.target.value as "Active" | "Inactive" }))}>
                    <MenuItem value="Active">Active</MenuItem>
                    <MenuItem value="Inactive">Inactive</MenuItem>
                  </Select>
                </FormControl>
              </Stack>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 8 }}>
            <CompactFilterCard title="Supplier list" subtitle="View, edit, activate, and deactivate suppliers locally.">
              {suppliers.length ? (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Supplier</TableCell>
                      <TableCell>Contact</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {suppliers.map((supplier) => (
                      <TableRow key={supplier.id}>
                        <TableCell>{supplier.name}</TableCell>
                        <TableCell>{supplier.contact || "-"}</TableCell>
                        <TableCell>{supplier.status}</TableCell>
                        <TableCell align="right">
                          <Button size="small" onClick={() => setSupplierForm({ name: supplier.name, gstin: supplier.gstin, contact: supplier.contact, phone: supplier.phone, email: supplier.email, status: supplier.status })}>View</Button>
                          <Button size="small" onClick={() => { setEditingSupplierId(supplier.id); setSupplierForm({ name: supplier.name, gstin: supplier.gstin, contact: supplier.contact, phone: supplier.phone, email: supplier.email, status: supplier.status }); }}>Edit</Button>
                          <Button size="small" onClick={() => setSuppliers((current) => current.map((row) => row.id === supplier.id ? { ...row, status: row.status === "Active" ? "Inactive" : "Active" } : row))}>{supplier.status === "Active" ? "Deactivate" : "Activate"}</Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <CompactEmptyState title="No suppliers" subtitle="Add the first supplier to continue." />
              )}
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}

      {workspace === "purchase-orders" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <CompactFilterCard title="Purchase order" subtitle="Simple local PO form." actions={<Button size="small" variant="contained" onClick={savePurchaseOrder}>Save PO</Button>}>
              <Stack spacing={1}>
                <FormControl size="small">
                  <InputLabel>Supplier</InputLabel>
                  <Select label="Supplier" value={poForm.supplierId} onChange={(e) => setPoForm((current) => ({ ...current, supplierId: e.target.value }))}>
                    {suppliers.map((supplier) => <MenuItem key={supplier.id} value={supplier.id}>{supplier.name}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField size="small" label="PO number *" value={poForm.poNumber} onChange={(e) => setPoForm((current) => ({ ...current, poNumber: e.target.value }))} />
                <TextField size="small" label="Order date" type="date" InputLabelProps={{ shrink: true }} value={poForm.orderDate} onChange={(e) => setPoForm((current) => ({ ...current, orderDate: e.target.value }))} />
                <TextField size="small" label="Expected delivery" type="date" InputLabelProps={{ shrink: true }} value={poForm.expectedDelivery} onChange={(e) => setPoForm((current) => ({ ...current, expectedDelivery: e.target.value }))} />
                {poLines.map((line, index) => (
                  <Card key={index} variant="outlined">
                    <CardContent sx={{ p: 1 }}>
                      <Stack spacing={1}>
                        <Typography variant="caption" color="text.secondary">Line item {index + 1}</Typography>
                        <TextField size="small" label="Medicine *" value={line.medicine} onChange={(e) => setPoLines((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, medicine: e.target.value } : row))} />
                        <TextField size="small" label="Line item name *" value={line.name} onChange={(e) => setPoLines((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, name: e.target.value } : row))} />
                        <Stack direction="row" spacing={1}>
                          <TextField size="small" label="Qty *" value={line.qty} onChange={(e) => setPoLines((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, qty: e.target.value } : row))} />
                          <TextField size="small" label="Unit cost" value={line.unitCost} onChange={(e) => setPoLines((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, unitCost: e.target.value } : row))} />
                        </Stack>
                      </Stack>
                    </CardContent>
                  </Card>
                ))}
                <Button size="small" variant="outlined" onClick={addPoLine}>Add item</Button>
              </Stack>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 7 }}>
            <CompactFilterCard title="Purchase order list" subtitle="Saved purchase orders appear immediately.">
              {purchaseOrders.length ? (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>PO number</TableCell>
                      <TableCell>Supplier</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {purchaseOrders.map((po) => (
                      <TableRow key={po.id}>
                        <TableCell>{po.poNumber}</TableCell>
                        <TableCell>{supplierById.get(po.supplierId)?.name || "-"}</TableCell>
                        <TableCell>{po.status}</TableCell>
                        <TableCell align="right">
                          <Button size="small">View</Button>
                          <Button size="small">Edit</Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <CompactEmptyState title="No purchase orders" subtitle="Create the first PO to continue." />
              )}
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}

      {workspace === "supplier-invoices" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <CompactFilterCard title="Supplier invoice" subtitle="Match an invoice to an existing PO." actions={<Button size="small" variant="contained" onClick={saveInvoice}>Save invoice</Button>}>
              <Stack spacing={1}>
                <FormControl size="small">
                  <InputLabel>Purchase order</InputLabel>
                  <Select label="Purchase order" value={invoiceForm.purchaseOrderId} onChange={(e) => setInvoiceForm((current) => ({ ...current, purchaseOrderId: e.target.value }))}>
                    {purchaseOrders.map((po) => <MenuItem key={po.id} value={po.id}>{po.poNumber}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField size="small" label="Supplier invoice number" value={invoiceForm.invoiceNumber} onChange={(e) => setInvoiceForm((current) => ({ ...current, invoiceNumber: e.target.value }))} />
                <TextField size="small" label="Invoice date" type="date" InputLabelProps={{ shrink: true }} value={invoiceForm.invoiceDate} onChange={(e) => setInvoiceForm((current) => ({ ...current, invoiceDate: e.target.value }))} />
                <TextField size="small" label="Invoice amount" value={invoiceForm.amount} onChange={(e) => setInvoiceForm((current) => ({ ...current, amount: e.target.value }))} />
              </Stack>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 7 }}>
            <CompactFilterCard title="Invoice list" subtitle="Matched and pending invoices.">
              {invoices.length ? (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Invoice</TableCell>
                      <TableCell>Purchase order</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Amount</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {invoices.map((invoice) => (
                      <TableRow key={invoice.id}>
                        <TableCell>{invoice.invoiceNumber}</TableCell>
                        <TableCell>{poById.get(invoice.purchaseOrderId)?.poNumber || "-"}</TableCell>
                        <TableCell>{invoice.status}</TableCell>
                        <TableCell align="right">{invoice.amount}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <CompactEmptyState title="No supplier invoices" subtitle="Create an invoice to match to a PO." />
              )}
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}

      {workspace === "goods-receipt" ? (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <CompactFilterCard title="Goods receipt" subtitle="Receive stock against an existing PO." actions={<Button size="small" variant="contained" onClick={saveGrn}>Post GRN</Button>}>
              <Stack spacing={1}>
                <FormControl size="small">
                  <InputLabel>Purchase order</InputLabel>
                  <Select label="Purchase order" value={grnForm.purchaseOrderId} onChange={(e) => setGrnForm((current) => ({ ...current, purchaseOrderId: e.target.value }))}>
                    {purchaseOrders.map((po) => <MenuItem key={po.id} value={po.id}>{po.poNumber}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField size="small" label="Receipt number" value={grnForm.receiptNumber} onChange={(e) => setGrnForm((current) => ({ ...current, receiptNumber: e.target.value }))} />
                <TextField size="small" label="Received at" type="date" InputLabelProps={{ shrink: true }} value={grnForm.receivedAt} onChange={(e) => setGrnForm((current) => ({ ...current, receivedAt: e.target.value }))} />
                <TextField size="small" label="Received quantity" value={grnForm.receivedQty} onChange={(e) => setGrnForm((current) => ({ ...current, receivedQty: e.target.value }))} />
                <TextField size="small" label="Batch" value={grnForm.batch} onChange={(e) => setGrnForm((current) => ({ ...current, batch: e.target.value }))} />
                <TextField size="small" label="Expiry" type="date" InputLabelProps={{ shrink: true }} value={grnForm.expiry} onChange={(e) => setGrnForm((current) => ({ ...current, expiry: e.target.value }))} />
              </Stack>
            </CompactFilterCard>
          </Grid>
          <Grid size={{ xs: 12, lg: 7 }}>
            <CompactFilterCard title="GRN list" subtitle="Posted receipts update inventory later in the real workflow.">
              {grns.length ? (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Receipt</TableCell>
                      <TableCell>Purchase order</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Qty</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {grns.map((grn) => (
                      <TableRow key={grn.id}>
                        <TableCell>{grn.receiptNumber}</TableCell>
                        <TableCell>{poById.get(grn.purchaseOrderId)?.poNumber || "-"}</TableCell>
                        <TableCell>{grn.status}</TableCell>
                        <TableCell align="right">{grn.receivedQty}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <CompactEmptyState title="No goods receipts" subtitle="Post a GRN to continue." />
              )}
            </CompactFilterCard>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  );
}
