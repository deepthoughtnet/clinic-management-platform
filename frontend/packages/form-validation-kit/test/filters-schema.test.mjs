import test from "node:test";
import assert from "node:assert/strict";

import { dateRangeFilterSchema, paginationFilterSchema, sortFilterSchema, tableSearchSchema } from "../dist/index.js";

test("table search schema accepts trimmed search text", () => {
  const result = tableSearchSchema.safeParse({
    search: "   follow up  ",
  });

  assert.equal(result.success, true);
  assert.equal(result.data.search, "follow up");
});

test("date range filter rejects inverted dates", () => {
  const result = dateRangeFilterSchema.safeParse({
    from: "2026-06-20",
    to: "2026-06-18",
  });

  assert.equal(result.success, false);
});

test("pagination filter rejects invalid page size", () => {
  const result = paginationFilterSchema.safeParse({
    page: 0,
    pageSize: 500,
  });

  assert.equal(result.success, false);
});

test("sort filter accepts asc and desc", () => {
  const result = sortFilterSchema.safeParse({
    sortField: "createdAt",
    sortDirection: "desc",
  });

  assert.equal(result.success, true);
});
