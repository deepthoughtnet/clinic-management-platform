import * as React from "react";

export type DoctorSelectionOption = {
  appUserId: string;
};

export type ActiveDoctorLike = {
  appUserId: string;
  membershipRole?: string | null;
  membershipStatus?: string | null;
  userStatus?: string | null;
};

type UseAutoSelectSingleDoctorOptions<TDoctor extends DoctorSelectionOption = DoctorSelectionOption> = {
  doctors: readonly TDoctor[];
  selectedDoctorId: string;
  setSelectedDoctorId: React.Dispatch<React.SetStateAction<string>>;
  tenantId: string | null | undefined;
  allowAllDoctors?: boolean;
};

function isDoctorIdValid<TDoctor extends DoctorSelectionOption>(doctors: readonly TDoctor[], doctorId: string) {
  return doctors.some((doctor) => doctor.appUserId === doctorId);
}

export function isActiveDoctorUser(user: ActiveDoctorLike) {
  if ((user.membershipRole || "").toUpperCase() !== "DOCTOR") {
    return false;
  }
  const membershipStatus = (user.membershipStatus || "ACTIVE").toUpperCase();
  const userStatus = (user.userStatus || "ACTIVE").toUpperCase();
  return membershipStatus === "ACTIVE" && userStatus === "ACTIVE";
}

export function useAutoSelectSingleDoctor<TDoctor extends DoctorSelectionOption>({
  doctors,
  selectedDoctorId,
  setSelectedDoctorId,
  tenantId,
  allowAllDoctors = true,
}: UseAutoSelectSingleDoctorOptions<TDoctor>) {
  const manualOverrideRef = React.useRef(false);
  const autoSelectedDoctorIdRef = React.useRef<string | null>(null);
  const previousTenantIdRef = React.useRef<string | null | undefined>(tenantId);
  const previousSelectedDoctorIdRef = React.useRef(selectedDoctorId);
  const suppressManualTrackingRef = React.useRef(false);

  const setDoctorIdProgrammatically = React.useCallback((nextDoctorId: string) => {
    suppressManualTrackingRef.current = true;
    setSelectedDoctorId(nextDoctorId);
  }, [setSelectedDoctorId]);

  React.useEffect(() => {
    if (previousTenantIdRef.current === tenantId) {
      return;
    }
    previousTenantIdRef.current = tenantId;
    manualOverrideRef.current = false;
    autoSelectedDoctorIdRef.current = null;
    previousSelectedDoctorIdRef.current = selectedDoctorId;
    suppressManualTrackingRef.current = false;
  }, [selectedDoctorId, tenantId]);

  React.useEffect(() => {
    const previousSelectedDoctorId = previousSelectedDoctorIdRef.current;
    previousSelectedDoctorIdRef.current = selectedDoctorId;
    if (suppressManualTrackingRef.current) {
      suppressManualTrackingRef.current = false;
      return;
    }
    if (selectedDoctorId === previousSelectedDoctorId) {
      return;
    }
    if (selectedDoctorId && selectedDoctorId === autoSelectedDoctorIdRef.current) {
      return;
    }
    if (!selectedDoctorId && !previousSelectedDoctorId) {
      return;
    }
    manualOverrideRef.current = true;
  }, [selectedDoctorId]);

  React.useEffect(() => {
    if (!tenantId || doctors.length === 0) {
      return;
    }

    const singleDoctorId = doctors.length === 1 ? doctors[0]?.appUserId || "" : "";
    const selectedIsValid = selectedDoctorId ? isDoctorIdValid(doctors, selectedDoctorId) : false;

    if (selectedDoctorId && !selectedIsValid) {
      if (singleDoctorId && !manualOverrideRef.current) {
        autoSelectedDoctorIdRef.current = singleDoctorId;
        setDoctorIdProgrammatically(singleDoctorId);
      } else {
        setDoctorIdProgrammatically("");
      }
      return;
    }

    if (!selectedDoctorId && singleDoctorId && !manualOverrideRef.current) {
      autoSelectedDoctorIdRef.current = singleDoctorId;
      setDoctorIdProgrammatically(singleDoctorId);
    }
  }, [doctors, selectedDoctorId, setDoctorIdProgrammatically, tenantId, allowAllDoctors]);
}
