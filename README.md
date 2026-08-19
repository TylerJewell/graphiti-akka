# graphiti-akka

Tell it things. It remembers them, and when you later tell it something that contradicts what it
already knew, it works out when the old thing stopped being true and records that — instead of
overwriting it. You can still ask what it believed last year.

A port of [getzep/graphiti](https://github.com/getzep/graphiti) onto **Akka**, built with
**Akka Specify**.

---

## Where it came from

`getzep/graphiti` is a Python library that turns a stream of messages into a memory an AI assistant
can search. It was rebuilt here to find out how precisely a system has to be written down before it
can be rebuilt on a different stack.

Those written specifications live in a separate repository, `akka-specify-harness`, under
`graphiti-port/`. It is private for now.

---

## getzep/graphiti → this port

📉 11,387 Python lines → **2,411 Java lines**<br>
📁 37 files → **26 files**<br>
⚡ 1,107 → **69** nanoseconds, deciding whether one fact replaces another<br>
⚡ 2,917,079 → **938,176** nanoseconds, deciding whether two names mean the same thing<br>
⚡ 93,734 → **14,390** nanoseconds, merging search results into one order<br>
🎯 3 of 3 calculations give identical answers<br>
🖥️ 2 programs running → **2 programs running**<br>
💾 not measured → **425 MB** of memory<br>
🚀 not measured → **3.55 seconds** to start<br>
🔌 4 databases supported → **1**

The original needs a graph database and a paid model account before it will start, so its startup
time and memory use were never observed.

Every number here is reproduced by `mvn test -Dtest=BenchmarkRunner`, which writes
`target/bench-java.json`. The full method and the numbers that did not make the list are in
`graphiti-port/bench/REPORT.md` in the specifications repository.

---

## What it took to build

⏱️ **20.9 hours** from the first command to the published repository, **5.6** of them active<br>
💬 **1,757** exchanges with the model<br>
✍️ **2.0M** tokens written by the model, **824M** counting everything sent and re-sent<br>
🙋 **8** questions to a human<br>
🧪 **126** tests

The written figure is the one that tracks effort; the larger one is mostly the same context
being read again on every exchange.

Read from the session transcript on 2026-08-19. The record of every question asked and where the
time went is in the specifications repository.

---

## What it does

- **A newer, conflicting fact ends the older one instead of deleting it.** If Ana joined Acme in
  March and Globex in July, the Acme fact is marked as having stopped being true in July. Ask about
  April and you still get Acme.
- **It keeps "when it was true" separate from "when we found out".** Something learned in March
  about January is recorded as true from January, and as unknown to the service until March. Each
  question gives a different answer.
- **Two facts that start at the exact same moment both stay.** Neither ends the other.
- **A fact with no date does nothing.** It cannot end another fact and nothing can end it.
- **Deciding whether two names mean the same person runs through checks, cheapest first.** Look for
  things it might be, then an exact name match, then a near-match on spelling, and only then ask the
  model. Two identical names are never merged unless the first step found one while looking for the
  other.
- **It says "got it" before it does the work.** Only the date is checked while you wait. Anything
  else that goes wrong happens afterwards, quietly.

---

## Design decisions

**Event-sourced entities.** Instead of keeping only the newest version of everything, the service
writes down each change as it happens and works out the current state by replaying that list. If it
is restarted or something breaks halfway through, it can rebuild exactly where it was, and anyone
can see how it got there rather than having to trust it.

**One group at a time.** Everything about a single group of messages is handled by one worker in
order, so two facts about the same person can never be decided at the same instant and end up
contradicting each other. Different groups run side by side at full speed, so being careful about
one topic never slows down any of the others.

**Command Query Responsibility Segregation.** Questions are answered from a separate up-to-date
copy, and never from the records the service writes into. A long write cannot make someone's search
slow, and a thousand people reading at once cannot get in the way of new information arriving.

**One database instead of four.** The original let you pick between four different databases, which
meant four versions of the same code to write and keep working forever. Choosing a single one that
can do all three jobs — how things connect, finding things that mean something similar, and finding
words — deleted about thirteen thousand lines that existed only to hide the differences between
them.

**A domain layer with no framework in it.** The decisions that actually matter — when a new fact
replaces an old one, whether two names mean the same person, how to put search results in order —
are ordinary code that does not know the database, the web server or the model exist. They can be
tested in a few milliseconds with nothing running, so most of the ways this could have gone wrong
were ruled out before the rest of it was built.

---

## Running it — the short path

You do not need Java, Maven, or the Akka command-line tool installed. Akka Specify installs them.

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

You will also need FlureeDB running, with a ledger called `memory`. See below.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once
- FlureeDB running on `127.0.0.1:8090`, with a ledger called `memory`
- A model provider key — `OPENAI_API_KEY` by default, or any other provider (see below)

### The database

```powershell
irm https://github.com/fluree/db/releases/latest/download/fluree-db-cli-installer.ps1 | iex
```

```bash
brew install fluree/tap/fluree          # macOS and Linux
```

Then start it and create the ledger the service writes to:

```bash
fluree server start --listen-addr 127.0.0.1:8090
curl -X POST localhost:8090/v1/fluree/create -H 'content-type: application/json' -d '{"ledger":"memory"}'
```

**The ledger is not created for you.** Without it, sending a message still replies `202`,
because the reply comes before the work; the write then fails in the background where nothing
is watching. `curl "localhost:9000/episodes/demo?last_n=5"` coming back empty after a `202` is
usually this.

Built and tested against Fluree 4.1.5.

### Start it

```bash
mvn compile
akka local run
```

It listens on **port 9000**.

### Try it

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

# Ask what it knows
curl -X POST localhost:9000/search -H 'content-type: application/json' \
  -d '{"group_ids": ["demo"], "query": "Where does Ana work?", "max_facts": 10}'
```

You get both facts back. The Acme one now has an end date of July.

### Everything else

```bash
curl "localhost:9000/episodes/demo?last_n=5"       # the messages it was sent, newest first
curl localhost:9000/entity-edge/<fact-id>          # one fact
curl -X DELETE localhost:9000/entity-edge/<id>     # delete a fact, keep the message it came from
curl -X DELETE localhost:9000/episode/<id>         # delete a message and anything only it taught
curl -X DELETE localhost:9000/group/demo           # empty one group
curl -X POST   localhost:9000/clear                # empty every group
```

All of it is also available to AI assistants at `/mcp`. The addresses, field names, reply codes and
wording of the replies match the original exactly, so anything already written to talk to the
original works here unchanged.

### Tests

```bash
mvn test                          # the rules, the two ways in, and sending a message end to end
mvn verify                        # adds the tests that need the database running
mvn test -Dtest=BenchmarkRunner   # compares answers and speed against the original
```

118 tests. The ones needing the database skip when it is not running, so check the skipped count.
No test calls a real model.

---

## Model providers

The four agents run on whichever provider you select. Nothing in the code is tied to one — set
`MODEL_PROVIDER` and the matching key, and restart.

```bash
export MODEL_PROVIDER=anthropic
export ANTHROPIC_API_KEY=...
```

Leave `MODEL_PROVIDER` unset to use OpenAI, which is what the original uses.

### Hosted providers

| `MODEL_PROVIDER` | Variables to set | Default model |
|---|---|---|
| `openai` *(default)* | `OPENAI_API_KEY` | `gpt-5.5` |
| `anthropic` | `ANTHROPIC_API_KEY` | `claude-sonnet-5` |
| `googleai-gemini` | `GOOGLE_AI_GEMINI_API_KEY` | `gemini-2.5-flash` |
| `mistral-ai` | `MISTRAL_AI_API_KEY`, `MODEL_NAME` | none — set `MODEL_NAME` |
| `vertex-ai` | `VERTEX_AI_API_KEY`, `VERTEX_AI_PROJECT_ID`, `VERTEX_AI_LOCATION`, `MODEL_NAME` | none — set `MODEL_NAME` |
| `azure-openai` | `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_DEPLOYMENT` | set by deployment |
| `bedrock` | `AWS_REGION`, `BEDROCK_MODEL_ID`, plus your usual AWS credentials | set by `BEDROCK_MODEL_ID` |
| `hugging-face` | `HUGGING_FACE_ACCESS_TOKEN`, `HUGGING_FACE_MODEL_ID` | set by `HUGGING_FACE_MODEL_ID` |

### Local providers

No key required — only a reachable address.

| `MODEL_PROVIDER` | Variables to set | Default address |
|---|---|---|
| `ollama` | `MODEL_NAME`, optionally `OLLAMA_BASE_URL` | `http://localhost:11434` |
| `local-ai` | `MODEL_NAME`, optionally `LOCAL_AI_BASE_URL` | `http://localhost:8080/v1` |

### Two models, not one

Three agents use the main model. Writing entity summaries uses a smaller one, because the original
asks for a reduced model at exactly that step. Each provider has a smaller twin, chosen for you
when you choose the provider:

| `MODEL_PROVIDER` | Main | Smaller |
|---|---|---|
| `openai` | `gpt-5.5` | `gpt-4.1-nano` |
| `anthropic` | `claude-sonnet-5` | `claude-haiku-4-5` |
| `googleai-gemini` | `gemini-2.5-flash` | `gemini-2.5-flash-lite` |

`MODEL_NAME` overrides the main model and `SMALL_MODEL_NAME` the smaller one, so changing model and
changing provider are independent:

```bash
export MODEL_PROVIDER=anthropic
export ANTHROPIC_API_KEY=...
export MODEL_NAME=claude-opus-5
```

For the providers with no default model, set `MODEL_NAME` and `SMALL_MODEL_NAME` to models the
account can reach. Provider settings live in `src/main/resources/application.conf` if you want to
pin a model rather than pass an environment variable.

### Turning text into numbers

Searching by meaning needs a second account, set separately. Anthropic and Bedrock serve chat models
but no embeddings at all, so joining the two would mean picking a chat provider silently decided
whether search works.

| Variable | Default | What happens |
|---|---|---|
| `EMBEDDING_API_KEY` | falls back to `OPENAI_API_KEY` | Without either, search still answers — using word matching alone, and it says so. |
| `EMBEDDING_BASE_URL` | `https://api.openai.com` | Any address speaking the OpenAI embeddings shape, which includes Azure OpenAI, LocalAI, Ollama and most gateways. |
| `EMBEDDING_MODEL_NAME` | `text-embedding-3-small` | The original's model. |
| `EMBEDDING_DIMENSIONS` | `1024` | The original's width. Change it and stored vectors from before the change stop matching. |

---

## Settings

| Variable | Default | What happens |
|---|---|---|
| `MEMORY_STORE_URL` | `http://127.0.0.1:8090` | Where the database is. |
| `MEMORY_STORE_LEDGER` | `memory` | Which collection to write to. |
| `MEMORY_AUTH_TOKEN` | none | An optional password on every request. Without it there is no password, which is what lets anything written for the original keep working. |

---

## Where it differs from getzep/graphiti

Everything not listed here was copied on purpose, including the parts that look like mistakes.

| | getzep/graphiti | This port |
|---|---|---|
| Storage | four databases to choose from | one |
| Two messages in one group at once | no predictable result | handled one at a time |
| Replying "got it" | after putting the work in memory | after writing it down, about 90 ms slower |
| Loading many messages at once | yes | not built |
| Grouping related things into clusters | yes | not built; the tool says so when asked |
| A second pass to re-order search results | written, never switched on | left out |
| Password on requests | none | optional, off by default |
| Recording when the service learned something | read from the database's own history | written down explicitly |

---

## Licence

`getzep/graphiti` is Apache-2.0, © 2024 Zep Software, Inc. This port is built from it and includes
eight instruction files copied word for word, so it is **Apache-2.0** as well. See
[`ACKNOWLEDGEMENTS.md`](ACKNOWLEDGEMENTS.md).
