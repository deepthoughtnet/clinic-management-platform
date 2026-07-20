import * as React from "react";
import { getClinicClock, type ClinicClock } from "../../../api/clinicApi";
import { getCarePilotTenantTimeZone } from "./carepilotFormatting";

type TimeZoneState = {
  clinicTimeZone: string;
  clinicClock: ClinicClock | null;
  loading: boolean;
};

export function useCarePilotTenantTimezone(token: string | null, tenantId: string | null) {
  const [state, setState] = React.useState<TimeZoneState>({
    clinicTimeZone: "UTC",
    clinicClock: null,
    loading: true,
  });

  React.useEffect(() => {
    let active = true;
    if (!token || !tenantId) {
      setState({ clinicTimeZone: "UTC", clinicClock: null, loading: false });
      return () => {
        active = false;
      };
    }

    setState((current) => ({ ...current, loading: true }));
    void getClinicClock(token, tenantId)
      .then((clock) => {
        if (!active) return;
        setState({
          clinicTimeZone: getCarePilotTenantTimeZone(clock.clinicTimeZone),
          clinicClock: clock,
          loading: false,
        });
      })
      .catch(() => {
        if (!active) return;
        setState({ clinicTimeZone: "UTC", clinicClock: null, loading: false });
      });

    return () => {
      active = false;
    };
  }, [tenantId, token]);

  return state;
}
