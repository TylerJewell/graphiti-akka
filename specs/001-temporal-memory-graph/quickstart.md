# Quickstart

## Prerequisites

- Java 21+, Maven 3.9+
- A FlureeDB instance reachable locally
- A model provider API key exported per `akka-context/sdk/model-provider-details.html.md`

## Build and run

```bash
mvn compile
mvn compile exec:java
```

## Prove it remembers, and notices a change

The two calls below are the whole product in miniature: a fact is recorded, then a later,
contradicting fact closes it *at the moment the new one takes effect* — and both stay readable.

```bash
# 1. Ana joins Acme in March
curl -X POST localhost:9000/messages -H 'content-type: application/json' -d '{
  "group_id": "demo",
  "messages": [{"content": "Ana started at Acme in March 2024.",
                "name": "note", "role_type": "user",
                "timestamp": "2024-03-01T00:00:00Z", "source_description": "quickstart"}]
}'
# -> 202 Accepted. Note: accepted, not done.
```

**Expect a 202 and an immediate return.** Ingest is asynchronous by contract — the response
says the episode was accepted, not that it was processed. Only the timestamp is validated
before you get that answer; anything else that goes wrong happens afterwards, silently. Give it
a moment before querying.

```bash
# 2. Ana moves to Globex in July
curl -X POST localhost:9000/messages -H 'content-type: application/json' -d '{
  "group_id": "demo",
  "messages": [{"content": "Ana joined Globex in July 2024.",
                "name": "note", "role_type": "user",
                "timestamp": "2024-07-01T00:00:00Z", "source_description": "quickstart"}]
}'

# 3. Ask what is known
curl -X POST localhost:9000/search -H 'content-type: application/json' \
  -d '{"group_ids": ["demo"], "query": "Where does Ana work?", "max_facts": 10}'
```

Note `group_ids`, plural, and a list — that is the field name on the wire. Sending `group_id`
here is not an error: the request succeeds, the partition falls back to `default`, and you get
`{"facts": []}` back from a graph you never wrote to. An empty result is indistinguishable from
a mistyped field, which is worth knowing before you conclude that nothing was stored.

You should see **both** facts. The Acme one carries `invalid_at` set to July — closed, not
deleted, and still returned. That is the point of the system: history survives correction.

### If you have no model provider configured

Ingest still answers 202 and the episode is still stored — you can see it with
`curl "localhost:9000/episodes/demo?last_n=5"`. But no entities or facts are extracted, so the
search above returns `{"facts": []}`.

That is the designed degradation, not a failure: each agent falls back rather than failing the
episode, so the pipeline runs to completion having learned nothing. It is also why the search
result alone cannot tell you whether ingest worked. Check the episode listing first.

## What "correct" means here

This service reproduces an existing one, including its defects (D-006). Several behaviours look
like bugs and are deliberate:

- Two contradicting facts starting at the **same instant** both stay open.
- A fact arriving **out of order** closes nothing in either direction.
- An over-long entity attribute is **dropped**, not shortened — unless your entity type marks it
  required, in which case the limit does not apply at all.
- An unparseable date from the extractor leaves the fact **inert**: it can neither close another
  fact nor be closed.

Each is specified, each has a Phase 2 flag that corrects it, and each defaults to the
reproduced behaviour so the improvement can be measured rather than asserted.

## Verifying the port

Three layers, cheapest first:

```bash
mvn test      # domain rules and the surface contract, plus the agent and ingest tests that
              # need the runtime. The domain tests — invalidation, identity cascade, rank
              # fusion — need no runtime, no model and no store, and most of the risk is there.

mvn verify    # adds store-backed integration: the projection, retrieval, the two timelines
              # and wire-shape conformance against contracts/surface-inventory.json

mvn test -Dtest=BenchmarkRunner   # cross-language parity and timings; writes
                                  # target/bench-java.json for the comparison
```

Tests that need the store **skip rather than fail** when none is reachable, so the suite is
green on a machine without one. A green run is therefore not by itself proof the store paths
were exercised — read the skip count.

The model is stubbed everywhere, deliberately. A test that calls a real model measures the
model's mood as much as the code.

## Ingest ordering

Episodes are serialised **per partition** (`group_id`) and processed independently across
partitions. That is not a scaling compromise — it is what the source system does, and it is what
prevents a contradiction from being lost when two episodes are ingested at once.

Equivalence is claimed for serialised ingest only. Within a partition you get that by
construction.
