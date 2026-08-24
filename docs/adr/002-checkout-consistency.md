# ADR-002: Transactional, idempotent checkout with conditional inventory writes

**Status:** accepted | **Date:** 2026-08-24

## Invariants

1. Inventory never becomes negative.
2. One customer/idempotency key produces at most one order.
3. An order is never durable without all inventory decrements and a cleared cart.
4. A stale cart cannot be checked out silently.
5. Purchase-time price, product name, and address remain historically stable.

## Decision

Oracle executes checkout in a JPA transaction and decrements each product through a conditional `UPDATE ... WHERE stock >= quantity`. MongoDB executes the same sequence in a replica-set transaction and uses `updateFirst` with the stock constraint. Both persist a unique composite idempotency key and clear a versioned cart in the same transaction. Orders embed line/address snapshots.

## Rejected alternatives

- Read stock then write a new value: vulnerable to check-then-act races.
- Synchronize in the JVM: does not coordinate multiple instances or external writers.
- Distributed lock: unnecessary when each database can enforce the invariants itself.
- A collection/table per legacy relation: misses MongoDB single-document atomicity and the order-history access pattern.

## Consequences

MongoDB must be a replica set or sharded cluster. Multi-document transactions cost more than single-document writes, so the schema embeds values that change together; inventory stays separate because it has independent write contention. If throughput later requires reservations/events, introduce an explicit order state machine and outbox rather than weakening these invariants.
