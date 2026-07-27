import * as React from "react";
import { Box, Skeleton, Stack, Typography } from "@mui/material";
import LocalHospitalRoundedIcon from "@mui/icons-material/LocalHospitalRounded";

import { useAuth } from "../../../auth/useAuth";
import { getClinicProfile, getPrescriptionTemplate, type ClinicProfile, type PrescriptionTemplateConfig } from "../../../api/clinicApi";
import { useAuthenticatedImage } from "../../../hooks/useAuthenticatedImage";
import BrandMark from "./BrandMark";
import { TENANT_BRANDING_UPDATED_EVENT } from "./tenantBrandingEvents";

type TenantBrand = {
  name: string;
  subtitle: string | null;
  logoUrl: string | null;
};

function resolveTenantName(profile: ClinicProfile | null, selectedName: string | null, tenantName: string | null): string {
  return profile?.displayName?.trim()
    || profile?.clinicName?.trim()
    || selectedName?.trim()
    || tenantName?.trim()
    || "Clinic Workspace";
}

function resolveTenantSubtitle(profile: ClinicProfile | null): string | null {
  const parts = [profile?.city?.trim(), profile?.state?.trim()].filter((value): value is string => Boolean(value));
  if (parts.length === 0) {
    return null;
  }
  return parts.join(", ");
}

function resolveBrand(selectedName: string | null, tenantName: string | null, profile: ClinicProfile | null, template: PrescriptionTemplateConfig | null): TenantBrand {
  return {
    name: resolveTenantName(profile, selectedName, tenantName),
    subtitle: resolveTenantSubtitle(profile),
    logoUrl: template?.logoUrl || null,
  };
}

export default function TenantHeaderBrand() {
  const auth = useAuth();
  const tenantId = auth.selectedTenant?.id || auth.tenantId || null;
  const selectedName = auth.selectedTenant?.name || null;
  const [profile, setProfile] = React.useState<ClinicProfile | null>(null);
  const [template, setTemplate] = React.useState<PrescriptionTemplateConfig | null>(null);
  const [loading, setLoading] = React.useState(false);

  React.useEffect(() => {
    let cancelled = false;

    async function load() {
      if (!auth.accessToken || !tenantId) {
        setProfile(null);
        setTemplate(null);
        setLoading(false);
        return;
      }

      setLoading(true);
      try {
        const [nextProfile, nextTemplate] = await Promise.all([
          getClinicProfile(auth.accessToken, tenantId).catch(() => null),
          getPrescriptionTemplate(auth.accessToken, tenantId).catch(() => null),
        ]);
        if (cancelled) {
          return;
        }
        setProfile(nextProfile);
        setTemplate(nextTemplate);
      } catch (err) {
        if (!cancelled) {
          console.warn("[tenant-header-brand] failed to load tenant branding", {
            tenantId,
            error: err instanceof Error ? err.message : "Unknown error",
          });
          setProfile(null);
          setTemplate(null);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();

    const onBrandingUpdate = () => {
      void load();
    };

    window.addEventListener(TENANT_BRANDING_UPDATED_EVENT, onBrandingUpdate);
    return () => {
      cancelled = true;
      window.removeEventListener(TENANT_BRANDING_UPDATED_EVENT, onBrandingUpdate);
    };
  }, [auth.accessToken, tenantId]);

  const brand = React.useMemo(() => resolveBrand(selectedName, auth.tenantName, profile, template), [auth.tenantName, profile, selectedName, template]);
  const { objectUrl: logoObjectUrl, loading: logoLoading } = useAuthenticatedImage(brand.logoUrl);

  if (!tenantId) {
    return (
      <BrandMark
        compact
        showCopy
        title="Jeevanam Platform Administration"
        subtitle="Intelligent Healthcare Platform"
      />
    );
  }

  const hasLogo = Boolean(logoObjectUrl) && !loading && !logoLoading;

  return (
    <Stack direction="row" spacing={1.25} alignItems="center" sx={{ minWidth: 0, maxWidth: "100%" }}>
      <Box
        sx={(theme) => ({
          width: 42,
          height: 42,
          borderRadius: 2,
          flexShrink: 0,
          display: "grid",
          placeItems: "center",
          overflow: "hidden",
          bgcolor: "rgba(14, 165, 233, 0.08)",
          color: theme.palette.primary.main,
          border: "1px solid",
          borderColor: "divider",
        })}
      >
        {hasLogo ? (
          <Box
            component="img"
            src={logoObjectUrl || undefined}
            alt={`${brand.name} logo`}
            sx={{ width: "100%", height: "100%", objectFit: "contain", display: "block" }}
          />
        ) : loading || logoLoading ? (
          <Skeleton variant="rounded" width="100%" height="100%" />
        ) : (
          <LocalHospitalRoundedIcon aria-hidden sx={{ fontSize: 24 }} />
        )}
      </Box>

      <Box sx={{ minWidth: 0, maxWidth: "100%" }}>
        <Typography
          variant="subtitle1"
          sx={{
            fontWeight: 900,
            lineHeight: 1.1,
            letterSpacing: -0.2,
            whiteSpace: "nowrap",
            overflow: "hidden",
            textOverflow: "ellipsis",
          }}
          >
          {brand.name}
        </Typography>
        {brand.subtitle ? (
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{
              display: "block",
              lineHeight: 1.15,
              fontWeight: 600,
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
            }}
          >
            {brand.subtitle}
          </Typography>
        ) : null}
      </Box>
    </Stack>
  );
}
