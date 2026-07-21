import * as React from "react";
import { Autocomplete, TextField } from "@mui/material";
import { lookupCarePilotCampaigns, type CarePilotCampaignLookup } from "../../../api/clinicApi";
import { campaignTypeLabel } from "../campaigns/campaignLabels";

type Props = {
  token: string | null;
  tenantId: string | null;
  value: string;
  onChange: (campaignId: string) => void;
  onSelectOption?: (campaign: CarePilotCampaignLookup | null) => void;
  label: string;
  placeholder?: string;
  helperText?: React.ReactNode;
  disabled?: boolean;
  error?: boolean;
  limit?: number;
};

function optionLabel(option: CarePilotCampaignLookup) {
  return `${option.name} • ${campaignTypeLabel(option.campaignType)} • ${option.campaignReference}`;
}

export default function CampaignLookupField({
  token,
  tenantId,
  value,
  onChange,
  onSelectOption,
  label,
  placeholder = "Select campaign",
  helperText,
  disabled,
  error,
  limit = 20,
}: Props) {
  const [query, setQuery] = React.useState("");
  const [options, setOptions] = React.useState<CarePilotCampaignLookup[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [selectedOption, setSelectedOption] = React.useState<CarePilotCampaignLookup | null>(null);

  React.useEffect(() => {
    if (!token || !tenantId) return;
    const handle = window.setTimeout(async () => {
      setLoading(true);
      try {
        const rows = await lookupCarePilotCampaigns(token, tenantId, query, limit);
        setOptions(rows);
      } catch {
        setOptions([]);
      } finally {
        setLoading(false);
      }
    }, 250);
    return () => window.clearTimeout(handle);
  }, [query, limit, tenantId, token]);

  React.useEffect(() => {
    if (!value) {
      setSelectedOption(null);
      return;
    }
    const next = options.find((option) => option.id === value);
    if (next) {
      setSelectedOption(next);
    }
  }, [options, value]);

  const selected = React.useMemo(
    () => options.find((option) => option.id === value) ?? selectedOption ?? null,
    [options, value, selectedOption],
  );

  return (
    <Autocomplete<CarePilotCampaignLookup, false, false, false>
      fullWidth
      options={options}
      loading={loading}
      value={selected}
      inputValue={query}
      onInputChange={(_, nextQuery, reason) => {
        if (reason === "input" || reason === "clear") {
          setQuery(nextQuery);
        }
      }}
      onChange={(_, next) => {
        setSelectedOption(next ?? null);
        onSelectOption?.(next ?? null);
        onChange(next?.id || "");
      }}
      getOptionLabel={optionLabel}
      isOptionEqualToValue={(option, candidate) => option.id === candidate.id}
      disabled={disabled || !token || !tenantId}
      renderInput={(params) => (
        <TextField
          {...params}
          fullWidth
          label={label}
          placeholder={placeholder}
          error={error}
          helperText={helperText}
        />
      )}
    />
  );
}
