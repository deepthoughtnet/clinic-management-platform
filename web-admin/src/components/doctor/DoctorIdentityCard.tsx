import * as React from "react";
import { Box, Typography } from "@mui/material";
import { alpha } from "@mui/material/styles";

import DoctorAvatar from "./DoctorAvatar";

export type DoctorIdentityCardDoctor = {
  id: string;
  fullName: string;
  qualification?: string;
  primarySpecialization?: string;
  registrationNumber?: string;
  photoUrl?: string;
};

export interface DoctorIdentityCardProps {
  doctorId?: string;
  doctor?: {
    id: string;
    fullName: string;
    qualification?: string;
    primarySpecialization?: string;
    registrationNumber?: string;
    photoUrl?: string;
  };
  variant?: "avatar" | "compact" | "full";
  avatarSize?: number;
  loading?: boolean;
}

function doctorDisplayName(name: string | null | undefined, fallback = "Doctor") {
  const trimmed = name?.trim();
  if (!trimmed) {
    return fallback;
  }
  if (/^all doctors$/i.test(trimmed)) {
    return "All Doctors";
  }
  if (/^dr\.?\s/i.test(trimmed)) {
    return trimmed;
  }
  return `Dr. ${trimmed}`;
}

function displayValue(value: string | null | undefined, fallback = "—") {
  const trimmed = value?.trim();
  return trimmed || fallback;
}

function isAllDoctors(doctor: DoctorIdentityCardDoctor | undefined) {
  return !doctor || /^all doctors$/i.test(doctor.fullName.trim());
}

export default function DoctorIdentityCard({
  doctorId,
  doctor,
  variant = "full",
  avatarSize = 88,
  loading = false,
}: DoctorIdentityCardProps) {
  const placeholder = isAllDoctors(doctor);
  const resolvedName = doctor?.fullName?.trim() || (placeholder ? "All Doctors" : doctorId || "Doctor");
  const resolvedAvatarSize = variant === "avatar" ? avatarSize : Math.max(80, avatarSize);
  const containerPadding = variant === "avatar" ? 0 : 1.5;

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: variant === "avatar" ? 0 : 1.5,
        p: containerPadding,
        width: variant === "avatar" ? "auto" : "100%",
        bgcolor: variant === "avatar" ? "transparent" : "common.white",
        borderRadius: variant === "avatar" ? "50%" : 3,
        border: variant === "avatar" ? "none" : (theme) => `1px solid ${alpha(theme.palette.divider, 0.9)}`,
        boxShadow: variant === "avatar" ? "none" : (theme) => `0 10px 24px ${alpha(theme.palette.common.black, 0.06)}`,
      }}
    >
      <DoctorAvatar
        name={loading ? "Loading doctor" : (placeholder ? "All Doctors" : resolvedName)}
        photoUrl={placeholder ? null : doctor?.photoUrl}
        alt={doctorDisplayName(resolvedName, "Doctor")}
        sx={{
          width: resolvedAvatarSize,
          height: resolvedAvatarSize,
          flexShrink: 0,
          fontWeight: 800,
          bgcolor: placeholder ? "grey.300" : "primary.main",
          color: placeholder ? "text.secondary" : "primary.contrastText",
          border: variant === "avatar" ? (theme) => `1px solid ${theme.palette.divider}` : "none",
        }}
      />

      {variant === "avatar" ? null : (
        <Box sx={{ minWidth: 0, display: "grid", gap: 0.35, alignContent: "center" }}>
          <Typography variant={variant === "compact" ? "subtitle2" : "subtitle1"} sx={{ fontWeight: 800, lineHeight: 1.2 }} noWrap>
            {loading ? "Loading doctor…" : doctorDisplayName(resolvedName, "Doctor")}
          </Typography>

          {variant === "compact" ? (
            <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.35 }} noWrap>
              {displayValue(doctor?.primarySpecialization, placeholder ? "Clinic-wide view" : "—")}
            </Typography>
          ) : (
            <>
              <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.35 }} noWrap>
                {displayValue(doctor?.qualification, placeholder ? "Doctor group view" : "—")}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.35 }} noWrap>
                {displayValue(doctor?.primarySpecialization, placeholder ? "Clinic-wide view" : "—")}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.35 }} noWrap>
                {`Reg. No: ${displayValue(doctor?.registrationNumber)}`}
              </Typography>
            </>
          )}
        </Box>
      )}
    </Box>
  );
}
