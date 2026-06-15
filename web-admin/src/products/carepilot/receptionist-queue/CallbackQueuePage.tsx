import ReceptionistQueuePage from "./ReceptionistQueuePage";

export default function CallbackQueuePage() {
  return (
    <ReceptionistQueuePage
      title="AI Receptionist Callback Queue"
      description="Manage callback requests raised by AIVA and keep patient follow-up windows on time."
      forcedType="CALLBACK_REQUEST"
      hideTypeFilter
    />
  );
}
