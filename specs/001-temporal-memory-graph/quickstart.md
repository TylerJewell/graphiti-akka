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
  -d '{"group_id": "demo", "query": "Where does Ana work?", "max_facts": 10}'
```

You should see **both** facts. The Acme one carries `invalid_at` set to July — closed, not
deleted, and still returned. That is the point of the system: history survives correction.

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
mvn test      # domain rules — the invalidation predicate, identity cascade, rank fusion.
              # No runtime, no model, no store. Most of the risk lives here.

mvn verify    # component and endpoint integration, including wire-shape conformance
              # against contracts/surface-inventory.json
```

Then end-to-end parity against the source system's own benchmark, replaying pinned model
responses so a difference means a porting error rather than model variance.

## Ingest ordering

Episodes are serialised **per partition** (`group_id`) and processed independently across
partitions. That is not a scaling compromise — it is what the source system does, and it is what
prevents a contradiction from being lost when two episodes are ingested at once.

Equivalence is claimed for serialised ingest only. Within a partition you get that by
construction.
