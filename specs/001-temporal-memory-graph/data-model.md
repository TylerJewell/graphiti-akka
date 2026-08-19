# Phase 1 — Data Model

Four domain concepts. **A persisted type that no behavioural specification names is a
violation**, not merely unnecessary (RENDER-001 §3.2), so this file is the complete list.

## Fact

The central record, and the only one carrying two independent timelines.

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | |
| `partition` | `String` | scopes the fact; not a security boundary |
| `subjectId`, `objectId` | `String` | the two entities related |
| `relation` | `String` | the relationship name |
| `statement` | `String` | the same fact as a sentence, used for retrieval |
| `validFrom` | `Optional<Instant>` | **valid time** — when true in the world |
| `validUntil` | `Optional<Instant>` | **valid time** — when it stopped being true |
| `recordedAt` | `Instant` | **transaction time** — when first recorded |
| `supersededAt` | `Optional<Instant>` | **transaction time** — when learned to be over |
| `episodeIds` | `List<String>` | provenance |
| `embedding` | `Optional<float[]>` | for semantic retrieval |

**Nullability is load-bearing.** An absent `validFrom` is not "unknown but harmless" — it makes
the fact inert: it can neither close another fact nor be closed by one.

### Invalidation — a pure function, and the highest-value unit test in the system

Given a new fact `n` and an existing candidate `c` sharing subject and relation, `c` is closed
**if and only if**:

```
c.validFrom is present
AND n.validFrom is present
AND NOT (c.validUntil present AND c.validUntil <= n.validFrom)
AND NOT (n.validUntil present AND n.validUntil <= c.validFrom)
AND c.validFrom < n.validFrom
```

On closing: `c.validUntil := n.validFrom` — the **new** fact's start, not the wall clock — and
`c.supersededAt := now` **only if currently absent**. All comparisons in UTC.

Three consequences the predicate encodes deliberately, each reproducing source behaviour:

- **Equal starts close nothing.** Two contradicting facts both stay open. The source has no
  branch for this; neither does the port.
- **Out-of-order arrival closes nothing**, in either direction. Ingest order therefore affects
  the resulting graph, which is why R-001 keys the write path by partition.
- **Closing is never deletion.** A closed fact stays queryable.

### State transitions

```
proposed ──persist──▶ open ──closed by a newer overlapping fact──▶ closed
                       │                                            │
                       └──────────── remains queryable ─────────────┘
```

There is no `deleted` transition on this path. Deletion exists only through the explicit
delete-episode and clear-partition operations.

## Entity

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | |
| `partition` | `String` | |
| `name` | `String` | as extracted |
| `types` | `Set<String>` | always contains the base type; **order is unspecified** |
| `summary` | `String` | empty until hydration |
| `attributes` | `Map<String, Object>` | caller-defined schema |
| `nameEmbedding` | `Optional<float[]>` | drives the candidate stage of identity |

**Identity is not a field.** There is no natural key; identity is the outcome of a four-stage
cascade, and the same two names can resolve differently depending on what else exists and what
the candidate search returned. Modelled as a domain function, not a lookup.

`types` order is deliberately **unspecified**: the source builds it from an unordered set whose
iteration order varies between processes, so there is no order to match. The port sorts, for its
own reproducibility.

### Identity cascade

1. **Candidate gate** — semantic search per extracted entity. *Empty ⇒ new entity, no further
   stage runs.* This gate dominates everything below it: two byte-identical names do not merge
   if the search does not surface one for the other.
2. **Exact name** — normalised comparison, **among candidates only**. One match resolves; more
   than one escalates.
3. **Entropy gate** — `(length ≥ 6 OR tokens ≥ 2) AND entropy ≥ 1.5`. Most single-word names
   fail on *length*, not entropy, and escalate.
4. **Fuzzy** — 3-gram shingles, BLAKE2b-keyed MinHash over seeds 0…31, bands of 4, Jaccard ≥ 0.9.
5. **Escalation** — model decides by *index into an offered list*; it can never name an entity
   that was not offered.

Stages 2–4 are deterministic and unit-testable with no runtime. The constants are contract:
substituting the hash or its byte order produces a different graph while every test still passes.

## Episode

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | |
| `partition` | `String` | |
| `content` | `String` | **blanked before persistence** when retention is disabled |
| `kind` | `enum` | selects the extraction instruction; unknown kinds fall back to prose |
| `referenceTime` | `Instant` | the only field validated synchronously |
| `sourceDescription` | `String` | provenance |
| `recordedAt` | `Instant` | |

## Partition

Not a persisted record — the **key** of the write path, and therefore the unit of
serialisation. It scopes queries. It does **not** restrict access: any caller may name any
partition. Its only validation is that the identifier is safe to use as an identifier.

## Aggregate state and events

`PartitionState` holds the entities and facts for one partition. Events are a sealed interface,
each `@TypeName`-annotated:

| Event | Emitted when |
|---|---|
| `EpisodeRecorded` | an episode is persisted |
| `EntityRecognised` | an extracted entity resolved to an existing one |
| `EntityCreated` | no stage resolved it |
| `FactRecorded` | a new fact is persisted |
| `FactClosed` | an existing fact was closed by a newer one |
| `EntityHydrated` | attributes and summary populated |
| `EpisodeRemoved`, `PartitionCleared` | explicit deletion |

`FactClosed` is a distinct event from any deletion, mirroring §3.1 of the source specification:
closing is a state transition, and the record survives it.

## Read projections

| Projection | Serves |
|---|---|
| `FactsByPartitionView` | fact retrieval, fusion ranking, valid-time queries |
| `EpisodesByPartitionView` | episode listing and lookup |

Both are built from the events above. **No read operation loads write state** (RENDER-001 §3.4).

## Traceability

| Concept | Source specification |
|---|---|
| Fact, both timelines, invalidation predicate | SPEC-001 §2, §3.2, §3.3 |
| Entity, identity cascade, constants | SPEC-003 §2, §3.2–§3.4 |
| Episode, extraction guardrails | SPEC-006 §2, §3.1–§3.2 |
| Partition as unit of serialisation | SPEC-007 §3.0 |
| Attributes, length cap, required-field exemption | SPEC-008 §3 |
| Retention blanking | SPEC-007 §3.6 |

Every field above traces to a specification section. Nothing here was introduced for the
target stack's convenience.
