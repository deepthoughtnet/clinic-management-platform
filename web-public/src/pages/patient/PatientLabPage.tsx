import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import {
  getPatientLabOrders,
  getPatientLabReports,
  getPatientLabReportPdfPath,
  isPatientPortalPatientSession,
  openPatientPortalPdf,
  type PatientPortalLabOrderResponse,
  type PatientPortalSession,
} from "../../api/patientPortal";
import { branding } from "../../branding";
import { formatDisplayDateTime } from "../../utils/dateDisplay";

function statusLabel(status: string | null | undefined) {
  return (status || "PENDING").replaceAll("_", " ");
}

function statusTone(status: string | null | undefined) {
  switch ((status || "").toUpperCase()) {
    case "DELIVERED":
    case "DOCTOR_REVIEWED":
      return "success";
    case "REPORT_READY":
    case "REPORT_GENERATED":
      return "info";
    case "SAMPLE_COLLECTED":
    case "PROCESSING":
    case "RESULT_ENTERED":
      return "warning";
    default:
      return "default";
  }
}

export default function PatientLabPage({
  session,
  onSignOut,
}: {
  session: PatientPortalSession | null;
  onSignOut: () => void;
}) {
  const navigate = useNavigate();
  const portalSession = isPatientPortalPatientSession(session) ? session : null;
  const [orders, setOrders] = useState<PatientPortalLabOrderResponse[]>([]);
  const [reports, setReports] = useState<PatientPortalLabOrderResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [workingOrder, setWorkingOrder] = useState<string | null>(null);

  useEffect(() => {
    if (!portalSession) {
      navigate("/patient/login?next=%2Fpatient%2Flab", { replace: true });
      return;
    }
    const activeSession = portalSession;
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const [orderRows, reportRows] = await Promise.all([
          getPatientLabOrders(activeSession),
          getPatientLabReports(activeSession),
        ]);
        if (!cancelled) {
          setOrders(orderRows);
          setReports(reportRows);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load lab reports");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [navigate, portalSession]);

  const openPdf = async (orderNumber: string) => {
    if (!portalSession) return;
    const activeSession = portalSession;
    setWorkingOrder(orderNumber);
    try {
      const path = await getPatientLabReportPdfPath(orderNumber);
      await openPatientPortalPdf(path, activeSession);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to open report PDF");
    } finally {
      setWorkingOrder(null);
    }
  };

  if (!portalSession) {
    return null;
  }

  const reportCount = reports.length;
  const readyCount = reports.filter((row) => ["REPORT_READY", "REPORT_GENERATED", "DOCTOR_REVIEWED", "DELIVERED"].includes((row.status || "").toUpperCase())).length;
  const loadErrorMessage = error ? "Unable to load laboratory reports. Please sign in again or try later." : null;
  const noLabData = !orders.length && !reports.length;

  return (
    <section className="page-section patient-portal-page patient-lab-page">
      <div className="patient-lab-shell">
        <header className="patient-lab-header">
          <div className="section-heading patient-lab-heading">
            <span className="eyebrow">{branding.productName} Patient Portal</span>
            <h1>Laboratory Reports</h1>
            <p>View your lab orders, track report status, and download PDFs securely from your own tenant session.</p>
          </div>
          <div className="cta-row patient-lab-actions">
            <Link className="secondary-button" to="/patient/dashboard">Back to dashboard</Link>
            <button className="ghost-button" type="button" onClick={onSignOut}>Sign out</button>
          </div>
        </header>

        <div className="portal-dashboard-grid patient-lab-stats">
          <article className="dashboard-card">
            <span>Lab orders</span>
            <strong>{orders.length}</strong>
          </article>
          <article className="dashboard-card">
            <span>Ready reports</span>
            <strong>{readyCount}</strong>
          </article>
          <article className="dashboard-card">
            <span>All reports</span>
            <strong>{reportCount}</strong>
          </article>
        </div>

        {loadErrorMessage ? (
          <div className="portal-empty-state error-state portal-alert">
            <strong>{loadErrorMessage}</strong>
          </div>
        ) : null}

        {loading ? (
          <div className="portal-empty-state portal-loading-state">
            <strong>Loading laboratory reports...</strong>
            <p>Please wait while we fetch your latest results.</p>
          </div>
        ) : null}

        {noLabData && !loading ? (
          <div className="portal-empty-state">
            <strong>No laboratory reports yet.</strong>
            <p>Reports will appear here once they are ready.</p>
          </div>
        ) : null}

        {!noLabData ? (
          <div className="portal-section-grid patient-lab-grid">
          <section className="portal-panel">
            <div className="portal-panel-header">
              <h2>Lab Orders</h2>
            </div>
            {!orders.length ? (
              <div className="portal-empty-state">
                <strong>No lab orders yet.</strong>
                <p>Your ordered tests will appear here once the clinic creates them.</p>
              </div>
            ) : (
              <div className="portal-list">
                {orders.map((order) => (
                  <article className="portal-list-card" key={order.orderNumber}>
                    <div className="portal-list-card-header">
                      <strong>{order.orderNumber}</strong>
                      <span className={`status-pill status-${statusTone(order.status)}`}>{statusLabel(order.status)}</span>
                    </div>
                    <div className="portal-list-meta">
                      <span>Doctor: {order.doctorName || "-"}</span>
                      <span>Ordered: {formatDisplayDateTime(order.orderedAt)}</span>
                      <span>Sample: {order.sampleCollectedAt ? formatDisplayDateTime(order.sampleCollectedAt) : "Pending"}</span>
                      <span>Review: {order.doctorReviewedAt ? formatDisplayDateTime(order.doctorReviewedAt) : "Pending"}</span>
                    </div>
                    {order.results.length ? (
                      <ul className="portal-inline-list">
                        {order.results.slice(0, 5).map((result, index) => (
                          <li key={`${order.orderNumber}-${index}`}>
                            <strong>{result.testName}</strong>
                            <span>{result.parameterName || result.componentName || "Result"}: {result.resultValue || "-"}</span>
                            <span>{result.referenceRange || "-"}</span>
                            <span>{result.resultFlag || "NORMAL"}</span>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="portal-help-text">Results are not available yet.</p>
                    )}
                  </article>
                ))}
              </div>
            )}
          </section>

          <section className="portal-panel">
            <div className="portal-panel-header">
              <h2>Reports</h2>
            </div>
            {!reports.length ? (
              <div className="portal-empty-state">
                <strong>No reports available.</strong>
                <p>Completed and reviewed reports will appear here for secure download.</p>
              </div>
            ) : (
              <div className="portal-list">
                {reports.map((order) => (
                  <article className="portal-list-card" key={order.orderNumber}>
                    <div className="portal-list-card-header">
                      <strong>{order.orderNumber}</strong>
                      <span className={`status-pill status-${statusTone(order.status)}`}>{statusLabel(order.status)}</span>
                    </div>
                    <div className="portal-list-meta">
                      <span>Report: {order.reportGeneratedAt ? formatDisplayDateTime(order.reportGeneratedAt) : "Pending"}</span>
                      <span>Doctor review: {order.doctorReviewedAt ? formatDisplayDateTime(order.doctorReviewedAt) : "Pending"}</span>
                      <span>Comments: {order.doctorComments || "-"}</span>
                    </div>
                    <div className="cta-row">
                      <button
                        className="primary-button"
                        type="button"
                        disabled={workingOrder === order.orderNumber}
                        onClick={() => void openPdf(order.orderNumber)}
                      >
                        {workingOrder === order.orderNumber ? "Opening..." : "Download Report"}
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
        ) : null}
      </div>
    </section>
  );
}
