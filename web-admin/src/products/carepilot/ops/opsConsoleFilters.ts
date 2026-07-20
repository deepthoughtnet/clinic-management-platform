import type { CarePilotChannelType, CarePilotExecutionStatus } from "../../../api/clinicApi";

export type OpsFilters = {
  campaignId: string;
  campaignRef: string;
  startDate: string;
  endDate: string;
  channel: CarePilotChannelType | "";
  status: CarePilotExecutionStatus | "";
  providerName: string;
  reminderWindow: "" | "H24" | "H2";
  retryableOnly: boolean;
};

export const DEFAULT_OPS_FILTERS: OpsFilters = {
  campaignId: "",
  campaignRef: "",
  startDate: "",
  endDate: "",
  channel: "",
  status: "",
  providerName: "",
  reminderWindow: "",
  retryableOnly: false,
};

export const OPS_QUEUED_STATUSES: CarePilotExecutionStatus[] = ["QUEUED", "PROCESSING", "RETRY_SCHEDULED"];
export const OPS_FAILED_STATUSES: CarePilotExecutionStatus[] = ["FAILED", "DEAD_LETTER", "RETRY_SCHEDULED"];

export function parseOpsFilters(params: URLSearchParams): OpsFilters {
  return {
    campaignId: "",
    campaignRef: params.get("campaignRef") || "",
    startDate: params.get("startDate") || "",
    endDate: params.get("endDate") || "",
    channel: (params.get("channel") || "") as OpsFilters["channel"],
    status: (params.get("status") || "") as OpsFilters["status"],
    providerName: params.get("providerName") || "",
    reminderWindow: (params.get("reminderWindow") || "") as OpsFilters["reminderWindow"],
    retryableOnly: params.get("retryableOnly") === "true",
  };
}

export function serializeOpsFilters(filters: OpsFilters) {
  const params = new URLSearchParams();
  if (filters.campaignRef) params.set("campaignRef", filters.campaignRef);
  if (filters.startDate) params.set("startDate", filters.startDate);
  if (filters.endDate) params.set("endDate", filters.endDate);
  if (filters.channel) params.set("channel", filters.channel);
  if (filters.status) params.set("status", filters.status);
  if (filters.providerName) params.set("providerName", filters.providerName);
  if (filters.reminderWindow) params.set("reminderWindow", filters.reminderWindow);
  if (filters.retryableOnly) params.set("retryableOnly", "true");
  return params;
}
