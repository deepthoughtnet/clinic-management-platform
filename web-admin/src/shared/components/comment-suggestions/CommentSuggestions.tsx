import * as React from "react";
import {
  Box,
  Chip,
  FormControl,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";

import RequiredLabel from "../../../components/forms/RequiredLabel";
import { appendSuggestionToRemarks, filterSuggestionChips, getCommentSuggestionCategoryConfig, type CommentSuggestionCategory } from "./commentSuggestionConfig.js";

type CommentSuggestionsProps = {
  category: CommentSuggestionCategory;
  selectedReason: string;
  remarks: string;
  onReasonChange: (value: string) => void;
  onRemarksChange: (value: string) => void;
  requiredReason?: boolean;
  maxRemarksLength?: number;
  disabled?: boolean;
  reasonLabel?: string;
  remarksLabel?: string;
  reasonError?: boolean;
  reasonHelperText?: React.ReactNode;
  remarksError?: boolean;
  remarksHelperText?: React.ReactNode;
  remarksInputRef?: React.Ref<HTMLInputElement | HTMLTextAreaElement>;
  dense?: boolean;
};

export default function CommentSuggestions({
  category,
  selectedReason,
  remarks,
  onReasonChange,
  onRemarksChange,
  requiredReason = false,
  maxRemarksLength = 250,
  disabled,
  reasonLabel = "Reason",
  remarksLabel = "Remarks",
  reasonError = false,
  reasonHelperText,
  remarksError = false,
  remarksHelperText,
  remarksInputRef,
  dense = false,
}: CommentSuggestionsProps) {
  const [query, setQuery] = React.useState("");
  const config = getCommentSuggestionCategoryConfig(category);
  const filteredSuggestions = React.useMemo(
    () => filterSuggestionChips(config.suggestions, query),
    [config.suggestions, query],
  );
  const showSearch = config.suggestions.length > 6;

  return (
    <Stack spacing={dense ? 0.75 : 1.25}>
      <FormControl fullWidth size="small" error={reasonError}>
        <InputLabel id={`${category}-reason-label`}>{requiredReason ? <RequiredLabel text={reasonLabel} required /> : reasonLabel}</InputLabel>
        <Select
          labelId={`${category}-reason-label`}
          label={reasonLabel}
          value={selectedReason}
          required={requiredReason}
          inputProps={{ "aria-required": requiredReason || undefined }}
          onChange={(event) => onReasonChange(String(event.target.value))}
          disabled={disabled}
        >
          <MenuItem value=""><em>None</em></MenuItem>
          {config.reasons.map((reason) => (
            <MenuItem key={reason} value={reason}>{reason}</MenuItem>
          ))}
        </Select>
        {reasonHelperText ? <FormHelperText>{reasonHelperText}</FormHelperText> : null}
      </FormControl>
      <TextField
        fullWidth
        multiline
        minRows={dense ? 2 : 3}
        size="small"
        label={remarksLabel}
        value={remarks}
        onChange={(event) => onRemarksChange(event.target.value.slice(0, maxRemarksLength))}
        disabled={disabled}
        error={remarksError}
        helperText={remarksHelperText || `${remarks.length}/${maxRemarksLength}`}
        inputRef={remarksInputRef}
        inputProps={{ maxLength: maxRemarksLength, "aria-required": false }}
      />
      {showSearch ? (
        <TextField
          fullWidth
          size="small"
          label="Search suggestions"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          disabled={disabled}
        />
      ) : null}
      {filteredSuggestions.length > 0 ? (
        <Box>
          <Typography variant="caption" color="text.secondary">Suggestions</Typography>
          <Stack direction="row" spacing={dense ? 0.5 : 0.75} useFlexGap flexWrap="wrap" sx={{ mt: dense ? 0.25 : 0.5 }}>
            {filteredSuggestions.map((suggestion) => (
              <Chip
                key={suggestion}
                size="small"
                clickable={!disabled}
                disabled={disabled}
                variant="outlined"
                label={suggestion}
                onClick={() => onRemarksChange(appendSuggestionToRemarks(remarks, suggestion))}
              />
            ))}
          </Stack>
        </Box>
      ) : null}
    </Stack>
  );
}
