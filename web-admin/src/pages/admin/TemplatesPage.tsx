import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
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
import { useAuth } from "../../auth/useAuth";
import {
  activateAdminTemplate,
  createAdminTemplate,
  deactivateAdminTemplate,
  deleteAdminTemplate,
  listAdminTemplates,
  previewAdminTemplate,
  updateAdminTemplate,
  type AdminTemplate,
  type AdminTemplateCategory,
  type AdminTemplateChannel,
  type AdminTemplateType,
} from "../../api/clinicApi";

const TEMPLATE_TYPES: AdminTemplateType[] = ["CAMPAIGN", "REMINDER", "WEBINAR", "BILLING", "LEAD", "NOTIFICATION", "AI_PROMPT", "GENERAL"];
const CHANNELS: AdminTemplateChannel[] = ["EMAIL", "SMS", "WHATSAPP", "INTERNAL", "VOICE"];
const CATEGORIES: AdminTemplateCategory[] = ["APPOINTMENT_REMINDER", "REFILL_REMINDER", "BILLING", "WEBINAR", "FOLLOW_UP", "LEAD", "VACCINATION", "WELLNESS", "GENERAL"];

type EditorForm = {
  name: string;
  description: string;
  templateType: AdminTemplateType;
  channel: AdminTemplateChannel;
  category: AdminTemplateCategory;
  subject: string;
  body: string;
  variablesJson: string;
  active: boolean;
};

const emptyForm = (): EditorForm => ({
  name: "",
  description: "",
  templateType: "GENERAL",
  channel: "EMAIL",
  category: "GENERAL",
  subject: "",
  body: "",
  variablesJson: "",
  active: true,
});

const variableChips = ["{{patientName}}", "{{doctorName}}", "{{appointmentDate}}", "{{clinicName}}", "{{billAmount}}", "{{webinarLink}}", "{{leadName}}"];

export default function TemplatesPage() {
  const auth = useAuth();
  const [rows, setRows] = React.useState<AdminTemplate[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [search, setSearch] = React.useState("");
  const [typeFilter, setTypeFilter] = React.useState("");
  const [channelFilter, setChannelFilter] = React.useState("");
  const [categoryFilter, setCategoryFilter] = React.useState("");
  const [activeFilter, setActiveFilter] = React.useState("");
  const [editorOpen, setEditorOpen] = React.useState(false);
  const [editing, setEditing] = React.useState<AdminTemplate | null>(null);
  const [form, setForm] = React.useState<EditorForm>(emptyForm());
  const [saving, setSaving] = React.useState(false);
  const [previewOpen, setPreviewOpen] = React.useState(false);
  const [previewVarsText, setPreviewVarsText] = React.useState('{"patientName":"John Doe","appointmentDate":"2026-05-30","clinicName":"Sunrise Clinic"}');
  const [previewSubject, setPreviewSubject] = React.useState("");
  const [previewBody, setPreviewBody] = React.useState("");
  const [previewTemplateId, setPreviewTemplateId] = React.useState<string | null>(null);

  const canMutate = auth.rolesUpper.includes("CLINIC_ADMIN") || auth.rolesUpper.includes("PLATFORM_ADMIN") || auth.rolesUpper.includes("PLATFORM_TENANT_SUPPORT");

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !auth.tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await listAdminTemplates(auth.accessToken, auth.tenantId, {
        templateType: typeFilter ? (typeFilter as AdminTemplateType) : undefined,
        channel: channelFilter ? (channelFilter as AdminTemplateChannel) : undefined,
        category: categoryFilter ? (categoryFilter as AdminTemplateCategory) : undefined,
        active: activeFilter === "" ? undefined : activeFilter === "true",
        search: search.trim() || undefined,
      });
      setRows(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load templates");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, auth.tenantId, typeFilter, channelFilter, categoryFilter, activeFilter, search]);

  React.useEffect(() => {
    void load();
  }, [load]);

  const total = rows.length;
  const activeCount = rows.filter((r) => r.active).length;
  const emailCount = rows.filter((r) => r.channel === "EMAIL").length;
  const smsCount = rows.filter((r) => r.channel === "SMS").length;
  const whatsappCount = rows.filter((r) => r.channel === "WHATSAPP").length;
  const systemCount = rows.filter((r) => r.systemTemplate).length;

  function openCreate() {
    setEditing(null);
    setForm(emptyForm());
    setEditorOpen(true);
  }

  function openEdit(row: AdminTemplate) {
    setEditing(row);
    setForm({
      name: row.name,
      description: row.description || "",
      templateType: row.templateType,
      channel: row.channel,
      category: row.category,
      subject: row.subject || "",
      body: row.body || "",
      variablesJson: row.variablesJson || "",
      active: row.active,
    });
    setEditorOpen(true);
  }

  function duplicate(row: AdminTemplate) {
    setEditing(null);
    setForm({
      name: `${row.name} Copy`,
      description: row.description || "",
      templateType: row.templateType,
      channel: row.channel,
      category: row.category,
      subject: row.subject || "",
      body: row.body || "",
      variablesJson: row.variablesJson || "",
      active: false,
    });
    setEditorOpen(true);
  }

  async function save() {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    try {
      const payload = {
        name: form.name.trim(),
        description: form.description.trim() || null,
        templateType: form.templateType,
        channel: form.channel,
        category: form.category,
        subject: form.subject.trim() || null,
        body: form.body,
        variablesJson: form.variablesJson.trim() || null,
        active: form.active,
      };
      if (editing) {
        await updateAdminTemplate(auth.accessToken, auth.tenantId, editing.id, payload);
        setSuccess("Template updated");
      } else {
        await createAdminTemplate(auth.accessToken, auth.tenantId, payload);
        setSuccess("Template created");
      }
      setEditorOpen(false);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to save template");
    } finally {
      setSaving(false);
    }
  }

  async function toggleActive(row: AdminTemplate) {
    if (!auth.accessToken || !auth.tenantId) return;
    try {
      if (row.active) {
        await deactivateAdminTemplate(auth.accessToken, auth.tenantId, row.id);
        setSuccess("Template deactivated");
      } else {
        await activateAdminTemplate(auth.accessToken, auth.tenantId, row.id);
        setSuccess("Template activated");
      }
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to update status");
    }
  }

  async function remove(row: AdminTemplate) {
    if (!auth.accessToken || !auth.tenantId || row.systemTemplate) return;
    if (!window.confirm(`Delete template "${row.name}"?`)) return;
    try {
      await deleteAdminTemplate(auth.accessToken, auth.tenantId, row.id);
      setSuccess("Template deleted");
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to delete template");
    }
  }

  async function openPreview(row: AdminTemplate) {
    setPreviewTemplateId(row.id);
    setPreviewSubject(row.subject || "");
    setPreviewBody(row.body || "");
    setPreviewOpen(true);
  }

  async function runPreview() {
    if (!auth.accessToken || !auth.tenantId || !previewTemplateId) return;
    try {
      const parsed = JSON.parse(previewVarsText || "{}") as Record<string, string>;
      const result = await previewAdminTemplate(auth.accessToken, auth.tenantId, previewTemplateId, parsed);
      setPreviewSubject(result.renderedSubject || "");
      setPreviewBody(result.renderedBody || "");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to preview template");
    }
  }

  return (
    <Stack spacing={2}>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>Templates</Typography>
      {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
      {success && <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert>}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 2 }}><Card><CardContent><Typography variant="caption">Total Templates</Typography><Typography variant="h6">{total}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, md: 2 }}><Card><CardContent><Typography variant="caption">Active Templates</Typography><Typography variant="h6">{activeCount}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, md: 2 }}><Card><CardContent><Typography variant="caption">Email Templates</Typography><Typography variant="h6">{emailCount}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, md: 2 }}><Card><CardContent><Typography variant="caption">SMS Templates</Typography><Typography variant="h6">{smsCount}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, md: 2 }}><Card><CardContent><Typography variant="caption">WhatsApp Templates</Typography><Typography variant="h6">{whatsappCount}</Typography></CardContent></Card></Grid>
        <Grid size={{ xs: 12, md: 2 }}><Card><CardContent><Typography variant="caption">System Templates</Typography><Typography variant="h6">{systemCount}</Typography></CardContent></Card></Grid>
      </Grid>

      <Card>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 2 }}><TextField label="Search" value={search} onChange={(e) => setSearch(e.target.value)} fullWidth /></Grid>
            <Grid size={{ xs: 12, md: 2 }}><FormControl fullWidth><InputLabel>Type</InputLabel><Select value={typeFilter} label="Type" onChange={(e) => setTypeFilter(e.target.value)}><MenuItem value="">All</MenuItem>{TEMPLATE_TYPES.map((v) => <MenuItem key={v} value={v}>{v}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 2 }}><FormControl fullWidth><InputLabel>Channel</InputLabel><Select value={channelFilter} label="Channel" onChange={(e) => setChannelFilter(e.target.value)}><MenuItem value="">All</MenuItem>{CHANNELS.map((v) => <MenuItem key={v} value={v}>{v}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 2 }}><FormControl fullWidth><InputLabel>Category</InputLabel><Select value={categoryFilter} label="Category" onChange={(e) => setCategoryFilter(e.target.value)}><MenuItem value="">All</MenuItem>{CATEGORIES.map((v) => <MenuItem key={v} value={v}>{v}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 2 }}><FormControl fullWidth><InputLabel>Active</InputLabel><Select value={activeFilter} label="Active" onChange={(e) => setActiveFilter(e.target.value)}><MenuItem value="">All</MenuItem><MenuItem value="true">Active</MenuItem><MenuItem value="false">Inactive</MenuItem></Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              <Stack direction="row" spacing={1}>
                <Button variant="contained" onClick={() => void load()}>Apply</Button>
                {canMutate && <Button variant="outlined" onClick={openCreate}>Create</Button>}
              </Stack>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      <Card>
        <CardContent sx={{ p: 0 }}>
          {loading ? <Box sx={{ p: 3 }}><Typography color="text.secondary">Loading templates…</Typography></Box> : rows.length === 0 ? <Box sx={{ p: 3 }}><Typography color="text.secondary">No templates found.</Typography></Box> : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Channel</TableCell>
                  <TableCell>Category</TableCell>
                  <TableCell>Active</TableCell>
                  <TableCell>System</TableCell>
                  <TableCell>Updated At</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell>{row.name}</TableCell>
                    <TableCell>{row.templateType}</TableCell>
                    <TableCell>{row.channel}</TableCell>
                    <TableCell>{row.category}</TableCell>
                    <TableCell><Chip size="small" label={row.active ? "Active" : "Inactive"} color={row.active ? "success" : "default"} /></TableCell>
                    <TableCell>{row.systemTemplate ? <Chip size="small" label="System" variant="outlined" /> : "No"}</TableCell>
                    <TableCell>{new Date(row.updatedAt).toLocaleString()}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} justifyContent="flex-end">
                        <Button size="small" onClick={() => void openPreview(row)}>Preview</Button>
                        {canMutate && <Button size="small" onClick={() => openEdit(row)}>Edit</Button>}
                        {canMutate && <Button size="small" onClick={() => duplicate(row)}>Duplicate</Button>}
                        {canMutate && <Button size="small" onClick={() => void toggleActive(row)}>{row.active ? "Deactivate" : "Activate"}</Button>}
                        {canMutate && !row.systemTemplate && <Button size="small" color="error" onClick={() => void remove(row)}>Delete</Button>}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={editorOpen} onClose={() => !saving && setEditorOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editing ? "Edit Template" : "Create Template"}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Name" value={form.name} onChange={(e) => setForm((v) => ({ ...v, name: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Description" value={form.description} onChange={(e) => setForm((v) => ({ ...v, description: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><FormControl fullWidth><InputLabel>Type</InputLabel><Select value={form.templateType} label="Type" onChange={(e) => setForm((v) => ({ ...v, templateType: e.target.value as AdminTemplateType }))}>{TEMPLATE_TYPES.map((v) => <MenuItem key={v} value={v}>{v}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 4 }}><FormControl fullWidth><InputLabel>Channel</InputLabel><Select value={form.channel} label="Channel" onChange={(e) => setForm((v) => ({ ...v, channel: e.target.value as AdminTemplateChannel }))}>{CHANNELS.map((v) => <MenuItem key={v} value={v}>{v}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12, md: 4 }}><FormControl fullWidth><InputLabel>Category</InputLabel><Select value={form.category} label="Category" onChange={(e) => setForm((v) => ({ ...v, category: e.target.value as AdminTemplateCategory }))}>{CATEGORIES.map((v) => <MenuItem key={v} value={v}>{v}</MenuItem>)}</Select></FormControl></Grid>
            <Grid size={{ xs: 12 }}><TextField fullWidth label="Subject" value={form.subject} onChange={(e) => setForm((v) => ({ ...v, subject: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12 }}><TextField fullWidth multiline minRows={6} label="Body" value={form.body} onChange={(e) => setForm((v) => ({ ...v, body: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12 }}><TextField fullWidth multiline minRows={3} label="Variables JSON (optional)" value={form.variablesJson} onChange={(e) => setForm((v) => ({ ...v, variablesJson: e.target.value }))} /></Grid>
            <Grid size={{ xs: 12 }}><Stack direction="row" spacing={1} flexWrap="wrap">{variableChips.map((token) => <Chip key={token} label={token} onClick={() => setForm((v) => ({ ...v, body: `${v.body}${v.body ? " " : ""}${token}` }))} />)}</Stack></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditorOpen(false)} disabled={saving}>Cancel</Button>
          <Button variant="contained" onClick={() => void save()} disabled={saving || !form.name.trim() || !form.body.trim()}>Save</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={previewOpen} onClose={() => setPreviewOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Template Preview</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 0.5 }}>
            <TextField label="Sample Variables JSON" multiline minRows={4} fullWidth value={previewVarsText} onChange={(e) => setPreviewVarsText(e.target.value)} />
            <Button variant="outlined" onClick={() => void runPreview()} disabled={!previewTemplateId}>Render Preview</Button>
            <TextField label="Rendered Subject" value={previewSubject} fullWidth InputProps={{ readOnly: true }} />
            <TextField label="Rendered Body" value={previewBody} multiline minRows={6} fullWidth InputProps={{ readOnly: true }} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPreviewOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
