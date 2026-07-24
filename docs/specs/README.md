# Specs

Approved feature work in Jeevanam is specification-driven.

Before implementing a non-trivial batch, create or update an approved spec under `docs/specs/` and align the implementation to that spec.

## Spec expectations

- clear product boundary
- explicit bounded context owner
- in-scope and out-of-scope behavior
- migration and compatibility plan
- validation and test expectations
- file ownership map

## Batch workflow

1. Approve the spec.
2. Implement additively.
3. Add architecture checks if the batch affects boundaries.
4. Validate with functional and regression tests.
5. Update the spec if behavior changes in later batches.
