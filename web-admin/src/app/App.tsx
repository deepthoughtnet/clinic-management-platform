import * as React from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { Box, Button, Paper, Typography } from "@mui/material";

import AppShell from "../layout/AppShell";
import { AuthContext } from "../auth/AuthContext";
import { useAuth } from "../auth/useAuth";
import DashboardPage from "../pages/DashboardPage";
import ClinicProfilePage from "../pages/settings/ClinicProfilePage";
import UsersRolesPage from "../pages/settings/UsersRolesPage";
import DoctorDetailPage from "../pages/doctors/DoctorDetailPage";
import DoctorAvailabilityPage from "../pages/doctors/DoctorAvailabilityPage";
import PatientsPage from "../pages/patients/PatientsPage";
import PatientFormPage from "../pages/patients/PatientFormPage";
import PatientDetailPage from "../pages/patients/PatientDetailPage";
import AppointmentsPage from "../pages/appointments/AppointmentsPage";
import DayBoardPage from "../pages/appointments/DayBoardPage";
import QueuePage from "../pages/appointments/QueuePage";
import ConsultationsPage from "../pages/consultations/ConsultationsPage";
import ConsultationWorkspacePage from "../pages/consultations/ConsultationWorkspacePage";
import PrescriptionsPage from "../pages/prescriptions/PrescriptionsPage";
import BillsPage from "../pages/billing/BillsPage";
import NotificationsPage from "../pages/notifications/NotificationsPage";
import InventoryPage from "../pages/inventory/InventoryPage";
import ReportsPage from "../pages/reports/ReportsPage";
import VaccinationsPage from "../pages/vaccinations/VaccinationsPage";
import PlaceholderPage from "../pages/PlaceholderPage";
import TenantsPage from "../pages/platform/TenantsPage";
import TenantDetailPage from "../pages/platform/TenantDetailPage";
import PlansModulesPage from "../pages/platform/PlansModulesPage";

function RequireAuth({ children }: { children: React.ReactNode }) {
  const auth = React.useContext(AuthContext);
  const location = useLocation();

  if (!auth || !auth.initialized) {
    return <Box sx={{ p: 3 }}>Loading authentication…</Box>;
  }

  if (auth.initError) {
    return (
      <Box sx={{ p: 3, maxWidth: 720 }}>
        <Paper elevation={0} sx={{ p: 3, border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
          <Typography variant="h6" sx={{ fontWeight: 900, mb: 1.5 }}>
            Authentication Error
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {auth.initError}
          </Typography>
          <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
            <Button variant="contained" onClick={() => auth.retryInit()}>
              Retry
            </Button>
            <Button variant="outlined" onClick={() => auth.clearSession()}>
              Clear Session
            </Button>
            <Button variant="text" onClick={() => auth.logout()}>
              Logout
            </Button>
          </Box>
        </Paper>
      </Box>
    );
  }

  if (!auth.authenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <>{children}</>;
}

function LoginPage() {
  const auth = React.useContext(AuthContext);

  if (auth?.initialized && auth.authenticated) {
    return <Navigate to="/" replace />;
  }

  return (
    <Box
      sx={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        bgcolor: "background.default",
        p: 2,
      }}
    >
      <Paper
        elevation={0}
        sx={{
          width: "100%",
          maxWidth: 460,
          p: 4,
          borderRadius: 4,
          border: "1px solid",
          borderColor: "divider",
          textAlign: "center",
        }}
      >
        <Typography variant="h5" sx={{ fontWeight: 900, mb: 1 }}>
          Clinic Management
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Sign in to manage patients, appointments, prescriptions, billing, and
          clinic operations.
        </Typography>
        <Button size="large" variant="contained" onClick={() => auth?.login(true)} fullWidth>
          Sign in
        </Button>
      </Paper>
    </Box>
  );
}

function AuthedApp() {
  const auth = useAuth();

  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/patients" element={<PatientsPage />} />
        <Route path="/patients/new" element={<PatientFormPage mode="create" />} />
        <Route path="/patients/:id" element={<PatientDetailPage />} />
        <Route path="/patients/:id/edit" element={<PatientFormPage mode="edit" />} />
        <Route path="/appointments" element={<AppointmentsPage />} />
        <Route path="/appointments/day-board" element={<DayBoardPage />} />
        <Route path="/queue" element={<QueuePage />} />
        <Route path="/consultations" element={<ConsultationsPage />} />
        <Route path="/consultations/:id" element={<ConsultationWorkspacePage />} />
        <Route path="/prescriptions" element={<PrescriptionsPage />} />
        <Route path="/billing" element={<BillsPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/vaccinations" element={<VaccinationsPage />} />
        <Route path="/inventory" element={<InventoryPage />} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/platform/tenants" element={<TenantsPage />} />
        <Route path="/platform/tenants/:tenantId" element={<TenantDetailPage />} />
        <Route path="/platform/plans" element={<PlansModulesPage />} />
        <Route
          path="/platform/users"
          element={<PlaceholderPage title="Users / Admins" description="Platform user administration can be enabled when backend APIs are exposed." />}
        />
        <Route path="/settings" element={<Navigate to="/settings/clinic-profile" replace />} />
        <Route path="/settings/clinic-profile" element={<ClinicProfilePage />} />
        <Route path="/settings/users-roles" element={<UsersRolesPage />} />
        <Route path="/doctors/availability" element={<DoctorAvailabilityPage />} />
        <Route path="/doctors/:id" element={<DoctorDetailPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <Box sx={{ display: "none" }}>{auth.username}</Box>
    </AppShell>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/*"
          element={
            <RequireAuth>
              <AuthedApp />
            </RequireAuth>
          }
        />
      </Routes>
    </BrowserRouter>
  );
}
