import * as React from "react";
import { BrowserRouter, Link, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { Box, Button, Paper, Typography } from "@mui/material";

import AppShell from "../layout/AppShell";
import { AuthContext } from "../auth/AuthContext";
import { useAuth } from "../auth/useAuth";
import HelpProvider from "../shared/components/help/HelpProvider";
import DashboardPage from "../pages/DashboardPage";
import ClinicProfilePage from "../pages/settings/ClinicProfilePage";
import UsersRolesPage from "../pages/settings/UsersRolesPage";
import TemplatesPage from "../pages/admin/TemplatesPage";
import NotificationSettingsPage from "../pages/admin/NotificationSettingsPage";
import IntegrationsPage from "../pages/admin/IntegrationsPage";
import AiOpsPage from "../pages/admin/AiOpsPage";
import PlatformOpsPage from "../pages/admin/PlatformOpsPage";
import HelpCmsPage from "../pages/admin/HelpCmsPage";
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
import LabPage from "../pages/lab/LabPage";
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
import ActiveConversationsPage from "../products/carepilot/receptionist-queue/ActiveConversationsPage";
import AppointmentHandoffsPage from "../products/carepilot/receptionist-queue/AppointmentHandoffsPage";
import CallbackQueuePage from "../products/carepilot/receptionist-queue/CallbackQueuePage";
import EscalationQueuePage from "../products/carepilot/receptionist-queue/EscalationQueuePage";
import ReceptionistQueuePage from "../products/carepilot/receptionist-queue/ReceptionistQueuePage";
import { hasTenantModule } from "../auth/moduleEntitlements";
import { branding, productTitle } from "../branding";
import { canAccessFeature, resolveTenantLandingPage, type AppFeatureId } from "../modules/moduleRegistry";

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
  React.useEffect(() => {
    document.title = productTitle();
  }, []);

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
          {branding.productName}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Sign in to {branding.productName} Admin Console to manage patients, appointments,
          prescriptions, billing, and clinic operations.
        </Typography>
        <Button size="large" variant="contained" onClick={() => auth?.login(true)} fullWidth>
          Sign in
        </Button>
      </Paper>
    </Box>
  );
}

function FeatureDisabledPage({
  title = "Feature unavailable",
  message = "This feature is not enabled for the selected tenant.",
  actionLabel,
  actionTo,
}: {
  title?: string;
  message?: string;
  actionLabel?: string;
  actionTo?: string;
}) {
  return (
    <Box sx={{ p: 3, maxWidth: 760 }}>
      <Paper elevation={0} sx={{ p: 3, border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
        <Typography variant="h6" sx={{ fontWeight: 900, mb: 1.5 }}>
          {title}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: actionTo ? 2 : 0 }}>
          {message}
        </Typography>
        {actionTo ? (
          <Button component={Link} to={actionTo} variant="contained">
            {actionLabel || "Go back"}
          </Button>
        ) : null}
      </Paper>
    </Box>
  );
}

function ModuleGate({ moduleKey, children }: { moduleKey: "carePilot" | "aiCopilot"; children: React.ReactNode }) {
  const auth = useAuth();
  if (!hasTenantModule(auth, moduleKey)) {
    return (
      <FeatureDisabledPage
        title="Module disabled"
        message="This module is not enabled for the selected tenant."
        actionLabel="Open Home"
        actionTo={auth.rolesUpper.includes("PLATFORM_ADMIN") && !auth.tenantId ? "/platform/tenants" : "/"}
      />
    );
  }
  return <>{children}</>;
}

function FeatureGate({
  featureId,
  children,
  title,
}: {
  featureId: AppFeatureId;
  children: React.ReactNode;
  title?: string;
}) {
  const auth = useAuth();
  const isPlatformAdmin = auth.rolesUpper.includes("PLATFORM_ADMIN");

  if (!auth.tenantId) {
    return (
      <FeatureDisabledPage
        title={title || "Tenant context required"}
        message={isPlatformAdmin
          ? "Select a tenant to open tenant modules, navigation, and dashboards."
          : "No active tenant context is available for this session."}
        actionLabel={isPlatformAdmin ? "Open Tenants" : "Open Home"}
        actionTo={isPlatformAdmin ? "/platform/tenants" : "/"}
      />
    );
  }

  if (!canAccessFeature(auth, featureId)) {
    return (
      <FeatureDisabledPage
        title={title || "Feature disabled"}
        message="This page is not enabled for the selected tenant's subscribed modules."
        actionLabel="Open Tenant Home"
        actionTo="/"
      />
    );
  }

  return <>{children}</>;
}

function TenantRoleGate({ rolesAny, children }: { rolesAny: string[]; children: React.ReactNode }) {
  const auth = useAuth();
  const tenantRole = (auth.tenantRole || "").toUpperCase();
  if (!rolesAny.includes(tenantRole)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
}

function PlatformAdminGate({ children }: { children: React.ReactNode }) {
  const auth = useAuth();
  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
}

function HomeRedirect() {
  const auth = useAuth();
  if (auth.rolesUpper.includes("PLATFORM_ADMIN") && !auth.tenantId) {
    return <Navigate to="/platform/tenants" replace />;
  }
  return <Navigate to={resolveTenantLandingPage(auth)} replace />;
}

function PharmacyOperationsLegacyRedirect() {
  const location = useLocation();
  const searchParams = new URLSearchParams(location.search);
  const target = searchParams.get("tab") === "reconciliation" ? "/pharmacy/reconciliation" : "/pharmacy/procurement";
  return <Navigate to={target} replace />;
}

function AuthedApp() {
  const location = useLocation();

  React.useEffect(() => {
    document.title = `${formatPageTitle(location.pathname)} | ${branding.productName}`;
  }, [location.pathname]);

  return (
    <HelpProvider>
      <AppShell>
        <Routes>
        <Route path="/" element={<HomeRedirect />} />
        <Route path="/dashboard" element={<FeatureGate featureId="clinic-dashboard" title="Clinic dashboard unavailable"><DashboardPage /></FeatureGate>} />
        <Route path="/pharmacy/dashboard" element={<FeatureGate featureId="pharmacy-dashboard" title="Pharmacy dashboard unavailable"><PharmacyDashboardPage /></FeatureGate>} />
        <Route path="/patients" element={<FeatureGate featureId="patients"><PatientsPage /></FeatureGate>} />
        <Route path="/patients/new" element={<FeatureGate featureId="patients"><PatientFormPage mode="create" /></FeatureGate>} />
        <Route path="/patients/:id" element={<FeatureGate featureId="patients"><PatientDetailPage /></FeatureGate>} />
        <Route path="/patients/:id/edit" element={<FeatureGate featureId="patients"><PatientFormPage mode="edit" /></FeatureGate>} />
        <Route path="/appointments" element={<FeatureGate featureId="appointments"><AppointmentsPage /></FeatureGate>} />
        <Route path="/appointments/day-board" element={<FeatureGate featureId="day-board"><DayBoardPage /></FeatureGate>} />
        <Route path="/queue" element={<FeatureGate featureId="queue"><QueuePage /></FeatureGate>} />
        <Route path="/consultations" element={<FeatureGate featureId="consultations"><ConsultationsPage /></FeatureGate>} />
        <Route
          path="/consultations/:id"
          element={
            <FeatureGate featureId="consultations">
              <TenantRoleGate rolesAny={["DOCTOR"]}>
                <ConsultationWorkspacePage />
              </TenantRoleGate>
            </FeatureGate>
          }
        />
        <Route path="/prescriptions" element={<FeatureGate featureId="prescriptions"><PrescriptionsPage /></FeatureGate>} />
        <Route path="/billing" element={<FeatureGate featureId="billing"><BillsPage /></FeatureGate>} />
        <Route path="/finance/cash-counter" element={<FeatureGate featureId="cash-counter"><CashCounterPage /></FeatureGate>} />
        <Route path="/finance/payments" element={<FeatureGate featureId="payments"><PaymentsPage /></FeatureGate>} />
        <Route path="/finance/refunds" element={<FeatureGate featureId="refunds"><RefundsPage /></FeatureGate>} />
        <Route path="/notifications" element={<FeatureGate featureId="notifications"><NotificationsPage /></FeatureGate>} />
        <Route path="/vaccinations" element={<FeatureGate featureId="vaccinations"><VaccinationsPage /></FeatureGate>} />
        <Route path="/inventory" element={<FeatureGate featureId="inventory"><InventoryPage /></FeatureGate>} />
        <Route path="/pharmacy/inventory" element={<FeatureGate featureId="inventory"><InventoryPage /></FeatureGate>} />
        <Route path="/pharmacy/procurement" element={<FeatureGate featureId="pharmacy-procurement"><PharmacyOperationsPage /></FeatureGate>} />
        <Route path="/pharmacy/reconciliation" element={<FeatureGate featureId="pharmacy-reconciliation"><PharmacyOperationsPage /></FeatureGate>} />
        <Route path="/pharmacy/operations" element={<PharmacyOperationsLegacyRedirect />} />
        <Route path="/pharmacy/pos" element={<FeatureGate featureId="pharmacy-pos"><PharmacyPosPage /></FeatureGate>} />
        <Route path="/pharmacy/medicine-master" element={<FeatureGate featureId="pharmacy-medicines"><MedicineMasterPage /></FeatureGate>} />
        <Route path="/pharmacy/medicines" element={<FeatureGate featureId="pharmacy-medicines"><MedicineMasterPage /></FeatureGate>} />
        <Route path="/pharmacy/stock-movements" element={<FeatureGate featureId="pharmacy-stock-movements"><StockMovementsPage /></FeatureGate>} />
        <Route path="/pharmacy/dispensing" element={<FeatureGate featureId="pharmacy-dispensing"><DispensingPage /></FeatureGate>} />
        <Route path="/reports" element={<FeatureGate featureId="reports"><ReportsPage /></FeatureGate>} />
        <Route path="/lab" element={<FeatureGate featureId="laboratory" title="Laboratory dashboard unavailable"><LabPage /></FeatureGate>} />
        <Route path="/platform/tenants" element={<PlatformAdminGate><TenantsPage /></PlatformAdminGate>} />
        <Route path="/platform/help" element={<PlatformAdminGate><HelpCmsPage /></PlatformAdminGate>} />
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
        <Route path="/carepilot/ai-receptionist/active-conversations" element={<ModuleGate moduleKey="carePilot"><ActiveConversationsPage /></ModuleGate>} />
        <Route path="/carepilot/ai-receptionist/callback-queue" element={<ModuleGate moduleKey="carePilot"><CallbackQueuePage /></ModuleGate>} />
        <Route path="/carepilot/ai-receptionist/escalation-queue" element={<ModuleGate moduleKey="carePilot"><EscalationQueuePage /></ModuleGate>} />
        <Route path="/carepilot/ai-receptionist/appointment-handoffs" element={<ModuleGate moduleKey="carePilot"><AppointmentHandoffsPage /></ModuleGate>} />
        <Route path="/carepilot/receptionist-queue" element={<ModuleGate moduleKey="carePilot"><ReceptionistQueuePage /></ModuleGate>} />
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
        <Route path="/ai/voice-test" element={<ModuleGate moduleKey="aiCopilot"><VoiceTestPage /></ModuleGate>} />
        <Route path="/doctors/availability" element={<FeatureGate featureId="doctor-availability"><DoctorAvailabilityPage /></FeatureGate>} />
        <Route path="/doctors/:id" element={<FeatureGate featureId="appointments"><DoctorDetailPage /></FeatureGate>} />
        <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AppShell>
    </HelpProvider>
  );
}

function formatPageTitle(pathname: string): string {
  if (pathname === "/" || pathname === "/dashboard") return branding.productName;
  if (pathname === "/login") return `${branding.productName} Admin Console`;
  if (pathname.startsWith("/patient")) return "Patient Portal";
  if (pathname === "/pharmacy/procurement") return "Procurement";
  if (pathname === "/pharmacy/reconciliation") return "Reconciliation";
  if (pathname === "/pharmacy/pos") return "POS Sale";
  if (pathname.startsWith("/pharmacy/operations")) return "Procurement";
  const leaf = pathname.split("/").filter(Boolean).at(-1) || "Dashboard";
  return leaf.replace(/-/g, " ").replace(/\b\w/g, (char) => char.toUpperCase());
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
