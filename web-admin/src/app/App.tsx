import * as React from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { Box, Button, Paper, Typography } from "@mui/material";

import AppShell from "../layout/AppShell";
import { AuthContext } from "../auth/AuthContext";
import { useAuth } from "../auth/useAuth";
import DashboardPage from "../pages/DashboardPage";
import ClinicProfilePage from "../pages/settings/ClinicProfilePage";
import UsersRolesPage from "../pages/settings/UsersRolesPage";
import TemplatesPage from "../pages/admin/TemplatesPage";
import NotificationSettingsPage from "../pages/admin/NotificationSettingsPage";
import IntegrationsPage from "../pages/admin/IntegrationsPage";
import AiOpsPage from "../pages/admin/AiOpsPage";
import PlatformOpsPage from "../pages/admin/PlatformOpsPage";
import RealtimeAiPage from "../pages/admin/RealtimeAiPage";
import VoiceTestPage from "../pages/ai/VoiceTestPage";
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
import CashCounterPage from "../pages/finance/CashCounterPage";
import PaymentsPage from "../pages/finance/PaymentsPage";
import RefundsPage from "../pages/finance/RefundsPage";
import NotificationsPage from "../pages/notifications/NotificationsPage";
import InventoryPage from "../pages/inventory/InventoryPage";
import PharmacyDashboardPage from "../pages/pharmacy/PharmacyDashboardPage";
import MedicineMasterPage from "../pages/pharmacy/MedicineMasterPage";
import StockMovementsPage from "../pages/pharmacy/StockMovementsPage";
import DispensingPage from "../pages/pharmacy/DispensingPage";
import PharmacyOperationsPage from "../pages/pharmacy/PharmacyOperationsPage";
import PharmacyPosPage from "../pages/pharmacy/PharmacyPosPage";
import ReportsPage from "../pages/reports/ReportsPage";
import VaccinationsPage from "../pages/vaccinations/VaccinationsPage";
import PlaceholderPage from "../pages/PlaceholderPage";
import TenantsPage from "../pages/platform/TenantsPage";
import TenantDetailPage from "../pages/platform/TenantDetailPage";
import PlansModulesPage from "../pages/platform/PlansModulesPage";
import CampaignsPage from "../products/carepilot/campaigns/CampaignsPage";
import AnalyticsPage from "../products/carepilot/analytics/AnalyticsPage";
import OpsConsolePage from "../products/carepilot/ops/OpsConsolePage";
import MessagingPage from "../products/carepilot/messaging/MessagingPage";
import PatientEngagementPage from "../products/carepilot/engagement/PatientEngagementPage";
import RemindersPage from "../products/carepilot/reminders/RemindersPage";
import LeadsPage from "../products/carepilot/leads/LeadsPage";
import WebinarsPage from "../products/carepilot/webinars/WebinarsPage";
import AiCallsPage from "../products/carepilot/ai-calls/AiCallsPage";
import { hasTenantModule } from "../auth/moduleEntitlements";

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

function ModuleGate({ moduleKey, children }: { moduleKey: "carePilot" | "aiCopilot"; children: React.ReactNode }) {
  const auth = useAuth();
  if (!hasTenantModule(auth, moduleKey)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
}

function HomeRedirect() {
  const auth = useAuth();
  const tenantRole = (auth.tenantRole || "").toUpperCase();
  const isPharmacyRole = tenantRole === "PHARMA" || tenantRole === "PHARMACY" || tenantRole === "PHARMACIST";
  return <Navigate to={isPharmacyRole ? "/pharmacy/dashboard" : "/dashboard"} replace />;
}

function AuthedApp() {
  const auth = useAuth();

  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<HomeRedirect />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/pharmacy/dashboard" element={<PharmacyDashboardPage />} />
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
        <Route path="/finance/cash-counter" element={<CashCounterPage />} />
        <Route path="/finance/payments" element={<PaymentsPage />} />
        <Route path="/finance/refunds" element={<RefundsPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/vaccinations" element={<VaccinationsPage />} />
        <Route path="/inventory" element={<InventoryPage />} />
        <Route path="/pharmacy/operations" element={<PharmacyOperationsPage />} />
        <Route path="/pharmacy/pos" element={<PharmacyPosPage />} />
        <Route path="/pharmacy/medicines" element={<MedicineMasterPage />} />
        <Route path="/pharmacy/stock-movements" element={<StockMovementsPage />} />
        <Route path="/pharmacy/dispensing" element={<DispensingPage />} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/platform/tenants" element={<TenantsPage />} />
        <Route path="/platform/tenants/:tenantId" element={<TenantDetailPage />} />
        <Route path="/platform/plans" element={<PlansModulesPage />} />
        <Route path="/carepilot/campaigns" element={<ModuleGate moduleKey="carePilot"><CampaignsPage /></ModuleGate>} />
        <Route path="/carepilot/analytics" element={<ModuleGate moduleKey="carePilot"><AnalyticsPage /></ModuleGate>} />
        <Route path="/carepilot/ops" element={<ModuleGate moduleKey="carePilot"><OpsConsolePage /></ModuleGate>} />
        <Route path="/carepilot/messaging" element={<ModuleGate moduleKey="carePilot"><MessagingPage /></ModuleGate>} />
        <Route path="/carepilot/engagement" element={<ModuleGate moduleKey="carePilot"><PatientEngagementPage /></ModuleGate>} />
        <Route path="/carepilot/reminders" element={<ModuleGate moduleKey="carePilot"><RemindersPage /></ModuleGate>} />
        <Route path="/carepilot/leads" element={<ModuleGate moduleKey="carePilot"><LeadsPage /></ModuleGate>} />
        <Route path="/carepilot/webinars" element={<ModuleGate moduleKey="carePilot"><WebinarsPage /></ModuleGate>} />
        <Route path="/carepilot/ai-calls" element={<ModuleGate moduleKey="carePilot"><AiCallsPage /></ModuleGate>} />
        <Route
          path="/platform/users"
          element={<PlaceholderPage title="Users / Admins" description="Platform user administration can be enabled when backend APIs are exposed." />}
        />
        <Route path="/settings" element={<Navigate to="/settings/clinic-profile" replace />} />
        <Route path="/settings/clinic-profile" element={<ClinicProfilePage />} />
        <Route path="/settings/users-roles" element={<UsersRolesPage />} />
        <Route path="/admin/templates" element={<TemplatesPage />} />
        <Route path="/admin/notification-settings" element={<NotificationSettingsPage />} />
        <Route path="/admin/integrations" element={<IntegrationsPage />} />
        <Route path="/admin/ai-ops" element={<ModuleGate moduleKey="aiCopilot"><AiOpsPage /></ModuleGate>} />
        <Route path="/admin/platform-ops" element={<PlatformOpsPage />} />
        <Route path="/admin/realtime-ai" element={<ModuleGate moduleKey="aiCopilot"><RealtimeAiPage /></ModuleGate>} />
                <Route path="/ai/voice-test" element={<VoiceTestPage />} />
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
