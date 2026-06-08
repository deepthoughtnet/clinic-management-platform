import ReceptionistQueuePage from "./ReceptionistQueuePage";

export default function EscalationQueuePage() {
  return (
    <ReceptionistQueuePage
      title="AI Receptionist Escalation Queue"
      description="Review urgent CareAI escalations that need receptionist or clinic-admin intervention."
      forcedType="ESCALATION"
      hideTypeFilter
    />
  );
}
