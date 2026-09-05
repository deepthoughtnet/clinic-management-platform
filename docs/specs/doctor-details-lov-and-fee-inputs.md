# Doctor Details LOV and Fee Input Polish

## Scope

This spec covers Doctor Details form presentation and input behavior in `web-admin`:

1. Required-field indicators should render exactly once per required field.
2. Specialization should present as a selectable LOV with an `Other` option.
3. Qualification should present as a selectable LOV with an `Other` option where supported by the current persistence model.
4. OPD / follow-up / emergency fee inputs should not expose browser spinner controls.

## Ownership

- UI: `web-admin`
- Doctor profile API: existing doctor profile endpoint in `api-bff`
- Persistence: unchanged, existing doctor profile string/list fields remain authoritative

## Behavior

- Required labels remain visually marked with a single red asterisk.
- The browser/MUI required marker must not duplicate the custom required marker.
- Doctor profile specialization remains stored using the existing specialization string/list model.
- Doctor profile qualification remains stored using the existing qualification string model.
- Fee inputs should accept direct numeric entry, including decimals where already supported, without spinner controls.

## Safety

- No schema migration is required.
- Backend validation remains authoritative.
- Existing doctor profile save/reload behavior remains unchanged.
