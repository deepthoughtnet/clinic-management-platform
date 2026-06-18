export type RequiredFieldDefinition = {
  name: string;
  label?: string;
};

export function createRequiredFields(fields: readonly (string | RequiredFieldDefinition)[]) {
  const normalized = fields.map((field) => (typeof field === "string" ? { name: field } : field));
  const names = new Set(normalized.map((field) => field.name));
  return {
    fields: normalized,
    requiredFieldNames: [...names],
    isRequiredField(name: string) {
      return names.has(name);
    },
  };
}
