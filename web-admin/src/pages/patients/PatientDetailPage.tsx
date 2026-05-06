import * as React from "react";
import { Link as RouterLink, useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Grid,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import {
  getPatient,
  getPatientVaccinations,
  getPatientNotifications,
  searchBills,
  type PatientDetail,
  type Appointment,
  type Consultation,
  type Bill,
  type PatientVaccination,
  type NotificationHistory,
} from "../../api/clinicApi";

function statusColor(status: Appointment["status"]) {
  switch (status) {
    case "COMPLETED":
      return "success";
    case "IN_CONSULTATION":
      return "info";
    case "WAITING":
    case "BOOKED":
      return "warning";
    case "CANCELLED":
    case "NO_SHOW":
      return "default";
  }
}

function consultationStatusColor(status: Consultation["status"]) {
  switch (status) {
    case "COMPLETED":
      return "success";
    case "DRAFT":
      return "warning";
    case "CANCELLED":
      return "default";
  }
}

export default function PatientDetailPage() {
  const auth = useAuth();
  const params = useParams();
  const navigate = useNavigate();
  const id = params.id || "";
  const [detail, setDetail] = React.useState<PatientDetail | null>(null);
  const [bills, setBills] = React.useState<Bill[]>([]);
  const [vaccinations, setVaccinations] = React.useState<PatientVaccination[]>([]);
  const [notifications, setNotifications] = React.useState<NotificationHistory[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!auth.accessToken || !auth.tenantId || !id) {
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const [value, billRows, vaccinationRows, notificationRows] = await Promise.all([
          getPatient(auth.accessToken, auth.tenantId, id),
          searchBills(auth.accessToken, auth.tenantId, { patientId: id }),
          getPatientVaccinations(auth.accessToken, auth.tenantId, id),
          getPatientNotifications(auth.accessToken, auth.tenantId, id),
        ]);
        if (!cancelled) {
          setDetail(value);
          setBills(billRows);
          setVaccinations(vaccinationRows);
          setNotifications(notificationRows);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load patient");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, id]);

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  if (loading) {
    return (
      <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!detail) {
    return <Alert severity="info">{error || "Patient not found"}</Alert>;
  }

  const patient = detail.patient;

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            {patient.firstName} {patient.lastName}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {patient.patientNumber} • {patient.mobile}
          </Typography>
        </Box>
        <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
          <Button variant="outlined" onClick={() => navigate("/patients")}>Back</Button>
          <Button variant="outlined" onClick={() => navigate(`/patients/${patient.id}/edit`)}>Edit</Button>
          <Button variant="contained" component={RouterLink} to="/appointments">
            Create Appointment
          </Button>
        </Box>
      </Box>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Demographics</Typography>
                <Typography variant="body2">Gender: {patient.gender}</Typography>
                <Typography variant="body2">DOB: {patient.dateOfBirth || "Not set"}</Typography>
                <Typography variant="body2">Age: {patient.ageYears ?? "Not set"}</Typography>
                <Typography variant="body2">Mobile: {patient.mobile}</Typography>
                <Typography variant="body2">Email: {patient.email || "Not set"}</Typography>
                <Typography variant="body2">Address: {[patient.addressLine1, patient.addressLine2, patient.city, patient.state, patient.country, patient.postalCode].filter(Boolean).join(", ") || "Not set"}</Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Stack spacing={1}>
                <Typography variant="h6" sx={{ fontWeight: 800 }}>Medical summary</Typography>
                <Typography variant="body2">Emergency contact: {patient.emergencyContactName || "Not set"}</Typography>
                <Typography variant="body2">Emergency mobile: {patient.emergencyContactMobile || "Not set"}</Typography>
                <Typography variant="body2">Blood group: {patient.bloodGroup || "Not set"}</Typography>
                <Typography variant="body2">Allergies: {patient.allergies || "None recorded"}</Typography>
                <Typography variant="body2">Existing conditions: {patient.existingConditions || "None recorded"}</Typography>
                <Typography variant="body2">Notes: {patient.notes || "None recorded"}</Typography>
                <Chip size="small" label={patient.active ? "Active" : "Inactive"} color={patient.active ? "success" : "default"} />
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Upcoming appointments</Typography>
            {detail.upcomingAppointments.length === 0 ? (
              <Alert severity="info">No upcoming appointments were found for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Doctor</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Reason</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {detail.upcomingAppointments.map((appointment) => (
                    <TableRow key={appointment.id}>
                      <TableCell>{appointment.appointmentDate}{appointment.appointmentTime ? ` ${appointment.appointmentTime}` : ""}</TableCell>
                      <TableCell>{appointment.doctorName || appointment.doctorUserId}</TableCell>
                      <TableCell>{appointment.type}</TableCell>
                      <TableCell><Chip size="small" label={appointment.status} color={statusColor(appointment.status)} /></TableCell>
                      <TableCell>{appointment.reason || "-"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Bills</Typography>
            {bills.length === 0 ? (
              <Alert severity="info">No bills were found for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Bill</TableCell>
                    <TableCell>Date</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Total</TableCell>
                    <TableCell align="right">Paid</TableCell>
                    <TableCell align="right">Due</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {bills.map((bill) => (
                    <TableRow key={bill.id}>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{bill.billNumber}</Typography>
                          <Typography variant="caption" color="text.secondary">{bill.notes || "No notes"}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>{bill.billDate}</TableCell>
                      <TableCell><Chip size="small" label={bill.status} color={bill.status === "PAID" ? "success" : bill.status === "PARTIALLY_PAID" ? "warning" : bill.status === "CANCELLED" ? "default" : "info"} /></TableCell>
                      <TableCell align="right">{bill.totalAmount.toFixed(2)}</TableCell>
                      <TableCell align="right">{bill.paidAmount.toFixed(2)}</TableCell>
                      <TableCell align="right">{bill.dueAmount.toFixed(2)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Vaccinations</Typography>
            {vaccinations.length === 0 ? (
              <Alert severity="info">No vaccinations were recorded for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Vaccine</TableCell>
                    <TableCell>Given</TableCell>
                    <TableCell>Next due</TableCell>
                    <TableCell>Dose</TableCell>
                    <TableCell>Batch</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {vaccinations.map((vaccination) => (
                    <TableRow key={vaccination.id}>
                      <TableCell>{vaccination.vaccineName}</TableCell>
                      <TableCell>{vaccination.givenDate}</TableCell>
                      <TableCell>{vaccination.nextDueDate || "-"}</TableCell>
                      <TableCell>{vaccination.doseNumber ?? "-"}</TableCell>
                      <TableCell>{vaccination.batchNumber || "-"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Recent appointments</Typography>
            {detail.recentAppointments.length === 0 ? (
              <Alert severity="info">No recent appointments were found for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Doctor</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Reason</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {detail.recentAppointments.map((appointment) => (
                    <TableRow key={appointment.id}>
                      <TableCell>{appointment.appointmentDate}{appointment.appointmentTime ? ` ${appointment.appointmentTime}` : ""}</TableCell>
                      <TableCell>{appointment.doctorName || appointment.doctorUserId}</TableCell>
                      <TableCell>{appointment.type}</TableCell>
                      <TableCell><Chip size="small" label={appointment.status} color={statusColor(appointment.status)} /></TableCell>
                      <TableCell>{appointment.reason || "-"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Previous consultations</Typography>
            {detail.previousConsultations.length === 0 ? (
              <Alert severity="info">No previous consultations were found for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Doctor</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Diagnosis</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {detail.previousConsultations.map((consultation) => (
                    <TableRow key={consultation.id}>
                      <TableCell>{new Date(consultation.createdAt).toLocaleString()}</TableCell>
                      <TableCell>{consultation.doctorName || consultation.doctorUserId}</TableCell>
                      <TableCell><Chip size="small" label={consultation.status} color={consultationStatusColor(consultation.status)} /></TableCell>
                      <TableCell>{consultation.diagnosis || "-"}</TableCell>
                      <TableCell align="right">
                        <Button size="small" onClick={() => navigate(`/consultations/${consultation.id}`)}>Open</Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>Notifications</Typography>
            {notifications.length === 0 ? (
              <Alert severity="info">No notification history was found for this patient.</Alert>
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Event</TableCell>
                    <TableCell>Channel</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Recipient</TableCell>
                    <TableCell>Created</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {notifications.map((notification) => (
                    <TableRow key={notification.id}>
                      <TableCell>{notification.eventType}</TableCell>
                      <TableCell>{notification.channel}</TableCell>
                      <TableCell><Chip size="small" label={notification.status} color={notification.status === "SENT" ? "success" : notification.status === "FAILED" ? "error" : notification.status === "PENDING" ? "info" : "default"} /></TableCell>
                      <TableCell>{notification.recipient}</TableCell>
                      <TableCell>{new Date(notification.createdAt).toLocaleString()}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  );
}
