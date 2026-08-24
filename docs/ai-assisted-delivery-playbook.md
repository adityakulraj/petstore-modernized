# AI-Assisted Delivery Playbook

This is a reusable, human-owned workflow for iteratively modernizing PetStore with an AI assistant. It records the prompts/specifications that should be supplied, the evidence expected back, and the review decisions that remain the engineer's responsibility.

## Guardrails to give the assistant first

```text
You are assisting a Java application modernization. Do not invent legacy behavior.
Treat the checked-in source and captured execution evidence as authoritative. Preserve
observable behavior unless a requirement explicitly changes it. Return assumptions,
unknowns, file/line references, security implications, and a verification plan. Do
not emit secrets, use production data, or make architecture decisions without options
and trade-offs. All generated code must have focused tests and must compile/run.
```

## Inputs to prepare before prompting

1. `legacy-inventory.md`: modules, build/runtime versions, source layout, routes, EJBs, JSPs, JMS, DB schemas, properties files, integrations, known setup failures.
2. `behavior-catalog.md`: each route/journey, actor, preconditions, observed output, errors, and evidence link (test/screenshot/log).
3. `parity-matrix.md`: requirement ID, legacy evidence, modern implementation location, test IDs, status, intentional differences/approval.
4. ADR templates and acceptance criteria from the implementation PRD.
5. Sanitized fixtures only; never customer data, credentials, private keys, or unrestricted production logs.

## Prompt sequence

| Stage | Prompt/specification to provide | Expected deliverable | Human decision/review |
|---|---|---|---|
| 1. Inventory | “Read only these paths. Produce a component/dependency map with file/line citations. List unknowns; do not propose a rewrite.” | `legacy-inventory.md` draft | Verify every critical claim against source/runtime. |
| 2. Behavior discovery | “For these routes, trace request -> validation -> business rule -> persistence -> response. Make a characterization-test table.” | behavior catalog and test candidates | Run paths; distinguish observed from inferred behavior. |
| 3. Target options | “Given these constraints, compare modular monolith, microservices, and replatform-only. Include risks and reversibility.” | decision options | Choose architecture; write ADR yourself. |
| 4. Data model | “From these reads/writes and relational schema, propose MongoDB aggregates, indexes, embedding/reference rationale, ownership, and migration reconciliation checks.” | model proposal | Reject table-shaped collections; validate query/cardinality/transaction assumptions. |
| 5. Slice spec | “Write an implementation plan for catalog only. Include API contract, validation, error cases, test layers, files to add/change.” | small executable plan | Confirm it maps to parity matrix and fits scope. |
| 6. Code draft | “Implement only the approved catalog plan. Keep dependencies inward. Return a diff summary and tests. Do not refactor unrelated code.” | focused patch | Review security, API, idioms, compilation, and tests. |
| 7. Test challenge | “Try to falsify this implementation: invalid input, authorization, concurrency, retries, precision, missing data, and changed legacy behavior.” | adversarial test list | Add high-value tests; execute them. |
| 8. Debugging | “Here are the exact command, log, reproduction, expected and actual behavior. Rank hypotheses and propose smallest diagnostic first.” | diagnosis plan | Run diagnostics; do not accept speculative fix. |
| 9. Review | “Review this diff against the PRD, ADRs, parity matrix, and OWASP concerns. Categorize blocking/non-blocking findings with evidence.” | review report | Own final merge decision. |
| 10. Presentation | “Create a 90-minute walkthrough outline using only these verified artifacts. Mark claims that need live proof.” | talk track | Rehearse from clean environment; remove unverified claims. |

## Prompt examples that improve quality

**Characterization:** “Do not say ‘equivalent’ until you identify the legacy method and a runnable test. What does an unauthenticated request, invalid quantity, and duplicate action do today?”

**Data design:** “For each proposed embedded field, identify its update owner, maximum cardinality, historical-value requirement, and query that benefits. For each reference, explain why it is not embedded.”

**Code review:** “Assume this is a public API. Look for mass assignment, authorization bypass, money precision, error leakage, missing index, retry duplication, and invalid-state transitions.”

## AI decision log template

```markdown
## AI-### - <short title>
- Task and inputs: <sanitized scope>
- AI suggestion: <summary/link to diff>
- Evidence reviewed: <tests, source files, command output>
- Decision: accepted / modified / rejected
- Human rationale: <trade-off, security/performance/parity reason>
- Follow-up verification: <command/test/result>
```

## Examples to discuss in the panel

- **Accepted:** AI accelerated a route/dependency inventory, then the engineer checked cited files and used it to prioritize characterization tests.
- **Modified:** AI suggested a collection per relational table. The engineer replaced it with an order aggregate containing immutable line-item snapshots because order-history reads and historical correctness drive the model.
- **Rejected:** AI suggested splitting every legacy EJB into a microservice. The engineer retained a modular monolith because no independent deployment/scale/team boundary justified distributed transactions and operational complexity.
- **Rejected/fixed:** AI used `double` for money or trusted a client-provided total. The engineer used a decimal money representation and calculates totals from canonical server-side data.

## Evidence standard

AI output is a hypothesis until it is connected to source/behavior evidence and passes a human-reviewed test. Keep prompts, generated diffs, decisions, test commands, and results in the repository so the panel can inspect engineering ownership without needing access to a chat transcript.
