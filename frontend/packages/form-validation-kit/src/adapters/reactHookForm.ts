import { zodResolver } from "@hookform/resolvers/zod";
import type { ZodTypeAny } from "zod";

export function zodFormResolver<TSchema extends ZodTypeAny>(schema: TSchema) {
  return zodResolver(schema);
}
