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
      navigate("/patient/login");
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

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Patient portal</span>
        <h1>Laboratory Reports</h1>
        <p>View your lab orders, track report status, and download PDFs securely from your own tenant session.</p>
      </div>

      <div className="portal-dashboard-grid">
        <article className="dashboard-card">
          <strong>Lab orders</strong>
          <span>{orders.length}</span>
        </article>
        <article className="dashboard-card">
          <strong>Ready reports</strong>
          <span>{readyCount}</span>
        </article>
        <article className="dashboard-card">
          <strong>All reports</strong>
          <span>{reportCount}</span>
        </article>
      </div>

      {error ? <div className="portal-empty-state error-state"><strong>Unable to load</strong><p>{error}</p></div> : null}
      {loading ? <div className="portal-empty-state"><strong>Loading lab reports...</strong></div> : null}

      <div className="portal-section-grid">
        <section className="portal-panel">
          <div className="portal-panel-header">
            <h2>Lab Orders</h2>
          </div>
          {!orders.length ? (
            <div className="portal-empty-state"><strong>No lab orders yet</strong><p>Your ordered tests will appear here once the clinic creates them.</p></div>
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
                    <span>Ordered: {new Date(order.orderedAt).toLocaleString()}</span>
                    <span>Sample: {order.sampleCollectedAt ? new Date(order.sampleCollectedAt).toLocaleString() : "Pending"}</span>
                    <span>Review: {order.doctorReviewedAt ? new Date(order.doctorReviewedAt).toLocaleString() : "Pending"}</span>
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
            <div className="portal-empty-state"><strong>No reports available</strong><p>Completed and reviewed reports will appear here for download.</p></div>
          ) : (
            <div className="portal-list">
              {reports.map((order) => (
                <article className="portal-list-card" key={order.orderNumber}>
                  <div className="portal-list-card-header">
                    <strong>{order.orderNumber}</strong>
                    <span className={`status-pill status-${statusTone(order.status)}`}>{statusLabel(order.status)}</span>
                  </div>
                  <div className="portal-list-meta">
                    <span>Report: {order.reportGeneratedAt ? new Date(order.reportGeneratedAt).toLocaleString() : "Pending"}</span>
                    <span>Doctor review: {order.doctorReviewedAt ? new Date(order.doctorReviewedAt).toLocaleString() : "Pending"}</span>
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

      <div className="cta-row">
        <Link className="secondary-button" to="/patient/dashboard">Back to dashboard</Link>
        <button className="ghost-button" type="button" onClick={onSignOut}>Sign out</button>
      </div>
    </section>
  );
}
