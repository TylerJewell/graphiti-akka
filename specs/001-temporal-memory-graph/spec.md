# Feature Specification: Temporal Memory Graph

**Feature Branch**: `001-temporal-memory-graph`
**Created**: 2026-08-19
**Status**: Draft
**Input**: Port the temporally-aware knowledge-graph memory engine specified in `graphiti-port/specs` (SPEC-001…SPEC-008) onto the Akka SDK, bug-compatible with the source system so equivalence is provable.

## User Scenarios & Testing *(mandatory)*

The users here are **agent developers and the agents they build**. They already have working
integrations against an existing memory engine. The value of this feature is not new
capability — it is that the same integrations keep working while gaining durability and
operability they do not have today.

### User Story 1 - Remember what happened, and notice when it changes (Priority: P1)

An agent developer sends a conversation turn or document to the memory service. The service
works out which people, organisations and things it mentions, what is true about them, and
when each of those facts became true. When a later message contradicts an earlier one, the
old fact is closed off at the moment the new one takes effect rather than being overwritten
or deleted, so the history of what was believed remains intact.

**Why this priority**: This is the product. Everything else either feeds this or reads from
it, and a system that ingests but does not resolve contradictions is a document store.

**Independent Test**: Send two conflicting statements about the same subject, dated a day
apart, and confirm the earlier fact is closed at the later fact's start time while both
remain retrievable.

**Acceptance Scenarios**:

1. **Given** an empty memory, **When** an episode stating "Ana works at Acme, from March" is ingested, **Then** a fact linking Ana to Acme exists and is valid from March.
2. **Given** that fact exists, **When** a later episode states "Ana joined Globex in July", **Then** the Acme fact is closed at July, the Globex fact is open, and both are still readable.
3. **Given** a fact whose start date the extractor could not determine, **When** a contradicting fact arrives, **Then** the undated fact is left open and nothing is closed.
4. **Given** two contradicting facts that begin at exactly the same moment, **When** both are ingested, **Then** both remain open — the system does not silently choose between them.
5. **Given** an episode mentioning a person already known to the memory, **When** it is ingested, **Then** the mention attaches to the existing person rather than creating a second one.

---

### User Story 2 - Ask the memory a question (Priority: P2)

A developer or agent asks the memory a natural-language question and receives the facts,
entities or episodes most relevant to it, drawing on meaning, wording and graph structure
together rather than any one of them alone.

**Why this priority**: Retrieval is how the stored memory becomes useful, but it is
meaningless before Story 1 puts anything in the graph. It is independently testable against
a pre-seeded memory.

**Independent Test**: Seed a known set of facts, ask a question whose answer requires
combining two of them, and confirm the relevant facts rank above the irrelevant ones.

**Acceptance Scenarios**:

1. **Given** a populated memory, **When** a question is asked, **Then** the most relevant facts are returned, ordered, and capped at the requested count.
2. **Given** a question naming a specific entity as the centre of interest, **When** it is asked, **Then** results are ordered by closeness to that entity rather than by relevance alone.
3. **Given** a question with no relevant matches, **When** it is asked, **Then** an empty result is returned rather than an error.

---

### User Story 3 - Correct and clear the record (Priority: P3)

A developer removes an episode that should not have been ingested, deletes a specific fact,
or clears an entire partition — for instance to honour a deletion request or to reset a test
fixture.

**Why this priority**: Necessary for operating the service and for data-handling
obligations, but the memory is useful without it.

**Independent Test**: Ingest, delete, and confirm the removed material no longer appears in
retrieval while unrelated material is untouched.

**Acceptance Scenarios**:

1. **Given** an ingested episode, **When** it is deleted, **Then** it no longer appears in retrieval and facts derived solely from it are gone.
2. **Given** several partitions, **When** one is cleared, **Then** the others are unaffected.

---

### User Story 4 - Keep existing integrations working unchanged (Priority: P1)

A developer with a working integration against the source system points it at this service
and changes nothing but the address. Their agent tooling — which discovers the memory's
operations dynamically — behaves identically.

**Why this priority**: Equal to Story 1. A port that requires callers to change is a new
product, and the migration argument disappears with it.

**Independent Test**: Replay a recorded session captured against the source system, changing
only the endpoint address, and compare responses field by field.

**Acceptance Scenarios**:

1. **Given** a caller written against the source system, **When** it is repointed at this service, **Then** every request succeeds with the same response shape, field names, and status codes.
2. **Given** an agent that discovers the memory's operations dynamically, **When** it connects, **Then** it finds the same operation names, parameters and defaults.

### Edge Cases

- **Extractor emits an unparseable date** — the fact is still recorded but carries no start time, and by Scenario 1.3 it can neither close another fact nor be closed by one. It is not rejected and does not fail the episode.
- **Extractor emits an under-specified date** (a day with no time, or a time with no zone) — accepted, not rejected, and interpreted at the start of the day in UTC. This shifts a fact's start by up to a day and therefore changes which facts close which.
- **Two facts begin at the same instant** — both stay open (Scenario 1.4). The source system has no rule here; see Assumptions.
- **A fact arrives out of chronological order** — nothing closes in either direction. Ingest order therefore affects the resulting graph.
- **Concurrent ingest of contradicting episodes** — both may be recorded as open, because each is decided against the state before the other was written. Equivalence is claimed only for serialised ingest.
- **Extractor names an entity type that does not exist** — the entity is kept under the base type rather than dropped or rejected.
- **Extractor attributes an entity to episodes that do not exist** — attribution widens to all episodes in the batch rather than narrowing to none.
- **An entity attribute comes back longer than the allowed length** — the attribute is dropped entirely rather than shortened, unless the caller's type definition marks it required, in which case it is kept at full length.
- **A model call fails repeatedly** — after the retry budget the episode fails and is not recorded; anything already written by that episode remains.
- **Entity names of one or two characters** — behave inconsistently with each other by design of the source system; see Assumptions.

## Requirements *(mandatory)*

### Functional Requirements

**Remembering**

- **FR-001**: System MUST accept an episode consisting of content, a content kind, a reference time, and a partition identifier.
- **FR-002**: System MUST identify entities in the episode's content and record each with a name and a type.
- **FR-003**: System MUST identify facts connecting those entities and record each with the interval over which it is true.
- **FR-004**: System MUST derive each fact's validity interval from the episode's content, not from the caller, and not from the time of ingestion.
- **FR-005**: System MUST track two independent timelines per fact: when it was true in the world, and when the system believed it. Answers to a question about one MUST NOT be derived from the other.
- **FR-006**: System MUST close a fact when a newer contradicting fact arrives, setting its end to the newer fact's start.
- **FR-007**: System MUST close a fact if and only if both facts have known start times, their intervals overlap, and the existing fact starts strictly earlier.
- **FR-008**: System MUST retain closed facts and keep them retrievable. Closing MUST NOT delete.
- **FR-009**: System MUST record when it first learned a fact, and MUST NOT overwrite that once set.

**Recognising**

- **FR-010**: System MUST decide whether an identified entity is one already known, using in order: candidates found by meaning; an exact name match among them; a similarity match among them; and only then a judgement call. The first stage to decide MUST end the sequence.
- **FR-011**: System MUST create a new entity when no stage decides.
- **FR-012**: System MUST merge entities identified more than once within a single episode when their names match exactly, keeping the more specific type.
- **FR-013**: System MUST apply the similarity comparison reproducibly, such that the same pair of names always yields the same decision.

**Answering**

- **FR-014**: System MUST answer a question using meaning, wording, and graph structure, combining the separate result lists into one ranking.
- **FR-015**: System MUST rank combined results such that a result appearing near the top of any one list outranks a result appearing lower in several. Ties MUST resolve consistently for identical input.
- **FR-016**: System MUST allow a caller to name a central entity, and MUST then rank by closeness to it instead.
- **FR-017**: System MUST cap results at the caller's requested count, defaulting to ten.

**Describing**

- **FR-018**: System MUST populate each entity's attributes and summary after it has been recognised.
- **FR-019**: System MUST show summary generation only the facts new in the current episode, so existing facts are not restated.
- **FR-020**: System MUST discard an attribute value exceeding the configured length rather than shortening it, and MUST exempt values the caller's type definition marks required.

**Operating**

- **FR-021**: System MUST run the remembering stages in a fixed order, each consuming the previous stage's output.
- **FR-022**: System MUST persist nothing until every earlier stage has completed, so a failure before persistence leaves no trace of the episode.
- **FR-023**: System MUST propagate a failure to the caller and MUST NOT attempt to reverse work already written.
- **FR-024**: System MUST retry a failed model call a bounded number of times with increasing delay, and MUST fail the episode when the budget is exhausted.
- **FR-025**: System MUST let an operator disable storage of raw episode content, in which case derived entities and facts persist and the source content does not.
- **FR-026**: System MUST reject partition and type identifiers that are not safe to use as identifiers.

**Compatibility**

- **FR-027**: System MUST expose the same operations as the source system, with identical names, parameters, defaults, response shapes and status codes, such that an existing caller works unchanged apart from the service address.
- **FR-028**: System MUST NOT add, remove or rename any caller-visible element of those operations. Internal naming MAY differ freely.
- **FR-029**: System MUST reproduce the source system's behaviour including the defects catalogued in the source specifications, so that any difference in output is evidence of a porting error rather than an intentional improvement.
- **FR-030**: System MUST offer optional caller authentication, disabled by default so that FR-027 holds for a default deployment, and enabled wherever the service is reachable.

### Key Entities

- **Episode**: A unit of remembered input — its content, what kind of content it is, when it refers to, which partition it belongs to, and a description of where it came from. Facts and entities trace back to the episodes they came from.
- **Entity**: A person, organisation or thing the memory knows about. Carries a name, one or more types, a summary, and caller-defined attributes. Its identity is the outcome of the recognition sequence in FR-010, not a natural key.
- **Fact**: A directed, named relationship between two entities, expressed also as a sentence. Carries the interval over which it is true in the world and the interval over which the system believed it.
- **Partition**: A named division of the memory. Facts and entities belong to exactly one. It scopes queries; it does not restrict access.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A caller written against the source system runs unmodified against this service, with every response identical field-for-field, across a recorded session covering all supported operations.
- **SC-002**: Given the same episodes in the same order and the same model responses, this service produces the same facts, the same entities, and the same closed intervals as the source system — compared item by item, not by sampling.
- **SC-003**: On the shared public memory benchmark, this service scores within one percentage point of the source system's published accuracy, using the same benchmark and the same inputs on both sides.
- **SC-004**: An episode that fails partway through leaves the memory in one of the states catalogued in the source specification and never in an uncatalogued one.
- **SC-005**: Every recorded behavioural claim in the source specifications is exercised by an automated check that fails when the behaviour changes.
- **SC-006**: A question against a populated memory returns its results quickly enough that an interactive agent does not appear to stall — under half a second at the ninety-fifth percentile, excluding time spent waiting on a language model.
- **SC-007**: Operators can determine, for any fact, both when it was true and when the system came to believe it, without consulting the other.

## Assumptions

Recorded rather than asked, because the source specifications already settle them.

- **Bug-compatibility is deliberate** (governing decision D-006). Where the source system's behaviour is defective — contradictions that both stay open at an exact tie, ingest order affecting the graph, attributes dropped rather than shortened, a length limit that a caller's type definition can silently disable — this service reproduces it. Corrections are a later phase, each behind a switch defaulting to the reproduced behaviour, so the improvement can be measured rather than asserted.
- **Equivalence is claimed only for serialised ingest.** The source system has no ordering guarantee under concurrent ingest and can lose a contradiction entirely, so there is no deterministic behaviour there to match.
- **Two entity names of one or two characters are handled inconsistently** by the source system, through two interacting quirks that cancel. Both are reproduced together; correcting either alone would make every very short name match every other.
- **No cross-encoder reranking is required.** No caller-visible operation selects it, so it is out of scope despite existing in the source system.
- **Bulk ingest is out of scope.** It has no caller-visible operation in the source system.
- **Analytics reporting to the source vendor is not reproduced** — the credential belongs to that vendor. This is a recorded, deliberate divergence.
- **The length limit for attributes defaults to 250 characters**, matching the source system, and remains operator-configurable.
