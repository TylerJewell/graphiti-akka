# Phase 0 — Research

Most of what this phase normally discovers was discovered before the feature existed, by
probing the system being replaced. This file records those decisions in the expected form and
was explicit about the one question that remained genuinely open. That question is now
answered — see R-005.

## R-001 — Unit of concurrency and serialisation

**Decision**: key the write path by **partition**; one ingest at a time within a partition,
partitions independent.

**Rationale**: not a preference. The source system's own surfaces already behave this way —
one queue and one worker per partition on the agent-tool surface, a single global queue on the
request/response surface. Serialisation within a partition is also what prevents the lost
invalidation the source's core is vulnerable to when driven concurrently. A partition-keyed
entity gives both halves without a lock.

**Alternatives considered**: *Episode-keyed* — maximises parallelism and reintroduces the lost
invalidation, because contradicting facts share a partition. *Globally serialised* — matches
one surface exactly but not the other, and does not scale. Partition-keyed matches the stricter
of the two observed behaviours while permitting the looser.

## R-002 — Ingest as a durable multi-step process

**Decision**: model the seven ingest stages as a durable orchestration, **with automatic step
retry suppressed**.

**Rationale**: the stages are ordered, four of them call a model, and the source performs four
separate writes with nothing spanning them. A durable orchestration is the natural fit for the
shape. Retry must be suppressed because the runtime would otherwise recover from failures the
source system cannot recover from, producing states the specification says are unreachable.

**Alternatives considered**: *A consumer chain* — loses the explicit stage ordering the
specification requires and scatters failure semantics. *Synchronous in the endpoint* —
forbidden: the acknowledgement path must call no model.

## R-003 — Read side as projections

**Decision**: all reads resolve against projections; the write path is never queried to serve a
read.

**Rationale**: the read side must fuse semantic, lexical and graph-structural results into a
single ranking with a specific constant and a specific tie order. That is projection work, and
keeping it off the write path is what lets ingest stay serialised per partition without
serialising reads behind it.

**Alternatives considered**: *Query the write model directly* — simpler initially, but couples
read latency to ingest serialisation and fails RENDER-001 §3.4.

## R-004 — Where the two timelines live

**Decision**: transaction time comes from the store's own commit history; **valid time is
explicit metadata on each fact**.

**Rationale**: the store models one of the two axes natively and not the other. The failure
mode is specific and attractive: reconstructing prior state is a headline feature of the store,
so the obvious implementation answers *"what was true in the world at time T"* using
*"what did we believe at time T"* — a well-formed, confident, wrong answer.

**Alternatives considered**: *Both axes as explicit metadata* — uniform and discards a native
capability. Rejected, but it is the fallback if R-005 resolves badly.

## R-005 — The one open question

**Question**: can the store range-query a valid-time interval natively, or must the port filter
after retrieval?

**Status**: **RESOLVED 2026-08-19 against a running FlureeDB 4.1.5.**

**Answer: yes, natively — no retrieve-then-filter needed.** An `xsd:dateTime` interval stored as
explicit metadata is range-queryable in a single query, and the full valid-at predicate composes:

```
["filter", "(<= ?from \"2024-09-01T00:00:00Z\")",
           "(or (not (bound ?until)) (> ?until \"2024-09-01T00:00:00Z\"))"]
```

Verified end to end over the HTTP API against two facts with adjoining intervals: asking
2024-03-01 returns the earlier fact, 2024-09-01 returns the later one. Query time 1.0–1.1 ms.

**Consequences, all favourable:** the projection shape in R-003 stands unchanged, the read-latency
budget is unaffected, and R-004's split — valid time as explicit metadata, transaction time from
the commit log — is confirmed workable rather than merely plausible.

**Two details the probe surfaced that the implementation needs:**

- The filter vocabulary is `or` / `not` / `bound`, **not** `||`. `(coalesce ... true)` silently
  returns zero rows rather than erroring, which is the more dangerous of the two mistakes.
- The HTTP API is `POST /v1/fluree/query` and `POST /v1/fluree/update` — not the `/fluree/query`
  path the previous generation used. `/health` is the liveness path.

## R-006 — Prompts are fixed inputs, not implementation choices

**Decision**: carry the source system's extraction, resolution and summarisation instructions
**verbatim**, including a response field the source requires and never reads.

**Rationale**: the instructions and response schemas condition what the model produces, so they
are part of the observable contract. The unread field is not dead code — removing it changes the
model's output. A tool that eliminates unread fields will delete it and silently alter quality.

**Alternatives considered**: *Rewrite the prompts for the new stack* — invalidates every
recorded model interaction, so no comparison against the source is possible. Deferred to
Phase 2, where the change can be measured.

## R-007 — Testing strategy for model-mediated paths

**Decision**: pin model interactions in tests; assert exact equivalence for deterministic
paths and recorded-response equivalence for model-mediated ones.

**Rationale**: reproducibility is a property of the *path*, not the capability — a single
capability in this system changes tier four times. Deterministic branches (the invalidation
predicate, the identity cascade's exact and fuzzy stages, rank fusion) are exactly assertable
with no model at all, and those carry most of the risk.

**Alternatives considered**: *Live model calls in tests* — nondeterministic, slow, and
expensive; makes a failing test ambiguous between a porting error and model variance.

## Summary

| # | Decision | Status |
|---|---|---|
| R-001 | partition-keyed write path | settled by observed behaviour |
| R-002 | durable ingest, retry suppressed | settled; suppression tracked as OD-19 |
| R-003 | reads from projections only | settled by RENDER-001 §3.4 |
| R-004 | valid time explicit, transaction time native | settled in principle |
| R-005 | native range query for intervals? | **resolved — yes, natively (probed)** |
| R-006 | prompts carried verbatim | settled by D-006 |
| R-007 | pinned model responses in tests | settled |

**Nothing remains open.** R-005 was the only empirical unknown and it resolved favourably: the
projection shape stands, the latency budget is unaffected, and the persistence layer is unblocked.

Worth noting how it resolved. The answer was not derivable from the store's documentation — the
axis *argument* was, but whether an index covers a range predicate over an interval is a property
of a running instance. Reading would have produced a confident guess; probing produced the filter
vocabulary as well, including one form that silently returns zero rows instead of erroring.
