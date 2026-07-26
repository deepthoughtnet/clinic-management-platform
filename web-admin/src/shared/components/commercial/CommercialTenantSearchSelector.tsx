import * as React from "react";
import { Autocomplete, Box, Chip, Stack, TextField, Typography } from "@mui/material";

import type { PlatformTenant } from "../../../api/clinicApi";

export type CommercialTenantSearchSelectorTenant = PlatformTenant & {
  subscriptionName?: string | null;
  subscriptionStatus?: string | null;
  planTemplateName?: string | null;
  publishedVersionLabel?: string | null;
};

type Props = {
  tenants: CommercialTenantSearchSelectorTenant[];
  value: string;
  onChange: (tenantId: string) => void;
  label?: string;
  helperText?: string;
  loading?: boolean;
  disabled?: boolean;
};

function subscriptionSummary(tenant: CommercialTenantSearchSelectorTenant) {
  if (!tenant.subscriptionName && !tenant.planTemplateName) {
    return "No active commercial subscription";
  }
  const parts = [tenant.subscriptionName || "Subscription", tenant.planTemplateName, tenant.publishedVersionLabel, tenant.subscriptionStatus].filter(Boolean);
  return parts.join(" · ");
}

export default function CommercialTenantSearchSelector({
  tenants,
  value,
  onChange,
  label = "Commercial tenant under review",
  helperText = "Search by tenant name or code. Selection is URL-driven and independent of the header tenant.",
  loading = false,
  disabled = false,
}: Props) {
  const selectedTenant = React.useMemo(() => tenants.find((tenant) => tenant.id === value) || null, [tenants, value]);

  return (
    <Autocomplete
      disablePortal
      autoHighlight
      autoSelect
      options={tenants}
      value={selectedTenant}
      loading={loading}
      disabled={disabled}
      filterOptions={(options, state) => {
        const needle = state.inputValue.trim().toLowerCase();
        if (!needle) return options;
        return options.filter((tenant) => [tenant.name, tenant.code, tenant.status, tenant.subscriptionName || "", tenant.planTemplateName || ""].some((part) => part.toLowerCase().includes(needle)));
      }}
      getOptionLabel={(option) => option.name}
      isOptionEqualToValue={(option, current) => option.id === current.id}
      noOptionsText="No tenants found"
      onChange={(_, next) => onChange(next?.id || "")}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          helperText={helperText}
          placeholder="Search tenants"
          inputProps={{
            ...params.inputProps,
            autoComplete: "off",
            "aria-label": label,
          }}
        />
      )}
      renderOption={(props, option) => (
        <li {...props} key={option.id}>
          <Stack spacing={0.25} sx={{ py: 0.5, width: "100%" }}>
            <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
              <Typography sx={{ fontWeight: 800 }}>{option.name}</Typography>
              <Chip size="small" label={option.status} variant="outlined" />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              Tenant code: {option.code}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {subscriptionSummary(option)}
            </Typography>
          </Stack>
        </li>
      )}
      renderTags={() => null}
      renderGroup={(params) => params.group}
      sx={{ minWidth: 320 }}
      ListboxProps={{
        style: { maxHeight: 360 },
      }}
      slotProps={{
        popper: { placement: "bottom-start" },
      }}
    />
  );
}
