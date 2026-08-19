# graphiti-akka

A memory service that records what it is told, notices when a later statement contradicts an
earlier one, and closes the earlier one at the moment the new one takes effect — without deleting
it.

A port of [getzep/graphiti](https://github.com/getzep/graphiti) onto **Akka**, built with
**Akka Specify**.

---

## Where it came from

`getzep/graphiti` is a temporal knowledge-graph library that builds a queryable graph from a
stream of episodes. It was ported to derive a specification format precise enough to regenerate a
system on a different stack — the port is the vehicle, the specification is the deliverable.

The specifications this port was generated from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness) under
`graphiti-port/`: eight behavioural specifications written to be stack-neutral, plus a standing
rendering specification that fixes the target.

---

## getzep/graphiti → this port

📉 11,387 Python lines → **2,411 Java lines**
📁 37 files → **26 files**
⚡ 1,107 ns/op → **69 ns/op** (fact invalidation)
⚡ 2,917,079 ns/op → **938,176 ns/op** (entity identity)
⚡ 93,734 ns/op → **14,390 ns/op** (rank fusion)
🎯 3 of 3 deterministic workloads byte-identical
🖥️ 2 processes → **2 processes**
💾 not measured → **425 MB** resident
🚀 not measured → **3.55 s** cold start
🔌 4 graph backends → **1 store**

Counting rule: lines are source files only, blank and comment-only lines excluded, both sides
counted by the same tool (`toolkit/loc.py` in the harness). The line and file figures are
**scope-matched** — the behavioural slice this port implements, against the port's production
code — not whole projects. The whole-project ratio is 9.6:1 and is not quoted here, because it
credits the port for scope it never took on.

`not measured` means exactly that: the source needs a graph database and a model account to start,
neither of which was stood up, so its cold start and memory were never observed. An estimate would
have been easy to write and worth nothing.

Full method, the numbers that did not make this list, and the one budget this port misses:
[`bench/REPORT.md`](../graphiti-port/bench/REPORT.md) in the harness, and
[`docs/budget-report.md`](docs/budget-report.md) here.

---

## What it does

From the specification:

- **A later contradicting fact closes the earlier one at the new fact's start, and never deletes
  it.** History survives correction — a closed fact is still returned, carrying the instant its
  validity ended.
- **Two timelines are kept apart.** What was true in the world and what the system believed are
  separate axes, so a fact learned in March about January is visible to one and not the other.
- **Two facts starting at the same instant both stay open.** The comparison is strictly later, not
  later-or-equal. This is reproduced from the original, not chosen.
- **A fact whose start could not be determined is inert.** It closes nothing and nothing closes it.
- **Identity is decided by a cascade, not by a name index.** Candidate search, then exact name,
  then an entropy gate, then fuzzy similarity, then the model. Two identical names are never merged
  unless a candidate search surfaced one for the other.
- **Reads never load write state.** Retrieval, fact lookup and episode listing all resolve against
  a projection, checked by a test that reads the code.
- **Ingest acknowledges before it processes.** Exactly one field is validated synchronously; every
  other failure happens afterwards, silently. That asymmetry is the contract.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/graphiti-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9000/healthcheck.

You will also need a FlureeDB instance on `127.0.0.1:8090`.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once
- A FlureeDB instance reachable on `127.0.0.1:8090`
- `OPENAI_API_KEY` for extraction and embeddings

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9000**.

### The whole product in three calls

```bash
# Ana joins Acme in March
curl -X POST localhost:9000/messages -H 'content-type: application/json' -d '{
  "group_id": "demo",
  "messages": [{"content": "Ana started at Acme in March 2024.", "name": "note",
                "role_type": "user", "timestamp": "2024-03-01T00:00:00Z",
                "source_description": "readme"}]}'

# Ana moves to Globex in July
curl -X POST localhost:9000/messages -H 'content-type: application/json' -d '{
  "group_id": "demo",
  "messages": [{"content": "Ana joined Globex in July 2024.", "name": "note",
                "role_type": "user", "timestamp": "2024-07-01T00:00:00Z",
                "source_description": "readme"}]}'

# Ask what is known
curl -X POST localhost:9000/search -H 'content-type: application/json' \
  -d '{"group_ids": ["demo"], "query": "Where does Ana work?", "max_facts": 10}'
```

Both facts come back. The Acme one carries `invalid_at` set to July — closed, not deleted.

Three things about that sequence, each of which has cost someone an hour:

- **Ingest answers `202`, not `200`.** The episode is accepted, not processed. Give it a moment.
- **`group_ids` is plural and a list.** Sending `group_id` is not an error: the partition falls
  back to `default` and you get an empty result from a graph you never wrote to.
- **With no model key, `/search` returns nothing** while ingest still answers `202`. Check
  `GET /episodes/demo?last_n=5` to see whether ingest actually ran.

### Other operations

```bash
curl "localhost:9000/episodes/demo?last_n=5"       # most recent first
curl localhost:9000/entity-edge/<fact-uuid>        # one fact
curl -X DELETE localhost:9000/entity-edge/<uuid>   # delete a fact, keeping its episode
curl -X DELETE localhost:9000/episode/<uuid>       # delete an episode and what only it taught
curl -X DELETE localhost:9000/group/demo           # clear one partition
curl -X POST   localhost:9000/clear                # clear every partition
```

The same capabilities are exposed as agent tools at `/mcp`. Both surfaces match the original
verbatim — paths, field names, status codes and message strings are contract, not naming.

### Testing

```bash
mvn test                          # domain rules, surface contract, agents, ingest
mvn verify                        # adds the store-backed projection and retrieval tests
mvn test -Dtest=BenchmarkRunner   # cross-language parity and timings
```

118 tests. Tests needing the store **skip rather than fail** when none is reachable, so a green run
is not by itself proof the store paths ran — read the skip count. The model is stubbed everywhere:
a test that calls a real model measures the model's mood as much as the code.

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `OPENAI_API_KEY` | none | Extraction and embeddings. Unset means both degrade rather than fail — the service runs and learns nothing. |
| `MEMORY_STORE_URL` | `http://127.0.0.1:8090` | Where the store is. |
| `MEMORY_STORE_LEDGER` | `memory` | Which ledger to write. |
| `MEMORY_AUTH_TOKEN` | none | Optional bearer token. **Unset means no authentication**, which is what keeps a default build caller-compatible with the original. Setting it is a deliberate divergence. |

---

## Divergences from getzep/graphiti

Behaviour that differs, and why. Everything not listed here is reproduced, including the defects.

| Behaviour | getzep/graphiti | This port | Decided in |
|---|---|---|---|
| Graph, vector and full-text storage | four interchangeable backends | one store | D-001 |
| Concurrent ingest within a partition | no deterministic behaviour | serialised by construction | RENDER-001 §3.5 |
| Acknowledgement | after an in-memory enqueue | after a durable write, ~90 ms slower | budget report |
| Bulk ingest | present | not implemented | slice, `docs/slice.md` |
| Community detection | present | not implemented; the agent tool reports so | slice |
| Cross-encoder reranking | constructed, never selected | absent | question log 41 |
| Authentication | none | optional, off by default | D-008 |
| Transaction time | read from the graph's own history | explicit fact metadata | RENDER-001 §4, corrected |

The last row is a correction to the specification, not to the original: FlureeDB accepts a
query-at-an-earlier-commit parameter and silently ignores it, so a design that trusted it would
answer every historical question with current data.

Every reproduced defect and its Phase 2 correction flag is in
[`docs/correction-flags.md`](docs/correction-flags.md).

---

## Licence

`getzep/graphiti` is Apache-2.0, © 2024 Zep Software, Inc. This port is a derived work and ships
eight prompt files copied verbatim, so it is **Apache-2.0** too. See
[`ACKNOWLEDGEMENTS.md`](ACKNOWLEDGEMENTS.md).
