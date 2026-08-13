import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Alert, Box, Button, Chip, Divider, Paper, Stack, TextField, Typography } from "@mui/material";
import AddCircleOutlineIcon from "@mui/icons-material/AddCircleOutline";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import PersonOutlineOutlinedIcon from "@mui/icons-material/PersonOutlineOutlined";
import { ProviderEditorSectionCard } from "../../components/provider-profile-editor/ProviderProfileEditorControls";
import { formatConsultationFee } from "../../components/DiscoveryComponents";
import type { PublicDoctorSummaryResponse } from "../../api/publicCatalog";
import {
  addProviderHospitalDoctor,
  loadProviderHospitalDoctors,
  removeProviderHospitalDoctor,
  searchPublicDoctors,
  type ProviderHospitalDoctorAssociationResponse,
} from "../../api/providerHospitalDoctors";

type HospitalDoctorManagementSectionProps = {
  profileReference: string;
  hospitalDisplayName: string;
  city?: string | null;
};

function renderDoctorSummary(doctor: PublicDoctorSummaryResponse) {
  const speciality = doctor.speciality?.trim() || "Speciality not published";
  const bookingMode = doctor.bookingMode ?? "NOT_AVAILABLE";
  const fee = formatConsultationFee(doctor.consultationFee ?? null);
  return { speciality, bookingMode, fee };
}

export function HospitalDoctorManagementSection({ profileReference, hospitalDisplayName, city }: HospitalDoctorManagementSectionProps) {
  const [associatedDoctors, setAssociatedDoctors] = useState<ProviderHospitalDoctorAssociationResponse[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<PublicDoctorSummaryResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [savingDoctorId, setSavingDoctorId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const associatedIds = useMemo(() => new Set(associatedDoctors.map((item) => item.publicDoctorReference)), [associatedDoctors]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    loadProviderHospitalDoctors(profileReference)
      .then((items) => {
        if (!cancelled) {
          setAssociatedDoctors(items);
        }
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Could not load associated doctors.");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [profileReference]);

  async function runSearch(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    const query = searchQuery.trim();
    if (!query) {
      setSearchResults([]);
      return;
    }
    setSearchLoading(true);
    setError(null);
    try {
      const response = await searchPublicDoctors(query, city);
      setSearchResults(response.items);
    } catch (searchError) {
      setError(searchError instanceof Error ? searchError.message : "Could not search doctors.");
    } finally {
      setSearchLoading(false);
    }
  }

  async function addDoctor(publicDoctorReference: string) {
    setSavingDoctorId(publicDoctorReference);
    setError(null);
    try {
      const items = await addProviderHospitalDoctor(profileReference, publicDoctorReference);
      setAssociatedDoctors(items);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Could not add doctor.");
    } finally {
      setSavingDoctorId(null);
    }
  }

  async function removeDoctor(publicDoctorReference: string) {
    setSavingDoctorId(publicDoctorReference);
    setError(null);
    try {
      const items = await removeProviderHospitalDoctor(profileReference, publicDoctorReference);
      setAssociatedDoctors(items);
    } catch (removeError) {
      setError(removeError instanceof Error ? removeError.message : "Could not remove doctor.");
    } finally {
      setSavingDoctorId(null);
    }
  }

  return (
    <ProviderEditorSectionCard
      title="Doctors / Medical Team"
      description="Search and manage the doctors explicitly associated with this public hospital profile."
    >
      <Stack spacing={2}>
        <form onSubmit={(event) => void runSearch(event)} className="provider-hospital-doctor-search">
          <Stack spacing={1.5}>
            <TextField
              label="Search doctors"
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder={`Search doctors for ${hospitalDisplayName}`}
              helperText="Search uses published public doctor profiles."
              fullWidth
            />
            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Button type="submit" variant="contained" startIcon={<PersonOutlineOutlinedIcon />} disabled={searchLoading}>
                {searchLoading ? "Searching..." : "Search doctors"}
              </Button>
              <Button type="button" variant="outlined" onClick={() => void runSearch()} disabled={searchLoading}>
                Clear results
              </Button>
            </Stack>
          </Stack>
        </form>

        {error ? <Alert severity="warning" variant="outlined">{error}</Alert> : null}

        <Paper variant="outlined" sx={{ p: 2, borderRadius: 3 }}>
          <Stack spacing={1.5}>
            <Stack spacing={0.5}>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Current associations</Typography>
              <Typography variant="body2" color="text.secondary">
                These doctors are published on the hospital profile and can be removed individually.
              </Typography>
            </Stack>
            {loading ? (
              <Typography variant="body2" color="text.secondary">Loading associated doctors…</Typography>
            ) : associatedDoctors.length ? (
              <Stack spacing={1.5}>
                {associatedDoctors.map((item) => (
                  <Paper key={item.publicDoctorReference} variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                    <Stack spacing={1}>
                      <Stack direction="row" justifyContent="space-between" alignItems="start" spacing={1} flexWrap="wrap">
                        <Box>
                          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{item.doctorDisplayName}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {[item.speciality, item.qualification, item.registrationNumber].filter(Boolean).join(" · ") || "Doctor details not fully published"}
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 0.75 }}>
                            <Chip size="small" label={item.associationStatus} />
                            {item.yearsOfExperience != null ? <Chip size="small" variant="outlined" label={`${item.yearsOfExperience}+ years experience`} /> : null}
                          </Stack>
                        </Box>
                        <Button
                          variant="outlined"
                          color="error"
                          startIcon={<DeleteOutlineIcon />}
                          onClick={() => void removeDoctor(item.publicDoctorReference)}
                          disabled={savingDoctorId === item.publicDoctorReference}
                        >
                          {savingDoctorId === item.publicDoctorReference ? "Updating..." : "Remove"}
                        </Button>
                      </Stack>
                    </Stack>
                  </Paper>
                ))}
              </Stack>
            ) : (
              <Alert severity="info" variant="outlined">
                No doctors are associated with this hospital yet.
              </Alert>
            )}
          </Stack>
        </Paper>

        <Divider />

        <Stack spacing={1}>
          <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Search results</Typography>
          {searchResults.length ? (
            <Stack spacing={1.5}>
              {searchResults.map((doctor) => {
                const info = renderDoctorSummary(doctor);
                const alreadyAssociated = associatedIds.has(doctor.publicDoctorId);
                return (
                  <Paper key={doctor.publicDoctorId} variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                    <Stack spacing={1}>
                      <Stack direction="row" justifyContent="space-between" alignItems="start" spacing={1} flexWrap="wrap">
                        <Box>
                          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{doctor.doctorDisplayName}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {[doctor.speciality, doctor.subtitle, doctor.clinicDisplayName].filter(Boolean).join(" · ") || "Published doctor"}
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 0.75 }}>
                            <Chip size="small" variant="outlined" label={info.fee ? `Fee ${info.fee}` : "Fee not published"} />
                            <Chip size="small" variant="outlined" label={info.bookingMode === "ONLINE_BOOKING" ? "Book online" : "Call clinic"} />
                          </Stack>
                        </Box>
                        <Button
                          variant={alreadyAssociated ? "outlined" : "contained"}
                          startIcon={<AddCircleOutlineIcon />}
                          disabled={alreadyAssociated || savingDoctorId === doctor.publicDoctorId}
                          onClick={() => void addDoctor(doctor.publicDoctorId)}
                        >
                          {alreadyAssociated ? "Added" : savingDoctorId === doctor.publicDoctorId ? "Adding..." : "Add doctor"}
                        </Button>
                      </Stack>
                    </Stack>
                  </Paper>
                );
              })}
            </Stack>
          ) : (
            <Alert severity="info" variant="outlined">
              Search published doctors to add them to this hospital profile.
            </Alert>
          )}
        </Stack>
      </Stack>
    </ProviderEditorSectionCard>
  );
}
