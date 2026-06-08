import ReceptionistQueuePage from "./ReceptionistQueuePage";

export default function AppointmentHandoffsPage() {
  return (
    <ReceptionistQueuePage
      title="AI Receptionist Appointment Handoffs"
      description="Continue appointment-related flows handed off by CareAI without losing booking context."
      forcedType="APPOINTMENT_HANDOFF"
      hideTypeFilter
    />
  );
}
