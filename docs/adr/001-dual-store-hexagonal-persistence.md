# ADR-001: One domain with Oracle and MongoDB persistence adapters

**Status:** accepted | **Date:** 2026-08-24

## Context

The take-home must demonstrate modernization and MongoDB while remaining able to run against an RDBMS. Forking the application into database-specific implementations would make functional equivalence difficult to prove and allow business rules to drift.

## Decision

Controllers and application services depend on `StorefrontStore`. Exactly one adapter is activated by `APP_STORE=oracle|mongo`; the value also selects a matching Spring profile. The Oracle adapter uses JPA entities and the Mongo adapter uses access-pattern-oriented documents. Domain records, validation, API representations, UI, and behavioral tests are shared.

## Consequences

- A store-contract test can be executed against both implementations.
- Database-specific concurrency primitives remain visible instead of being hidden behind a lowest-common-denominator ORM abstraction.
- Some adapter orchestration is duplicated deliberately. Extracting that code could obscure transaction boundaries and couple the domain to persistence mechanics.
- Adding a third database requires a new adapter, configuration profile, and passing the same contract suite.
