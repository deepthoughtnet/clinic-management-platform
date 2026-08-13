import * as React from "react";
import { Link, Navigate } from "react-router-dom";
import { alpha } from "@mui/material/styles";
import {
  AdminPanelSettingsRounded,
  AutoAwesomeRounded,
  CampaignRounded,
  CheckCircleRounded,
  EventAvailableRounded,
  FavoriteRounded,
  HubRounded,
  LocalHospitalRounded,
  MedicalServicesRounded,
  MedicationRounded,
  ReceiptLongRounded,
  ScienceRounded,
  SearchRounded,
  ShieldRounded,
  GroupRounded,
  ArrowForwardRounded,
} from "@mui/icons-material";
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Container,
  Divider,
  Paper,
  Stack,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import { branding, productTitle } from "../../branding";
import { adminConfig } from "../../config";
import { resolveTenantLandingPage } from "../../modules/moduleRegistry";
import BrandMark from "../../shared/components/branding/BrandMark";

type FeatureCard = {
  title: string;
  description: string;
  icon: React.ReactNode;
};

type EcosystemCard = {
  title: string;
  description: string;
  icon: React.ReactNode;
  cta?: React.ReactNode;
};

const operationsCards: FeatureCard[] = [
  {
    title: "Reception & Appointments",
    description: "Patient registration, appointment handling, queue management and consultation handoff.",
    icon: <EventAvailableRounded fontSize="inherit" />,
  },
  {
    title: "Doctor Workspace",
    description: "Consultation, diagnosis, prescriptions, investigations and clinical history.",
    icon: <MedicalServicesRounded fontSize="inherit" />,
  },
  {
    title: "Billing",
    description: "Billing workflows, payments and financial operations.",
    icon: <ReceiptLongRounded fontSize="inherit" />,
  },
  {
    title: "Pharmacy",
    description: "Procurement, GRN, inventory, POS and reconciliation.",
    icon: <MedicationRounded fontSize="inherit" />,
  },
  {
    title: "Laboratory",
    description: "Orders, sample collection, results, verification and reporting.",
    icon: <ScienceRounded fontSize="inherit" />,
  },
  {
    title: "Patient Engagement",
    description: "Campaigns, reminders, follow-ups and operational engagement workflows.",
    icon: <CampaignRounded fontSize="inherit" />,
  },
  {
    title: "AIVA / AI Assistance",
    description: "Optional AI-assisted clinical and operational workflows when teams want them.",
    icon: <AutoAwesomeRounded fontSize="inherit" />,
  },
  {
    title: "Administration",
    description: "Users, roles, tenant configuration, audit and operational controls.",
    icon: <AdminPanelSettingsRounded fontSize="inherit" />,
  },
];

const audienceCards: FeatureCard[] = [
  {
    title: "Clinics",
    description: "Coordinate reception, doctors, billing, pharmacy, lab and patient follow-up.",
    icon: <GroupRounded fontSize="inherit" />,
  },
  {
    title: "Hospitals",
    description: "Manage connected clinical and administrative workflows across teams.",
    icon: <LocalHospitalRounded fontSize="inherit" />,
  },
  {
    title: "Diagnostic / Lab operations",
    description: "Support collection, processing, verification and report publication.",
    icon: <ScienceRounded fontSize="inherit" />,
  },
];

const ecosystemCards: EcosystemCard[] = [
  {
    title: "Jeevanam Discover",
    description: "Find doctors, clinics, hospitals and appointment options.",
    icon: <SearchRounded fontSize="inherit" />,
  },
  {
    title: "Jeevanam Connect",
    description: "Manage your provider profile, publish your presence, and connect with patients.",
    icon: <HubRounded fontSize="inherit" />,
    cta: null,
  },
  {
    title: "Jeevanam Care",
    description: "Patient appointments, prescriptions, reports, bills and care journey.",
    icon: <FavoriteRounded fontSize="inherit" />,
  },
  {
    title: "Jeevanam Healthcare",
    description: "Clinical and administrative operations for clinics and hospitals.",
    icon: <LocalHospitalRounded fontSize="inherit" />,
  },
];

function SectionHeading({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {
  return (
    <Stack spacing={1.25} sx={{ maxWidth: 760 }}>
      <Chip
        label={eyebrow}
        size="small"
        sx={(theme) => ({
          alignSelf: "flex-start",
          bgcolor: alpha(theme.palette.primary.main, 0.1),
          color: theme.palette.primary.dark,
          fontWeight: 800,
          letterSpacing: 0.5,
        })}
      />
      <Typography component="h2" variant="h4" sx={{ fontWeight: 900, letterSpacing: -0.6 }}>
        {title}
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ lineHeight: 1.75 }}>
        {description}
      </Typography>
    </Stack>
  );
}

function CapabilityCard({ title, description, icon }: FeatureCard) {
  return (
    <Card
      elevation={0}
      sx={(theme) => ({
        height: "100%",
        borderRadius: 4,
        border: "1px solid",
        borderColor: alpha(theme.palette.primary.main, 0.12),
        bgcolor: "background.paper",
        boxShadow: `0 18px 48px ${alpha(theme.palette.common.black, 0.05)}`,
      })}
    >
      <CardContent sx={{ p: 3, display: "flex", flexDirection: "column", gap: 1.5, height: "100%" }}>
        <Box
          sx={(theme) => ({
            width: 48,
            height: 48,
            borderRadius: 3,
            display: "grid",
            placeItems: "center",
            color: theme.palette.primary.main,
            bgcolor: alpha(theme.palette.primary.main, 0.08),
          })}
        >
          {icon}
        </Box>
        <Typography variant="h6" sx={{ fontWeight: 850, letterSpacing: -0.2 }}>
          {title}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.7 }}>
          {description}
        </Typography>
      </CardContent>
    </Card>
  );
}

function EcosystemCardView({ title, description, icon, cta }: EcosystemCard) {
  return (
    <Card
      elevation={0}
      sx={(theme) => ({
        height: "100%",
        borderRadius: 4,
        border: "1px solid",
        borderColor: alpha(theme.palette.secondary.main, 0.14),
        bgcolor: "background.paper",
        boxShadow: `0 18px 48px ${alpha(theme.palette.common.black, 0.05)}`,
      })}
    >
      <CardContent sx={{ p: 3, display: "flex", flexDirection: "column", gap: 1.5, height: "100%" }}>
        <Box
          sx={(theme) => ({
            width: 48,
            height: 48,
            borderRadius: 3,
            display: "grid",
            placeItems: "center",
            color: theme.palette.secondary.main,
            bgcolor: alpha(theme.palette.secondary.main, 0.08),
          })}
        >
          {icon}
        </Box>
        <Typography variant="h6" sx={{ fontWeight: 850, letterSpacing: -0.2 }}>
          {title}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.7, flex: 1 }}>
          {description}
        </Typography>
        <Box sx={{ pt: 0.5 }}>
          {cta ?? (
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
              Product surface
            </Typography>
          )}
        </Box>
      </CardContent>
    </Card>
  );
}

function WorkflowStep({ label, detail }: { label: string; detail: string }) {
  return (
    <Paper
      elevation={0}
      sx={(theme) => ({
        minWidth: 180,
        flex: "1 1 180px",
        p: 2,
        borderRadius: 3,
        border: "1px solid",
        borderColor: alpha(theme.palette.primary.main, 0.12),
        bgcolor: alpha(theme.palette.background.paper, 0.9),
      })}
    >
      <Typography variant="subtitle2" sx={{ fontWeight: 850, mb: 0.5 }}>
        {label}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.65 }}>
        {detail}
      </Typography>
    </Paper>
  );
}

export default function HealthcareLandingPage() {
  const auth = useAuth();

  React.useEffect(() => {
    document.title = productTitle();
  }, []);

  if (auth.initialized && auth.authenticated) {
    return <Navigate to={resolveTenantLandingPage(auth)} replace />;
  }

  const providerWorkspaceUrl = `${adminConfig.providerAppUrl.replace(/\/$/, "")}/provider/login`;

  const scrollToCapabilities = () => {
    document.getElementById("capabilities")?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  return (
    <Box
      sx={(theme) => ({
        minHeight: "100vh",
        background: `
          radial-gradient(circle at top left, ${alpha(theme.palette.primary.main, 0.12)}, transparent 30%),
          radial-gradient(circle at top right, ${alpha(theme.palette.secondary.main, 0.12)}, transparent 26%),
          linear-gradient(180deg, ${alpha(theme.palette.background.default, 0.96)} 0%, ${theme.palette.background.default} 52%)
        `,
      })}
    >
      <Box
        component="header"
        sx={(theme) => ({
          position: "sticky",
          top: 0,
          zIndex: 10,
          backdropFilter: "blur(14px)",
          backgroundColor: alpha(theme.palette.background.default, 0.84),
          borderBottom: "1px solid",
          borderColor: alpha(theme.palette.divider, 0.8),
        })}
      >
        <Container maxWidth="xl" sx={{ py: 1.5 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={2} flexWrap="wrap">
            <BrandMark title={branding.productName} subtitle={branding.tagline} />
            <Stack direction="row" spacing={1} flexWrap="wrap" justifyContent="flex-end" useFlexGap>
              <Button component={Link} to="#capabilities" color="inherit" sx={{ fontWeight: 700 }}>
                Capabilities
              </Button>
              <Button component={Link} to="#who-it-is-for" color="inherit" sx={{ fontWeight: 700 }}>
                For Clinics
              </Button>
              <Button component={Link} to="#who-it-is-for" color="inherit" sx={{ fontWeight: 700 }}>
                For Hospitals
              </Button>
              <Button component={Link} to="#ecosystem" color="inherit" sx={{ fontWeight: 700 }}>
                Jeevanam Ecosystem
              </Button>
              <Button component={Link} to="/login" variant="contained">
                Sign in
              </Button>
            </Stack>
          </Stack>
        </Container>
      </Box>

      <Box component="main">
        <Container maxWidth="xl" sx={{ py: { xs: 5, md: 8 } }}>
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: { xs: "1fr", lg: "1.08fr 0.92fr" },
              gap: { xs: 4, lg: 6 },
              alignItems: "center",
            }}
          >
            <Stack spacing={3} sx={{ maxWidth: 820 }}>
              <Chip
                label="JEEVANAM HEALTHCARE"
                sx={(theme) => ({
                  alignSelf: "flex-start",
                  bgcolor: alpha(theme.palette.primary.main, 0.1),
                  color: theme.palette.primary.dark,
                  fontWeight: 900,
                  letterSpacing: 1.2,
                })}
              />
              <Typography component="h1" variant="h2" sx={{ fontWeight: 950, letterSpacing: -1.4, lineHeight: 0.98 }}>
                Run your clinic or hospital on one connected healthcare platform.
              </Typography>
              <Typography variant="h6" color="text.secondary" sx={{ lineHeight: 1.7, maxWidth: 760 }}>
                Manage patient journeys, consultations, billing, pharmacy, laboratory, engagement and operations
                from one secure workspace.
              </Typography>
              <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap>
                {["Clinic operations", "Hospital workflows", "Role-based access", "AI-assisted workflows"].map((label) => (
                  <Chip
                    key={label}
                    label={label}
                    icon={<CheckCircleRounded fontSize="small" />}
                    variant="outlined"
                    sx={{ fontWeight: 700 }}
                  />
                ))}
              </Stack>
              <Stack direction="row" spacing={1.5} flexWrap="wrap" useFlexGap>
                <Button component={Link} to="/login" size="large" variant="contained" endIcon={<ArrowForwardRounded />}>
                  Sign in to Healthcare
                </Button>
                <Button size="large" variant="outlined" onClick={scrollToCapabilities}>
                  Explore capabilities
                </Button>
              </Stack>
            </Stack>

            <Paper
              elevation={0}
              sx={(theme) => ({
                borderRadius: 6,
                p: { xs: 3, md: 4 },
                border: "1px solid",
                borderColor: alpha(theme.palette.primary.main, 0.12),
                background: `linear-gradient(180deg, ${alpha(theme.palette.common.white, 0.9)} 0%, ${alpha(theme.palette.primary.main, 0.03)} 100%)`,
                boxShadow: `0 24px 72px ${alpha(theme.palette.common.black, 0.06)}`,
              })}
            >
              <Stack spacing={2.25}>
                <BrandMark size={48} />
                <Divider />
                <Stack spacing={1.25}>
                  <Typography variant="overline" sx={{ fontWeight: 900, letterSpacing: 1.3 }}>
                    What Healthcare gives you
                  </Typography>
                  {[
                    "One secure workspace for clinics and hospitals",
                    "Role-based access and tenant-aware controls",
                    "Operational workflows for every team",
                    "Optional AI assistance without forcing AI into core care",
                  ].map((item) => (
                    <Stack key={item} direction="row" spacing={1.25} alignItems="flex-start">
                      <CheckCircleRounded color="primary" sx={{ mt: 0.25, fontSize: 20 }} />
                      <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.65 }}>
                        {item}
                      </Typography>
                    </Stack>
                  ))}
                </Stack>
              </Stack>
            </Paper>
          </Box>
        </Container>

        <Box id="who-it-is-for" sx={{ py: { xs: 4, md: 6 } }}>
          <Container maxWidth="xl">
            <SectionHeading
              eyebrow="Who it is for"
              title="Built for healthcare operations"
              description="Jeevanam Healthcare is the operational platform used by clinics and hospitals. It keeps the workflows connected without forcing teams into a single rigid process."
            />
            <Box
              sx={{
                mt: 3,
                display: "grid",
                gridTemplateColumns: { xs: "1fr", md: "repeat(3, minmax(0, 1fr))" },
                gap: 2,
              }}
            >
              {audienceCards.map((card) => (
                <CapabilityCard key={card.title} {...card} />
              ))}
            </Box>
          </Container>
        </Box>

        <Box id="capabilities" sx={{ py: { xs: 4, md: 6 } }}>
          <Container maxWidth="xl">
            <SectionHeading
              eyebrow="Core capabilities"
              title="One platform. Connected workflows."
              description="Everything in Healthcare is designed to support the daily flow of a clinic or hospital without pulling teams out of their workspace."
            />
            <Box
              sx={{
                mt: 3,
                display: "grid",
                gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))", lg: "repeat(4, minmax(0, 1fr))" },
                gap: 2,
              }}
            >
              {operationsCards.map((card) => (
                <CapabilityCard key={card.title} {...card} />
              ))}
            </Box>
          </Container>
        </Box>

        <Box sx={{ py: { xs: 4, md: 6 } }}>
          <Container maxWidth="xl">
            <Box
              sx={(theme) => ({
                borderRadius: 6,
                border: "1px solid",
                borderColor: alpha(theme.palette.primary.main, 0.12),
                bgcolor: alpha(theme.palette.primary.main, 0.03),
                p: { xs: 3, md: 4 },
              })}
            >
              <Stack spacing={2.5}>
                <SectionHeading
                  eyebrow="Workflow"
                  title="Designed around the way healthcare teams actually work."
                  description="The product follows the patient journey and the operational handoffs that clinics and hospitals already use."
                />
                <Box
                  sx={{
                    display: "flex",
                    flexWrap: "wrap",
                    gap: 1.5,
                    alignItems: "stretch",
                  }}
                >
                  <WorkflowStep label="Patient arrives" detail="Registration, verification and the first operational handoff." />
                  <WorkflowStep label="Reception" detail="Scheduling, queueing and route-to-care coordination." />
                  <WorkflowStep label="Consultation" detail="Doctor-led clinical work, prescriptions and orders." />
                  <WorkflowStep label="Investigation / Pharmacy" detail="Lab processing, dispensing and related operations." />
                  <WorkflowStep label="Billing" detail="Payments, reconciliation and administrative closure." />
                  <WorkflowStep label="Follow-up" detail="Reminders, engagement and the next-care loop." />
                </Box>
              </Stack>
            </Box>
          </Container>
        </Box>

        <Box sx={{ py: { xs: 4, md: 6 } }}>
          <Container maxWidth="xl">
            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: { xs: "1fr", lg: "1.05fr 0.95fr" },
                gap: { xs: 3, md: 4 },
                alignItems: "start",
              }}
            >
              <Paper
                elevation={0}
                sx={(theme) => ({
                  p: { xs: 3, md: 4 },
                  borderRadius: 6,
                  border: "1px solid",
                  borderColor: alpha(theme.palette.primary.main, 0.12),
                })}
              >
                <Stack spacing={2}>
                  <SectionHeading
                    eyebrow="AI positioning"
                    title="AI when you want it. Manual workflows when you don't."
                    description="Jeevanam Healthcare supports optional AI-assisted workflows while keeping core clinical and operational processes usable without AI."
                  />
                  <Stack spacing={1.5}>
                    {[
                      "AI supports, but does not replace, clinicians and operational teams.",
                      "Manual workflows remain fully usable without enabling AI.",
                      "Use AI selectively for assistance, review and productivity.",
                    ].map((item) => (
                      <Stack key={item} direction="row" spacing={1.25} alignItems="flex-start">
                        <AutoAwesomeRounded color="primary" sx={{ mt: 0.25, fontSize: 20 }} />
                        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.65 }}>
                          {item}
                        </Typography>
                      </Stack>
                    ))}
                  </Stack>
                </Stack>
              </Paper>

              <Paper
                elevation={0}
                sx={(theme) => ({
                  p: { xs: 3, md: 4 },
                  borderRadius: 6,
                  border: "1px solid",
                  borderColor: alpha(theme.palette.primary.main, 0.12),
                })}
              >
                <Stack spacing={2}>
                  <SectionHeading
                    eyebrow="Platform foundations"
                    title="Security and control built in."
                    description="Healthcare operations need clear access boundaries and reliable auditability."
                  />
                  <Stack spacing={1.35}>
                    {[
                      "Role-based access",
                      "Multi-tenant architecture",
                      "Audit-ready workflows",
                      "Configurable modules",
                      "Controlled approvals",
                      "Secure operational access",
                    ].map((item) => (
                      <Stack key={item} direction="row" spacing={1.25} alignItems="flex-start">
                        <ShieldRounded color="primary" sx={{ mt: 0.25, fontSize: 20 }} />
                        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.65 }}>
                          {item}
                        </Typography>
                      </Stack>
                    ))}
                  </Stack>
                </Stack>
              </Paper>
            </Box>
          </Container>
        </Box>

        <Box id="ecosystem" sx={{ py: { xs: 4, md: 6 } }}>
          <Container maxWidth="xl">
            <SectionHeading
              eyebrow="Ecosystem"
              title="One connected healthcare ecosystem"
              description="Discover helps people find care. Connect helps providers manage their presence. Care supports the patient journey. Healthcare runs the clinical and administrative workspace."
            />
            <Box
              sx={{
                mt: 3,
                display: "grid",
                gridTemplateColumns: { xs: "1fr", md: "repeat(2, minmax(0, 1fr))", xl: "repeat(4, minmax(0, 1fr))" },
                gap: 2,
              }}
            >
              {ecosystemCards.map((card) => (
                <EcosystemCardView
                  key={card.title}
                  {...card}
                  cta={
                    card.title === "Jeevanam Connect" ? (
                      <Button
                        component="a"
                        href={providerWorkspaceUrl}
                        variant="contained"
                        size="small"
                        endIcon={<ArrowForwardRounded />}
                      >
                        Provider workspace
                      </Button>
                    ) : (
                      <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                        Product surface
                      </Typography>
                    )
                  }
                />
              ))}
            </Box>
          </Container>
        </Box>

        <Box sx={{ py: { xs: 4, md: 6 } }}>
          <Container maxWidth="xl">
            <Paper
              elevation={0}
              sx={(theme) => ({
                borderRadius: 6,
                p: { xs: 3, md: 4 },
                border: "1px solid",
                borderColor: alpha(theme.palette.primary.main, 0.12),
                bgcolor: alpha(theme.palette.primary.main, 0.03),
                textAlign: "center",
              })}
            >
              <Stack spacing={2} alignItems="center">
                <Typography variant="h4" sx={{ fontWeight: 950, letterSpacing: -0.8 }}>
                  Ready to manage your healthcare operations in one place?
                </Typography>
                <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 760, lineHeight: 1.75 }}>
                  Sign in to the existing Healthcare workspace to continue into your operational shell, tenant
                  context and role-based workflows.
                </Typography>
                <Stack direction="row" spacing={1.5} flexWrap="wrap" justifyContent="center" useFlexGap>
                  <Button component={Link} to="/login" size="large" variant="contained" endIcon={<ArrowForwardRounded />}>
                    Sign in to Healthcare
                  </Button>
                  <Button component={Link} to="#capabilities" size="large" variant="outlined">
                    Explore capabilities
                  </Button>
                </Stack>
              </Stack>
            </Paper>
          </Container>
        </Box>
      </Box>

      <Box component="footer" sx={{ py: 3 }}>
        <Container maxWidth="xl">
          <Stack spacing={0.75} alignItems="center" textAlign="center">
            <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>
              Jeevanam Healthcare
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Intelligent Healthcare Platform
            </Typography>
          </Stack>
        </Container>
      </Box>
    </Box>
  );
}
