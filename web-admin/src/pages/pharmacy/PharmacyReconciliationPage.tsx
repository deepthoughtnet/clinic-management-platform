import * as React from "react";
import { useLocation } from "react-router-dom";

import PharmacyOperationsPage from "./PharmacyOperationsPage";

export default function PharmacyReconciliationPage() {
  const location = useLocation();
  return (
    <>
      <PharmacyOperationsPage key={location.pathname} mode="reconciliation" />
    </>
  );
}
