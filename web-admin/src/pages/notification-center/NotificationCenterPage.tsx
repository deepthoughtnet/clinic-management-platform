import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Drawer,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import RefreshRoundedIcon from "@mui/icons-material/RefreshRounded";
import MarkEmailUnreadRoundedIcon from "@mui/icons-material/MarkEmailUnreadRounded";
import DoneAllRoundedIcon from "@mui/icons-material/DoneAllRounded";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";
import MarkUnreadChatAltRoundedIcon from "@mui/icons-material/MarkUnreadChatAltRounded";

import { useAuth } from "../../auth/useAuth";
import {
  getNotificationCenterInbox,
  getNotificationCenterUnreadCount,
  markNotificationCenterRead,
  markNotificationCenterReadAll,
  markNotificationCenterUnread,
  type NotificationCenterItem,
  type NotificationCenterPage as NotificationCenterPageResponse,
  type NotificationCenterPriority,
  type NotificationCenterCategory,
} from "../../api/clinicApi";
import { useNavigate } from "react-router-dom";

type ReadState = "ALL" | "READ" | "UNREAD";

function humanize(value: string | null | undefined) {
  if (!value) return "Unknown";
  return value
    .replace(/_/g, " ")
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "Unknown";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Unknown";
  return new Intl.DateTimeFormat(undefined, {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

function chipColor(category: string | null | undefined): "default" | "primary" | "secondary" | "success" | "warning" | "error" {
  switch ((category || "").toUpperCase()) {
    case "APPOINTMENT":
      return "primary";
    case "BILLING":
      return "warning";
    case "LAB":
      return "secondary";
    case "PHARMACY":
      return "success";
    case "SYSTEM":
    case "PLATFORM":
      return "error";
    default:
      return "default";
  }
}

export default function NotificationCenterPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const tenantId = auth.selectedTenant?.id || auth.tenantId;
  const [loading, setLoading] = React.useState(false);
  const [refreshing, setRefreshing] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [unreadCount, setUnreadCount] = React.useState(0);
  const [items, setItems] = React.useState<NotificationCenterPageResponse | null>(null);
  const [selected, setSelected] = React.useState<NotificationCenterItem | null>(null);
  const [readState, setReadState] = React.useState<ReadState>("ALL");
  const [category, setCategory] = React.useState<string>("");
  const [priority, setPriority] = React.useState<string>("");
  const [search, setSearch] = React.useState("");

  const canRead = Boolean(auth.accessToken && tenantId && auth.permissions.includes("notification.center.read"));

  const load = React.useCallback(async () => {
    if (!canRead || !auth.accessToken || !tenantId) {
      setItems({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
      setUnreadCount(0);
      return;
    }
    setRefreshing(true);
    try {
      const [count, page] = await Promise.all([
        getNotificationCenterUnreadCount(auth.accessToken, tenantId),
        getNotificationCenterInbox(auth.accessToken, tenantId, {
          readState,
          category: (category || null) as NotificationCenterCategory | null,
          priority: (priority || null) as NotificationCenterPriority | null,
          search: search || null,
          page: 0,
          size: 50,
        }),
      ]);
      setUnreadCount(count.count);
      setItems(page);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load notifications");
    } finally {
      setRefreshing(false);
      setLoading(false);
    }
  }, [auth.accessToken, canRead, category, priority, readState, search, tenantId]);

  React.useEffect(() => {
    setLoading(true);
    void load();
    const timer = window.setInterval(() => {
      void load();
    }, 60000);
    return () => window.clearInterval(timer);
  }, [load]);

  const markAllRead = async () => {
    if (!auth.accessToken || !tenantId) return;
    try {
      await markNotificationCenterReadAll(auth.accessToken, tenantId);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to mark all read");
    }
  };

  const onToggleRead = async (item: NotificationCenterItem) => {
    if (!auth.accessToken || !tenantId) return;
    try {
      const next = item.read ? await markNotificationCenterUnread(auth.accessToken, tenantId, item.id) : await markNotificationCenterRead(auth.accessToken, tenantId, item.id);
      setSelected(next);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update notification");
    }
  };

  if (!canRead) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="info">Notification Center is not available for the current user.</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <Stack spacing={2.5}>
        <Paper sx={{ p: 2.5, borderRadius: 3 }} elevation={0}>
          <Stack spacing={1.5}>
            <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5} alignItems={{ xs: "flex-start", md: "center" }}>
              <Box>
                <Typography variant="h4" sx={{ fontWeight: 900 }}>
                  Notification Center
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Staff inbox for tenant-scoped notifications and actionable deep links.
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip color={unreadCount > 0 ? "error" : "default"} label={`${unreadCount} unread`} />
                <Button variant="outlined" startIcon={<RefreshRoundedIcon />} onClick={() => void load()} disabled={refreshing}>
                  Refresh
                </Button>
                <Button variant="outlined" startIcon={<DoneAllRoundedIcon />} onClick={() => void markAllRead()} disabled={unreadCount === 0}>
                  Mark all read
                </Button>
              </Stack>
            </Stack>
            <Stack direction={{ xs: "column", lg: "row" }} spacing={1.25} flexWrap="wrap">
              <TextField
                label="Search"
                size="small"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                sx={{ minWidth: { xs: "100%", md: 260 } }}
              />
              <FormControl size="small" sx={{ minWidth: 160 }}>
                <InputLabel>Read state</InputLabel>
                <Select label="Read state" value={readState} onChange={(event) => setReadState(event.target.value as ReadState)}>
                  <MenuItem value="ALL">All</MenuItem>
                  <MenuItem value="UNREAD">Unread</MenuItem>
                  <MenuItem value="READ">Read</MenuItem>
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 160 }}>
                <InputLabel>Category</InputLabel>
                <Select label="Category" value={category} onChange={(event) => setCategory(event.target.value)}>
                  <MenuItem value="">All</MenuItem>
                  <MenuItem value="APPOINTMENT">Appointment</MenuItem>
                  <MenuItem value="BILLING">Billing</MenuItem>
                  <MenuItem value="CLINICAL">Clinical</MenuItem>
                  <MenuItem value="LAB">Lab</MenuItem>
                  <MenuItem value="PHARMACY">Pharmacy</MenuItem>
                  <MenuItem value="SYSTEM">System</MenuItem>
                  <MenuItem value="PLATFORM">Platform</MenuItem>
                  <MenuItem value="ENGAGE">Engage</MenuItem>
                  <MenuItem value="AI">AI</MenuItem>
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 160 }}>
                <InputLabel>Priority</InputLabel>
                <Select label="Priority" value={priority} onChange={(event) => setPriority(event.target.value)}>
                  <MenuItem value="">All</MenuItem>
                  <MenuItem value="LOW">Low</MenuItem>
                  <MenuItem value="NORMAL">Normal</MenuItem>
                  <MenuItem value="HIGH">High</MenuItem>
                  <MenuItem value="CRITICAL">Critical</MenuItem>
                </Select>
              </FormControl>
              <Button variant="contained" onClick={() => void load()} disabled={refreshing}>
                Apply
              </Button>
            </Stack>
          </Stack>
        </Paper>

        {error ? <Alert severity="error">{error}</Alert> : null}

        <Paper elevation={0} sx={{ borderRadius: 3, overflow: "hidden" }}>
          {loading && !items ? (
            <Box sx={{ p: 4, display: "grid", placeItems: "center" }}>
              <CircularProgress />
            </Box>
          ) : (
            <TableContainer sx={{ maxHeight: "72vh" }}>
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Status</TableCell>
                    <TableCell>Title</TableCell>
                    <TableCell>Preview</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Priority</TableCell>
                    <TableCell>Business reference</TableCell>
                    <TableCell>Received</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {items?.items.length ? (
                    items.items.map((item) => (
                      <TableRow key={item.id} hover selected={!item.read} sx={{ cursor: "pointer" }} onClick={() => setSelected(item)}>
                        <TableCell>
                          <Chip size="small" color={item.read ? "default" : "primary"} label={item.read ? "Read" : "Unread"} />
                        </TableCell>
                        <TableCell sx={{ fontWeight: 800 }}>{item.title}</TableCell>
                        <TableCell>
                          <Typography
                            variant="body2"
                            sx={{
                              display: "-webkit-box",
                              WebkitLineClamp: 2,
                              WebkitBoxOrient: "vertical",
                              overflow: "hidden",
                              lineHeight: 1.35,
                            }}
                          >
                            {item.preview}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" color={chipColor(item.category)} label={humanize(item.category)} />
                        </TableCell>
                        <TableCell>{humanize(item.priority)}</TableCell>
                        <TableCell>{item.businessReference || "—"}</TableCell>
                        <TableCell>{formatDateTime(item.occurredAt)}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                            <IconButton size="small" onClick={(event) => { event.stopPropagation(); setSelected(item); }} aria-label="Open notification details">
                              <OpenInNewRoundedIcon fontSize="small" />
                            </IconButton>
                            <IconButton size="small" onClick={(event) => { event.stopPropagation(); void onToggleRead(item); }} aria-label={item.read ? "Mark unread" : "Mark read"}>
                              {item.read ? <MarkUnreadChatAltRoundedIcon fontSize="small" /> : <MarkEmailUnreadRoundedIcon fontSize="small" />}
                            </IconButton>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={8}>
                        <Box sx={{ py: 6, textAlign: "center" }}>
                          <Typography sx={{ fontWeight: 800 }}>No notifications.</Typography>
                          <Typography variant="body2" color="text.secondary">
                            Your staff inbox is empty for the selected filters.
                          </Typography>
                        </Box>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      </Stack>

      <Drawer
        anchor="right"
        open={Boolean(selected)}
        onClose={() => setSelected(null)}
        PaperProps={{ sx: { width: { xs: "100%", sm: 460 }, maxWidth: "100vw" } }}
      >
        {selected ? (
          <Box sx={{ p: 2.5 }}>
            <Stack spacing={1.5}>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>
                    {selected.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {selected.sourceEventType} · {formatDateTime(selected.occurredAt)}
                  </Typography>
                </Box>
                <Chip size="small" color={selected.read ? "default" : "primary"} label={selected.read ? "Read" : "Unread"} />
              </Stack>

              <Divider />

              <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
                {selected.preview}
              </Typography>

              <Stack spacing={0.75}>
                <Typography variant="body2"><strong>Category:</strong> {humanize(selected.category)}</Typography>
                <Typography variant="body2"><strong>Priority:</strong> {humanize(selected.priority)}</Typography>
                <Typography variant="body2"><strong>Recipient:</strong> {selected.recipientDisplayName || "Unknown"}</Typography>
                <Typography variant="body2"><strong>Business reference:</strong> {selected.businessReference || "—"}</Typography>
                <Typography variant="body2"><strong>Source:</strong> {selected.sourceModule}</Typography>
                <Typography variant="body2"><strong>Matched audience:</strong> {selected.matchedAudience || "—"}</Typography>
                <Typography variant="body2"><strong>Correlation:</strong> {selected.correlationId || "—"}</Typography>
              </Stack>

              <Stack direction="row" spacing={1} flexWrap="wrap">
                {selected.actionRoute ? (
                <Button
                  variant="contained"
                  startIcon={<OpenInNewRoundedIcon />}
                  onClick={() => {
                    const route = selected.actionRoute || "/";
                    setSelected(null);
                    navigate(route);
                  }}
                >
                    {selected.actionLabel || "Open"}
                  </Button>
                ) : null}
                <Button variant="outlined" onClick={() => void onToggleRead(selected)}>
                  {selected.read ? "Mark unread" : "Mark read"}
                </Button>
              </Stack>
            </Stack>
          </Box>
        ) : null}
      </Drawer>
    </Box>
  );
}
