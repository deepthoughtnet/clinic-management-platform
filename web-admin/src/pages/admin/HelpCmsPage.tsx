import * as React from "react";
import { Navigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Divider,
  FormControl,
  FormControlLabel,
  Grid,
  MenuItem,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
  Chip,
} from "@mui/material";

import {
  archiveHelpPage,
  createHelpPage,
  getHelpPageAdmin,
  listHelpPages,
  publishHelpPage,
  rollbackHelpPage,
  updateHelpPage,
  type HelpPageRecord,
  type HelpPageSummary,
  type HelpPageUpsertInput,
  type HelpSectionUpsertInput,
} from "../../api/clinicApi";
import { useAuth } from "../../auth/useAuth";

type HelpEditorState = {
  moduleKey: string;
  pageKey: string;
  title: string;
  icon: string;
  status: string;
  active: boolean;
  sections: HelpSectionUpsertInput[];
  version: number;
  availableVersions: number[];
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

function emptyEditorState(): HelpEditorState {
  return {
    moduleKey: "PHARMACY",
    pageKey: "",
    title: "",
    icon: "",
    status: "DRAFT",
    active: true,
    sections: [],
    version: 1,
    availableVersions: [],
    createdBy: null,
    updatedBy: null,
    createdAt: null,
    updatedAt: null,
  };
}

function fromPageRecord(page: HelpPageRecord): HelpEditorState {
  return {
    moduleKey: page.moduleKey,
    pageKey: page.pageKey,
    title: page.title,
    icon: page.icon ?? "",
    status: page.status,
    active: page.active,
    version: page.version,
    availableVersions: page.availableVersions,
    createdBy: page.createdBy,
    updatedBy: page.updatedBy,
    createdAt: page.createdAt,
    updatedAt: page.updatedAt,
    sections: page.sections
      .slice()
      .sort((a, b) => a.displayOrder - b.displayOrder)
      .map((section) => ({
        sectionKey: section.sectionKey,
        sectionType: section.sectionType,
        displayOrder: section.displayOrder,
        collapsible: section.collapsible,
        active: section.active,
        contentJson: section.contentJson ?? "{}",
      })),
  };
}

function toRequest(editor: HelpEditorState): HelpPageUpsertInput {
  return {
    moduleKey: editor.moduleKey.trim(),
    pageKey: editor.pageKey.trim(),
    title: editor.title.trim(),
    icon: editor.icon.trim() || null,
    status: editor.status,
    active: editor.active,
    sections: editor.sections.map((section) => ({
      sectionKey: section.sectionKey.trim(),
      sectionType: section.sectionType.trim(),
      displayOrder: section.displayOrder ?? 0,
      collapsible: section.collapsible,
      active: section.active,
      contentJson: section.contentJson,
    })),
  };
}

export default function HelpCmsPage() {
  const auth = useAuth();
  const [pages, setPages] = React.useState<HelpPageSummary[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [selectedPageKey, setSelectedPageKey] = React.useState<string>("");
  const [editor, setEditor] = React.useState<HelpEditorState>(emptyEditorState());
  const [creatingNew, setCreatingNew] = React.useState(false);
  const [rollbackVersion, setRollbackVersion] = React.useState<number | "">("");

  const isPlatformAdmin = auth.rolesUpper.includes("PLATFORM_ADMIN");
  const canEdit = isPlatformAdmin;

  React.useEffect(() => {
    if (!isPlatformAdmin) return;
    let cancelled = false;
    async function loadPages() {
      if (!auth.accessToken || !auth.tenantId) return;
      setLoading(true);
      setError(null);
      try {
        const response = await listHelpPages(auth.accessToken, auth.tenantId);
        if (cancelled) return;
        setPages(response);
        if (!selectedPageKey && response.length > 0) {
          setSelectedPageKey(response[0].pageKey);
        }
      } catch (e) {
        if (!cancelled) setError((e as Error).message || "Failed to load help pages");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void loadPages();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, isPlatformAdmin, selectedPageKey]);

  React.useEffect(() => {
    if (!selectedPageKey || !auth.accessToken || !auth.tenantId || creatingNew) return;
    let cancelled = false;
    async function loadPage() {
      setLoading(true);
      setError(null);
      try {
        const response = await getHelpPageAdmin(auth.accessToken!, auth.tenantId!, selectedPageKey);
        if (!cancelled) {
          setEditor(fromPageRecord(response));
          setRollbackVersion(response.availableVersions[0] ?? "");
        }
      } catch (e) {
        if (!cancelled) setError((e as Error).message || "Failed to load help page");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void loadPage();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, creatingNew, selectedPageKey]);

  const selectedPage = React.useMemo(() => pages.find((page) => page.pageKey === selectedPageKey) ?? null, [pages, selectedPageKey]);

  if (!isPlatformAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  async function refresh() {
    if (!auth.accessToken || !auth.tenantId) return;
    const response = await listHelpPages(auth.accessToken, auth.tenantId);
    setPages(response);
  }

  function startNewPage() {
    setCreatingNew(true);
    setSelectedPageKey("");
    setEditor(emptyEditorState());
    setRollbackVersion("");
  }

  function updateSection(index: number, updates: Partial<HelpSectionUpsertInput>) {
    setEditor((current) => ({
      ...current,
      sections: current.sections.map((section, sectionIndex) => (sectionIndex === index ? { ...section, ...updates } : section)),
    }));
  }

  async function savePage() {
    if (!auth.accessToken || !auth.tenantId) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const request = toRequest(editor);
      if (!request.moduleKey || !request.pageKey || !request.title) {
        throw new Error("Module key, page key, and title are required");
      }
      const response = creatingNew || !selectedPage ? await createHelpPage(auth.accessToken, auth.tenantId, request) : await updateHelpPage(auth.accessToken, auth.tenantId, request);
      setCreatingNew(false);
      setSelectedPageKey(response.pageKey);
      setEditor(fromPageRecord(response));
      setRollbackVersion(response.availableVersions[0] ?? "");
      setSuccess("Help page saved.");
      await refresh();
    } catch (e) {
      setError((e as Error).message || "Save failed");
    } finally {
      setSaving(false);
    }
  }

  async function onPublish() {
    if (!auth.accessToken || !auth.tenantId || !editor.pageKey) return;
    setSaving(true);
    setError(null);
    try {
      const response = await publishHelpPage(auth.accessToken, auth.tenantId, { pageKey: editor.pageKey, version: editor.version });
      setEditor(fromPageRecord(response));
      setRollbackVersion(response.availableVersions[0] ?? "");
      setSuccess("Help page published.");
      await refresh();
    } catch (e) {
      setError((e as Error).message || "Publish failed");
    } finally {
      setSaving(false);
    }
  }

  async function onArchive() {
    if (!auth.accessToken || !auth.tenantId || !editor.pageKey) return;
    setSaving(true);
    setError(null);
    try {
      const response = await archiveHelpPage(auth.accessToken, auth.tenantId, { pageKey: editor.pageKey, version: editor.version });
      setEditor(fromPageRecord(response));
      setSuccess("Help page archived.");
      await refresh();
    } catch (e) {
      setError((e as Error).message || "Archive failed");
    } finally {
      setSaving(false);
    }
  }

  async function onRollback() {
    if (!auth.accessToken || !auth.tenantId || !editor.pageKey || rollbackVersion === "") return;
    setSaving(true);
    setError(null);
    try {
      const response = await rollbackHelpPage(auth.accessToken, auth.tenantId, { pageKey: editor.pageKey, version: Number(rollbackVersion) });
      setEditor(fromPageRecord(response));
      setSuccess("Help page rolled back.");
      await refresh();
    } catch (e) {
      setError((e as Error).message || "Rollback failed");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Stack spacing={2.5}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", alignItems: "flex-start" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Help CMS
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Platform-managed, versioned help pages with multilingual-ready structured sections.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={startNewPage} disabled={saving}>New page</Button>
          <Button variant="outlined" onClick={() => void refresh()} disabled={saving}>Refresh</Button>
        </Stack>
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {success ? <Alert severity="success">{success}</Alert> : null}
      {loading ? <Alert severity="info">Loading help CMS…</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="subtitle1" sx={{ fontWeight: 800, mb: 1 }}>
                Help pages
              </Typography>
              <Stack spacing={1}>
                {pages.map((page) => (
                  <Button
                    key={page.pageKey}
                    variant={page.pageKey === selectedPageKey && !creatingNew ? "contained" : "outlined"}
                    onClick={() => {
                      setCreatingNew(false);
                      setSelectedPageKey(page.pageKey);
                    }}
                    sx={{ justifyContent: "space-between" }}
                  >
                    <span>{page.title}</span>
                  </Button>
                ))}
                {pages.length === 0 ? <Typography variant="body2" color="text.secondary">No pages found.</Typography> : null}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 8 }}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={1}>
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>
                    {creatingNew ? "New help page" : selectedPage?.title || editor.title || "Help page"}
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Button variant="outlined" onClick={() => void savePage()} disabled={saving || !canEdit}>
                      Save
                    </Button>
                    <Button variant="outlined" onClick={() => void onPublish()} disabled={saving || !canEdit || !editor.pageKey}>
                      Publish
                    </Button>
                    <Button variant="outlined" onClick={() => void onArchive()} disabled={saving || !canEdit || !editor.pageKey}>
                      Archive
                    </Button>
                    <FormControl size="small" sx={{ minWidth: 120 }}>
                      <Select
                        value={rollbackVersion}
                        onChange={(e) => {
                          const next = String(e.target.value);
                          setRollbackVersion(next === "" ? "" : Number(next));
                        }}
                        displayEmpty
                        disabled={!editor.availableVersions.length}
                      >
                        <MenuItem value="">Rollback</MenuItem>
                        {editor.availableVersions.map((version) => (
                          <MenuItem key={version} value={version}>
                            v{version}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    <Button variant="outlined" onClick={() => void onRollback()} disabled={saving || !canEdit || rollbackVersion === "" || !editor.pageKey}>
                      Roll back
                    </Button>
                  </Stack>
                </Stack>

                <Grid container spacing={1.5}>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField fullWidth label="Module key" value={editor.moduleKey} onChange={(e) => setEditor((current) => ({ ...current, moduleKey: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 3 }}>
                    <TextField fullWidth label="Page key" value={editor.pageKey} onChange={(e) => setEditor((current) => ({ ...current, pageKey: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField fullWidth label="Title" value={editor.title} onChange={(e) => setEditor((current) => ({ ...current, title: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 2 }}>
                    <TextField fullWidth label="Icon" value={editor.icon} onChange={(e) => setEditor((current) => ({ ...current, icon: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField fullWidth label="Status" value={editor.status} onChange={(e) => setEditor((current) => ({ ...current, status: e.target.value }))} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <TextField fullWidth type="number" label="Version" value={editor.version} onChange={(e) => setEditor((current) => ({ ...current, version: Number(e.target.value || "1") }))} />
                  </Grid>
                  <Grid size={{ xs: 12, md: 4 }}>
                    <FormControlLabel
                      control={<Switch checked={editor.active} onChange={(e) => setEditor((current) => ({ ...current, active: e.target.checked }))} />}
                      label="Active"
                    />
                  </Grid>
                </Grid>

                <Card variant="outlined" sx={{ bgcolor: "background.default" }}>
                  <CardContent sx={{ py: 1.5 }}>
                    <Stack spacing={1}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                        Version history
                      </Typography>
                      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                        <Chip size="small" label={`Current v${editor.version}`} color="primary" variant="outlined" />
                        {editor.availableVersions.map((version) => <Chip key={version} size="small" label={`v${version}`} variant="outlined" />)}
                      </Stack>
                      <Typography variant="body2" color="text.secondary">
                        Updated by: {editor.updatedBy || "Unknown"} • Updated at: {editor.updatedAt ? new Date(editor.updatedAt).toLocaleString() : "Unknown"}
                      </Typography>
                    </Stack>
                  </CardContent>
                </Card>

                <Divider />

                <Stack spacing={1.5}>
                  {editor.sections.map((section, index) => (
                    <Card key={`${section.sectionKey}-${index}`} variant="outlined">
                      <CardContent>
                        <Stack spacing={1.25}>
                          <Stack direction="row" justifyContent="space-between" alignItems="center" gap={1} flexWrap="wrap">
                            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                              Section {index + 1}
                            </Typography>
                            <FormControlLabel
                              control={<Switch checked={section.active} onChange={(e) => updateSection(index, { active: e.target.checked })} />}
                              label="Active"
                            />
                          </Stack>
                          <Grid container spacing={1.25}>
                            <Grid size={{ xs: 12, md: 4 }}>
                              <TextField fullWidth label="Section key" value={section.sectionKey} onChange={(e) => updateSection(index, { sectionKey: e.target.value })} />
                            </Grid>
                            <Grid size={{ xs: 12, md: 4 }}>
                              <TextField fullWidth label="Section type" value={section.sectionType} onChange={(e) => updateSection(index, { sectionType: e.target.value })} />
                            </Grid>
                            <Grid size={{ xs: 12, md: 2 }}>
                              <TextField fullWidth type="number" label="Order" value={section.displayOrder} onChange={(e) => updateSection(index, { displayOrder: Number(e.target.value || "0") })} />
                            </Grid>
                            <Grid size={{ xs: 12, md: 2 }}>
                              <FormControlLabel
                                control={<Switch checked={section.collapsible} onChange={(e) => updateSection(index, { collapsible: e.target.checked })} />}
                                label="Collapsible"
                              />
                            </Grid>
                          </Grid>
                          <TextField
                            fullWidth
                            multiline
                            minRows={5}
                            label="Content JSON"
                            value={section.contentJson}
                            onChange={(e) => updateSection(index, { contentJson: e.target.value })}
                            helperText="Structured JSON content for this section."
                          />
                        </Stack>
                      </CardContent>
                    </Card>
                  ))}
                </Stack>

                <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
                  <Button
                    variant="outlined"
                    onClick={() => setEditor((current) => ({
                      ...current,
                      sections: [
                        ...current.sections,
                        {
                          sectionKey: `SECTION_${current.sections.length + 1}`,
                          sectionType: "DESCRIPTION",
                          displayOrder: current.sections.length + 1,
                          collapsible: true,
                          active: true,
                          contentJson: "{}",
                        },
                      ],
                    }))}
                    disabled={!canEdit}
                  >
                    Add section
                  </Button>
                  <Button variant="contained" onClick={() => void savePage()} disabled={saving || !canEdit}>
                    Save changes
                  </Button>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
