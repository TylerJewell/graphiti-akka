# Implementation Plan: Temporal Memory Graph

**Branch**: `001-temporal-memory-graph` | **Date**: 2026-08-19 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-temporal-memory-graph/spec.md`

## Summary

Reproduce an existing temporally-aware knowledge-graph memory engine on the Akka SDK, matching
its caller-visible surfaces exactly and its behaviour — including its defects — so that any
difference in output is evidence of a porting error rather than an improvement.

Approach is fixed by two standing documents and is **not re-decided here**:

- **Behaviour** — `graphiti-port/specs/SPEC-001…008`, stack-neutral, each grounded in executed
  probes rather than source-reading.
- **Rendering** — `graphiti-port/specs/RENDER-001-akka-fluree.md`, which fixes the target
  stack, the precedence rule, five falsifiable architectural qualities, the component mapping
  and the budgets.

The single most consequential design input is not a preference but a discovered contract:
**ingest is asynchronous and serialised per partition at the source's own surfaces**
(SPEC-007 §3.0). That makes partition-keyed write components and a durable multi-step ingest
the shape the contract already demands, rather than an architectural choice to argue.

## Technical Context

**Language/Version**: Java 21+
**Primary Dependencies**: Akka SDK 3.4+; FlureeDB client; one configured model provider
**Storage**: FlureeDB — graph, vector and full-text in one store (RENDER-001 §1, D-001)
**Testing**: JUnit 5 + `TestKitSupport`; `TestModelProvider` for model-mediated paths; the
source project's own benchmark harness for end-to-end parity
**Target Platform**: Akka service, container-deployed
**Project Type**: Single service — no frontend (the source system has **0** graphical assets
and **0** command-line commands; SPEC-002 §1)
**Performance Goals**: acknowledgement p95 < 50 ms; read p95 < 500 ms excluding model time
(RENDER-001 §5)
**Constraints**: cold start ≤ 5 s; resident ≤ 600 MB; exactly 1 store process; equivalence
scoped to serialised ingest
**Scale/Scope**: 11 request/response operations, 13 agent tools, 9 wire types, 4 domain
concepts, ~14,131 lines of source behaviour to reproduce

No `NEEDS CLARIFICATION` remains. Every value above is fixed by RENDER-001 or by a source
specification; none was invented here.

## Constitution Check

*GATE: evaluated before Phase 0, re-evaluated after Phase 1.*

### I. Akka SDK First (NON-NEGOTIABLE) — PASS with two justified dependencies

All components are SDK primitives: Endpoints, an MCP Endpoint, a Workflow, Entities, Views,
Consumers. No custom orchestration, no third-party HTTP or persistence framework.

Two external dependencies, both justified as the constitution requires:

- **FlureeDB** — the feature needs graph traversal, vector similarity and full-text search
  combined in a single ranking (SPEC-004 §3.1). The SDK does not provide graph or vector
  search, and splitting them across stores would reintroduce exactly the multi-backend
  redundancy RENDER-001 §3.1 forbids.
- **A model provider** — extraction, resolution and summarisation are model-mediated by
  specification (SPEC-006, SPEC-003, SPEC-008). Configured, not fixed.

### II. Design Principles — PASS

- **Domain independence**: the behavioural specs are stack-neutral by construction (D-005), so
  domain records carry the temporal rules with no SDK types. The invalidation predicate
  (SPEC-001 §3.2) is a pure function on two facts and is unit-testable with no runtime.
- **API isolation**: endpoints define their own request/response records. D-004 constrains
  what those records must *look like on the wire*; it does not permit exposing domain
  internals, and the two are satisfied together by a `toApi` conversion.
- **Single responsibility**: one component per capability, mirroring the one-spec-per-capability
  split already established.
- **Descriptive naming**: domain-aligned throughout — `EpisodeIngestWorkflow`,
  `FactsByPartitionView`. No `Manager`, `Service` or bare `Event`.

### III. Test Coverage — PASS, with a stronger obligation than usual

Every behavioural claim is already backed by an executed probe in the source project. The port
inherits those as its conformance targets rather than inventing new ones, and adds SDK-level
unit and integration tests per component.

### IV. Simplicity — **PASS only with justification; see Complexity Tracking**

RENDER-001 §3.2 is stricter than YAGNI: a persisted type that no specification names is a
violation, not merely unnecessary. Four domain concepts, no more.

But D-006 requires reproducing defects, and OD-19 requires *suppressing* a runtime capability
that comes for free. Both look like gratuitous complexity from the constitution's point of
view and are recorded below.

## Project Structure

### Documentation (this feature)

```text
specs/001-temporal-memory-graph/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── checklists/
    └── requirements.md  # from /akka:specify
```

### Source Code (repository root)

```text
src/main/java/io/akka/memory/
├── domain/                        # no Akka types; pure temporal + identity rules
│   ├── Fact.java                  # bi-temporal record + the invalidation predicate
│   ├── Entity.java                # name, types, summary, attributes
│   ├── Episode.java               # content, kind, reference time, partition
│   ├── PartitionState.java        # aggregate state keyed by partition
│   ├── PartitionEvent.java        # sealed interface, @TypeName per event
│   ├── EntityIdentity.java        # normalisation, entropy gate, shingle/minhash cascade
│   └── RankFusion.java            # fusion with rank_const = 1, first-seen tie order
├── application/
│   ├── PartitionEntity.java       # event-sourced; the unit of serialisation
│   ├── EpisodeIngestWorkflow.java # seven stages; retries suppressed (OD-19)
│   ├── EntityExtractionAgent.java # prose → entities
│   ├── FactExtractionAgent.java   # prose → facts with validity intervals
│   ├── EntityResolutionAgent.java # the escalation stage of the identity cascade
│   ├── AttributeHydrationAgent.java
│   ├── FactsByPartitionView.java  # read side
│   ├── EpisodesByPartitionView.java
│   └── FlureeProjectionConsumer.java  # write-side events → store projections
├── api/
│   ├── MemoryEndpoint.java        # the 11 request/response operations, verbatim
│   ├── MemoryMcpEndpoint.java     # the 13 agent tools, verbatim
│   └── (request/response records, `toApi` converters)
└── Bootstrap.java                 # ServiceSetup

src/test/java/io/akka/memory/
├── domain/                        # pure-function tests; mirror the source probes
├── application/                   # entity, workflow, agent, view tests
└── api/                           # endpoint integration tests, incl. wire conformance
```

**Structure Decision**: single Akka service. No frontend module, because the surface inventory
reports zero graphical assets and zero command-line commands — a fact established by
enumeration, not assumption. The `domain` package holds every rule the specifications state as
deterministic, so the highest-value tests need no runtime at all.

## Complexity Tracking

> Constitution violations requiring justification.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Reproducing known defects (temporal ties left unresolved, order-dependent invalidation, attributes dropped rather than truncated, a length cap a caller's schema can disable) | D-006. The port's purpose is to prove the specifications were complete enough to regenerate the system. A defect corrected here is an improvement that can never be measured, because no baseline will exist. | Correcting them during the port loses the equivalence claim entirely, and with it the evidence that the specifications were sufficient. D-007 restores each as a Phase 2 flag with a measured delta. |
| Suppressing the runtime's automatic step retry (OD-19) | The runtime would naturally retry a failed stage and reach a state the source system cannot reach, breaking equivalence in the one place the source is weakest. | Leaving retry enabled produces a *better* system that is not the specified one. The obligation is easy to forget precisely because the capability costs nothing to have — hence recording it as a violation rather than a footnote. |
| Two extraction paths retained conceptually (SPEC-006 §8) | Not merged, because they are distinct implementations rather than duplicates; merging would change behaviour if the second ever became reachable. | Only one is reachable under D-004, so Phase 1 builds one — but the specification records both so a future bulk surface does not silently reuse the wrong one. |

No other violations. RENDER-001 §3.2 constrains the domain model more tightly than the
constitution does, so Simplicity is over-satisfied everywhere except the rows above.
