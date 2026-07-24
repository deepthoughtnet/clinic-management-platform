import * as React from "react";
import { BrowserRouter, Link, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { Box, Button, CircularProgress, Paper, Typography } from "@mui/material";

import AppShell from "../layout/AppShell";
import { AuthContext } from "../auth/AuthContext";
import { useAuth } from "../auth/useAuth";
import HelpProvider from "../shared/components/help/HelpProvider";
import DashboardPage from "../pages/DashboardPage";
import ClinicProfilePage from "../pages/settings/ClinicProfilePage";
import UsersRolesPage from "../pages/settings/UsersRolesPage";
import TemplatesPage from "../pages/admin/TemplatesPage";
import NotificationSettingsPage from "../pages/admin/NotificationSettingsPage";
import NotificationOperationsPage from "../pages/admin/NotificationOperationsPage";
import IntegrationsPage from "../pages/admin/IntegrationsPage";
import ReasoningTestConsolePage from "../pages/admin/ReasoningTestConsolePage";
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
import NotificationCenterPage from "../pages/notification-center/NotificationCenterPage";
import InventoryPage from "../pages/inventory/InventoryPage";
import PharmacyDashboardPage from "../pages/pharmacy/PharmacyDashboardPage";
import MedicineMasterPage from "../pages/pharmacy/MedicineMasterPage";
import StockMovementsPage from "../pages/pharmacy/StockMovementsPage";
import DispensingPage from "../pages/pharmacy/DispensingPage";
import PharmacyProcurePage from "../pages/pharmacy/PharmacyProcurePage";
import PharmacyProcureTestPage from "../pages/pharmacy/PharmacyProcureTestPage";
import PharmacyProcurementPage from "../pages/pharmacy/PharmacyProcurementPage";
import PharmacyReconcilePage from "../pages/pharmacy/PharmacyReconcilePage";
import PharmacyReconcileTestPage from "../pages/pharmacy/PharmacyReconcileTestPage";
import PharmacyReconciliationPage from "../pages/pharmacy/PharmacyReconciliationPage";
import PharmacyPosPage from "../pages/pharmacy/PharmacyPosPage";
import ReportsPage from "../pages/reports/ReportsPage";
import VaccinationsPage from "../pages/vaccinations/VaccinationsPage";
import LabPage from "../pages/lab/LabPage";
import PlaceholderPage from "../pages/PlaceholderPage";
import TenantsPage from "../pages/platform/TenantsPage";
import TenantDetailPage from "../pages/platform/TenantDetailPage";
import PlansModulesPage from "../pages/platform/PlansModulesPage";
import ProductImplementationPage from "../pages/platform/ProductImplementationPage";
import CampaignsPage from "../products/carepilot/campaigns/CampaignsPage";
import AnalyticsPage from "../products/carepilot/analytics/AnalyticsPage";
import OpsConsolePage from "../products/carepilot/ops/OpsConsolePage";
import MessagingPage from "../products/carepilot/messaging/MessagingPage";
import PatientEngagementPage from "../products/carepilot/engagement/PatientEngagementPage";
import RemindersPage from "../products/carepilot/reminders/RemindersPage";
import LeadsPage from "../products/carepilot/leads/LeadsPage";
import WebinarsPage from "../products/carepilot/webinars/WebinarsPage";
import AiOperationsPage from "../products/carepilot/ai-operations/AiOperationsPage";
import { hasTenantModule } from "../auth/moduleEntitlements";
import {
  ENGAGE_ANALYTICS_VIEW,
  ENGAGE_LEAD_VIEW,
  ENGAGE_LEAD_VIEW_ALL,
  ENGAGE_LEAD_VIEW_AUDIT,
  ENGAGE_WEBINAR_VIEW,
  ENGAGE_WEBINAR_VIEW_ANALYTICS,
  ENGAGE_WEBINAR_VIEW_AUDIT,
} from "../auth/permissions";
import { branding, productTitle } from "../branding";
import { canAccessFeature, isRouteAccessibleForAuth, resolveTenantLandingPage, type AppFeatureId } from "../modules/moduleRegistry";

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

function RouteAccessGate({ children }: { children: React.ReactNode }) {
  const auth = useAuth();
  const location = useLocation();
  if (!isRouteAccessibleForAuth(auth, location.pathname)) {
    return <Navigate to={resolveTenantLandingPage(auth)} replace />;
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

function PermissionGate({
  anyPermissions,
  title = "Access denied",
  message = "You do not have access to this page.",
  children,
}: {
  anyPermissions: string[];
  title?: string;
  message?: string;
  children: React.ReactNode;
}) {
  const auth = useAuth();
  if (!auth.initialized) {
    return <Box sx={{ p: 3, display: "grid", placeItems: "center" }}><CircularProgress /></Box>;
  }
  const allowed = anyPermissions.some((permission) => auth.permissions.includes(permission));

  if (!allowed) {
    return <FeatureDisabledPage title={title} message={message} actionLabel="Open Tenant Home" actionTo="/" />;
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

function NotificationOperationsGate({ children }: { children: React.ReactNode }) {
  const auth = useAuth();
  if (!auth.tenantId && !auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Navigate to={resolveTenantLandingPage(auth)} replace />;
  }
  return <>{children}</>;
}

type RouteErrorBoundaryState = {
  hasError: boolean;
};

class RouteErrorBoundary extends React.Component<{ children: React.ReactNode }, RouteErrorBoundaryState> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error: unknown) {
    if (typeof console !== "undefined" && console.error) {
      console.error("Route rendering failed.", error);
    }
  }

  render() {
    if (this.state.hasError) {
      return (
        <Box sx={{ p: 3, maxWidth: 760 }}>
          <Paper elevation={0} sx={{ p: 3, border: "1px solid", borderColor: "divider", borderRadius: 2 }}>
            <Typography variant="h6" sx={{ fontWeight: 900, mb: 1.5 }}>
              Something went wrong
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Reload the page or return to the dashboard.
            </Typography>
            <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
              <Button variant="contained" onClick={() => window.location.reload()}>
                Retry
              </Button>
              <Button component={Link} to="/dashboard" variant="outlined">
                Go to Dashboard
              </Button>
            </Box>
          </Paper>
        </Box>
      );
    }

    return this.props.children;
  }
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
  const target = searchParams.get("tab") === "reconciliation"
    ? "/pharmacy/reconciliation"
    : "/pharmacy/procurement";
  return <Navigate to={target} replace />;
}

function PathnameKeyedRoute({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  return <React.Fragment key={location.pathname}>{children}</React.Fragment>;
}

function AuthedApp() {
  const location = useLocation();
  const auth = useAuth();
  const sessionSignature = React.useMemo(() => [
    auth.tenantId || "",
    auth.tenantRole || "",
    auth.rolesUpper.join(","),
    auth.enabledTenantModules ? JSON.stringify(auth.enabledTenantModules) : "",
  ].join("|"), [auth.enabledTenantModules, auth.rolesUpper, auth.tenantId, auth.tenantRole]);
  const previousSessionSignatureRef = React.useRef<string | null>(null);
  const shouldRedirectToLanding = React.useMemo(() => {
    if (!previousSessionSignatureRef.current) return false;
    if (previousSessionSignatureRef.current === sessionSignature) return false;
    return !isRouteAccessibleForAuth(auth, location.pathname);
  }, [auth, location.pathname, sessionSignature]);

  React.useEffect(() => {
    document.title = `${formatPageTitle(location.pathname)} | ${branding.productName}`;
  }, [location.pathname]);

  React.useEffect(() => {
    previousSessionSignatureRef.current = sessionSignature;
  }, [sessionSignature]);

  if (shouldRedirectToLanding) {
    return <Navigate to={resolveTenantLandingPage(auth)} replace />;
  }

  return (
    <HelpProvider>
      <AppShell>
        <RouteErrorBoundary>
          <Routes>
        <Route path="/" element={<HomeRedirect />} />
        <Route path="/dashboard" element={<PathnameKeyedRoute><FeatureGate featureId="clinic-dashboard" title="Clinic dashboard unavailable"><DashboardPage /></FeatureGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/dashboard" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-dashboard" title="Pharmacy dashboard unavailable"><PharmacyDashboardPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/patients" element={<PathnameKeyedRoute><FeatureGate featureId="patients"><PatientsPage /></FeatureGate></PathnameKeyedRoute>} />
        <Route path="/patients/new" element={<PathnameKeyedRoute><FeatureGate featureId="patients"><PatientFormPage mode="create" /></FeatureGate></PathnameKeyedRoute>} />
        <Route path="/patients/:id" element={<PathnameKeyedRoute><RouteAccessGate><PatientDetailPage /></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/patients/:id/edit" element={<PathnameKeyedRoute><FeatureGate featureId="patients"><PatientFormPage mode="edit" /></FeatureGate></PathnameKeyedRoute>} />
        <Route path="/appointments" element={<PathnameKeyedRoute><FeatureGate featureId="appointments"><AppointmentsPage /></FeatureGate></PathnameKeyedRoute>} />
        <Route path="/appointments/day-board" element={<PathnameKeyedRoute><FeatureGate featureId="day-board"><DayBoardPage /></FeatureGate></PathnameKeyedRoute>} />
        <Route path="/queue" element={<PathnameKeyedRoute><FeatureGate featureId="queue"><QueuePage /></FeatureGate></PathnameKeyedRoute>} />
        <Route path="/consultations" element={<PathnameKeyedRoute><FeatureGate featureId="consultations"><ConsultationsPage /></FeatureGate></PathnameKeyedRoute>} />
        <Route
          path="/consultations/:id"
          element={
            <PathnameKeyedRoute>
              <FeatureGate featureId="consultations">
                <TenantRoleGate rolesAny={["DOCTOR"]}>
                  <ConsultationWorkspacePage />
                </TenantRoleGate>
              </FeatureGate>
            </PathnameKeyedRoute>
          }
        />
        <Route path="/prescriptions" element={<PathnameKeyedRoute><FeatureGate featureId="prescriptions"><PrescriptionsPage /></FeatureGate></PathnameKeyedRoute>} />
        <Route path="/billing" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="billing"><BillsPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/finance/cash-counter" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="cash-counter"><CashCounterPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/finance/payments" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="payments"><PaymentsPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/finance/refunds" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="refunds"><RefundsPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/notifications" element={<PathnameKeyedRoute><FeatureGate featureId="notifications"><NotificationsPage /></FeatureGate></PathnameKeyedRoute>} />
        <Route path="/admin/notification-operations" element={<PathnameKeyedRoute><NotificationOperationsGate><NotificationOperationsPage /></NotificationOperationsGate></PathnameKeyedRoute>} />
        <Route path="/vaccinations" element={<PathnameKeyedRoute><FeatureGate featureId="vaccinations"><VaccinationsPage /></FeatureGate></PathnameKeyedRoute>} />
        <Route path="/inventory" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="inventory"><InventoryPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/inventory" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="inventory"><InventoryPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/procure" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-procurement" title="Procure unavailable"><PharmacyProcurePage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/procure-test" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-procurement" title="Procure unavailable"><PharmacyProcureTestPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/reconcile" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-reconciliation" title="Reconcile unavailable"><PharmacyReconcilePage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/reconcile-test" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-reconciliation" title="Reconcile unavailable"><PharmacyReconcileTestPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/procurement" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-procurement"><PharmacyProcurementPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/reconciliation" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-reconciliation"><PharmacyReconciliationPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/operations" element={<RouteAccessGate><PharmacyOperationsLegacyRedirect /></RouteAccessGate>} />
        <Route path="/pharmacy/pos" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-pos"><PharmacyPosPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/medicine-master" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-medicines"><MedicineMasterPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/medicines" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-medicines"><MedicineMasterPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/stock-movements" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-stock-movements"><StockMovementsPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/pharmacy/dispensing" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="pharmacy-dispensing"><DispensingPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/reports" element={<PathnameKeyedRoute><FeatureGate featureId="reports"><ReportsPage /></FeatureGate></PathnameKeyedRoute>} />
        <Route path="/lab" element={<PathnameKeyedRoute><RouteAccessGate><FeatureGate featureId="laboratory" title="Laboratory dashboard unavailable"><LabPage /></FeatureGate></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/laboratory" element={<PathnameKeyedRoute><RouteAccessGate><Navigate to="/lab" replace /></RouteAccessGate></PathnameKeyedRoute>} />
        <Route path="/platform/tenants" element={<PathnameKeyedRoute><PlatformAdminGate><TenantsPage /></PlatformAdminGate></PathnameKeyedRoute>} />
        <Route path="/platform/help" element={<PathnameKeyedRoute><PlatformAdminGate><HelpCmsPage /></PlatformAdminGate></PathnameKeyedRoute>} />
        <Route path="/platform/tenants/:tenantId" element={<PathnameKeyedRoute><TenantDetailPage /></PathnameKeyedRoute>} />
        <Route path="/platform/plans" element={<PathnameKeyedRoute><PlansModulesPage /></PathnameKeyedRoute>} />
        <Route path="/platform/product-implementation" element={<PathnameKeyedRoute><PlatformAdminGate><ProductImplementationPage /></PlatformAdminGate></PathnameKeyedRoute>} />
        <Route
          path="/carepilot/campaigns"
          element={
            <PathnameKeyedRoute>
              <ModuleGate moduleKey="carePilot">
                <PermissionGate anyPermissions={["engage.campaign.view", "engage.audit.view"]}>
                  <CampaignsPage />
                </PermissionGate>
              </ModuleGate>
            </PathnameKeyedRoute>
          }
        />
        <Route
          path="/carepilot/analytics"
          element={
            <PathnameKeyedRoute>
              <ModuleGate moduleKey="carePilot">
                <PermissionGate anyPermissions={[ENGAGE_ANALYTICS_VIEW]}>
                  <AnalyticsPage />
                </PermissionGate>
              </ModuleGate>
            </PathnameKeyedRoute>
          }
        />
        <Route
          path="/carepilot/ops"
          element={
            <PathnameKeyedRoute>
              <ModuleGate moduleKey="carePilot">
                <PermissionGate anyPermissions={["engage.ops.view"]}>
                  <OpsConsolePage />
                </PermissionGate>
              </ModuleGate>
            </PathnameKeyedRoute>
          }
        />
        <Route
          path="/carepilot/messaging"
          element={
            <PathnameKeyedRoute>
              <ModuleGate moduleKey="carePilot">
                <PermissionGate anyPermissions={["engage.provider.view"]}>
                  <MessagingPage />
                </PermissionGate>
              </ModuleGate>
            </PathnameKeyedRoute>
          }
        />
        <Route
          path="/carepilot/engagement"
          element={
            <PathnameKeyedRoute>
              <ModuleGate moduleKey="carePilot">
                <PermissionGate anyPermissions={[ENGAGE_ANALYTICS_VIEW]}>
                  <PatientEngagementPage />
                </PermissionGate>
              </ModuleGate>
            </PathnameKeyedRoute>
          }
        />
        <Route
          path="/carepilot/reminders"
          element={
            <PathnameKeyedRoute>
              <ModuleGate moduleKey="carePilot">
                <PermissionGate anyPermissions={["engage.reminder.view", "engage.reminder.operate", "engage.audit.view"]}>
                  <RemindersPage />
                </PermissionGate>
              </ModuleGate>
            </PathnameKeyedRoute>
          }
        />
        <Route
          path="/carepilot/leads"
          element={
            <PathnameKeyedRoute>
              <ModuleGate moduleKey="carePilot">
                <PermissionGate anyPermissions={[ENGAGE_LEAD_VIEW, ENGAGE_LEAD_VIEW_ALL, ENGAGE_LEAD_VIEW_AUDIT]}>
                  <LeadsPage />
                </PermissionGate>
              </ModuleGate>
            </PathnameKeyedRoute>
          }
        />
        <Route
          path="/carepilot/webinars"
          element={
            <PathnameKeyedRoute>
              <ModuleGate moduleKey="carePilot">
                <PermissionGate anyPermissions={[ENGAGE_WEBINAR_VIEW, ENGAGE_WEBINAR_VIEW_ANALYTICS, ENGAGE_WEBINAR_VIEW_AUDIT]}>
                  <WebinarsPage />
                </PermissionGate>
              </ModuleGate>
            </PathnameKeyedRoute>
          }
        />
        <Route
          path="/carepilot/ai-operations"
          element={
            <PathnameKeyedRoute>
              <ModuleGate moduleKey="carePilot">
                <PermissionGate anyPermissions={["engage.ai.operate", "engage.reception.operate", "engage.view"]}>
                  <AiOperationsPage />
                </PermissionGate>
              </ModuleGate>
            </PathnameKeyedRoute>
          }
        />
        <Route
          path="/carepilot/ai-calls"
          element={<Navigate to="/carepilot/ai-operations?tab=calls" replace />}
        />
        <Route
          path="/carepilot/ai-receptionist/active-conversations"
          element={<Navigate to="/carepilot/ai-operations?tab=conversations" replace />}
        />
        <Route
          path="/carepilot/ai-receptionist/callback-queue"
          element={<Navigate to="/carepilot/ai-operations?tab=work-queue&type=callback" replace />}
        />
        <Route
          path="/carepilot/ai-receptionist/escalation-queue"
          element={<Navigate to="/carepilot/ai-operations?tab=work-queue&type=escalation" replace />}
        />
        <Route
          path="/carepilot/ai-receptionist/appointment-handoffs"
          element={<Navigate to="/carepilot/ai-operations?tab=work-queue&type=appointment-handoff" replace />}
        />
        <Route
          path="/carepilot/receptionist-queue"
          element={<Navigate to="/carepilot/ai-operations?tab=work-queue" replace />}
        />
        <Route
          path="/platform/users"
          element={<PathnameKeyedRoute><PlaceholderPage title="Users / Admins" description="Platform user administration can be enabled when backend APIs are exposed." /></PathnameKeyedRoute>}
        />
        <Route path="/settings" element={<Navigate to="/settings/clinic-profile" replace />} />
        <Route path="/settings/clinic-profile" element={<ClinicProfilePage />} />
        <Route path="/settings/users-roles" element={<UsersRolesPage />} />
        <Route path="/admin/templates" element={<TemplatesPage />} />
        <Route path="/admin/notification-settings" element={<NotificationSettingsPage />} />
        <Route path="/notification-center" element={<PathnameKeyedRoute><NotificationCenterPage /></PathnameKeyedRoute>} />
        <Route path="/admin/integrations" element={<IntegrationsPage />} />
        <Route path="/admin/ai-ops" element={<ModuleGate moduleKey="aiCopilot"><AiOpsPage /></ModuleGate>} />
        <Route path="/admin/ai-reasoning-console" element={<ReasoningTestConsolePage />} />
        <Route path="/admin/platform-ops" element={<PlatformOpsPage />} />
        <Route path="/admin/realtime-ai" element={<ModuleGate moduleKey="aiCopilot"><RealtimeAiPage /></ModuleGate>} />
        <Route path="/ai/voice-test" element={<ModuleGate moduleKey="aiCopilot"><VoiceTestPage /></ModuleGate>} />
        <Route path="/doctors/availability" element={<FeatureGate featureId="doctor-availability"><DoctorAvailabilityPage /></FeatureGate>} />
        <Route path="/doctors/:id" element={<FeatureGate featureId="appointments"><DoctorDetailPage /></FeatureGate>} />
        <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </RouteErrorBoundary>
      </AppShell>
    </HelpProvider>
  );
}

function formatPageTitle(pathname: string): string {
  if (pathname === "/" || pathname === "/dashboard") return branding.productName;
  if (pathname === "/login") return `${branding.productName} Admin Console`;
  if (pathname === "/patients" || pathname.startsWith("/patients/")) return "Patient Portal";
  if (pathname === "/pharmacy/procure") return "Procure";
  if (pathname === "/pharmacy/reconcile") return "Reconcile";
  if (pathname === "/pharmacy/procure-test") return "Procure Test";
  if (pathname === "/pharmacy/reconcile-test") return "Reconcile Test";
  if (pathname === "/pharmacy/procurement") return "Procurement";
  if (pathname === "/pharmacy/reconciliation") return "Reconciliation";
  if (pathname === "/pharmacy/pos") return "POS Sale";
  if (pathname === "/pharmacy/operations") return "Procurement";
  if (pathname === "/admin/notification-operations") return "Notification Operations";
  if (pathname === "/notification-center") return "My Notifications";
  if (pathname === "/carepilot/ai-operations") return "AI Operations";
  if (pathname.startsWith("/platform/product-implementation")) return "Product Implementation";
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
