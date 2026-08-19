# Tasks: Temporal Memory Graph

**Input**: Design documents from `/specs/001-temporal-memory-graph/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: **Required.** SC-005 obliges every recorded behavioural claim to be exercised by an
automated check that fails when the behaviour changes, and the constitution requires coverage
for every behavioural change. Test tasks are therefore first-class here, not optional.

**Organization**: grouped by user story. One deviation is recorded and justified: **US4
(compatibility) is verified last but constrains every phase from the start**, because a port has
no surface to verify until the capabilities behind it exist.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelisable — different files, no dependency on incomplete work
- **[Story]**: US1…US4, on user-story phases only

## Path Conventions

Single Akka service. `src/main/java/io/akka/memory/{domain,application,api}` and
`src/test/java/io/akka/memory/…` per plan.md.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: project initialisation

- [X] T001 Rename service and package from the template default to `io.akka.memory` in pom.xml and src/main/java/
- [X] T002 [P] Add the FlureeDB client dependency and connection configuration in pom.xml and src/main/resources/application.conf
- [ ] T003 [P] Configure the model provider per akka-context/sdk/model-provider-details.html.md in src/main/resources/application.conf
- [X] T004 [P] Copy the source system's extraction, resolution and summarisation instructions **verbatim** into src/main/resources/prompts/ (R-006 — paraphrasing invalidates every recorded model interaction)
- [X] T005 [P] Vendor specs/001-temporal-memory-graph/contracts/surface-inventory.json into src/test/resources/surface-inventory.json as the conformance fixture
- [ ] T006 [P] Configure TestKitSupport and TestModelProvider scaffolding in src/test/java/io/akka/memory/TestBase.java

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: no user story work begins until this phase completes.

**T007 is first for a reason.** R-005 is the project's only unresolved question, and it is
empirical: whether the store can range-query a valid-time interval natively decides the shape of
every projection. Building persistence against an assumed capability is exactly the class of
assumption that has been wrong before here.

- [X] T007 ✅ **RESOLVED — valid-time intervals ARE natively range-queryable (Fluree 4.1.5).** Probe a running FlureeDB instance to resolve R-005 — can a valid-time interval be range-queried natively, or must results be filtered after retrieval? Record the finding in specs/001-temporal-memory-graph/research.md and update R-005's status
- [X] T008 [P] Create the Fact record with both timelines in src/main/java/io/akka/memory/domain/Fact.java
- [X] T009 [P] Create the Entity record in src/main/java/io/akka/memory/domain/Entity.java
- [X] T010 [P] Create the Episode record in src/main/java/io/akka/memory/domain/Episode.java
- [X] T011 Implement the invalidation predicate as a pure function on two Facts in src/main/java/io/akka/memory/domain/Fact.java (data-model.md §Fact — exact starts close nothing; out-of-order closes nothing; closing is never deletion)
- [X] T012 [P] Test the invalidation predicate exhaustively over every combination of absent/before/equal/after for four nullable instants in src/test/java/io/akka/memory/domain/FactInvalidationTest.java
- [X] T013 [P] Test that the invalidation test suite can fail — mutate the predicate (flip the comparison, drop each null guard) and assert every mutant is caught, in src/test/java/io/akka/memory/domain/FactInvalidationMutationTest.java
- [X] T014 [P] Implement exact and fuzzy name normalisation and the entropy gate `(length ≥ 6 OR tokens ≥ 2) AND entropy ≥ 1.5` in src/main/java/io/akka/memory/domain/EntityIdentity.java
- [X] T015 Implement 3-gram shingling, BLAKE2b-keyed MinHash over seeds 0–31, bands of 4, and Jaccard scoring in src/main/java/io/akka/memory/domain/EntityIdentity.java (constants are contract — substituting the hash or its byte order produces a different graph)
- [X] T016 [P] Test the identity cascade against generated name pairs, asserting stage order and the ≥ 0.9 threshold, in src/test/java/io/akka/memory/domain/EntityIdentityTest.java
- [X] T017 [P] Test the identity constants against known values — BLAKE2b digest, big-endian conversion, band count — in src/test/java/io/akka/memory/domain/EntityIdentityConstantsTest.java
- [X] T018 [P] Test that a one-character name is matchable and a two-character name is not, reproducing the interacting short-name quirks, in src/test/java/io/akka/memory/domain/EntityIdentityShortNameTest.java
- [X] T019 Implement rank fusion with `rank_const = 1` and first-seen tie order in src/main/java/io/akka/memory/domain/RankFusion.java
- [X] T020 [P] Test rank fusion including tie order stability and that the constant is behavioural, in src/test/java/io/akka/memory/domain/RankFusionTest.java
- [X] T021 Create PartitionState in src/main/java/io/akka/memory/domain/PartitionState.java
- [X] T022 Create the PartitionEvent sealed interface with `@TypeName` on every variant in src/main/java/io/akka/memory/domain/PartitionEvent.java (FactClosed is distinct from any deletion)
- [X] T023 Implement the FlureeDB persistence adapter — graph, vector and full-text in one store — in src/main/java/io/akka/memory/application/FlureeStore.java, using the R-005 finding from T007
- [X] T024 [P] Test the persistence adapter round-trip including a valid-time interval query in src/test/java/io/akka/memory/application/FlureeStoreIntegrationTest.java
- [ ] T025 Create the Bootstrap ServiceSetup in src/main/java/io/akka/memory/Bootstrap.java
- [X] T026 Build the wire-conformance harness that reads src/test/resources/surface-inventory.json and asserts paths, methods, status codes, field names, enum literals and defaults, in src/test/java/io/akka/memory/api/SurfaceConformanceTest.java

**Checkpoint**: domain rules are complete and tested with no runtime, no model and no store. Most of the project's risk is now retired.

---

## Phase 3: User Story 1 - Remember what happened, and notice when it changes (Priority: P1) 🎯 MVP

**Goal**: ingest an episode, extract entities and facts, recognise entities already known, and close contradicted facts at the moment the new fact takes effect.

**Independent Test**: send two conflicting statements dated a day apart; confirm the earlier fact is closed at the later fact's start time and both remain retrievable — driven through the component client, without endpoints.

### Tests for User Story 1

- [ ] T027 [P] [US1] Integration test: a later contradicting fact closes the earlier one at the new fact's start, and both remain readable, in src/test/java/io/akka/memory/application/IngestContradictionIntegrationTest.java
- [ ] T028 [P] [US1] Integration test: a fact whose start could not be determined stays open and closes nothing, in src/test/java/io/akka/memory/application/IngestUndatedFactTest.java
- [ ] T029 [P] [US1] Integration test: two facts starting at the same instant both stay open, in src/test/java/io/akka/memory/application/IngestExactTieTest.java
- [ ] T030 [P] [US1] Integration test: a mention of an already-known entity attaches to it rather than creating a second, in src/test/java/io/akka/memory/application/EntityRecognitionIntegrationTest.java

### Implementation for User Story 1

- [X] T031 [US1] Implement PartitionEntity as an event-sourced entity keyed by partition in src/main/java/io/akka/memory/application/PartitionEntity.java (the unit of serialisation — R-001)
- [X] T032 [P] [US1] Unit-test PartitionEntity command handlers with EventSourcedTestKit in src/test/java/io/akka/memory/application/PartitionEntityTest.java
- [X] T033 [P] [US1] Implement EntityExtractionAgent in src/main/java/io/akka/memory/application/EntityExtractionAgent.java, selecting the instruction by episode kind with prose as the fallback
- [X] T034 [P] [US1] Implement FactExtractionAgent in src/main/java/io/akka/memory/application/FactExtractionAgent.java, inferring validity intervals from content
- [X] T035 [P] [US1] Implement EntityResolutionAgent in src/main/java/io/akka/memory/application/EntityResolutionAgent.java, selecting a candidate **by index**, never by identifier
- [X] T036 [P] [US1] Implement AttributeHydrationAgent in src/main/java/io/akka/memory/application/AttributeHydrationAgent.java, shown only the facts new in this episode
- [ ] T037 [P] [US1] Test all four agents with TestModelProvider fixed responses in src/test/java/io/akka/memory/application/AgentContractTest.java
- [X] T038 [US1] Implement the seven-stage EpisodeIngestWorkflow in src/main/java/io/akka/memory/application/EpisodeIngestWorkflow.java (stages 1–6 in memory; stage 7 is the only writer)
- [X] T039 [US1] Suppress automatic step retry in EpisodeIngestWorkflow settings so the runtime cannot recover from failures the source cannot, in src/main/java/io/akka/memory/application/EpisodeIngestWorkflow.java (OD-19)
- [X] T040 [US1] Test that a failed stage abandons the episode without reversing earlier writes, in src/test/java/io/akka/memory/application/IngestFailureSemanticsTest.java
- [ ] T041 [US1] Implement extraction guardrails — an out-of-range entity type falls back to the base type, an excluded type is dropped, unusable episode indices widen attribution to **all** episodes — in src/main/java/io/akka/memory/application/EntityExtractionAgent.java
- [ ] T042 [P] [US1] Test the extraction guardrail table in src/test/java/io/akka/memory/application/ExtractionGuardrailTest.java
- [X] T043 [US1] Implement timestamp parse degradation — malformed drops to absent without raising, retrying or substituting the reference time; date-only and zone-less values are accepted and interpreted at UTC midnight — in src/main/java/io/akka/memory/domain/Fact.java
- [X] T044 [P] [US1] Test timestamp degradation against a hostile corpus, asserting nothing escapes as an exception, in src/test/java/io/akka/memory/domain/TimestampDegradationTest.java
- [X] T045 [US1] Implement the attribute length cap as a **drop** with the required-field exemption in src/main/java/io/akka/memory/application/AttributeHydrationAgent.java
- [X] T046 [P] [US1] Test the cap on both axes and the required-field exemption in src/test/java/io/akka/memory/application/AttributeCapTest.java
- [X] T047 [US1] Implement raw-content retention blanking before the persist stage in src/main/java/io/akka/memory/application/EpisodeIngestWorkflow.java
- [ ] T048 [US1] Implement FlureeProjectionConsumer to project partition events into the store in src/main/java/io/akka/memory/application/FlureeProjectionConsumer.java
- [X] T049 [US1] Implement the ingest operations with asynchronous handoff — validate the reference time synchronously, enqueue, answer 202 — in src/main/java/io/akka/memory/api/MemoryEndpoint.java and src/main/java/io/akka/memory/api/MemoryMcpEndpoint.java
- [ ] T050 [US1] Integration test: ingest returns immediately without awaiting the pipeline, and a malformed reference time is a synchronous error, in src/test/java/io/akka/memory/api/IngestHandoffIntegrationTest.java

**Checkpoint**: episodes can be remembered and contradictions close correctly. This is the MVP.

---

## Phase 4: User Story 2 - Ask the memory a question (Priority: P2)

**Goal**: answer a natural-language question from stored memory using meaning, wording and graph structure fused into one ranking.

**Independent Test**: seed a known set of facts, ask a question requiring two of them, confirm the relevant facts outrank the irrelevant.

### Tests for User Story 2

- [ ] T051 [P] [US2] Integration test: a question against a populated memory returns relevant facts, ordered, capped at the requested count, in src/test/java/io/akka/memory/application/RetrievalIntegrationTest.java
- [ ] T052 [P] [US2] Integration test: naming a central entity orders by closeness to it rather than relevance, in src/test/java/io/akka/memory/application/CentredRetrievalTest.java
- [ ] T053 [P] [US2] Integration test: a question with no matches returns empty rather than erroring, in src/test/java/io/akka/memory/application/EmptyRetrievalTest.java

### Implementation for User Story 2

- [X] T054 [P] [US2] Implement FactsByPartitionView in src/main/java/io/akka/memory/application/FactsByPartitionView.java, consuming partition events
- [X] T055 [P] [US2] Implement EpisodesByPartitionView in src/main/java/io/akka/memory/application/EpisodesByPartitionView.java
- [ ] T056 [US2] Implement hybrid retrieval — semantic, lexical and graph-structural lists fused by RankFusion — in src/main/java/io/akka/memory/application/RetrievalService.java (reads resolve against projections only; the write path is never queried — RENDER-001 §3.4)
- [X] T057 [US2] Implement the valid-time query path so a question about world-truth is never answered from record-keeping history, in src/main/java/io/akka/memory/application/RetrievalService.java
- [ ] T058 [P] [US2] Test that valid-time and transaction-time queries return different answers where the two axes diverge, in src/test/java/io/akka/memory/application/TemporalQuerySeparationTest.java
- [X] T059 [US2] Implement the retrieval operations in src/main/java/io/akka/memory/api/MemoryEndpoint.java and src/main/java/io/akka/memory/api/MemoryMcpEndpoint.java, with defaults matching the contract
- [ ] T060 [P] [US2] Test that no read path loads write state, by static inspection of the read components, in src/test/java/io/akka/memory/application/ReadPathIsolationTest.java

**Checkpoint**: memory can be both written and queried.

---

## Phase 5: User Story 3 - Correct and clear the record (Priority: P3)

**Goal**: remove an episode, delete a fact, or clear a partition without touching unrelated material.

**Independent Test**: ingest, delete, confirm the removed material is gone from retrieval and unrelated partitions are untouched.

### Tests for User Story 3

- [ ] T061 [P] [US3] Integration test: a deleted episode disappears from retrieval and takes facts derived solely from it, in src/test/java/io/akka/memory/application/EpisodeDeletionTest.java
- [ ] T062 [P] [US3] Integration test: clearing one partition leaves the others untouched, in src/test/java/io/akka/memory/application/PartitionClearTest.java

### Implementation for User Story 3

- [X] T063 [US3] Implement episode removal, fact deletion and partition clearing on PartitionEntity in src/main/java/io/akka/memory/application/PartitionEntity.java (deletion is distinct from closing — a closed fact is not deleted)
- [ ] T064 [US3] Propagate deletions to the store in src/main/java/io/akka/memory/application/FlureeProjectionConsumer.java
- [X] T065 [US3] Implement the deletion operations in src/main/java/io/akka/memory/api/MemoryEndpoint.java and src/main/java/io/akka/memory/api/MemoryMcpEndpoint.java

**Checkpoint**: all three capability stories are functional.

---

## Phase 6: User Story 4 - Keep existing integrations working unchanged (Priority: P1)

**Goal**: an existing caller works against this service with no change but the address.

**Independent Test**: replay a session recorded against the source system, changing only the endpoint address, and compare responses field by field.

**Why this P1 story is verified last.** A port has no surface to verify until the capabilities
behind it exist. The contract is not deferred, though: T005 vendors it in Setup, T026 builds the
harness in Foundational, and every endpoint task in Phases 3–5 is written against it. This phase
completes the remaining operations and proves the whole surface at once.

### Tests for User Story 4

- [X] T066 [P] [US4] Contract test: every route, method, status code and wire type matches src/test/resources/surface-inventory.json, in src/test/java/io/akka/memory/api/SurfaceConformanceTest.java
- [X] T067 [P] [US4] Contract test: every agent tool matches by name, parameter names, **parameter order**, defaults and return shape, in src/test/java/io/akka/memory/api/McpConformanceTest.java
- [ ] T068 [US4] Replay a recorded upstream session against the service and compare responses field by field, in src/test/java/io/akka/memory/api/RecordedSessionReplayTest.java

### Implementation for User Story 4

- [X] T069 [US4] Implement the remaining operations not covered by Phases 3–5 — health, statistics, entity-node creation, fact lookup — in src/main/java/io/akka/memory/api/MemoryEndpoint.java
- [X] T070 [US4] Implement the remaining agent tools in src/main/java/io/akka/memory/api/MemoryMcpEndpoint.java, carrying tool descriptions **verbatim** (they condition calling-agent behaviour — R-006)
- [X] T071 [US4] Add `toApi` converters so wire shapes are matched without exposing domain internals, in src/main/java/io/akka/memory/api/
- [ ] T072 [US4] Implement optional bearer-token authentication, **disabled by default** so a default deployment stays caller-compatible, in src/main/java/io/akka/memory/api/MemoryEndpoint.java (D-008)

**Checkpoint**: an unmodified caller works. The port is behaviourally complete.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T073 Run the source system's benchmark against this service with pinned model responses and compare fact-for-fact, in src/test/java/io/akka/memory/BenchmarkParityTest.java (SC-002, SC-003)
- [ ] T074 [P] Measure cold start, resident memory and the two latency budgets against a deployed build and record the measurements in docs/budget-report.md; report any miss rather than restating the target (RENDER-001 §5)
- [ ] T075 [P] Verify no persisted type exists that no specification names, and no named concept lacks a type, via a check in src/test/java/io/akka/memory/domain/DomainModelTraceabilityTest.java (RENDER-001 §3.2)
- [X] T076 [P] Verify no capability is implemented twice and attribute the line-count reduction to named causes, recorded in docs/reduction-report.md (RENDER-001 §3.1)
- [ ] T077 [P] Update README.md with the operations and curl examples from quickstart.md
- [ ] T078 Run every command in specs/001-temporal-memory-graph/quickstart.md end to end and confirm each stated behaviour, including the deliberate defects
- [ ] T079 [P] Record every Phase 2 correction flag as unimplemented-by-design in docs/, defaulting to reproduced behaviour (D-007)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup — **blocks every user story**. T007 blocks T023 and all of US2.
- **US1 (Phase 3)**: depends on Foundational
- **US2 (Phase 4)**: depends on Foundational; T007's finding shapes T054–T057
- **US3 (Phase 5)**: depends on Foundational; T063 touches PartitionEntity from T031
- **US4 (Phase 6)**: depends on US1, US2 and US3 — it verifies surfaces those phases create
- **Polish (Phase 7)**: depends on all stories

### User Story Dependencies

- **US1 (P1)**: independent once Foundational completes. The MVP.
- **US2 (P2)**: independent of US1 in code; needs seeded data to test, which US1 provides or a fixture can
- **US3 (P3)**: shares PartitionEntity with US1 — sequence T063 after T031
- **US4 (P1)**: **not independent by construction.** Recorded as the one deviation from the independence guidance: a compatibility story has nothing to be compatible with until the operations exist.

### Within Each User Story

Tests before implementation. Domain before application. Application before API. Story complete before the next priority.

### Parallel Opportunities

- Setup: T002–T006 all parallel
- Foundational: T008–T010 parallel; T012/T013 parallel; T016–T018 parallel; T020 and T024 parallel
- US1: T027–T030 parallel; T033–T036 parallel (four separate agents)
- US2: T051–T053 parallel; T054/T055 parallel
- US4: T066/T067 parallel
- Polish: T074–T077, T079 parallel

---

## Parallel Example: User Story 1

```bash
# All four behavioural tests first — they must fail before implementation:
Task: "Integration test: later contradicting fact closes the earlier one (T027)"
Task: "Integration test: undated fact stays open (T028)"
Task: "Integration test: exact tie leaves both open (T029)"
Task: "Integration test: known entity is recognised, not duplicated (T030)"

# The four agents are separate files with no shared state:
Task: "EntityExtractionAgent (T033)"
Task: "FactExtractionAgent (T034)"
Task: "EntityResolutionAgent (T035)"
Task: "AttributeHydrationAgent (T036)"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 Setup
2. Phase 2 Foundational — **resolve R-005 at T007 before anything touches persistence**
3. Phase 3 US1
4. **STOP and VALIDATE**: two conflicting statements, one closed at the other's start, both readable
5. Demo — this is the product in miniature

### Incremental Delivery

Setup + Foundational → US1 (MVP) → US2 → US3 → US4 (whole-surface proof) → Polish.

The order is by capability, not by priority label: US4 is P1 but must come last, because
compatibility is a property of surfaces that do not exist until the capabilities do.

### Parallel Team Strategy

After Foundational, one developer can take US1 while another takes US2 against fixture data;
US3 is small and shares one file with US1. US4 needs all three and is best done by whoever
built the endpoints.

---

## Notes

- Phase 2 carries an unusual share of the value: the invalidation predicate, the identity
  cascade and rank fusion are pure functions, exactly testable with no runtime, model or store —
  and they hold most of the project's risk.
- T013 tests that the tests can fail. A suite that cannot fail proves nothing, and this
  predicate is the one place where a silently-wrong implementation would pass everything else.
- Several tasks implement behaviour that looks like a defect. Each is deliberate under D-006,
  each has a Phase 2 flag under D-007, and each defaults to the reproduced behaviour so the
  eventual improvement can be measured rather than asserted.
- T039 *removes* a capability the runtime provides free. It is the easiest task here to skip by
  accident and the hardest to notice afterwards.
