import * as React from "react";
import { Alert, Button, Card, CardContent, Checkbox, FormControlLabel, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import {
  createPlatformCommunicationChannel,
  getPlatformCommunicationChannels,
  setPlatformCommunicationChannelEnabled,
  updatePlatformCommunicationChannel,
  type CommunicationChannel,
  type CommunicationChannelInput,
} from "../../api/clinicApi";

type Props = { tenantId: string };
type Draft = CommunicationChannelInput;

const emptyDraft = (): Draft => ({
  clinicId: null, locationId: null, channelType: "VOICE", provider: "DOTVOICE", address: "",
  displayName: null, trunk: null, providerConfigRef: null, enabled: true, inboundEnabled: true,
  outboundEnabled: true, defaultOutbound: false, priority: 0,
});

export default function CommunicationChannelsPanel({ tenantId }: Props) {
  const auth = useAuth();
  const [rows, setRows] = React.useState<CommunicationChannel[]>([]);
  const [draft, setDraft] = React.useState<Draft>(emptyDraft);
  const [editingId, setEditingId] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [message, setMessage] = React.useState<string | null>(null);

  const resetToCreate = React.useCallback(() => {
    setEditingId(null);
    setDraft(emptyDraft());
  }, []);

  const load = React.useCallback(async () => {
    if (!auth.accessToken) return;
    resetToCreate();
    setLoading(true); setError(null);
    try { setRows(await getPlatformCommunicationChannels(auth.accessToken, tenantId)); }
    catch (err) { setError(err instanceof Error ? err.message : "Failed to load communication channels"); }
    finally { setLoading(false); }
  }, [auth.accessToken, resetToCreate, tenantId]);
  React.useEffect(() => { void load(); }, [load]);

  const set = <K extends keyof Draft>(key: K, value: Draft[K]) => setDraft((current) => ({ ...current, [key]: value }));
  const save = async () => {
    if (!auth.accessToken || !draft.address.trim()) { setError("A DID/phone address is required."); return; }
    setError(null);
    try {
      if (editingId) await updatePlatformCommunicationChannel(auth.accessToken, tenantId, editingId, draft);
      else await createPlatformCommunicationChannel(auth.accessToken, tenantId, draft);
      setMessage(editingId ? "Communication channel updated." : "Communication channel added.");
      resetToCreate(); await load();
    } catch (err) { setError(err instanceof Error ? err.message : "Failed to save communication channel"); }
  };
  const edit = (row: CommunicationChannel) => {
    setEditingId(row.id);
    setDraft({ clinicId: row.clinicId, locationId: row.locationId, channelType: row.channelType, provider: row.provider, address: row.address, displayName: row.displayName, trunk: row.trunk, providerConfigRef: row.providerConfigRef, enabled: row.enabled, inboundEnabled: row.inboundEnabled, outboundEnabled: row.outboundEnabled, defaultOutbound: row.defaultOutbound, priority: row.priority });
  };

  return <Card variant="outlined"><CardContent><Stack spacing={1.5}>
    <Typography variant="h6">Communication Channels</Typography>
    <Typography variant="body2" color="text.secondary">Platform Admin configuration for tenant-scoped voice numbers. Provider secrets are configured outside this screen.</Typography>
    {error ? <Alert severity="error" onClose={() => setError(null)}>{error}</Alert> : null}
    {message ? <Alert severity="success" onClose={() => setMessage(null)}>{message}</Alert> : null}
    <Stack direction={{ xs: "column", md: "row" }} spacing={1} flexWrap="wrap">
      <TextField size="small" label="DID / phone" value={draft.address} onChange={(e) => set("address", e.target.value)} />
      <TextField size="small" label="Provider" value={draft.provider} onChange={(e) => set("provider", e.target.value)} />
      <TextField size="small" label="Trunk" value={draft.trunk || ""} onChange={(e) => set("trunk", e.target.value || null)} />
      <TextField size="small" label="Display name" value={draft.displayName || ""} onChange={(e) => set("displayName", e.target.value || null)} />
      <TextField size="small" label="Location ID (optional)" value={draft.locationId || ""} onChange={(e) => set("locationId", e.target.value || null)} />
      <TextField size="small" type="number" label="Priority" value={draft.priority ?? ""} onChange={(e) => set("priority", e.target.value === "" ? null : Number(e.target.value))} />
    </Stack>
    <Stack direction={{ xs: "column", md: "row" }}>
      <FormControlLabel control={<Checkbox checked={draft.inboundEnabled} onChange={(e) => set("inboundEnabled", e.target.checked)} />} label="Inbound" />
      <FormControlLabel control={<Checkbox checked={draft.outboundEnabled} onChange={(e) => set("outboundEnabled", e.target.checked)} />} label="Outbound" />
      <FormControlLabel control={<Checkbox checked={draft.defaultOutbound} onChange={(e) => set("defaultOutbound", e.target.checked)} />} label="Default outbound" />
      <FormControlLabel control={<Checkbox checked={draft.enabled} onChange={(e) => set("enabled", e.target.checked)} />} label="Enabled" />
    </Stack>
    <Stack direction="row" spacing={1}><Button variant="contained" onClick={() => void save()}>{editingId ? "Save changes" : "Add channel"}</Button>{editingId ? <Button onClick={resetToCreate}>Cancel</Button> : null}</Stack>
    {loading ? <Typography color="text.secondary">Loading channels…</Typography> : rows.length === 0 ? <Typography color="text.secondary">No persisted channels. Existing process-level DotVoice configuration remains available to the communication test.</Typography> : <Table size="small"><TableHead><TableRow><TableCell>Number</TableCell><TableCell>Scope</TableCell><TableCell>Provider</TableCell><TableCell>Status</TableCell><TableCell>Directions</TableCell><TableCell>Actions</TableCell></TableRow></TableHead><TableBody>{rows.map((row) => <TableRow key={row.id}><TableCell>{row.normalizedAddress}<br /><Typography variant="caption" color="text.secondary">Entered as {row.address}</Typography></TableCell><TableCell>{row.locationId ? `Location ${row.locationId}` : row.clinicId ? "Clinic" : "Tenant"}</TableCell><TableCell>{row.provider}</TableCell><TableCell>{row.status}{row.defaultOutbound ? " · default outbound" : ""}</TableCell><TableCell>{[row.inboundEnabled ? "in" : "", row.outboundEnabled ? "out" : ""].filter(Boolean).join(" / ") || "—"}</TableCell><TableCell><Stack direction="row"><Button size="small" onClick={() => edit(row)}>Edit</Button><Button size="small" onClick={async () => { if (!auth.accessToken) return; try { await setPlatformCommunicationChannelEnabled(auth.accessToken, tenantId, row.id, !row.enabled); resetToCreate(); await load(); } catch (err) { setError(err instanceof Error ? err.message : "Failed to update channel status"); } }}>{row.enabled ? "Disable" : "Enable"}</Button></Stack></TableCell></TableRow>)}</TableBody></Table>}
  </Stack></CardContent></Card>;
}
