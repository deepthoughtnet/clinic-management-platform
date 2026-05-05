import * as React from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { Box, Button, Paper, Typography } from "@mui/material";

import AppShell from "../layout/AppShell";
import { AuthContext } from "../auth/AuthContext";
import { useAuth } from "../auth/useAuth";
import DashboardPage from "../pages/DashboardPage";
import PlaceholderPage from "../pages/PlaceholderPage";

function RequireAuth({ children }: { children: React.ReactNode }) {
  const auth = React.useContext(AuthContext);
  const location = useLocation();

  if (!auth || !auth.initialized) {
    return <Box sx={{ p: 3 }}>Loading authentication…</Box>;
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
        <Route path="/" element={<DashboardPage />} />
        <Route path="/patients" element={<PlaceholderPage title="Patients" description="Patient registration, profiles, and clinical history will live here." />} />
        <Route path="/appointments" element={<PlaceholderPage title="Appointments" description="Scheduling, waiting lists, and clinician calendars will live here." />} />
        <Route path="/consultations" element={<PlaceholderPage title="Consultations" description="Encounter notes, assessments, and visit summaries will live here." />} />
        <Route path="/prescriptions" element={<PlaceholderPage title="Prescriptions" description="Medication orders, refill workflows, and review queues will live here." />} />
        <Route path="/billing" element={<PlaceholderPage title="Billing" description="Claims, payments, and payment follow-up will live here." />} />
        <Route path="/vaccinations" element={<PlaceholderPage title="Vaccinations" description="Immunization schedules and registry tracking will live here." />} />
        <Route path="/inventory" element={<PlaceholderPage title="Inventory" description="Supplies, stock movement, and reorder points will live here." />} />
        <Route path="/reports" element={<PlaceholderPage title="Reports" description="Operational and clinical reporting will live here." />} />
        <Route path="/settings" element={<PlaceholderPage title="Settings" description="Tenant, user, and integration settings will live here." />} />
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
