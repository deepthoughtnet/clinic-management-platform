import * as React from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";

import PharmacyOperationsPage from "./PharmacyOperationsPage";

export default function PharmacyProcurementPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const workspace = searchParams.get("workspace");
  const focus = searchParams.get("focus");
  const shouldNormalizeSupplierRoute =
    location.pathname === "/pharmacy/procurement"
    && (!workspace || (workspace === "suppliers" && focus !== "supplier"));

  React.useEffect(() => {
    console.info("[mount] ProcurementPage");
    return () => {
      console.info("[unmount] ProcurementPage");
    };
  }, []);

  React.useEffect(() => {
    console.info("[route]", location.pathname);
  }, [location.pathname]);

  React.useEffect(() => {
    if (location.pathname !== "/pharmacy/procurement") return;
    if (!shouldNormalizeSupplierRoute) return;
    navigate("/pharmacy/procurement?workspace=suppliers&focus=supplier", { replace: true });
  }, [location.pathname, navigate, shouldNormalizeSupplierRoute]);

  if (shouldNormalizeSupplierRoute) {
    return null;
  }

  return (
    <>
      <PharmacyOperationsPage key={location.pathname} mode="procurement" />
    </>
  );
}
