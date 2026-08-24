# PetStore Modernization - Panel One-Pager

**Status:** presentation template - replace bracketed evidence and change statements to past tense only after implementation and verification.

## The outcome

The planned modernization takes the legacy Java Pet Store from J2EE-era JSP/Servlet/EJB/JMS technology to a Java 21 Spring Boot modular monolith, preserving agreed catalog-to-order behavior and introducing a MongoDB document model designed for the application's access patterns. MongoDB is a purposeful data-layer modernization, not a table-for-collection translation.

## Why this approach

- **Risk reduction:** establish a runnable legacy baseline and characterization tests first.
- **Right-sized architecture:** modular monolith provides clear boundaries without unearned microservice complexity.
- **Data aligned to behavior:** products serve catalog reads; orders are immutable purchase aggregates; carts protect concurrent updates.
- **Production discipline:** externalized config, health/readiness, structured logs, OpenAPI, Docker, CI, security validation, and Testcontainers.
- **Engineering ownership:** AI accelerated inventory/test drafting; every accepted change has human review, source evidence, and executed tests.

## Walkthrough map

```text
Browser -> /api/v1 -> Controller/DTO validation -> Domain use case -> Repository port -> MongoDB
                         |                       |                     |
                    Problem details          business rules        indexes/transactions
```

## Proof points to demo after implementation

1. Start both applications from documented clean-environment commands.
2. Browse catalog, add/update cart items, checkout, and view the new order.
3. Navigate one request end-to-end: route -> controller -> validation -> service -> repository -> document/index -> test.
4. Show legacy/modern parity evidence and one intentional safety improvement (idempotent checkout).
5. Show CI/test reports, configuration, health endpoint, and AI decision log.

## Trade-offs I would defend

| Choice | Rationale | Revisit when |
|---|---|---|
| Modular monolith | fastest safe delivery, coherent transactions, low operations burden | proven independent scaling/teams justify services |
| MongoDB aggregates | fewer joins and correct historical orders | access patterns/cardinality change |
| Idempotent checkout | prevents duplicate orders on retry | external payment/provider semantics are introduced |
| Characterization before rewrite | protects hidden legacy rules | baseline evidence demonstrates a stable contract |

## Delivery measures to report with evidence

Report the actual values only after execution: core parity rows complete, CI results for unit/integration/E2E suites, dependency/security scan result, reproducible Java 21/MongoDB start commands, demo-reset evidence, and measured performance. Do not replace measurement with a claim of equivalence.
