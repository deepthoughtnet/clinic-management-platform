import * as React from "react";
import { Box, Typography } from "@mui/material";
import { alpha } from "@mui/material/styles";
import DoctorAvatar from "./DoctorAvatar";

export type DoctorIdentityCardDoctor = {
  name: string | null | undefined;
  photoUrl?: string | null | undefined;
  qualification?: string | null | undefined;
  primarySpecialization?: string | null | undefined;
  specialization?: string | null | undefined;
  registrationNumber?: string | null | undefined;
};

type DoctorIdentityCardProps = {
  doctor: DoctorIdentityCardDoctor;
  variant?: "default" | "compact";
};

function doctorDisplayName(name: string | null | undefined) {
  const trimmed = name?.trim();
  if (!trimmed) {
    return "Doctor";
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

export default function DoctorIdentityCard({ doctor, variant = "default" }: DoctorIdentityCardProps) {
  const compact = variant === "compact";
  const specialization = doctor.primarySpecialization || doctor.specialization;

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: compact ? 1.5 : 2,
        p: compact ? 1.5 : 2,
        width: "100%",
        bgcolor: "common.white",
        borderRadius: 3,
        border: (theme) => `1px solid ${alpha(theme.palette.divider, 0.9)}`,
        boxShadow: (theme) => `0 10px 24px ${alpha(theme.palette.common.black, 0.06)}`,
      }}
    >
      <DoctorAvatar
        name={doctor.name}
        photoUrl={doctor.photoUrl}
        alt={doctorDisplayName(doctor.name)}
        sx={{
          width: compact ? 72 : 88,
          height: compact ? 72 : 88,
          flexShrink: 0,
          fontWeight: 800,
          bgcolor: "primary.main",
          color: "primary.contrastText",
        }}
      />

      <Box sx={{ minWidth: 0, display: "grid", gap: 0.4, alignContent: "center" }}>
        <Typography variant={compact ? "subtitle1" : "h6"} sx={{ fontWeight: 800, lineHeight: 1.2 }} noWrap>
          {doctorDisplayName(doctor.name)}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.35 }} noWrap>
          {displayValue(doctor.qualification)}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.35 }} noWrap>
          {displayValue(specialization)}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.35 }} noWrap>
          {`Reg. No: ${displayValue(doctor.registrationNumber)}`}
        </Typography>
      </Box>
    </Box>
  );
}
