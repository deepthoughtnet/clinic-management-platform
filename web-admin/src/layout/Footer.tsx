import * as React from "react";
import { useLocation } from "react-router-dom";
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Switch,
  Stack,
  Typography,
} from "@mui/material";
import { useAuth } from "../auth/useAuth";
import { hasTenantModule } from "../auth/moduleEntitlements";
import { useDoctorAiAssistPreference } from "../hooks/useDoctorAiAssistPreference";
import { footerBrandingLine } from "../branding";

const DOCTOR_WORKSPACE_PATH_RE = /^\/(consultations|prescriptions|lab|queue)(?:\/|$)/i;

export default function Footer() {
  const auth = useAuth();
  const location = useLocation();
  const [disclaimerOpen, setDisclaimerOpen] = React.useState(false);
  const [doctorAiAssistEnabled, setDoctorAiAssistEnabled] = useDoctorAiAssistPreference({
    tenantId: auth.tenantId,
    appUserId: auth.appUserId,
    username: auth.username,
  });

  const rawVersion = import.meta.env.VITE_APP_VERSION?.trim() || "";
  const normalizedVersion = rawVersion && rawVersion !== "0.0.0" && rawVersion !== "v0.0.0"
    ? (rawVersion.startsWith("v") ? rawVersion : `v${rawVersion}`)
    : "";
  const showDoctorDisclaimer = auth.rolesUpper.includes("DOCTOR")
    && DOCTOR_WORKSPACE_PATH_RE.test(location.pathname)
    && hasTenantModule(auth, "aiCopilot");
  const compactDisclaimer = "AI assistance only. Jeevanam AI-generated clinical suggestions, including diagnoses, medications, dosages, investigations, drug interactions, summaries, treatment guidance, and patient advice, are assistive only and may be incomplete or inaccurate. All AI-generated content must be independently reviewed and validated by the treating healthcare professional before clinical use. AI does not replace professional clinical judgment, and the treating clinician remains responsible for final clinical decisions.";

  const handleDisclaimerOpen = React.useCallback(() => setDisclaimerOpen(true), []);
  const handleDisclaimerClose = React.useCallback(() => setDisclaimerOpen(false), []);

  return (
    <Box
      sx={{
        px: { xs: 2, md: 3 },
        py: 1.25,
        borderTop: "1px solid",
        borderColor: "divider",
        bgcolor: "background.paper",
      }}
    >
      <Stack spacing={0.8}>
        {showDoctorDisclaimer ? (
          <Box
            sx={{
              px: 1.25,
              py: 0.9,
              borderRadius: 2,
              border: "1px solid",
              borderColor: "warning.light",
              bgcolor: "warning.50",
            }}
          >
            <Stack spacing={0.85}>
              <Stack direction={{ xs: "column", md: "row" }} spacing={1} alignItems={{ xs: "flex-start", md: "center" }} justifyContent="space-between">
                <Stack spacing={0.35} sx={{ minWidth: 0 }}>
                  <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
                    <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.45, fontWeight: 800 }}>
                      AI Assist:
                    </Typography>
                    <Switch
                      size="small"
                      checked={doctorAiAssistEnabled}
                      onChange={(_, checked) => setDoctorAiAssistEnabled(checked)}
                      inputProps={{ "aria-label": `AI Assist ${doctorAiAssistEnabled ? "On" : "Off"}` }}
                    />
                    <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.45, fontWeight: 800 }}>
                      {doctorAiAssistEnabled ? "On" : "Off"}
                    </Typography>
                  </Stack>
                  {!doctorAiAssistEnabled ? (
                    <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.45 }}>
                      AI assistance is turned off. Manual consultation workflows remain fully available.
                    </Typography>
                  ) : null}
                </Stack>
                <Button type="button" size="small" variant="text" onClick={handleDisclaimerOpen} sx={{ alignSelf: { xs: "flex-start", md: "center" }, flexShrink: 0 }}>
                  View full AI disclaimer
                </Button>
              </Stack>
              <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.45, fontWeight: 700 }}>
                {compactDisclaimer}
              </Typography>
            </Stack>
          </Box>
        ) : null}
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 1,
            flexWrap: "wrap",
          }}
        >
          {normalizedVersion ? (
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
              {normalizedVersion}
            </Typography>
          ) : null}
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textAlign: "center" }}>
            {footerBrandingLine()}
          </Typography>
        </Box>
      </Stack>

      <Dialog
        open={disclaimerOpen}
        onClose={handleDisclaimerClose}
        maxWidth="md"
        fullWidth
        aria-labelledby="ai-clinical-disclaimer-title"
        aria-describedby="ai-clinical-disclaimer-description"
      >
        <DialogTitle id="ai-clinical-disclaimer-title">AI Clinical Assistance Disclaimer</DialogTitle>
        <DialogContent dividers id="ai-clinical-disclaimer-description">
          <Stack spacing={1}>
            <Typography variant="body2" sx={{ lineHeight: 1.7 }}>
              Jeevanam AI features are designed only to assist qualified healthcare professionals with clinical documentation, decision support, medication suggestions, investigations, summaries, treatment planning, patient communication, and related workflow guidance.
            </Typography>
            <Typography variant="body2" sx={{ lineHeight: 1.7 }}>
              AI-generated content may be incomplete, inaccurate, outdated, inconsistent, or inappropriate for a specific patient and must not be relied upon as a substitute for independent professional clinical judgment.
            </Typography>
            <Typography variant="body2" sx={{ lineHeight: 1.7 }}>
              The treating healthcare professional is responsible for independently reviewing and validating all AI-generated recommendations before using them in patient care. This includes, but is not limited to:
            </Typography>
            <Box component="ul" sx={{ m: 0, pl: 3, "& li": { mb: 0.45 } }}>
              <li><Typography variant="body2">diagnoses and differential diagnoses</Typography></li>
              <li><Typography variant="body2">medication selection</Typography></li>
              <li><Typography variant="body2">medication dosage, frequency, route, duration, and timing</Typography></li>
              <li><Typography variant="body2">allergies, contraindications, precautions, and drug interactions</Typography></li>
              <li><Typography variant="body2">investigations, laboratory tests, and imaging recommendations</Typography></li>
              <li><Typography variant="body2">interpretation of laboratory or clinical findings</Typography></li>
              <li><Typography variant="body2">treatment plans</Typography></li>
              <li><Typography variant="body2">clinical notes and summaries</Typography></li>
              <li><Typography variant="body2">follow-up recommendations</Typography></li>
              <li><Typography variant="body2">patient advice and communication</Typography></li>
            </Box>
            <Typography variant="body2" sx={{ lineHeight: 1.7 }}>
              Before acting on AI-generated content, the treating healthcare professional should independently consider all relevant patient-specific information, including clinical history, examination findings, allergies, current and previous medications, comorbidities, laboratory and imaging results, applicable clinical guidelines, approved drug references, and other relevant medical information.
            </Typography>
            <Typography variant="body2" sx={{ lineHeight: 1.7 }}>
              Jeevanam does not independently diagnose, prescribe, or provide medical treatment. AI-generated suggestions do not constitute an independent medical opinion or final clinical decision. The decision to accept, modify, reject, or ignore any AI-generated recommendation remains with the treating healthcare professional.
            </Typography>
            <Typography variant="body2" sx={{ lineHeight: 1.7 }}>
              Where AI-generated information conflicts with the healthcare professional’s judgment, established clinical guidance, approved medical references, or patient-specific evidence, the healthcare professional should rely on independent clinical judgment and appropriate authoritative sources.
            </Typography>
            <Typography variant="body2" sx={{ lineHeight: 1.7 }}>
              If AI output is uncertain, incomplete, inconsistent, or potentially unsafe, it should not be used until independently verified.
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button type="button" onClick={handleDisclaimerClose} autoFocus>
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
